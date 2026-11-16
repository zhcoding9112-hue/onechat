-- =============================================================================
-- V16 元子账户与流水
--
-- 本阶段元子「只记账」：没有粉丝充值、没有提现、没有转让，余额只由运营手工
-- 发放或调整产生。表结构按完整账务设计，是为了日后真要开放消耗时不必改表。
--
-- 余额用 BIGINT 整数计数（元子是点数而不是钱，没有「分」的概念），
-- 任何情况下都不用浮点。
--
-- 为什么余额变动走「带条件的原子 UPDATE」而不是「读出来判断再写回」：
--   UPDATE credit_account SET balance = balance + delta ... WHERE balance + delta >= 0
-- 这一条语句里，MySQL 对该行做当前读并加行锁，并发的第二个事务会等到第一个
-- 提交后再基于新值判断，因此不存在超扣窗口。而「先 SELECT 余额、在应用层
-- 判断够不够、再 UPDATE」在并发下必然超扣：两个事务都读到 100，都认为扣 80
-- 没问题。version 列仍然保留并在每次变动时自增，一是给实体的普通
-- updateById 做乐观锁，二是任何外部读改写都能据此发现自己读到的是旧值。
--
-- CHECK 约束是最后一道闸：即使将来有人绕过服务层写 SQL，也写不出负余额。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `credit_account` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '粉丝 app_user.id',
  `balance`    BIGINT          NOT NULL DEFAULT 0 COMMENT '元子余额，整数计数',
  `version`    INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，每次余额变动自增',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  -- 一位粉丝一个账户。并发首次开户靠这个唯一索引兜底，而不是先查后插。
  UNIQUE KEY `uk_credit_account_user` (`user_id`),
  CONSTRAINT `ck_credit_account_balance` CHECK (`balance` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '元子账户';

CREATE TABLE IF NOT EXISTS `credit_transaction` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `account_id`       BIGINT UNSIGNED NOT NULL COMMENT 'credit_account.id',
  `user_id`          BIGINT UNSIGNED NOT NULL COMMENT '冗余一份粉丝 id，流水按人查不必回表',

  `delta`            BIGINT          NOT NULL COMMENT '变动数量，正为增、负为减，不允许为 0',
  `balance_after`    BIGINT          NOT NULL COMMENT '本次变动后的余额',
  `type`             VARCHAR(24)     NOT NULL COMMENT '类型：GRANT 发放 / ADJUST 调整 / DEDUCT 消耗',
  -- 原因是必填：一笔余额变动没有原因就无法追溯，对账时只能靠猜。
  `reason`           VARCHAR(255)    NOT NULL COMMENT '变动原因，运营必填',
  `operator_user_id` BIGINT UNSIGNED NULL     COMMENT '操作者 app_user.id；系统自动变动时为空',

  `created_at`       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发生时间',

  PRIMARY KEY (`id`),
  -- 流水页按时间倒序翻页。同一毫秒内多笔的顺序由自增 id 兜定，所以索引带上 id。
  KEY `idx_credit_tx_user` (`user_id`, `id`),
  KEY `idx_credit_tx_account` (`account_id`, `id`),
  CONSTRAINT `ck_credit_tx_delta` CHECK (`delta` <> 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '元子流水';
