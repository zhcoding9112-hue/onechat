-- =============================================================================
-- V2 用户与角色
--
-- 角色用位标记存在同一列：一个微信用户可同时是粉丝与主播（主播本人也会下单），
-- 也可同时是主播与运营。拆成独立布尔列或关联表在 10 主播的体量下只是负担。
--   bit 0 (1) 粉丝    bit 1 (2) 主播    bit 2 (4) 运营
-- 新建用户一律且仅为粉丝；主播与运营位只能由运营在后台授予。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `app_user` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `open_id`     VARCHAR(64)     NOT NULL COMMENT '服务号下的微信 openid',
  `union_id`    VARCHAR(64)     NULL     COMMENT '开放平台 unionid，未绑定开放平台时为空',
  `nickname`    VARCHAR(64)     NULL     COMMENT '微信昵称，获取失败时为空并由前端用占位展示',
  `avatar_url`  VARCHAR(512)    NULL     COMMENT '微信头像地址，获取失败时为空',
  `roles`       INT UNSIGNED    NOT NULL DEFAULT 1 COMMENT '角色位：1 粉丝 2 主播 4 运营，可叠加',
  `created_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_user_open_id` (`open_id`),
  KEY `idx_app_user_union_id` (`union_id`),
  KEY `idx_app_user_roles` (`roles`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统用户';
