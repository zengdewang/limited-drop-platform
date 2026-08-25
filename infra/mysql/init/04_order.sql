-- 订单服务库
CREATE DATABASE IF NOT EXISTS drop_order_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE drop_order_db;

CREATE TABLE IF NOT EXISTS orders (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no     VARCHAR(40)   NOT NULL,           -- cross-service idempotency key
  customer_id  BIGINT        NOT NULL,
  product_id   BIGINT        NOT NULL,
  drop_id      BIGINT        NOT NULL,
  status       VARCHAR(20)   NOT NULL,           -- PENDING_PAYMENT / PAID / EXPIRED
  amount_cents BIGINT        NOT NULL,
  expire_at    DATETIME      NOT NULL,           -- payment window deadline
  paid_at      DATETIME      NULL,
  created_at   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_customer (customer_id),
  KEY idx_drop_status_expire (drop_id, status, expire_at)
) ENGINE=InnoDB;
