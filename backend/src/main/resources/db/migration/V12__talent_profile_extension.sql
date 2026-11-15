-- =============================================================================
-- V12 主播扩展资料：性别、年龄、地区、两个宣传语标签
--
-- 全部可空。库中已有 34 条主播档案没有这些值，设非空约束会让迁移在既有数据上失败；
-- 且这几项在粉丝端是「有就展示、没有就整项不渲染」，不存在默认值语义。
--
-- 宣传语标签固定为两列而非沿用 talent_tag 表：它与能力标签是两种东西。
-- 能力标签是枚举化的服务属性，数量不定、要参与筛选；宣传语是主播自填的自由文案，
-- 固定两条、只做展示、不参与任何查询。混在一张表里会让筛选逻辑被迫区分标签类型。
--
-- 性别存字符串枚举，与 service_order.status 的做法一致：可读性优先，
-- 且后续若出现第三种取值不需要改列类型。
-- =============================================================================

ALTER TABLE `talent`
  ADD COLUMN `gender`      VARCHAR(8)  NULL COMMENT '性别：FEMALE 女 / MALE 男；为空表示未填写' AFTER `nickname`,
  ADD COLUMN `age`         INT         NULL COMMENT '年龄；为空表示未填写' AFTER `gender`,
  ADD COLUMN `region`      VARCHAR(32) NULL COMMENT '地区，如「北京」「广东省」；为空表示未填写' AFTER `age`,
  ADD COLUMN `promo_tag_1` VARCHAR(16) NULL COMMENT '宣传语标签一，主播或运营自填的自由文本' AFTER `intro`,
  ADD COLUMN `promo_tag_2` VARCHAR(16) NULL COMMENT '宣传语标签二，主播或运营自填的自由文本' AFTER `promo_tag_1`;

-- 按性别筛选是粉丝端信息流的默认能力，与上架状态、排序一起走同一个索引。
CREATE INDEX `idx_talent_listed_gender_sort` ON `talent` (`listed`, `gender`, `sort_order`);
