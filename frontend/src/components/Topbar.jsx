export default function Topbar({ loggedIn, adminId, currentCouponId, batchStatus, onToggleConsole }) {
  return (
    <header className="topbar">
      <div className="brand">
        <div className="brand-mark">H</div>
        <div className="brand-text">
          <b>Hwan Coupon Lab</b>
          <span>Backend Verification Dashboard</span>
        </div>
      </div>
      <div className="topbar-divider" />
      <div className="global-context">
        <div className="chip">
          <span className={`dot ${loggedIn ? "on" : "off"}`} />
          관리자 세션 <b>{loggedIn ? adminId : "미로그인"}</b>
        </div>
        <div className="chip">기준 쿠폰 <b>{currentCouponId || "—"}</b></div>
        <div className="chip">배치 상태 <b>{batchStatus || "—"}</b></div>
      </div>
      <div className="topbar-actions">
        <button className="btn-console-toggle" onClick={() => onToggleConsole(true)}>
          Console <span className="kbd">last response</span>
        </button>
      </div>
    </header>
  );
}