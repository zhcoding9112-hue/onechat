-- =============================================================================
-- V11 埋点事件
--
-- 单表存全部节点事件，不预聚合。理由见 design D6：
--   - 本期要的是「付费转化」「接单率」这类强业务语义指标，它们必须和订单表关联才算得准，
--     放到第三方分析系统反而取不到。
--   - 月事件量级在十万以内，直接查明细表毫无压力。真到了需要预聚合的量级，
--     说明业务已经成立，那时再加也不迟。
--
-- props 用 JSON 存扩展属性：节点各自需要带的东西差异很大，为每个节点加列会让表无限膨胀。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `analytics_event` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `event_key`       VARCHAR(48)     NOT NULL COMMENT '事件标识，见 docs/analytics.md',
  `user_id`         BIGINT UNSIGNED NULL     COMMENT '用户 app_user.id；关注服务号时可能尚未建号',
  `open_id`         VARCHAR(64)     NULL     COMMENT '微信 openid，用于尚未建号时的归属',
  `talent_id`       BIGINT UNSIGNED NULL     COMMENT '归因主播，与主播相关的节点才有',
  `service_item_id` BIGINT UNSIGNED NULL     COMMENT '归因服务项目',
  `order_ref`       CHAR(18)        NULL     COMMENT '关联订单号',
  `props`           JSON            NULL     COMMENT '扩展属性',
  `occurred_at`     DATETIME(3)     NOT NULL COMMENT '发生时间',
  `created_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '入库时间',
  PRIMARY KEY (`id`),
  -- 漏斗按「事件标识 + 时间范围」查去重人数，这是最主要的查询形态
  KEY `idx_event_key_time` (`event_key`, `occurred_at`),
  KEY `idx_event_user_time` (`user_id`, `occurred_at`),
  KEY `idx_event_talent_key` (`talent_id`, `event_key`, `occurred_at`),
  KEY `idx_event_order` (`order_ref`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '埋点事件';

-- 接单率告警阈值，运营可配
CREATE TABLE IF NOT EXISTS `analytics_setting` (
  `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `singleton_key`         TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '单例约束：恒为 1',
  `accept_rate_threshold_bp` INT UNSIGNED NOT NULL COMMENT '接单率告警阈值，基点',
  `created_at`            DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`            DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_analytics_setting_singleton` (`singleton_key`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '看板配置';

-- 80% 来自 proposal：接单率低于它，转化率数据就失去参考价值
INSERT INTO `analytics_setting` (`singleton_key`, `accept_rate_threshold_bp`)
SELECT 1, 8000
WHERE NOT EXISTS (SELECT 1 FROM `analytics_setting` WHERE `singleton_key` = 1);
