-- =============================================================================
-- V14 粉丝收藏主播
--
-- (user_id, talent_id) 建唯一索引，幂等由数据库保证而不是先查后插：
-- 先查后插在并发下必然出现双写窗口，唯一索引是唯一可靠的那道闸。
--
-- 不做软删除：取消收藏就是删行。收藏关系没有审计价值，
-- 留着 deleted 标记只会让唯一索引失效、查询多一个条件。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `talent_favorite` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '粉丝 app_user.id',
  `talent_id`  BIGINT UNSIGNED NOT NULL COMMENT '主播 talent.id',
  `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '收藏时间',
  `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_favorite_user_talent` (`user_id`, `talent_id`),
  -- 收藏列表按收藏时间倒序展示，走这个索引
  KEY `idx_favorite_user_created` (`user_id`, `created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '粉丝收藏的主播';
