-- =============================================================================
-- V21 入口归因
--
-- 两条约定：
--
-- 1. entry_source 不可为空，没有来源的记 DIRECT（直接访问）。
--    允许为空会让「按入口拆分」的各行之和小于总数，而少掉的那部分
--    没人知道去了哪——拆分表最要紧的性质就是加起来等于总数。
--    ADD COLUMN 带 DEFAULT 时既有行会拿到该默认值，而 DIRECT 对历史行
--    恰好是正确的口径（那时还没有入口归因），所以这里不需要额外回填。
--
-- 2. 订单上也存一份来源快照。确认收款是运营在后台点的，那一刻早已没有
--    「粉丝是从哪个入口来的」这个上下文；与价格、分成比例一样，
--    归因必须在下单那一刻就钉死。
-- =============================================================================

ALTER TABLE `analytics_event`
  ADD COLUMN `entry_source` VARCHAR(32) NOT NULL DEFAULT 'DIRECT'
    COMMENT '发现入口来源，见 EntrySource；无来源记 DIRECT' AFTER `event_key`;

-- 按入口拆分漏斗时的主要查询形态：限定时间与事件后按来源分组
ALTER TABLE `analytics_event`
  ADD KEY `idx_event_entry` (`event_key`, `entry_source`, `occurred_at`);

ALTER TABLE `service_order`
  ADD COLUMN `entry_source` VARCHAR(32) NOT NULL DEFAULT 'DIRECT'
    COMMENT '下单时的发现入口快照，用于把后续的收款事件归因回入口' AFTER `remark`;
