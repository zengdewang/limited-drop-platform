-- 用户服务库
CREATE DATABASE IF NOT EXISTS drop_user_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE drop_user_db;

CREATE TABLE IF NOT EXISTS customer (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  username      VARCHAR(64)  NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  member_level  VARCHAR(20)  NOT NULL DEFAULT 'NORMAL',  -- reserved, no tier logic in v1
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB;
