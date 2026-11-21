-- =============================================================================
-- 清理开发期留下的测试数据
--
-- 用法：先只跑「盘点」那一段看数字，确认无误后再跑「清理」那一段。
--
--   # 只盘点，不删任何东西
--   mysql -u root private_checkout < scripts/cleanup-test-data.sql
--
--   # 真的执行清理
--   { echo "SET @CONFIRM='YES';"; cat scripts/cleanup-test-data.sql; } \
--     | mysql -u root private_checkout
--
-- 注意 SOURCE 是 mysql 客户端的内建命令，不能跟在 -e 后面用，只能走标准输入；
-- 所以这里是把 SET 与脚本内容拼起来一起喂给客户端。
--
-- 为什么会有这么多测试数据：有几个测试类刻意不加 @Transactional（它们要验的
-- 就是真实提交下的行为，套一层测试事务什么也验不到），于是造出来的数据不回滚。
-- AnalyticsResilienceTest 此前没有自清，每跑一次就漏四个**已上架**的主播和
-- 四个服务项目——那是开发库里一百多个「小雨」的来源，现已修复。
-- 其余几个并发测试会把主播下架但不删行，留下的是不可见的残留，危害小得多。
--
-- 判定规则：测试夹具的 open_id / code / nickname 一律带 System.nanoTime() 后缀，
-- 也就是结尾一长串数字。靠命名而不是靠创建时间，脚本才能反复跑。
--
-- 什么时候跑：**最后一次跑测试之后**。实测跑一轮 770 条测试会漏回 14 位主播、
-- 6 个账号、10 张券。漏下来的主播都是未上架的（粉丝端看不见），不致命，
-- 但清完再跑一轮测试就等于白清。
--
-- 本脚本只管数据库，**不管 backend/uploads/ 下的文件**。那边要单独对一遍：
-- 把 talent.cover_url、talent.audio_url、talent_media.url、home_entry.image_url、
-- feed_post_image.url、payment_proof.url、payment_setting.qr_url 全取出来，
-- 和 find 的结果比对，两个方向都要看——孤儿文件（白占地方）和裂图（数据库里
-- 有地址、磁盘上没文件，页面不报错只是图裂）。做法见 docs/launch-checklist.md。
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
-- 注意：这一行会把**所有**已发出的券连根拔掉，包括注册时自动发的新人券。
-- 开发期这是对的（券都是测试发的），但上线之后再跑这个脚本就等于收回全体用户的券。
-- 上线后若还要用本脚本，先把这一行注释掉，或者改成只删测试账号名下的。
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

-- 扫一遍孤儿行：引用的主播或档位已经不在了的价格行。
-- 上面按「测试主播」「测试项目」删，删不掉那些指向更早就消失的主播的残留——
-- 开发库里就有一条 talent_id = 1 的价格行，而 id 1 的主播早已不存在。
DELETE p FROM talent_price p
LEFT JOIN talent t ON t.id = p.talent_id
LEFT JOIN service_spec s ON s.id = p.service_spec_id
WHERE @go = 1 AND (t.id IS NULL OR s.id IS NULL);

DELETE x FROM talent_tag x LEFT JOIN talent t ON t.id = x.talent_id
WHERE @go = 1 AND t.id IS NULL;
DELETE x FROM talent_media x LEFT JOIN talent t ON t.id = x.talent_id
WHERE @go = 1 AND t.id IS NULL;
DELETE x FROM service_spec x LEFT JOIN service_item i ON i.id = x.service_item_id
WHERE @go = 1 AND i.id IS NULL;
DELETE x FROM user_coupon x LEFT JOIN app_user u ON u.id = x.user_id
WHERE @go = 1 AND u.id IS NULL;
DELETE x FROM crush_wall_pick x LEFT JOIN talent t ON t.id = x.talent_id
WHERE @go = 1 AND t.id IS NULL;

DROP TEMPORARY TABLE IF EXISTS tmp_test_talent;
DROP TEMPORARY TABLE IF EXISTS tmp_test_user;
DROP TEMPORARY TABLE IF EXISTS tmp_test_item;

-- ---------------------------------------------------------------------------
-- 三、清理后复核
-- ---------------------------------------------------------------------------
SELECT 'talent_price 孤儿（应为 0）' AS 表, COUNT(*) AS n
FROM talent_price p
LEFT JOIN talent t ON t.id = p.talent_id
LEFT JOIN service_spec s ON s.id = p.service_spec_id
WHERE t.id IS NULL OR s.id IS NULL
UNION ALL
SELECT 'talent 剩余', COUNT(*) FROM talent
UNION ALL SELECT 'talent 已上架', COUNT(*) FROM talent WHERE listed = 1
UNION ALL SELECT 'service_item 剩余', COUNT(*) FROM service_item
UNION ALL SELECT 'app_user 剩余', COUNT(*) FROM app_user
UNION ALL SELECT 'service_order 剩余', COUNT(*) FROM service_order;
