-- =====================================================================
-- FK 제약조건 추가
--   coupon_issue, coupon_issue_batch 생성 시 FK를 누락했던 부분을 보완.
--   참조 무결성을 DB 레벨에서도 보장하여 애플리케이션 레벨 방어와 이중화.
-- =====================================================================

ALTER TABLE coupon_issue
    ADD CONSTRAINT fk_coupon_issue_coupon
        FOREIGN KEY (coupon_id) REFERENCES coupon (id),
    ADD CONSTRAINT fk_coupon_issue_member
        FOREIGN KEY (user_id) REFERENCES member (id);

ALTER TABLE coupon_issue_batch
    ADD CONSTRAINT fk_coupon_issue_batch_coupon
        FOREIGN KEY (coupon_id) REFERENCES coupon (id);

-- =====================================================================
-- 인덱스 추가
--   각 인덱스는 실제 조회 패턴 기준으로 설계.
-- =====================================================================

-- coupon: CouponExpiryScheduler의 "WHERE status = ? AND expired_at < ?" 패턴
--   status 등호 조건을 선행, expired_at 범위 조건을 후행으로 배치
ALTER TABLE coupon
    ADD INDEX idx_coupon_status_expired_at (status, expired_at);

-- coupon_issue: countByCouponId, deleteByCouponId, expireIssuedByCouponIds 등
--   coupon_id 단독 조회가 여러 곳에서 발생하나 기존 유니크 키가 (user_id, coupon_id) 순서라
--   coupon_id 선행 인덱스가 없어 풀스캔 발생 → 단독 인덱스 추가
ALTER TABLE coupon_issue
    ADD INDEX idx_coupon_issue_coupon_id (coupon_id);

-- coupon_issue_batch: BatchRecoveryScheduler의 "WHERE status = ? AND requested_at < ?" 패턴
--   status 등호 조건을 선행, requested_at 범위 조건을 후행으로 배치
ALTER TABLE coupon_issue_batch
    ADD INDEX idx_batch_status_requested_at (status, requested_at);

-- =====================================================================
-- 컬럼 길이 조정
--   도메인 특성을 반영하지 않은 VARCHAR(255) 기본값을 현실적인 길이로 수정.
-- =====================================================================

ALTER TABLE member
    MODIFY COLUMN phone VARCHAR(20)  NOT NULL,
    MODIFY COLUMN name  VARCHAR(100) NOT NULL;
