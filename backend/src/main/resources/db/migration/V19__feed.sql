-- =============================================================================
-- V19 内容社区：动态、点赞、评论、举报、心动墙
--
-- 几条贯穿整组的约定：
--
-- 1. 下架是软的。内容原样保留，另记下架人、时间、原因。
--    删行就查不到「这条曾经发过什么」，纠纷与申诉时无从追溯；
--    而规格明确要求「保留记录供追溯」。
--
-- 2. 点赞数与评论数不冗余在动态行上，读时按 post_id 批量 GROUP BY 算。
--    冗余计数要在点赞、取消点赞、评论、删评论、下架评论每条路径上维护，
--    漏一条就与真实行数对不上，而对不上没人会发现。
--    一页十来条动态，一条带索引的分组查询足够。
--
-- 3. 点赞幂等靠 (post_id, user_id) 唯一索引，不靠「先查后插」。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `feed_post` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `talent_id`     BIGINT UNSIGNED NOT NULL COMMENT '发布者主播档案 id',
  `content`       VARCHAR(1000)   NULL     COMMENT '正文；可为空，但不能与图片同时为空',

  `status`        VARCHAR(16)     NOT NULL DEFAULT 'VISIBLE' COMMENT '状态：VISIBLE 可见 / REMOVED 已下架',
  -- 下架留痕三件套。软下架的意义全在这三列上
  `removed_by`    BIGINT UNSIGNED NULL     COMMENT '下架操作者 app_user.id',
  `removed_at`    DATETIME(3)     NULL     COMMENT '下架时间',
  `remove_reason` VARCHAR(255)    NULL     COMMENT '下架原因',

  `created_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发布时间',
  `updated_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',

  PRIMARY KEY (`id`),
  -- 广场按发布时间倒序翻页；同一毫秒内多条由自增 id 兜定顺序
  KEY `idx_feed_post_status_id` (`status`, `id`),
  KEY `idx_feed_post_talent` (`talent_id`, `status`, `id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '主播动态';

CREATE TABLE IF NOT EXISTS `feed_post_image` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `post_id`    BIGINT UNSIGNED NOT NULL COMMENT 'feed_post.id',
  `url`        VARCHAR(512)    NOT NULL COMMENT '图片地址',
  `sort_order` INT             NOT NULL DEFAULT 0 COMMENT '展示顺序，小的在前',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_feed_image_post` (`post_id`, `sort_order`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '动态图片';

CREATE TABLE IF NOT EXISTS `feed_like` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `post_id`    BIGINT UNSIGNED NOT NULL COMMENT 'feed_post.id',
  `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '点赞粉丝 app_user.id',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '点赞时间',
  PRIMARY KEY (`id`),
  -- 幂等的最终保证在这里，不在应用层的 if 判断上
  UNIQUE KEY `uk_feed_like` (`post_id`, `user_id`),
  KEY `idx_feed_like_user` (`user_id`, `post_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '动态点赞';

CREATE TABLE IF NOT EXISTS `feed_comment` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `post_id`       BIGINT UNSIGNED NOT NULL COMMENT 'feed_post.id',
  `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '评论者 app_user.id',
  `content`       VARCHAR(500)    NOT NULL COMMENT '评论正文',

  `status`        VARCHAR(16)     NOT NULL DEFAULT 'VISIBLE' COMMENT '状态：VISIBLE 可见 / REMOVED 已下架',
  `removed_by`    BIGINT UNSIGNED NULL     COMMENT '下架操作者 app_user.id',
  `removed_at`    DATETIME(3)     NULL     COMMENT '下架时间',
  `remove_reason` VARCHAR(255)    NULL     COMMENT '下架原因',

  `created_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '评论时间',
  `updated_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',

  PRIMARY KEY (`id`),
  KEY `idx_feed_comment_post` (`post_id`, `status`, `id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '动态评论';

CREATE TABLE IF NOT EXISTS `content_report` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `target_type`      VARCHAR(16)     NOT NULL COMMENT '举报对象：POST 动态 / COMMENT 评论',
  `target_id`        BIGINT UNSIGNED NOT NULL COMMENT '对象 id',
  `reporter_user_id` BIGINT UNSIGNED NOT NULL COMMENT '举报人 app_user.id',
  `reason`           VARCHAR(255)    NOT NULL COMMENT '举报理由，必填',

  `status`           VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING 待处理 / HANDLED 已处理 / DISMISSED 已驳回',
  `handled_by`       BIGINT UNSIGNED NULL     COMMENT '处理人 app_user.id',
  `handled_at`       DATETIME(3)     NULL     COMMENT '处理时间',
  `handle_note`      VARCHAR(255)    NULL     COMMENT '处理说明',

  `created_at`       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '举报时间',
  `updated_at`       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',

  PRIMARY KEY (`id`),
  -- 同一个人对同一对象只记一条，防止刷量把待处理队列淹掉
  UNIQUE KEY `uk_report_target_reporter` (`target_type`, `target_id`, `reporter_user_id`),
  KEY `idx_report_status` (`status`, `id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '内容举报';

-- 心动墙：本阶段只做运营手工挑选（tasks 9.5 明确「先做运营手工挑选」）。
-- 按规则自动筛选留到规则定下来之后，届时这张表仍是手工置顶的那一层。
CREATE TABLE IF NOT EXISTS `crush_wall_pick` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `talent_id`  BIGINT UNSIGNED NOT NULL COMMENT '主播档案 id',
  `sort_order` INT             NOT NULL DEFAULT 0 COMMENT '展示顺序，小的在前',
  `enabled`    TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_crush_talent` (`talent_id`),
  KEY `idx_crush_enabled_sort` (`enabled`, `sort_order`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '心动墙的运营挑选';

-- 动态相关的可配参数。与 tip_setting 同样的单例写法。
CREATE TABLE IF NOT EXISTS `feed_setting` (
  `id`            BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
  `singleton_key` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '单例约束：恒为 1',
  `max_images`    INT              NOT NULL COMMENT '单条动态的图片数量上限',
  `created_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_feed_setting_singleton` (`singleton_key`),
  CONSTRAINT `ck_feed_setting_max_images` CHECK (`max_images` BETWEEN 1 AND 20)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '动态配置';

INSERT INTO `feed_setting` (`singleton_key`, `max_images`)
SELECT 1, 9
WHERE NOT EXISTS (SELECT 1 FROM `feed_setting` WHERE `singleton_key` = 1);
