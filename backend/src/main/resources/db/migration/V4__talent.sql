-- =============================================================================
-- V4 主播档案
--
-- 档案与微信账号是可空关联：运营可以先录入档案、配好价格，等主播本人首次授权
-- 登录后再把账号关联上。关联前该档案可以正常展示与被下单，只是主播本人进不了工作台。
--
-- 在线状态存在档案上而非 Redis：它由主播手动切换、变更极少，且需要随列表一次查出；
-- 放 Redis 反而要为列表页做 N 次查询。Redis 留给超时队列那类真正需要过期语义的数据。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `talent` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`       BIGINT UNSIGNED NULL     COMMENT '关联的 app_user.id；未关联时为空',
  `nickname`      VARCHAR(64)     NOT NULL COMMENT '对外展示昵称，与微信昵称无关',
  `cover_url`     VARCHAR(512)    NULL     COMMENT '封面图地址',
  `intro`         VARCHAR(500)    NULL     COMMENT '简介',
  `audio_url`     VARCHAR(512)    NULL     COMMENT '试听片段地址；为空则不展示播放控件',
  `audio_seconds` INT             NULL     COMMENT '试听片段时长（秒）',
  `online`        TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '在线状态：0 离线 1 在线。仅展示，不阻止下单',
  `listed`        TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否上架：0 未上架 1 已上架',
  `sort_order`    INT             NOT NULL DEFAULT 0 COMMENT '展示顺序，小的在前',
  `created_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  -- 一个微信账号最多关联一条主播档案。唯一索引不约束 NULL，故未关联的档案不受限制。
  UNIQUE KEY `uk_talent_user_id` (`user_id`),
  KEY `idx_talent_listed_sort` (`listed`, `sort_order`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '主播档案';

CREATE TABLE IF NOT EXISTS `talent_tag` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `talent_id`  BIGINT UNSIGNED NOT NULL COMMENT '主播档案 id',
  `name`       VARCHAR(32)     NOT NULL COMMENT '标签文案',
  `sort_order` INT             NOT NULL DEFAULT 0 COMMENT '展示顺序，小的在前',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_talent_tag_name` (`talent_id`, `name`),
  KEY `idx_talent_tag_talent_sort` (`talent_id`, `sort_order`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '主播标签';

CREATE TABLE IF NOT EXISTS `talent_media` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `talent_id`     BIGINT UNSIGNED NOT NULL COMMENT '主播档案 id',
  `media_type`    VARCHAR(16)     NOT NULL COMMENT '类型：cover 封面 photo 相册 audio 试听',
  `url`           VARCHAR(512)    NOT NULL COMMENT '资源地址',
  `duration_seconds` INT          NULL     COMMENT '音频时长（秒），非音频为空',
  `sort_order`    INT             NOT NULL DEFAULT 0 COMMENT '展示顺序，小的在前',
  `created_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_talent_media_talent_type` (`talent_id`, `media_type`, `sort_order`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '主播媒体资源';
