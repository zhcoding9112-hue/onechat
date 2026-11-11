-- =============================================================================
-- V1 基线迁移
--
-- 本文件只确立迁移基线与全库约定，不创建任何业务表。
-- 各业务表由其所属任务各自新增迁移（app_user 见 V2，服务与价格配置见 V3，依此类推），
-- 以保证每个迁移的归属与其 tasks.md 条目一一对应。
--
-- 全库约定（后续迁移一律遵守）：
--   1. 引擎 InnoDB，字符集 utf8mb4，排序规则 utf8mb4_0900_ai_ci。
--   2. 金额一律 BIGINT 存「分」，禁止 DECIMAL/FLOAT/DOUBLE。
--   3. 分成比例一律 INT 存「基点」（万分之一），禁止百分比小数。
--   4. 主键一律 BIGINT UNSIGNED AUTO_INCREMENT，列名 id。
--   5. 每表带 created_at / updated_at，类型 DATETIME(3)。
--   6. 不使用数据库外键约束，关联完整性由应用层保证。
--   7. 布尔语义一律 TINYINT(1)，0 假 1 真。
-- =============================================================================

-- 迁移基线标记表：仅用于记录基线建立时间，不承载业务数据。
CREATE TABLE IF NOT EXISTS `schema_baseline` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `note`       VARCHAR(255)    NOT NULL COMMENT '基线说明',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '迁移基线标记';

INSERT INTO `schema_baseline` (`note`) VALUES ('private-checkout 基线 V1');
