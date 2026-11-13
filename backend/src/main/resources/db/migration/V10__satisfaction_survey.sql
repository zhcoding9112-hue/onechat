-- =============================================================================
-- V10 满意度回访
--
-- 可见范围是这张表唯一需要小心的地方（ordering 规格）：
--   - 粉丝端不公开展示：其他粉丝浏览主播详情页看不到任何评分。
--   - 主播只能看到聚合值（均值与分值分布），MUST NOT 看到单条评分出自哪一笔订单、
--     哪一位粉丝。否则粉丝会怕被针对，低分不敢打，数据全变 5 分，回访就失去意义。
--   - 运营可以看逐单明细。
--
-- 一笔订单至多一条有效评分，由 order_id 唯一索引保证。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `satisfaction_survey` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id`    BIGINT UNSIGNED NOT NULL COMMENT '订单 id',
  `order_ref`   CHAR(18)        NOT NULL COMMENT '订单号',
  `talent_id`   BIGINT UNSIGNED NOT NULL COMMENT '主播档案 id，聚合查询按它分组',
  `fan_user_id` BIGINT UNSIGNED NOT NULL COMMENT '评分粉丝 id，仅运营可追溯',
  `score`       TINYINT UNSIGNED NOT NULL COMMENT '满意度评分，1 到 5',
  `created_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '提交时间',
  `updated_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  -- 一笔订单至多一条评分
  UNIQUE KEY `uk_survey_order` (`order_id`),
  KEY `idx_survey_talent` (`talent_id`, `score`),
  CONSTRAINT `ck_survey_score` CHECK (`score` BETWEEN 1 AND 5)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '满意度评分';
