-- =============================================================================
-- V20 站内聊天：会话与消息
--
-- 几条贯穿本组的约定：
--
-- 1. 会话对 (fan_user_id, talent_id) 唯一。重复发起不新建，靠唯一索引兜底，
--    不靠「先查后插」——先查后插在并发下会插出两条会话，
--    两边各写各的消息，谁也看不见谁。
--
-- 2. 未读数不冗余成计数列，用「已读水位线」算。
--    每侧只记「我读到哪条消息 id 为止」，未读 = 比水位线新、且不是我发的、且可见的消息数。
--    冗余计数要在发消息、读会话、运营下架消息每条路径上加减，漏一条就对不上；
--    而水位线只在「打开会话」这一处前移，算错了下次打开就自愈。
--
-- 3. last_message_at 反过来**是**冗余的，因为会话列表要按它排序。
--    它只在发消息这一条路径上更新，不存在多路径漏改的问题；
--    下架一条消息不改它——下架不该让会话在列表里跳位置。
--
-- 4. 只有文字与图片。config.yaml 明确不承载音视频通话，
--    这条约束写进 CHECK，而不是只写在文档里。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `chat_conversation` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `fan_user_id`     BIGINT UNSIGNED NOT NULL COMMENT '粉丝 app_user.id',
  `talent_id`       BIGINT UNSIGNED NOT NULL COMMENT '主播档案 talent.id',

  `last_message_id` BIGINT UNSIGNED NULL COMMENT '最后一条消息 id；尚无消息时为空',
  -- 排序键。尚无消息时等于会话创建时间，这样刚发起的空会话也排在最前，
  -- 而不是因为 NULL 掉到列表末尾。
  `last_message_at` DATETIME(3)     NOT NULL COMMENT '最后一条消息时间；无消息时为会话创建时间',

  -- 已读水位线：各自记到哪条消息为止算读过。只增不减，见 ChatService#markRead。
  `fan_read_until`    BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '粉丝已读到的消息 id',
  `talent_read_until` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '主播已读到的消息 id',

  `created_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',

  PRIMARY KEY (`id`),
  -- 「一对粉丝与主播只有一个会话」的最终保证在这里，不在应用层的 if 判断上
  UNIQUE KEY `uk_chat_pair` (`fan_user_id`, `talent_id`),
  KEY `idx_chat_fan_recent` (`fan_user_id`, `last_message_at`),
  KEY `idx_chat_talent_recent` (`talent_id`, `last_message_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '粉丝与主播的一对一会话';

CREATE TABLE IF NOT EXISTS `chat_message` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `conversation_id` BIGINT UNSIGNED NOT NULL COMMENT 'chat_conversation.id',

  -- 发送方同时记角色与 app_user.id：
  -- 角色决定消息气泡落在哪一边与未读算给谁，user_id 用于纠纷追溯到具体账号。
  `sender_role`     VARCHAR(8)      NOT NULL COMMENT '发送方：FAN 粉丝 / TALENT 主播',
  `sender_user_id`  BIGINT UNSIGNED NOT NULL COMMENT '发送者 app_user.id',

  `content_type`    VARCHAR(8)      NOT NULL COMMENT '类型：TEXT 文字 / IMAGE 图片',
  `content`         VARCHAR(1000)   NOT NULL COMMENT '文字正文，或图片地址',

  `status`          VARCHAR(16)     NOT NULL DEFAULT 'VISIBLE' COMMENT '状态：VISIBLE 可见 / REMOVED 已被运营下架',
  -- 下架留痕三件套。与动态一致：内容原样保留，供纠纷追溯
  `removed_by`      BIGINT UNSIGNED NULL     COMMENT '下架操作者 app_user.id',
  `removed_at`      DATETIME(3)     NULL     COMMENT '下架时间',
  `remove_reason`   VARCHAR(255)    NULL     COMMENT '下架原因',

  `created_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发送时间',
  `updated_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',

  PRIMARY KEY (`id`),
  -- 会话内按 id 升序读；同一毫秒内多条也有确定顺序，双方看到的序列因此一致
  KEY `idx_chat_message_conv` (`conversation_id`, `id`),
  KEY `idx_chat_message_sender` (`sender_user_id`, `id`),
  CONSTRAINT `ck_chat_sender_role` CHECK (`sender_role` IN ('FAN', 'TALENT')),
  -- 不承载音视频通话：这条产品约束落到数据层，而不是只落在文档里
  CONSTRAINT `ck_chat_content_type` CHECK (`content_type` IN ('TEXT', 'IMAGE'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '会话消息';
