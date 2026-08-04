import { useEffect, useRef, useState } from "react";

const DEFAULT_NAMES = {
  FIRST_COME: "여름특가-선착순",
  ADMIN_ISSUED: "여름특가-관리자발급"
};

export default function ControlDesk({
  loggedIn,
  onLogin,
  coupons,
  currentCouponId,
  couponName,
  totalQty,
  onCreateCoupon,
  onSelectCoupon,
  issuedRows,
  issuedQty,
  batchStatus,
  couponStatus,
  redisRemainingQty,
  onRefreshCoupons,
  onRefreshSummary
}) {
  const [loginId, setLoginId] = useState("admin@test.com");
  const [loginPw, setLoginPw] = useState("123");
  const [couponNameInput, setCouponName] = useState(DEFAULT_NAMES.FIRST_COME);
  const [couponQty, setCouponQty] = useState(100);
  const [couponIssueType, setCouponIssueType] = useState("FIRST_COME");
  const prevTypeRef = useRef("FIRST_COME");

  useEffect(() => {
    const prevType = prevTypeRef.current;
    if (!couponNameInput || couponNameInput === DEFAULT_NAMES[prevType]) {
      setCouponName(DEFAULT_NAMES[couponIssueType]);
    }
    prevTypeRef.current = couponIssueType;
  }, [couponIssueType]);

  const batchBadgeClass =
    batchStatus === "DONE"
      ? "status-completed"
      : batchStatus === "PROCESSING"
        ? "status-running"
        : "status-none";

  return (
    <section className="view">
      <div className="view-head">
        <div className="view-eyebrow">01 · Overview</div>
        <h1 className="view-title">검증형 운영화면</h1>
        <p className="view-desc">
          관리자 로그인, 기준 쿠폰 생성, 현재 상태 확인을 위한 화면입니다.
        </p>
      </div>

      <div className="stat-row">
        <div className="stat-card">
          <div className="stat-label">기준 쿠폰 ID</div>
          <div className="stat-value">{currentCouponId || "—"}</div>
          <div className="stat-sub">현재 테스트 대상</div>
        </div>
        <div className="stat-card">
          <div className="stat-label">쿠폰명</div>
          <div className="stat-value stat-value-compact">{couponName || "—"}</div>
          <div className="stat-sub">summary 기준</div>
        </div>
        <div className="stat-card">
          <div className="stat-label">전체 수량</div>
          <div className="stat-value">{totalQty ?? "—"}</div>
          <div className="stat-sub">coupon.totalQuantity</div>
        </div>
        <div className="stat-card">
          <div className="stat-label">발급 row 수</div>
          <div className="stat-value">{issuedRows}</div>
          <div className="stat-sub">coupon_issue 기준</div>
        </div>
        <div className="stat-card">
          <div className="stat-label">issued_quantity</div>
          <div className="stat-value">{issuedQty}</div>
          <div className="stat-sub">coupon 기준</div>
        </div>
        <div className="stat-card">
          <div className="stat-label">coupon status</div>
          <div className="stat-value">
            <span className={`badge ${couponStatus === "ACTIVE" ? "status-completed" : "status-none"}`}>
              {couponStatus || "—"}
            </span>
          </div>
          <div className="stat-sub">coupon.status</div>
        </div>
        <div className="stat-card">
          <div className="stat-label">redis 잔여 재고</div>
          <div className="stat-value">{redisRemainingQty ?? "—"}</div>
          <div className="stat-sub">coupon:stock:{'{id}'}</div>
        </div>
        <div className="stat-card">
          <div className="stat-label">batch status</div>
          <div className="stat-value">
            <span className={`badge ${batchBadgeClass}`}>{batchStatus || "NONE"}</span>
          </div>
          <div className="stat-sub">최근 배치 작업</div>
        </div>
      </div>

      <div className="grid-2">
        <div className="card">
          <div className="card-title">
            관리자 로그인 <span className="tag">POST /api/members/login</span>
          </div>
          <div className="field-row">
            <div className="field">
              <label>Admin ID</label>
              <input value={loginId} onChange={(e) => setLoginId(e.target.value)} />
            </div>
            <div className="field">
              <label>Password</label>
              <input type="password" value={loginPw} onChange={(e) => setLoginPw(e.target.value)} />
            </div>
          </div>
          <button className="btn btn-primary btn-block" onClick={() => onLogin({ id: loginId, password: loginPw })}>
            {loggedIn ? "다시 로그인" : "로그인"}
          </button>
        </div>

        <div className="card">
          <div className="card-title">
            기준 쿠폰 생성 <span className="tag">POST /api/coupons</span>
          </div>
          <div className="field">
            <label>쿠폰명</label>
            <input value={couponNameInput} onChange={(e) => setCouponName(e.target.value)} />
          </div>
          <div className="field-row">
            <div className="field">
              <label>쿠폰 유형</label>
              <select value={couponIssueType} onChange={(e) => setCouponIssueType(e.target.value)}>
                <option value="FIRST_COME">선착순 쿠폰</option>
                <option value="ADMIN_ISSUED">관리자 발급 쿠폰</option>
              </select>
            </div>
            <div className="field">
              <label>수량</label>
              <input
                type="number"
                value={couponIssueType === "FIRST_COME" ? couponQty : ""}
                onChange={(e) => setCouponQty(Number(e.target.value))}
                disabled={couponIssueType !== "FIRST_COME"}
                placeholder={couponIssueType === "FIRST_COME" ? "100" : "사용 안 함"}
              />
            </div>
          </div>
          <div className="lab-note" style={{ marginTop: -4, marginBottom: 12 }}>
            관리자 발급 쿠폰은 대상 유저 목록 기준으로 배치 발급하므로 수량을 따로 쓰지 않습니다.
          </div>
          <button
            className="btn btn-primary btn-block"
            onClick={() => onCreateCoupon({ name: couponNameInput, qty: couponQty, issueType: couponIssueType })}
          >
            쿠폰 생성
          </button>
        </div>
      </div>

      <div className="card" style={{ marginTop: 16 }}>
        <div className="card-title">
          생성된 쿠폰 목록 <span className="tag">클릭 시 기준 쿠폰으로 설정</span>
        </div>
        <div className="action-row">
          <button className="btn btn-ghost" onClick={onRefreshCoupons}>목록 새로고침</button>
          <button className="btn btn-ghost" onClick={onRefreshSummary} disabled={!currentCouponId}>기준 쿠폰 다시 조회</button>
        </div>
        <table>
          <thead>
            <tr>
              <th>Coupon ID</th>
              <th>쿠폰명</th>
              <th>유형</th>
              <th>수량</th>
              <th>생성 시각</th>
              <th>선택</th>
            </tr>
          </thead>
          <tbody>
            {coupons.length === 0 ? (
              <tr className="empty-row">
                <td colSpan={6}>아직 생성된 쿠폰이 없습니다.</td>
              </tr>
            ) : (
              coupons.map((c) => (
                <tr
                  key={c.id}
                  className={`row-selectable ${currentCouponId === c.id ? "row-active" : ""}`}
                  onClick={() => onSelectCoupon(c.id)}
                >
                  <td>{c.id}</td>
                  <td style={{ fontFamily: "var(--sans)", color: "var(--text-1)" }}>{c.name}</td>
                  <td>{c.issueType === "FIRST_COME" ? "선착순" : "관리자 발급"}</td>
                  <td>{c.qty}</td>
                  <td>{c.createdAt}</td>
                  <td>
                    {currentCouponId === c.id ? (
                      <span className="badge status-completed">기준</span>
                    ) : (
                      <span className="badge status-none">선택</span>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}
