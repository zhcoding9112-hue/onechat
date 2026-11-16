-- =============================================================================
-- V15 试音选人：粉丝发布试音需求，主播应征
--
-- 过期不靠定时任务：状态在读取时按 expires_at 判定并落库（惰性关闭）。
-- 所有在意「是否进行中」的路径都会先读一次——发布前查有没有在进行的、
-- 主播列表取开放中的、粉丝看自己的——所以不存在「过期了却没人发现」的窗口。
-- 多一个定时任务只会多一处可能漏跑的地方。
--
-- (request_id, talent_id) 唯一索引：同一主播对同一需求只能应征一次，
-- 幂等交给数据库，不靠应用层先查后插。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `audition_request` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `fan_user_id`     BIGINT UNSIGNED NOT NULL COMMENT '发布需求的粉丝 app_user.id',
  `service_item_id` BIGINT UNSIGNED NULL     COMMENT '想要的服务项目；为空表示不限',
  `voice_desc`      VARCHAR(200)    NOT NULL COMMENT '想要的声音描述',
  `status`          VARCHAR(16)     NOT NULL COMMENT '状态：OPEN 进行中 / CLOSED 已关闭',
  `expires_at`      DATETIME(3)     NOT NULL COMMENT '有效期截止，过期自动关闭',
  `closed_at`       DATETIME(3)     NULL     COMMENT '关闭时间',
  `created_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_audition_request_fan_status` (`fan_user_id`, `status`),
  KEY `idx_audition_request_status_expires` (`status`, `expires_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '粉丝发布的试音需求';

CREATE TABLE IF NOT EXISTS `audition_reply` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `request_id` BIGINT UNSIGNED NOT NULL COMMENT '试音需求 id',
  `talent_id`  BIGINT UNSIGNED NOT NULL COMMENT '应征主播 id',
  `message`    VARCHAR(200)    NULL     COMMENT '应征留言',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '应征时间',
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_audition_reply_request_talent` (`request_id`, `talent_id`),
  KEY `idx_audition_reply_request_created` (`request_id`, `created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '主播对试音需求的应征';
