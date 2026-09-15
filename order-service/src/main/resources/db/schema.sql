CREATE DATABASE IF NOT EXISTS ctrpc_order DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE ctrpc_order;

CREATE TABLE IF NOT EXISTS t_order (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    product_name  VARCHAR(128) NOT NULL,
    quantity      INT          NOT NULL DEFAULT 1,
    amount_cents  BIGINT       NOT NULL,
    status        VARCHAR(32)  NOT NULL DEFAULT 'CREATED',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    KEY idx_user_id (user_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
