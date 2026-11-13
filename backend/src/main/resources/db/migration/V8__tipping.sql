-- =============================================================================
-- V8 打赏
--
-- 打赏与服务订单分开计量：它没有接单、完成与超时，支付确认后直接计为已完成。
-- 金额与分成的约定与订单完全一致（整数分、基点、创建瞬间快照）。
--
-- 留言只对目标主播与运营可见，所以它存在这张表上而不是任何公开展示的地方。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `tip_record` (
  `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tip_ref`            CHAR(18)        NOT NULL COMMENT '打赏单号，与订单号同样格式便于对账',

  `fan_user_id`        BIGINT UNSIGNED NOT NULL COMMENT '打赏粉丝的 app_user.id',
  `talent_id`          BIGINT UNSIGNED NOT NULL COMMENT '目标主播档案 id',

  `amount_cents`       BIGINT          NOT NULL COMMENT '打赏金额，单位：分',
  `paid_cents`         BIGINT          NULL     COMMENT '实收金额，单位：分',
  `commission_rate_bp` INT UNSIGNED    NOT NULL COMMENT '分成比例快照，基点',
  `guild_cents`        BIGINT          NOT NULL COMMENT '公会分成快照，单位：分',
  `talent_cents`       BIGINT          NOT NULL COMMENT '主播应得快照，单位：分',

  `status`             VARCHAR(24)     NOT NULL COMMENT '状态：PENDING_PAYMENT / PENDING_CONFIRM / COMPLETED / CANCELLED / REFUNDED',
  `message`            VARCHAR(200)    NULL     COMMENT '粉丝留言，仅目标主播与运营可见',

  `pay_deadline`       DATETIME(3)     NULL     COMMENT '待支付截止时间（绝对时间戳）',
  `paid_at`            DATETIME(3)     NULL     COMMENT '确认收款时间',
  `closed_at`          DATETIME(3)     NULL     COMMENT '进入终止态的时间',
  `close_reason`       VARCHAR(255)    NULL     COMMENT '取消或退款原因',

  `version`            INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `created_at`         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tip_record_ref` (`tip_ref`),
  KEY `idx_tip_record_fan` (`fan_user_id`, `created_at`),
  KEY `idx_tip_record_talent_status` (`talent_id`, `status`, `created_at`),
  KEY `idx_tip_record_pay_deadline` (`status`, `pay_deadline`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '打赏记录';

-- 打赏金额上下限，运营可配
CREATE TABLE IF NOT EXISTS `tip_setting` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `singleton_key` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '单例约束：恒为 1',
  `min_cents`     BIGINT          NOT NULL COMMENT '最低打赏金额，单位：分',
  `max_cents`     BIGINT          NOT NULL COMMENT '最高打赏金额，单位：分',
  `created_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tip_setting_singleton` (`singleton_key`),
  CONSTRAINT `ck_tip_setting_range` CHECK (`min_cents` > 0 AND `max_cents` >= `min_cents`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '打赏金额配置';

-- 下限 1 元、上限 2000 元。上限不是技术限制而是风控：
-- 单笔过大的打赏在人工收款期对不上账的代价很高，具体数值由运营按实际情况调。
INSERT INTO `tip_setting` (`singleton_key`, `min_cents`, `max_cents`)
SELECT 1, 100, 200000
WHERE NOT EXISTS (SELECT 1 FROM `tip_setting` WHERE `singleton_key` = 1);
