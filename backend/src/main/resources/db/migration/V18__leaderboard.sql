-- =============================================================================
-- V18 打赏榜单与粉丝等级
--
-- 三个决定值得写下来：
--
-- 1. 累计按「打赏的收款时间」归期，而不是按「当前时间」。
--    一笔上月收款、本月退款的打赏，要从上月的账上减掉，而不是从本月减。
--    若按当前时间归期，本月会凭空多出一笔负数，上月则永远是错的。
--    这样每个周期的累计值都只由属于它的打赏构成，也就不会被减成负数。
--
-- 2. 累计值用带条件的原子 UPDATE 增减，不走「读出来算好再写回」。
--    理由与元子余额、券占用完全相同：并发下先读后写必然丢更新。
--
-- 3. 快照一经固化就不再变。若某个已固化周期事后发生退款，累计值会变，
--    而快照不变——规格明确要求「上一周期的榜单结果被固化保存」。
--    把历史名次改掉，等于让已经公示过的排名事后翻案。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `leaderboard_total` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `period_type`  VARCHAR(8)      NOT NULL COMMENT '周期类型：DAY / WEEK / MONTH',
  -- 日 2026-11-16、周 2026-W47、月 2026-11。按字符串存，排序与比较都直观
  `period_key`   VARCHAR(16)     NOT NULL COMMENT '周期标识',
  `talent_id`    BIGINT UNSIGNED NOT NULL COMMENT '主播档案 id',

  `amount_cents` BIGINT          NOT NULL DEFAULT 0 COMMENT '该周期已确认打赏金额合计，单位：分',
  `tip_count`    INT             NOT NULL DEFAULT 0 COMMENT '该周期已确认打赏笔数',

  `created_at`   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',

  PRIMARY KEY (`id`),
  -- 一个周期一位主播一行。并发首次累计靠这个唯一索引兜底，而不是先查后插
  UNIQUE KEY `uk_lb_total` (`period_type`, `period_key`, `talent_id`),
  -- 榜单按金额倒序取前若干名，走这条索引
  KEY `idx_lb_total_rank` (`period_type`, `period_key`, `amount_cents`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '周期打赏累计（当前周期可增可减）';

CREATE TABLE IF NOT EXISTS `leaderboard_snapshot` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `period_type`  VARCHAR(8)      NOT NULL COMMENT '周期类型：DAY / WEEK / MONTH',
  `period_key`   VARCHAR(16)     NOT NULL COMMENT '周期标识',
  `talent_id`    BIGINT UNSIGNED NOT NULL COMMENT '主播档案 id',

  `rank_no`      INT             NOT NULL COMMENT '名次，从 1 开始',
  `amount_cents` BIGINT          NOT NULL COMMENT '固化时的金额合计，单位：分',
  `tip_count`    INT             NOT NULL COMMENT '固化时的打赏笔数',
  -- 昵称也快照下来：主播事后改名，历史榜单仍应显示当时的名字
  `nickname`     VARCHAR(64)     NOT NULL COMMENT '主播昵称快照',

  `frozen_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '固化时间',

  PRIMARY KEY (`id`),
  -- 固化是幂等的：重复触发会撞这个唯一索引，而不是写出第二份名次
  UNIQUE KEY `uk_lb_snapshot` (`period_type`, `period_key`, `talent_id`),
  KEY `idx_lb_snapshot_rank` (`period_type`, `period_key`, `rank_no`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '历史周期榜单快照（固化后不再变化）';

-- -----------------------------------------------------------------------------
-- 粉丝等级门槛，运营可配
--
-- 等级只影响展示，不参与计价与分成——与 talent.level 同样的定位。
-- 这一点在 leaderboard 规格里是明文要求，也有专门的用例盯着。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `fan_level_setting` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `level`           INT             NOT NULL COMMENT '等级，从 1 开始',
  `name`            VARCHAR(32)     NOT NULL COMMENT '等级名称',
  `threshold_cents` BIGINT          NOT NULL COMMENT '达到该等级所需的累计消费，单位：分',
  `created_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fan_level` (`level`),
  CONSTRAINT `ck_fan_level_threshold` CHECK (`threshold_cents` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '粉丝等级门槛';

-- 初始五档。具体数值是占位，运营按实际客单价调——
-- design.md 里把「等级门槛数值」列为待定项，这里给的是能跑起来的默认值。
INSERT INTO `fan_level_setting` (`level`, `name`, `threshold_cents`)
SELECT * FROM (
  SELECT 1 AS l, 'L1' AS n, 0 AS t UNION ALL
  SELECT 2, 'L2', 20000 UNION ALL
  SELECT 3, 'L3', 100000 UNION ALL
  SELECT 4, 'L4', 300000 UNION ALL
  SELECT 5, 'L5', 1000000
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM `fan_level_setting`);
