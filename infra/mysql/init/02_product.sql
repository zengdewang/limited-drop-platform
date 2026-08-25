-- 商品服务库
CREATE DATABASE IF NOT EXISTS drop_product_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE drop_product_db;

CREATE TABLE IF NOT EXISTS product (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  brand        VARCHAR(64)  NOT NULL,
  name         VARCHAR(128) NOT NULL,
  category     VARCHAR(64),
  official_doc TEXT         NOT NULL,          -- RAG source #1
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS `drop` (                -- 发售事件 (Drop)
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_id  BIGINT       NOT NULL,
  name        VARCHAR(128) NOT NULL,
  start_time  DATETIME     NOT NULL,
  end_time    DATETIME     NOT NULL,
  stock       INT          NOT NULL,
  price_cents BIGINT       NOT NULL,
  status      VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',  -- SCHEDULED / OPEN / ENDED
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_product (product_id),
  KEY idx_start  (start_time)
) ENGINE=InnoDB;

-- 已支付订单（product 从 OrderPaidEvent 事件同步而来，用于评价资格校验）
CREATE TABLE IF NOT EXISTS paid_order (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no    VARCHAR(40) NOT NULL,
  customer_id BIGINT      NOT NULL,
  product_id  BIGINT      NOT NULL,
  paid_at     DATETIME    NULL,
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_customer (customer_id)
) ENGINE=InnoDB;

-- 评价：用跨服务 order_no 作为唯一键（一单一评）
CREATE TABLE IF NOT EXISTS review (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no     VARCHAR(40)    NOT NULL,
  customer_id  BIGINT         NOT NULL,
  product_id   BIGINT         NOT NULL,
  rating       TINYINT        NOT NULL,           -- 1..5
  content      VARCHAR(1000)  NOT NULL,
  status       VARCHAR(20)    NOT NULL DEFAULT 'PENDING',  -- PENDING / APPROVED / REJECTED
  moderated_at DATETIME(3)    NULL,
  created_at   DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_order (order_no),                -- one Review per Order in v1
  KEY idx_product_status (product_id, status),
  KEY idx_customer (customer_id)
) ENGINE=InnoDB;
