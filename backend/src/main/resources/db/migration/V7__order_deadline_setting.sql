-- =============================================================================
-- V7 订单时限配置
--
-- 时限需要运营在后台随时调整（首周看真实数据后多半要改），因此必须落库，
-- 不能只靠启动参数。但订单在创建时已把时限折算成绝对时间戳写入自身，
-- 所以调整只影响之后创建的订单，已在计时中的订单沿用其创建时的时限。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `order_deadline_setting` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `singleton_key`  TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '单例约束：恒为 1',
  `pay_minutes`    INT             NOT NULL COMMENT '待支付有效期（分钟）',
  `accept_minutes` INT             NOT NULL COMMENT '接单时限（分钟）',
  `created_at`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_deadline_singleton` (`singleton_key`),
  CONSTRAINT `ck_order_deadline_positive` CHECK (`pay_minutes` > 0 AND `accept_minutes` > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '订单时限配置';

INSERT INTO `order_deadline_setting` (`singleton_key`, `pay_minutes`, `accept_minutes`)
SELECT 1, 30, 15
WHERE NOT EXISTS (SELECT 1 FROM `order_deadline_setting` WHERE `singleton_key` = 1);
