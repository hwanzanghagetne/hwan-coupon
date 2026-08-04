import { useState } from "react";

export default function FirstComeLab({ currentCouponId, onRun }) {
  const [concurrency, setConcurrency] = useState(500);
  const [threads, setThreads] = useState(32);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);

  const handleRun = async () => {
    if (!currentCouponId) {
      alert("먼저 기준 쿠폰을 선택하세요.");
      return;
    }

    setLoading(true);
    const res = await onRun({ couponId: currentCouponId, concurrency, threads });
    setResult(res.result);
    setLoading(false);
  };

  return (
    <section className="view">
      <div className="view-head">
        <div className="view-eyebrow">02 · 선착순 검증</div>
        <h1 className="view-title">선착순 쿠폰</h1>
        <p className="view-desc">
          선택한 기준 쿠폰으로 동시 요청 상황을 검증하는 화면입니다.
        </p>
      </div>

      <div className="grid-2">
        <div className="card">
          <div className="card-title">
            실행 파라미터 <span className="tag">POST /api/demo/first-come</span>
          </div>
          <div className="field">
            <label>대상 쿠폰 ID</label>
            <input readOnly value={currentCouponId || "—"} />
          </div>
          <div className="field">
            <label>동시 요청 수</label>
            <input type="number" value={concurrency} onChange={(e) => setConcurrency(Number(e.target.value))} />
          </div>
          <div className="field">
            <label>실행 스레드 수</label>
            <input type="number" value={threads} onChange={(e) => setThreads(Number(e.target.value))} />
          </div>
          <button className="btn btn-primary btn-block" disabled={loading || !currentCouponId} onClick={handleRun}>
            {loading ? (<><span className="spinner" /> 실행 중...</>) : ("선착순 검증 실행")}
          </button>
          <p className="lab-note">
            기준 쿠폰을 먼저 선택한 뒤 실행하세요.
          </p>
        </div>

        <div className="card">
          <div className="card-title">
            내부 검증 결과 <span className="tag">{result ? `${result.durationMs}ms` : "demo"}</span>
          </div>
          {!result ? (
            <div className="placeholder-result">
              아직 실행 결과가 없습니다.
              <br />
              왼쪽에서 조건을 입력하고 실행하세요.
            </div>
          ) : (
            <div className="result-grid result-grid-wide">
              <ResultCell label="발급 성공" value={result.successCount} tone="good" />
              <ResultCell label="재고 소진" value={result.exhaustedCount} tone="warn" />
              <ResultCell label="중복 발급" value={result.duplicateCount} tone="bad" />
              <ResultCell label="기타 실패" value={result.otherFailureCount} tone="bad" />
              <ResultCell label="발급 row 수" value={result.issuedRows} tone="neutral" />
              <ResultCell label="issued_quantity" value={result.issuedQuantity} tone="neutral" />
              <ResultCell label="실행 시간(ms)" value={result.durationMs} tone="neutral" />
            </div>
          )}
        </div>
      </div>
    </section>
  );
}

function ResultCell({ label, value, tone }) {
  return (
    <div className="result-cell">
      <div className="k">{label}</div>
      <div className={`v ${tone}`}>{value}</div>
    </div>
  );
}
