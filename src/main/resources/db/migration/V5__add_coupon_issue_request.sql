-- =====================================================================
-- 선착순 발급 요청 상태 테이블 (큐 기반 비동기 처리)
--   Redis 당첨 판정 직후 DB에 바로 쓰지 않고 이 테이블에 PENDING으로
--   기록한 뒤 RabbitMQ에 발행한다. 컨슈머(FirstComeIssueProcessor)가
--   메시지를 순차적으로 소비해 SUCCESS/FAILED로 갱신한다.
--   당첨자 100명이 동시에 DB에 쓰던 기존 구조의 데드락을, 동시 쓰기 자체를
--   없애서 구조적으로 제거하기 위한 설계.
-- =====================================================================

CREATE TABLE coupon_issue_request (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    coupon_id      BIGINT NOT NULL,
    user_id        BIGINT NOT NULL,
    status         VARCHAR(20) NOT NULL,
    requested_at   DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    completed_at   DATETIME(6) NULL,
    failure_reason VARCHAR(255) NULL,
    CONSTRAINT fk_coupon_issue_request_coupon
        FOREIGN KEY (coupon_id) REFERENCES coupon (id),
    CONSTRAINT fk_coupon_issue_request_member
        FOREIGN KEY (user_id) REFERENCES member (id),
    INDEX idx_coupon_issue_request_coupon_id (coupon_id),
    INDEX idx_coupon_issue_request_status (status)
);
