-- =============================================================================
-- V6 收款记录与支付凭证
--
-- payment_record 的 (order_ref, provider, external_ref) 唯一索引是幂等的根基：
-- 微信支付回调天然会重投，同一笔收款被送达多次时，第二次会在这里撞上唯一键，
-- 从而被识别为重复事件并忽略。这个约束现在就必须成立，不能等接微信支付时再加。
--
-- payment_proof 保留全部上传记录而不覆盖：运营核对时可能需要看粉丝先后传的几张图。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `payment_record` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_ref`    CHAR(18)        NOT NULL COMMENT '订单号',
  `order_id`     BIGINT UNSIGNED NOT NULL COMMENT '订单 id',
  `provider`     VARCHAR(32)     NOT NULL COMMENT '支付方式标识，如 manual、wechat',
  `external_ref` VARCHAR(64)     NOT NULL COMMENT '支付方式侧的唯一流水标识',
  `paid_cents`   BIGINT          NOT NULL COMMENT '实收金额，单位：分',
  `operator_id`  BIGINT UNSIGNED NULL     COMMENT '确认者 app_user.id；异步回调触发时为空',
  `note`         VARCHAR(255)    NULL     COMMENT '备注，如金额差异说明',
  `occurred_at`  DATETIME(3)     NOT NULL COMMENT '收款发生时间',
  `created_at`   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  -- 幂等键：同一支付方式对同一订单的同一笔流水只能记一次
  UNIQUE KEY `uk_payment_record_idem` (`order_ref`, `provider`, `external_ref`),
  KEY `idx_payment_record_order` (`order_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '收款记录';

CREATE TABLE IF NOT EXISTS `payment_proof` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id`    BIGINT UNSIGNED NOT NULL COMMENT '订单 id',
  `order_ref`   CHAR(18)        NOT NULL COMMENT '订单号',
  `url`         VARCHAR(512)    NOT NULL COMMENT '凭证图片地址',
  `size_bytes`  BIGINT          NOT NULL COMMENT '文件字节数',
  `content_type` VARCHAR(64)    NOT NULL COMMENT '媒体类型',
  `uploaded_by` BIGINT UNSIGNED NOT NULL COMMENT '上传者 app_user.id',
  `created_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '上传时间',
  `updated_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_payment_proof_order` (`order_id`, `id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '支付凭证';

-- 被忽略的重复收款事件也要留痕：对账时需要能解释"为什么这笔流水没有产生状态变更"
CREATE TABLE IF NOT EXISTS `payment_event_log` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_ref`    CHAR(18)        NOT NULL COMMENT '订单号',
  `provider`     VARCHAR(32)     NOT NULL COMMENT '支付方式标识',
  `external_ref` VARCHAR(64)     NOT NULL COMMENT '支付方式侧流水标识',
  `paid_cents`   BIGINT          NOT NULL COMMENT '事件携带的实收金额，单位：分',
  `outcome`      VARCHAR(24)     NOT NULL COMMENT '处理结果：ACCEPTED 已入账 / DUPLICATE 重复忽略 / REJECTED 拒绝',
  `detail`       VARCHAR(255)    NULL     COMMENT '结果说明',
  `occurred_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '处理时间',
  PRIMARY KEY (`id`),
  KEY `idx_payment_event_log_order` (`order_ref`, `id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '收款事件处理日志';

-- 收款信息与支付方式开关：全站单例配置，不得硬编码
CREATE TABLE IF NOT EXISTS `payment_setting` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `provider`     VARCHAR(32)     NOT NULL COMMENT '支付方式标识',
  `enabled`      TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否启用：0 停用 1 启用',
  `account_name` VARCHAR(64)     NULL     COMMENT '收款账户名称',
  `account_no`   VARCHAR(64)     NULL     COMMENT '收款账号',
  `bank_name`    VARCHAR(64)     NULL     COMMENT '开户行',
  `qr_url`       VARCHAR(512)    NULL     COMMENT '收款码图片地址',
  `tips`         VARCHAR(500)    NULL     COMMENT '给粉丝的付款提示',
  `created_at`   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_setting_provider` (`provider`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '支付方式配置';

-- 人工收款默认启用：上线期它是唯一可用的收款方式。
-- 具体账户信息留空，由运营在后台填写——不得把任何真实账号写进迁移脚本。
INSERT INTO `payment_setting` (`provider`, `enabled`, `tips`)
SELECT 'manual', 1, '请按上方信息完成转账，并在付款后上传支付凭证截图。'
WHERE NOT EXISTS (SELECT 1 FROM `payment_setting` WHERE `provider` = 'manual');
