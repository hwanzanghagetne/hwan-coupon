import { useState } from "react";

export default function BatchLab({ currentCouponId, currentCouponIssueType, onRunBatch, onLookupBatch }) {
  const [users, setUsers] = useState(10000);
  const [lookupId, setLookupId] = useState("");
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);

  const handleRun = async () => {
    if (!currentCouponId) {
      alert("먼저 기준 쿠폰을 선택하세요.");
      return;
    }
    if (currentCouponIssueType !== "ADMIN_ISSUED") {
      alert("관리자 발급 쿠폰을 선택하세요.");
      return;
    }

    setLoading(true);
    const res = await onRunBatch({ couponId: currentCouponId, users });
    setResult(res);
    setLookupId(String(res.batchId ?? ""));
    setLoading(false);
  };

  const handleLookup = async () => {
    if (!lookupId.trim()) return;
    const res = await onLookupBatch(lookupId.trim());
    if (res.status === 404) {
      alert("해당 batchId를 찾을 수 없습니다.");
      return;
    }
    setResult(res);
  };

  return (
    <section className="view">
      <div className="view-head">
        <div className="view-eyebrow">03 · 대량 발급 검증</div>
        <h1 className="view-title">대량 발급</h1>
        <p className="view-desc">
          선택한 관리자 발급 쿠폰으로 배치 요청을 보내고 상태를 확인하는 화면입니다.
        </p>
      </div>

      <div className="grid-2">
        <div className="card">
          <div className="card-title">
            대량 발급 실행 <span className="tag">POST /api/demo/batch</span>
          </div>
          <div className="field">
            <label>대상 쿠폰 ID</label>
            <input readOnly value={currentCouponId || "—"} />
          </div>
          <div className="field">
            <label>배치 유저 수</label>
            <input type="number" value={users} onChange={(e) => setUsers(Number(e.target.value))} />
          </div>
          <button className="btn btn-batch btn-block" disabled={loading || !currentCouponId} onClick={handleRun}>
            {loading ? (<><span className="spinner" /> 실행 중...</>) : ("대량 발급 실행")}
          </button>
          <p className="lab-note">
            관리자 발급 쿠폰을 선택한 뒤 실행하세요. 요청은 바로 끝나고 실제 처리는 배치 상태로 확인합니다.
          </p>

          <div style={{ height: 1, background: "var(--border-soft)", margin: "18px 0" }} />

          <div className="card-title" style={{ marginBottom: 10 }}>
            배치 상태 조회 <span className="tag">GET /api/demo/batch-summary?batchId=...</span>
          </div>
          <div className="field" style={{ marginBottom: 0 }}>
            <label>batchId</label>
            <input placeholder="1" value={lookupId} onChange={(e) => setLookupId(e.target.value)} />
          </div>
          <button className="btn btn-ghost btn-block" style={{ marginTop: 10 }} onClick={handleLookup}>
            조회
          </button>
        </div>

        <div className="card">
          <div className="card-title">
            실행 / 조회 결과 <span className="tag">{result?.durationMs ? `${result.durationMs}ms` : ""}</span>
          </div>
          {!result ? (
            <div className="placeholder-result">
              아직 결과가 없습니다.
              <br />
              배치를 실행하거나 batchId로 조회하세요.
            </div>
          ) : (
            <div className="result-grid result-grid-wide">
              <ResultCell label="batchId" value={result.batchId} />
              <ResultCell label="couponId" value={result.couponId ?? "—"} />
              <ResultCell label="batchStatus" value={result.batchStatus} />
              <ResultCell label="completed" value={String(result.completed)} />
              <ResultCell label="targetCount" value={result.targetCount ?? result.userCount ?? "—"} />
              <ResultCell label="issuedRows" value={result.issuedRows ?? 0} />
              <ResultCell label="issuedQuantity" value={result.issuedQuantity ?? 0} />
              <ResultCell label="completedAt" value={result.completedAt ?? "—"} />
              <ResultCell label="durationMs" value={result.durationMs ?? "—"} />
            </div>
          )}
        </div>
      </div>
    </section>
  );
}

function ResultCell({ label, value }) {
  return (
    <div className="result-cell">
      <div className="k">{label}</div>
      <div className="v neutral">{value}</div>
    </div>
  );
}
