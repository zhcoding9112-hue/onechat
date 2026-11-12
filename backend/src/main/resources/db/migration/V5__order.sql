-- =============================================================================
-- V5 订单与状态留痕
--
-- 快照字段（unit_price_cents / commission_rate_bp / guild_cents / talent_cents
-- 以及项目与档位名称）在下单瞬间写死，此后任何配置变更都不回溯。
-- 时限存绝对时间戳而非相对分钟数：运营改时限不影响已在计时中的订单，
-- 且超时判定无需再读配置表。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `service_order` (
  `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_ref`          CHAR(18)        NOT NULL COMMENT '订单号：yyMMddHHmmss + 6 位随机',

  `fan_user_id`        BIGINT UNSIGNED NOT NULL COMMENT '下单粉丝的 app_user.id',
  `talent_id`          BIGINT UNSIGNED NOT NULL COMMENT '指定的主播档案 id',

  `service_item_id`    BIGINT UNSIGNED NOT NULL COMMENT '服务项目 id',
  `service_spec_id`    BIGINT UNSIGNED NOT NULL COMMENT '档位 id',
  `service_item_name`  VARCHAR(64)     NOT NULL COMMENT '项目名称快照',
  `service_spec_name`  VARCHAR(64)     NOT NULL COMMENT '档位名称快照',

  `unit_price_cents`   BIGINT          NOT NULL COMMENT '单价快照，单位：分',
  `quantity`           INT             NOT NULL COMMENT '数量',
  `payable_cents`      BIGINT          NOT NULL COMMENT '应付金额，单位：分',
  `paid_cents`         BIGINT          NULL     COMMENT '实收金额，单位：分；与应付不一致时留痕于差异说明',
  `commission_rate_bp` INT UNSIGNED    NOT NULL COMMENT '分成比例快照，基点',
  `guild_cents`        BIGINT          NOT NULL COMMENT '公会分成快照，单位：分',
  `talent_cents`       BIGINT          NOT NULL COMMENT '主播应得快照，单位：分',

  `status`             VARCHAR(24)     NOT NULL COMMENT '状态：PENDING_PAYMENT / PENDING_CONFIRM / PENDING_ACCEPT / ACCEPTED / COMPLETED / TIMEOUT / CANCELLED / REFUNDED',
  `contact`            VARCHAR(64)     NOT NULL COMMENT '粉丝的站外联系方式，主播据此发起服务',
  `remark`             VARCHAR(255)    NULL     COMMENT '粉丝的附加需求备注',

  `pay_deadline`       DATETIME(3)     NULL     COMMENT '待支付截止时间（绝对时间戳）',
  `accept_deadline`    DATETIME(3)     NULL     COMMENT '待接单截止时间（绝对时间戳）',
  `paid_at`            DATETIME(3)     NULL     COMMENT '确认收款时间',
  `accepted_at`        DATETIME(3)     NULL     COMMENT '主播接单时间',
  `completed_at`       DATETIME(3)     NULL     COMMENT '标记完成时间',
  `closed_at`          DATETIME(3)     NULL     COMMENT '进入终止态的时间',
  `close_reason`       VARCHAR(255)    NULL     COMMENT '取消、超时或退款的原因',

  `version`            INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，防接单与超时并发',
  `created_at`         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_service_order_ref` (`order_ref`),
  KEY `idx_service_order_fan` (`fan_user_id`, `created_at`),
  KEY `idx_service_order_talent_status` (`talent_id`, `status`, `created_at`),
  KEY `idx_service_order_status` (`status`, `created_at`),
  -- 超时兜底扫表按这两个时限查，各建一条索引
  KEY `idx_service_order_pay_deadline` (`status`, `pay_deadline`),
  KEY `idx_service_order_accept_deadline` (`status`, `accept_deadline`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '服务订单';

CREATE TABLE IF NOT EXISTS `order_status_log` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id`     BIGINT UNSIGNED NOT NULL COMMENT '订单 id',
  `order_ref`    CHAR(18)        NOT NULL COMMENT '订单号，便于按单号直接查日志',
  `from_status`  VARCHAR(24)     NULL     COMMENT '变更前状态；创建时为空',
  `to_status`    VARCHAR(24)     NOT NULL COMMENT '变更后状态',
  `operator_type` VARCHAR(16)    NOT NULL COMMENT '触发者类型：FAN / TALENT / OPERATOR / SYSTEM',
  `operator_id`  BIGINT UNSIGNED NULL     COMMENT '触发者 app_user.id；系统触发时为空',
  `reason`       VARCHAR(255)    NULL     COMMENT '变更原因',
  `occurred_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_status_log_order` (`order_id`, `id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '订单状态变更日志';
