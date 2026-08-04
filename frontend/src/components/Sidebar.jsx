const NAV_ITEMS = [
  { key: "control", label: "운영 화면", icon: "◩" },
  { key: "firstcome", label: "선착순 쿠폰", icon: "⚡" },
  { key: "batch", label: "대량 발급", icon: "▤" }
];

export default function Sidebar({ activeView, onChangeView, onToggleConsole }) {
  return (
    <nav className="sidebar">
      <div className="nav-label">검증 메뉴</div>
      {NAV_ITEMS.map((item) => (
        <button
          key={item.key}
          className={`nav-item ${activeView === item.key ? "active" : ""}`}
          onClick={() => onChangeView(item.key)}
        >
          <span className="nav-ico">{item.icon}</span> {item.label}
        </button>
      ))}
      <div className="sidebar-foot">
        <button className="nav-item" onClick={() => onToggleConsole()}>
          <span className="nav-ico">›_</span> 콘솔 <span className="nav-sub">JSON</span>
        </button>
      </div>
    </nav>
  );
}
