-- 发售服务库（仅审计/对账 + 本地发售会话，热路径只走 Redis）
CREATE DATABASE IF NOT EXISTS drop_flashsale_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE drop_flashsale_db;

-- 本地发售会话：由 product 的 DropPublished 事件同步而来（ADR-0003，无同步 RPC）
CREATE TABLE IF NOT EXISTS drop_session (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  drop_id     BIGINT       NOT NULL,
  product_id  BIGINT       NOT NULL,
  name        VARCHAR(128) NOT NULL,
  start_time  DATETIME     NOT NULL,
  end_time    DATETIME     NOT NULL,
  stock       INT          NOT NULL,
  price_cents BIGINT       NOT NULL,
  status      VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',  -- SCHEDULED / OPEN / ENDED
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_drop_id (drop_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS flash_hit_log (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no    VARCHAR(40)  NOT NULL,
  customer_id BIGINT       NOT NULL,
  drop_id     BIGINT       NOT NULL,
  status      VARCHAR(20)  NOT NULL,             -- RESERVED / PAID / RELEASED
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_order_no (order_no)
) ENGINE=InnoDB;
