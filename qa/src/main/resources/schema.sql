CREATE TABLE IF NOT EXISTS document_chunk (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  source_type VARCHAR(20) NOT NULL,
  source_id   VARCHAR(64) NOT NULL,
  product_id  BIGINT NOT NULL,
  chunk_index INT NOT NULL DEFAULT 0,
  content     TEXT NOT NULL,
  milvus_id   BIGINT NULL,
  status      VARCHAR(20) NOT NULL DEFAULT 'INDEXED',
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_product (product_id),
  KEY idx_source (source_type, source_id)
);

CREATE TABLE IF NOT EXISTS eval_question (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  question          VARCHAR(500) NOT NULL,
  reference_answer  TEXT,
  expected_keywords VARCHAR(500),
  product_id        BIGINT,
  created_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);

CREATE TABLE IF NOT EXISTS eval_run (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  run_id         VARCHAR(40) NOT NULL,
  question_id    BIGINT NOT NULL,
  answer         TEXT,
  keyword_score  DECIMAL(5,4),
  citation_score DECIMAL(5,4),
  total_score    DECIMAL(5,4),
  created_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_run (run_id)
);
