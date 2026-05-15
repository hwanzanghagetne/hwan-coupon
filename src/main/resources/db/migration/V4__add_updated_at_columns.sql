-- =====================================================================
-- updated_at 컬럼 추가
--   coupon: status(ACTIVE→EXHAUSTED/INACTIVE)와 issued_quantity가 지속적으로 변하므로
--           마지막 변경 시각 추적이 필요
--   coupon_issue_batch: status 전이(PENDING→PROCESSING→DONE/FAILED)마다
--                       변경 시각을 기록해 운영 추적성 확보
-- =====================================================================

ALTER TABLE coupon
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT '1970-01-01 00:00:00.000000'
        AFTER created_at;

-- 기존 row backfill: created_at 기준으로 초기화
UPDATE coupon SET updated_at = created_at;

ALTER TABLE coupon_issue_batch
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT '1970-01-01 00:00:00.000000'
        AFTER requested_at;

-- 기존 row backfill: requested_at 기준으로 초기화
UPDATE coupon_issue_batch SET updated_at = requested_at;
