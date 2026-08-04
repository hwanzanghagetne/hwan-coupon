import { useState } from "react";
import Topbar from "./components/Topbar";
import Sidebar from "./components/Sidebar";
import ConsoleDrawer from "./components/ConsoleDrawer";
import ControlDesk from "./components/ControlDesk";
import FirstComeLab from "./components/FirstComeLab";
import BatchLab from "./components/BatchLab";
import { loginAdmin, createCouponApi, listCouponsApi, runFirstComeApi, runBatchApi, lookupBatchApi, getCouponSummaryApi } from "./api";
import "./styles.css";

export default function App() {
  const [activeView, setActiveView] = useState("control");
  const [consoleOpen, setConsoleOpen] = useState(false);
  const [consoleEndpoint, setConsoleEndpoint] = useState("");
  const [consoleData, setConsoleData] = useState(null);

  const [loggedIn, setLoggedIn] = useState(false);
  const [adminId, setAdminId] = useState("");

  const [coupons, setCoupons] = useState([]);
  const [currentCouponId, setCurrentCouponId] = useState(null);
  const [currentCouponIssueType, setCurrentCouponIssueType] = useState(null);
  const [couponName, setCouponName] = useState(null);
  const [totalQty, setTotalQty] = useState(null);
  const [issuedRows, setIssuedRows] = useState(0);
  const [issuedQty, setIssuedQty] = useState(0);
  const [batchStatus, setBatchStatus] = useState(null);
  const [couponStatus, setCouponStatus] = useState(null);
  const [redisRemainingQty, setRedisRemainingQty] = useState(null);

  const toggleConsole = (forceOpen) => setConsoleOpen(forceOpen === true ? true : (v) => !v);

  const logConsole = (endpoint, data) => {
    setConsoleEndpoint(endpoint);
    setConsoleData(data);
    setConsoleOpen(true);
  };

  const applyCouponSummary = (summary) => {
    setCurrentCouponId(summary.couponId);
    setCurrentCouponIssueType(summary.issueType ?? null);
    setCouponName(summary.couponName ?? null);
    setTotalQty(summary.totalQuantity ?? null);
    setIssuedRows(summary.couponIssueCount ?? 0);
    setIssuedQty(summary.issuedQuantity ?? 0);
    setCouponStatus(summary.couponStatus ?? null);
    setRedisRemainingQty(summary.redisRemainingQuantity ?? null);
  };

  const handleLogin = async ({ id, password }) => {
    const res = await loginAdmin({ id, password });
    setLoggedIn(true);
    setAdminId(res.admin);
    logConsole("POST /api/members/login", res);
  };

  const handleCreateCoupon = async ({ name, qty, issueType }) => {
    const res = await createCouponApi({ name, qty, issueType });
    const coupon = { id: res.couponId, name: res.name, qty: res.quantity ?? "-", issueType: res.issueType, createdAt: res.createdAt };
    setCoupons((prev) => [coupon, ...prev]);
    logConsole("POST /api/coupons", res);
    await handleSelectCoupon(coupon.id, false);
  };

  const refreshCouponSummary = async (couponId, shouldLog = true) => {
    if (!couponId) return null;
    try {
      const summary = await getCouponSummaryApi(couponId);
      applyCouponSummary(summary);
      if (shouldLog) {
        logConsole(`GET /api/demo/summary?couponId=${couponId}`, summary);
      }
      return summary;
    } catch (error) {
      const payload = error.payload ?? { status: error.status, message: error.message };
      if (shouldLog) {
        logConsole(`GET /api/demo/summary?couponId=${couponId}`, payload);
      }
      return null;
    }
  };

  const handleSelectCoupon = async (id, shouldLog = true) => {
    setCurrentCouponId(id);
    setCurrentCouponIssueType(null);
    setCouponName(null);
    setTotalQty(null);
    setIssuedRows(0);
    setIssuedQty(0);
    setCouponStatus(null);
    setRedisRemainingQty(null);
    await refreshCouponSummary(id, shouldLog);
  };

  const handleRefreshCoupons = async () => {
    const rows = await listCouponsApi();
    setCoupons(rows);
    logConsole("GET /api/coupons", rows);
  };

  const handleRunFirstCome = async ({ couponId, concurrency, threads }) => {
    const res = await runFirstComeApi({ couponId, concurrency, threads });
    setCurrentCouponId(res.couponId);
    setIssuedRows(res.result.issuedRows);
    setIssuedQty(res.result.issuedQuantity);
    logConsole("POST /api/demo/first-come", res);
    await refreshCouponSummary(res.couponId, false);
    return res;
  };

  const handleRunBatch = async ({ couponId, users }) => {
    setBatchStatus("PROCESSING");
    const res = await runBatchApi({ couponId, users });
    logConsole("POST /api/demo/batch", res);

    let merged = res;
    if (res.batchId) {
      try {
        const summary = await lookupBatchApi(res.batchId);
        merged = { ...res, ...summary, durationMs: res.durationMs };
        logConsole(`GET /api/demo/batch-summary?batchId=${res.batchId}`, merged);
      } catch (error) {
        const payload = error.payload ?? { status: error.status, message: error.message };
        logConsole(`GET /api/demo/batch-summary?batchId=${res.batchId}`, payload);
      }
    }

    if (merged.couponId) {
      await refreshCouponSummary(merged.couponId, false);
    }
    setBatchStatus(merged.batchStatus ?? res.batchStatus ?? null);
    return merged;
  };

  const handleLookupBatch = async (batchId) => {
    try {
      const res = await lookupBatchApi(batchId);
      setBatchStatus(res.batchStatus);
      logConsole(`GET /api/demo/batch-summary?batchId=${batchId}`, res);
      if (res.couponId) {
        await refreshCouponSummary(res.couponId, false);
      }
      return res;
    } catch (error) {
      const payload = error.payload ?? { status: error.status, message: error.message };
      logConsole(`GET /api/demo/batch-summary?batchId=${batchId}`, payload);
      return payload;
    }
  };

  return (
    <div className="app">
      <Topbar
        loggedIn={loggedIn}
        adminId={adminId}
        currentCouponId={currentCouponId}
        batchStatus={batchStatus}
        onToggleConsole={toggleConsole}
      />
      <div className="body">
        <Sidebar activeView={activeView} onChangeView={setActiveView} onToggleConsole={toggleConsole} />
        <main className="content">
          {activeView === "control" && (
            <ControlDesk
              loggedIn={loggedIn}
              onLogin={handleLogin}
              coupons={coupons}
              currentCouponId={currentCouponId}
              couponName={couponName}
              totalQty={totalQty}
              onCreateCoupon={handleCreateCoupon}
              onSelectCoupon={handleSelectCoupon}
              issuedRows={issuedRows}
              issuedQty={issuedQty}
              batchStatus={batchStatus}
              couponStatus={couponStatus}
              redisRemainingQty={redisRemainingQty}
              onRefreshCoupons={handleRefreshCoupons}
              onRefreshSummary={() => refreshCouponSummary(currentCouponId)}
            />
          )}
          {activeView === "firstcome" && (
            <FirstComeLab currentCouponId={currentCouponId} onRun={handleRunFirstCome} />
          )}
          {activeView === "batch" && (
            <BatchLab
              currentCouponId={currentCouponId}
              currentCouponIssueType={currentCouponIssueType}
              onRunBatch={handleRunBatch}
              onLookupBatch={handleLookupBatch}
            />
          )}
        </main>
      </div>
      <ConsoleDrawer open={consoleOpen} onToggle={toggleConsole} endpoint={consoleEndpoint} data={consoleData} />
    </div>
  );
}
