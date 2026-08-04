export default function ConsoleDrawer({ open, onToggle, endpoint, data }) {
  return (
    <div className={`console-drawer ${open ? "open" : ""}`}>
      <div className="console-head" onClick={() => onToggle()}>
        <span className="prompt">›_</span>
        <span className="title">Console</span>
        <span className="last-endpoint">{endpoint || "대기 중"}</span>
        <span className="spacer" />
        <span className="caret">▲</span>
      </div>
      <div className="console-body">
        {data ? (
          <pre>{JSON.stringify(data, null, 2)}</pre>
        ) : (
          <div className="console-empty">마지막으로 실행한 API 응답이 여기에 표시됩니다.</div>
        )}
      </div>
    </div>
  );
}
