-- =============================================================================
-- V13 首页可配置位与主播等级
--
-- home_entry 一张表同时承载九宫格入口与运营横幅：两者的字段完全一致
-- （标题、图、跳转、排序、生效时间），区别只在 entry_type 与展示位置。
-- 拆成两张表会让后台维护、生效时间判断、排序逻辑各写两遍。
--
-- talent.level：粉丝端信息流要支持按等级筛选，而此前数据模型里没有主播等级
-- （已有的「等级」概念是第 8 组的粉丝等级，与此无关）。这里补一个运营可设的
-- 整数等级，默认 1；它只用于展示与筛选，不参与计价与分成。
-- =============================================================================

ALTER TABLE `talent`
  ADD COLUMN `level` INT NOT NULL DEFAULT 1 COMMENT '主播等级，运营设定，仅用于展示与筛选，不参与计价与分成' AFTER `region`;

CREATE INDEX `idx_talent_listed_level` ON `talent` (`listed`, `level`);

CREATE TABLE IF NOT EXISTS `home_entry` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `entry_type` VARCHAR(16)     NOT NULL COMMENT '位置类型：GRID 九宫格入口 / BANNER 运营横幅',
  `title`      VARCHAR(32)     NOT NULL COMMENT '标题',
  `subtitle`   VARCHAR(64)     NULL     COMMENT '副标题，九宫格通常不填',
  `image_url`  VARCHAR(512)    NULL     COMMENT '图片地址；横幅必填，九宫格可只用文字',
  `target_url` VARCHAR(512)    NULL     COMMENT '点击跳转地址；为空表示不可点击',
  `sort_order` INT             NOT NULL DEFAULT 0 COMMENT '展示顺序，小的在前',
  `enabled`    TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用',
  -- 生效时间两端都可空：空表示该端不限制。横幅过期无需运营手工下线。
  `start_at`   DATETIME(3)     NULL     COMMENT '生效开始时间；为空表示立即生效',
  `end_at`     DATETIME(3)     NULL     COMMENT '生效结束时间；为空表示长期有效',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_home_entry_type_sort` (`entry_type`, `enabled`, `sort_order`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '首页可配置位：九宫格入口与运营横幅';
