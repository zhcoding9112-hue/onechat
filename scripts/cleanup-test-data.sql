-- =============================================================================
-- 清理开发期留下的测试数据
--
-- 用法：先只跑「盘点」那一段看数字，确认无误后再跑「清理」那一段。
--
--   mysql -u root private_checkout < scripts/cleanup-test-data.sql        # 只盘点
--   mysql -u root private_checkout -e "SET @CONFIRM='YES'; SOURCE scripts/cleanup-test-data.sql"
--
-- 为什么会有这么多测试数据：有几个测试类刻意不加 @Transactional（它们要验的
-- 就是真实提交下的行为，套一层测试事务什么也验不到），于是造出来的数据不回滚。
-- AnalyticsResilienceTest 此前没有自清，每跑一次就漏四个**已上架**的主播和
-- 四个服务项目——那是开发库里一百多个「小雨」的来源，现已修复。
-- 其余几个并发测试会把主播下架但不删行，留下的是不可见的残留，危害小得多。
--
-- 判定规则：测试夹具的 open_id / code / nickname 一律带 System.nanoTime() 后缀，
-- 也就是结尾一长串数字。靠命名而不是靠创建时间，脚本才能反复跑。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 一、盘点：这段只读，先看清要删什么
-- ---------------------------------------------------------------------------
SELECT '以下是将被删除的数量，确认后再执行清理段' AS note;

SELECT 'app_user（测试账号）' AS 表, COUNT(*) AS 将删除
FROM app_user WHERE open_id REGEXP '[0-9]{12,}$'
UNION ALL
SELECT 'talent（测试主播）', COUNT(*)
FROM talent WHERE nickname REGEXP '[0-9]{10,}$'
   OR (nickname IN ('小雨', '收藏测试主播', '后收藏的') AND id > 22)
UNION ALL
SELECT 'service_item（测试服务项目）', COUNT(*)
FROM service_item WHERE code REGEXP '[0-9]{12,}$'
UNION ALL
SELECT 'service_order（全部，开发期订单无保留价值）', COUNT(*) FROM service_order
UNION ALL
SELECT 'analytics_event（全部，口径已变过多次）', COUNT(*) FROM analytics_event;

SELECT '以下是将被保留的账号，请确认里面只有真人与本机调试桩' AS note;
SELECT id, open_id, nickname, roles FROM app_user
WHERE open_id NOT REGEXP '[0-9]{12,}$' ORDER BY id;

-- ---------------------------------------------------------------------------
-- 二、清理：需要 @CONFIRM='YES' 才真的执行
--
-- 每条 DELETE 都带 @CONFIRM 条件，直接 source 本文件只会盘点，不会删任何东西。
-- 删除顺序按引用方向来：先删依赖方，再删被依赖方。
-- ---------------------------------------------------------------------------
SET @go = (SELECT IF(@CONFIRM = 'YES', 1, 0));
SELECT IF(@go = 1, '开始清理', '未设置 @CONFIRM=YES，本次只盘点，未删除任何数据') AS note;

-- 测试主播与测试账号的 id 先各自定好，后面反复用
CREATE TEMPORARY TABLE IF NOT EXISTS tmp_test_talent (id BIGINT UNSIGNED PRIMARY KEY);
CREATE TEMPORARY TABLE IF NOT EXISTS tmp_test_user (id BIGINT UNSIGNED PRIMARY KEY);
CREATE TEMPORARY TABLE IF NOT EXISTS tmp_test_item (id BIGINT UNSIGNED PRIMARY KEY);

INSERT IGNORE INTO tmp_test_talent
SELECT id FROM talent
WHERE @go = 1
  AND (nickname REGEXP '[0-9]{10,}$'
       OR (nickname IN ('小雨', '收藏测试主播', '后收藏的') AND id > 22));

INSERT IGNORE INTO tmp_test_user
SELECT id FROM app_user WHERE @go = 1 AND open_id REGEXP '[0-9]{12,}$';

INSERT IGNORE INTO tmp_test_item
SELECT id FROM service_item WHERE @go = 1 AND code REGEXP '[0-9]{12,}$';

-- 互动与内容
DELETE FROM feed_like          WHERE @go = 1;
DELETE FROM feed_comment       WHERE @go = 1;
DELETE FROM feed_post_image    WHERE @go = 1;
DELETE FROM feed_post          WHERE @go = 1;
DELETE FROM content_report     WHERE @go = 1;
DELETE FROM crush_wall_pick    WHERE @go = 1;
DELETE FROM chat_message       WHERE @go = 1;
DELETE FROM chat_conversation  WHERE @go = 1;
DELETE FROM talent_favorite    WHERE @go = 1;

-- 钱与台账
DELETE FROM settlement_entry   WHERE @go = 1;
DELETE FROM settlement_mark    WHERE @go = 1;
DELETE FROM payment_proof      WHERE @go = 1;
DELETE FROM payment_record     WHERE @go = 1;
DELETE FROM payment_event_log  WHERE @go = 1;
DELETE FROM satisfaction_survey WHERE @go = 1;
DELETE FROM order_status_log  WHERE @go = 1;
DELETE FROM service_order      WHERE @go = 1;
DELETE FROM tip_record         WHERE @go = 1;
DELETE FROM credit_transaction WHERE @go = 1;
DELETE FROM credit_account     WHERE @go = 1;
DELETE FROM user_coupon        WHERE @go = 1;

-- 榜单与埋点：口径在开发期变过多次，留着只会误导
DELETE FROM leaderboard_snapshot WHERE @go = 1;
DELETE FROM leaderboard_total    WHERE @go = 1;
DELETE FROM analytics_event      WHERE @go = 1;

-- 试音
DELETE FROM audition_reply   WHERE @go = 1;
DELETE FROM audition_request WHERE @go = 1;

-- 价格、档位、项目、标签、主播、账号
DELETE FROM talent_price WHERE @go = 1
  AND (talent_id IN (SELECT id FROM tmp_test_talent)
       OR service_spec_id IN (SELECT id FROM service_spec
                              WHERE service_item_id IN (SELECT id FROM tmp_test_item)));
DELETE FROM service_spec WHERE @go = 1 AND service_item_id IN (SELECT id FROM tmp_test_item);
DELETE FROM service_item WHERE @go = 1 AND id IN (SELECT id FROM tmp_test_item);
DELETE FROM talent_tag   WHERE @go = 1 AND talent_id IN (SELECT id FROM tmp_test_talent);
DELETE FROM talent_media WHERE @go = 1 AND talent_id IN (SELECT id FROM tmp_test_talent);
DELETE FROM talent       WHERE @go = 1 AND id IN (SELECT id FROM tmp_test_talent);
DELETE FROM app_user     WHERE @go = 1 AND id IN (SELECT id FROM tmp_test_user);

DROP TEMPORARY TABLE IF EXISTS tmp_test_talent;
DROP TEMPORARY TABLE IF EXISTS tmp_test_user;
DROP TEMPORARY TABLE IF EXISTS tmp_test_item;

-- ---------------------------------------------------------------------------
-- 三、清理后复核
-- ---------------------------------------------------------------------------
SELECT 'talent 剩余' AS 表, COUNT(*) AS n FROM talent
UNION ALL SELECT 'talent 已上架', COUNT(*) FROM talent WHERE listed = 1
UNION ALL SELECT 'service_item 剩余', COUNT(*) FROM service_item
UNION ALL SELECT 'app_user 剩余', COUNT(*) FROM app_user
UNION ALL SELECT 'service_order 剩余', COUNT(*) FROM service_order;
