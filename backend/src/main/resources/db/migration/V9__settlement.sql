-- =============================================================================
-- V9 分成记账与结算
--
-- 分账台账是财务的唯一依据。三条约束决定了它的形状：
--   1. 金额一律取来源（订单或打赏）上的快照，绝不读当前配置——否则改一次比例，
--      历史账目全部漂移，月底永远对不平。
--   2. (source_type, source_id) 唯一，保证同一来源只记一次账。重复生成被挡在数据库层，
--      而不是靠应用层"先查再写"。
--   3. 退款不删除记录而是冲销：删掉就查不到"这笔钱曾经算过账"，纠纷时无从追溯。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `settlement_entry` (
  `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',

  `source_type`        VARCHAR(16)     NOT NULL COMMENT '来源类型：ORDER 服务订单 / TIP 打赏',
  `source_id`          BIGINT UNSIGNED NOT NULL COMMENT '来源记录 id',
  `source_ref`         CHAR(18)        NOT NULL COMMENT '来源单号，便于人工核对',

  `talent_id`          BIGINT UNSIGNED NOT NULL COMMENT '主播档案 id',
  `amount_cents`       BIGINT          NOT NULL COMMENT '来源金额，单位：分',
  `commission_rate_bp` INT UNSIGNED    NOT NULL COMMENT '分成比例快照，基点',
  `guild_cents`        BIGINT          NOT NULL COMMENT '公会分成，单位：分',
  `talent_cents`       BIGINT          NOT NULL COMMENT '主播应得，单位：分',

  `status`             VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE 有效 / REVERSED 已冲销',
  `reversed_at`        DATETIME(3)     NULL     COMMENT '冲销时间',
  `reverse_reason`     VARCHAR(255)    NULL     COMMENT '冲销原因',

  `occurred_at`        DATETIME(3)     NOT NULL COMMENT '业务发生时间，报表按它归集到周期',
  `created_at`         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',

  PRIMARY KEY (`id`),
  -- 同一来源只记一次账
  UNIQUE KEY `uk_settlement_source` (`source_type`, `source_id`),
  KEY `idx_settlement_talent_period` (`talent_id`, `occurred_at`),
  KEY `idx_settlement_status_period` (`status`, `occurred_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '分账台账';

-- 线下结清标记。只记录"这个周期这位主播已结"，不发生任何资金动作。
CREATE TABLE IF NOT EXISTS `settlement_mark` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `talent_id`   BIGINT UNSIGNED NOT NULL COMMENT '主播档案 id',
  `period`      CHAR(7)         NOT NULL COMMENT '结算周期，格式 yyyy-MM',
  `settled_at`  DATETIME(3)     NOT NULL COMMENT '标记时间',
  `operator_id` BIGINT UNSIGNED NOT NULL COMMENT '操作者 app_user.id',
  `note`        VARCHAR(255)    NULL     COMMENT '备注',
  `created_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_mark` (`talent_id`, `period`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '线下结清标记';
