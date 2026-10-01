-- =====================================================================
-- BatchRecoveryScheduler의 고착 판단 기준이 requested_at에서 updated_at으로
--   바뀌었는데(V4), 인덱스는 V3의 (status, requested_at) 그대로 남아 있었다.
--   조회 컬럼과 인덱스 컬럼을 맞춘다.
-- =====================================================================

ALTER TABLE coupon_issue_batch
    DROP INDEX idx_batch_status_requested_at,
    ADD INDEX idx_batch_status_updated_at (status, updated_at);
