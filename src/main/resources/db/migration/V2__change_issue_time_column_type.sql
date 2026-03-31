-- issue_start_time, issue_end_time 컬럼을 VARCHAR(10)에서 TIME으로 변경
-- 주의: 운영 데이터가 있는 경우 기존 값이 "HH:mm" 형식인지 사전 확인 후 적용
ALTER TABLE coupon
    MODIFY COLUMN issue_start_time TIME,
    MODIFY COLUMN issue_end_time   TIME;