-- =============================================================================
-- V3 服务目录、档位、价格与分成配置
--
-- 金额一律 BIGINT 存「分」；分成比例一律 INT 存「基点」（万分之一）。
-- 档位结构全站统一：档位挂在服务项目下，价格则按「主播 × 档位」单独配置。
-- 未配置价格的「主播 × 档位」组合不得在该主播详情页展示为可购。
-- =============================================================================

-- 服务项目：情感倾听 / 情绪疏导 / 游戏陪玩 等
CREATE TABLE IF NOT EXISTS `service_item` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code`        VARCHAR(32)     NOT NULL COMMENT '项目编码，稳定标识，埋点与报表按它归类',
  `name`        VARCHAR(64)     NOT NULL COMMENT '项目名称',
  `description` VARCHAR(255)    NULL     COMMENT '项目说明',
  `sort_order`  INT             NOT NULL DEFAULT 0 COMMENT '展示顺序，小的在前',
  `enabled`     TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用：0 停用 1 启用',
  `created_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_service_item_code` (`code`),
  KEY `idx_service_item_enabled_sort` (`enabled`, `sort_order`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '服务项目';

-- 档位：某服务项目下的计量单位，如「30 分钟」「60 分钟」
CREATE TABLE IF NOT EXISTS `service_spec` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `service_item_id` BIGINT UNSIGNED NOT NULL COMMENT '所属服务项目',
  `code`            VARCHAR(32)     NOT NULL COMMENT '档位编码，项目内唯一',
  `name`            VARCHAR(64)     NOT NULL COMMENT '档位名称，如 30分钟',
  `duration_minutes` INT            NULL     COMMENT '服务时长（分钟），用于展示与预期管理',
  `sort_order`      INT             NOT NULL DEFAULT 0 COMMENT '展示顺序，小的在前',
  `enabled`         TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用：0 停用 1 启用',
  `created_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_service_spec_item_code` (`service_item_id`, `code`),
  KEY `idx_service_spec_item_enabled_sort` (`service_item_id`, `enabled`, `sort_order`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '服务档位';

-- 主播价格：按「主播 × 档位」配置单价。无记录即该档位对该主播不可购。
CREATE TABLE IF NOT EXISTS `talent_price` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `talent_id`       BIGINT UNSIGNED NOT NULL COMMENT '主播档案 id',
  `service_spec_id` BIGINT UNSIGNED NOT NULL COMMENT '档位 id',
  `unit_price_cents` BIGINT         NOT NULL COMMENT '单价，单位：分',
  `enabled`         TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否可购：0 否 1 是',
  `created_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_talent_price_talent_spec` (`talent_id`, `service_spec_id`),
  KEY `idx_talent_price_spec` (`service_spec_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '主播档位价格';

-- 分成比例：talent_id 为 NULL 表示全局默认；按主播覆写则填具体 talent_id。
-- 唯一索引用生成列实现（MySQL 的唯一索引不约束 NULL，直接对 talent_id 建唯一键挡不住多条全局默认）。
CREATE TABLE IF NOT EXISTS `commission_config` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `talent_id`     BIGINT UNSIGNED NULL     COMMENT '主播档案 id；NULL 表示全局默认',
  `talent_key`    BIGINT UNSIGNED AS (IFNULL(`talent_id`, 0)) STORED COMMENT '唯一约束用：全局默认归一为 0',
  `rate_bp`       INT UNSIGNED    NOT NULL COMMENT '公会分成比例，基点（万分之一），0-10000',
  `created_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_commission_talent_key` (`talent_key`),
  CONSTRAINT `ck_commission_rate_bp` CHECK (`rate_bp` <= 10000)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '分成比例配置';

-- 全局默认分成比例必须存在，否则下单取不到比例。
-- 具体数值待公会与主播商定后由运营在后台改，这里先给一个显式的占位值。
INSERT INTO `commission_config` (`talent_id`, `rate_bp`)
SELECT NULL, 3000
WHERE NOT EXISTS (SELECT 1 FROM `commission_config` WHERE `talent_id` IS NULL);
