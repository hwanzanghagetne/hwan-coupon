const concurrencyBench = [
  { method: "비관적 락", swatch: "var(--lock)", avg: 1375, p90: 4021, p95: 4476, oversold: 0, duplicate: 0 },
  { method: "Redis Lua Script", swatch: "var(--redis)", avg: 851, p90: 2350, p95: 2543, oversold: 0, duplicate: 0 }
];

export default function Benchmark() {
  return (
    <section className="view">
      <div className="view-head">
        <div className="view-eyebrow">04 · Evidence</div>
        <h1 className="view-title">기술 선택 근거</h1>
        <p className="view-desc">
          이 섹션은 "왜 이 기술을 썼는가"에 답하기 위한 요약 보드입니다.
          선착순은 동시성 제어 방식, 대량 발급은 저장/전달 방식 비교를 중심으로 정리합니다.
        </p>
      </div>

      <div className="card">
        <div className="card-title">선착순 발급 — 동시성 제어 방식 비교</div>
        <div className="bench-table-wrap">
          <table className="bench-table">
            <thead>
              <tr>
                <th>방식</th>
                <th>avg (ms)</th>
                <th>p90 (ms)</th>
                <th>p95 (ms)</th>
                <th>oversold</th>
                <th>duplicate issue</th>
              </tr>
            </thead>
            <tbody>
              {concurrencyBench.map((row) => (
                <tr key={row.method}>
                  <td><span className="swatch" style={{ background: row.swatch }} />{row.method}</td>
                  <td>{row.avg}</td>
                  <td>{row.p90}</td>
                  <td>{row.p95}</td>
                  <td><span className="badge status-none">{row.oversold}</span></td>
                  <td><span className="badge status-none">{row.duplicate}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="summary-box">
          <b>핵심 해석</b> — Redis Lua Script는 재고 확인과 중복 체크를 원자적으로 처리해,
          비관적 락보다 DB 경합을 크게 줄였습니다. 두 방식 모두 초과 발급과 중복 발급은 막았지만,
          고동시성 구간에서는 p90 지연 차이가 더 크게 벌어집니다.
        </div>
      </div>

      <div className="card" style={{ marginTop: 16 }}>
        <div className="card-title">대량 발급 — 저장 / 전달 방식 비교</div>
        <div className="compare-cards">
          <div className="compare-card">
            <div className="compare-vs">
              <span className="vs-pill">JPA saveAll</span>
              <span className="vs-x">VS</span>
              <span className="vs-pill">JdbcTemplate</span>
            </div>
            <p>
              10만 건 기준 JPA saveAll은 약 65초, JdbcTemplate 대량 insert는 약 2초가 걸렸습니다.
              대량 발급에서는 엔티티 편의성보다 bulk insert 성능이 훨씬 중요했습니다.
            </p>
          </div>
          <div className="compare-card">
            <div className="compare-vs">
              <span className="vs-pill">@Async</span>
              <span className="vs-x">VS</span>
              <span className="vs-pill">RabbitMQ</span>
            </div>
            <p>
              @Async는 구현은 단순하지만 JVM 메모리에 의존합니다. RabbitMQ는 큐 적재 후 처리되므로
              상태 추적, 재전달, 실패 분리 관점에서 운영적으로 더 납득 가능한 구조였습니다.
            </p>
          </div>
        </div>
      </div>
    </section>
  );
}