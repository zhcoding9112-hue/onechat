-- =============================================================================
-- V17 优惠券与券包
--
-- 三条约束决定了这里的形状：
--   1. 面额与门槛一律整数分，与订单、打赏同一套约定。
--   2. 券的抵扣只减少粉丝实付，不减少主播分成——抵扣由公会承担。
--      因此订单上「原价」与「实付」必须分开存：分成算原价，收款算实付。
--   3. 一张券只能被一笔订单占用，一笔订单只能用一张券。两头都靠唯一索引兜底，
--      而不是靠应用层「先查后写」——那在并发下必然双花。
--
-- 券的有效期在发放时快照到 user_coupon.expires_at：运营事后改券模板的有效期，
-- 不该让已经发出去的券跟着变。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `coupon` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`            VARCHAR(64)     NOT NULL COMMENT '券名，如「满 100 减 20」',
  `amount_cents`    BIGINT          NOT NULL COMMENT '面额，单位：分',
  `threshold_cents` BIGINT          NOT NULL COMMENT '使用门槛（订单原价下限），单位：分',
  -- 适用范围：为空表示不限品类
  `service_item_id` BIGINT UNSIGNED NULL     COMMENT '限定的服务项目 id；为空表示全部品类',
  `valid_from`      DATETIME(3)     NOT NULL COMMENT '生效时间',
  `valid_to`        DATETIME(3)     NOT NULL COMMENT '失效时间',
  `enabled`         TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用',
  `created_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  -- 面额不得超过门槛：否则一张券能把订单抵成负数，或让 1 分钱的单白嫖 20 元
  CONSTRAINT `ck_coupon_amount` CHECK (`amount_cents` > 0 AND `amount_cents` <= `threshold_cents`),
  CONSTRAINT `ck_coupon_period` CHECK (`valid_to` > `valid_from`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '优惠券模板';

CREATE TABLE IF NOT EXISTS `user_coupon` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `coupon_id`   BIGINT UNSIGNED NOT NULL COMMENT 'coupon.id',
  `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '持有粉丝 app_user.id',

  -- AVAILABLE 可用 / LOCKED 已被某笔待支付订单占用 / USED 已核销 / EXPIRED 已过期
  -- LOCKED 单独成态：订单还没付款，券既不能再用于别的单，也还没核销。
  -- 只有 AVAILABLE / USED / EXPIRED 三态需要给粉丝分类展示，LOCKED 归入「已使用」一栏。
  `status`      VARCHAR(16)     NOT NULL DEFAULT 'AVAILABLE' COMMENT '状态',
  `order_id`    BIGINT UNSIGNED NULL     COMMENT '占用或核销它的订单 id',
  `expires_at`  DATETIME(3)     NOT NULL COMMENT '本张券的失效时间，发放时从模板快照',
  `used_at`     DATETIME(3)     NULL     COMMENT '核销时间',
  `remark`      VARCHAR(255)    NULL     COMMENT '备注，例如退款时未退回的原因',

  `created_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '领取/发放时间',
  `updated_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',

  PRIMARY KEY (`id`),
  -- 同一粉丝对同一张券只能持有一份。重复领取被挡在数据库层。
  UNIQUE KEY `uk_user_coupon_user_coupon` (`coupon_id`, `user_id`),
  -- 一笔订单只能用一张券。MySQL 的唯一索引允许多个 NULL，所以未占用的行不受影响。
  UNIQUE KEY `uk_user_coupon_order` (`order_id`),
  KEY `idx_user_coupon_user_status` (`user_id`, `status`, `expires_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '粉丝持有的券';

-- -----------------------------------------------------------------------------
-- 订单：拆出「原价」与「券抵扣」
--
-- payable_cents 的含义保持不变，仍是粉丝要付的钱（现在等于原价减抵扣），
-- 这样支付侧的收款指令与金额核对一行都不用改。
-- 新增的 original_cents 才是分成的计算基数。
-- -----------------------------------------------------------------------------
ALTER TABLE `service_order`
  ADD COLUMN `original_cents`        BIGINT          NOT NULL DEFAULT 0 COMMENT '订单原价（未用券前），单位：分' AFTER `quantity`,
  ADD COLUMN `coupon_discount_cents` BIGINT          NOT NULL DEFAULT 0 COMMENT '券抵扣金额，单位：分' AFTER `original_cents`,
  ADD COLUMN `user_coupon_id`        BIGINT UNSIGNED NULL             COMMENT '使用的 user_coupon.id' AFTER `coupon_discount_cents`;

-- 回填历史订单：当时没有券，原价就等于应付，抵扣为 0。
-- 必须显式回填而不是只给默认值——默认值只对新行生效，已有行会留下 original_cents = 0，
-- 那样历史订单的分成基数会变成 0。
UPDATE `service_order` SET `original_cents` = `payable_cents` WHERE `original_cents` = 0;

-- -----------------------------------------------------------------------------
-- 台账：单列促销成本
--
-- 报表只读台账、不回查订单表（见 V9 的说明），所以券抵扣必须在记账时就落到台账上，
-- 否则报表要么查不到促销成本，要么被迫去 join 订单表而破坏「金额快照固定」的前提。
-- -----------------------------------------------------------------------------
ALTER TABLE `settlement_entry`
  ADD COLUMN `promo_cost_cents` BIGINT NOT NULL DEFAULT 0 COMMENT '促销成本（券抵扣），由公会承担，单位：分' AFTER `talent_cents`;
