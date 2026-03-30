CREATE TABLE member
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    email      VARCHAR(255) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    name       VARCHAR(255) NOT NULL,
    birthdate  DATE         NOT NULL,
    phone      VARCHAR(255) NOT NULL,
    role       VARCHAR(10)  NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_member_email (email)
);

CREATE TABLE coupon
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    name             VARCHAR(255) NOT NULL,
    discount_type    VARCHAR(10)  NOT NULL,
    discount_value   INT          NOT NULL,
    total_quantity   INT,
    issued_quantity  INT          NOT NULL DEFAULT 0,
    min_order_amount INT,
    issue_type       VARCHAR(20)  NOT NULL,
    issue_start_time VARCHAR(10),
    issue_end_time   VARCHAR(10),
    expired_at       DATETIME(6)  NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE coupon_issue
(
    id        BIGINT      NOT NULL AUTO_INCREMENT,
    coupon_id BIGINT      NOT NULL,
    user_id   BIGINT      NOT NULL,
    status    VARCHAR(10) NOT NULL,
    issued_at DATETIME(6) NOT NULL,
    used_at   DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_coupon_issue_user_coupon (user_id, coupon_id),
    INDEX idx_issued_at_status (issued_at, status)
);

CREATE TABLE coupon_issue_batch
(
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    coupon_id    BIGINT      NOT NULL,
    target_count INT         NOT NULL,
    status       VARCHAR(20) NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6),
    PRIMARY KEY (id)
);