USE drop_product_db;

SET @image_url_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'drop_product_db'
    AND TABLE_NAME = 'product'
    AND COLUMN_NAME = 'image_url'
);
SET @image_url_ddl = IF(
  @image_url_exists = 0,
  'ALTER TABLE product ADD COLUMN image_url VARCHAR(1000) NULL AFTER category',
  'SELECT 1'
);
PREPARE image_url_statement FROM @image_url_ddl;
EXECUTE image_url_statement;
DEALLOCATE PREPARE image_url_statement;
