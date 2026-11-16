# 埋点事件清单

本文列出全部事件标识及其触发时机。**照本文应当能在代码里逐条找到对应的埋点**；
找不到就是漏埋了，看板上那一级的数字会凭空偏低。

事件定义在 `backend/src/main/java/com/guild/checkout/analytics/EventKey.java`，
枚举顺序即漏斗顺序。新增节点只需在枚举里加一项并在触发处上报，看板会自动把它算进去。

## 节点一览

| 事件标识 | 名称 | 触发时机 | 谁上报 | 归因维度 |
| --- | --- | --- | --- | --- |
| `FOLLOW_ACCOUNT` | 关注服务号 | 微信推送关注事件 | 后端 `POST /api/analytics/follow` | 仅 openId |
| `OPEN_H5` | 打开 H5 | 微信网页授权成功 | 后端 `WechatAuthController.callback` | userId + openId |
| `VIEW_TALENT_LIST` | 浏览主播列表 | 列表页挂载 | 前端 `TalentListView` | — |
| `VIEW_TALENT_DETAIL` | 进入主播详情 | 详情页数据加载完成 | 前端 `TalentDetailView` | 主播、项目 |
| `CLICK_ORDER` | 点击下单 | 点「选择服务下单」或某个档位 | 前端 `TalentDetailView.order()` | 主播、项目 |
| `SUBMIT_ORDER` | 提交订单 | 订单创建成功 | 后端 `OrderService.create` | 主播、项目、订单号 |
| `VIEW_PAYMENT_INFO` | 展示收款信息 | 打开支付页 | 后端 `PaymentController.page` | 主播、项目、订单号 |
| `UPLOAD_PROOF` | 上传支付凭证 | 凭证上传成功 | 后端 `PaymentController.uploadProof` | 主播、项目、订单号 |
| `CONFIRM_PAYMENT` | 确认收款 | 订单转入待接单 | 后端 `OrderService.confirmPayment` | 主播、项目、订单号 |
| `ACCEPT_ORDER` | 主播接单 | 接单成功 | 后端 `OrderService.accept` | 主播、项目、订单号 |
| `COMPLETE_ORDER` | 服务完成 | 标记完成 | 后端 `OrderService.complete` | 主播、项目、订单号 |
| `REPURCHASE_7D` | 7 日内复购 | 完成订单时判定 | 后端 `OrderService.recordRepurchaseIfAny` | 主播、项目、订单号 |

### 为什么有些节点只能后端埋

前端只允许上报 `OPEN_H5` / `VIEW_TALENT_LIST` / `VIEW_TALENT_DETAIL` / `CLICK_ORDER`
这四个浏览类节点，白名单写在 `AnalyticsTrackController.CLIENT_ALLOWED`。

涉及钱与状态的节点一律由后端在状态流转处自己记。这些事件是对账与复盘的依据，
不能让客户端有伪造的余地。前端上报白名单外的事件会被**静默忽略**——不是报错，
因为前端版本比后端新时会出现未知事件，那种情况不该让用户看到异常。

## 扩展属性

`props` 是 JSON 列，目前只有一个节点用到：

- `ACCEPT_ORDER.waitedSeconds`：从确认收款到主播接单等待了多少秒。平均接单时长由它派生。

## 去重口径

漏斗各级统计的是**去重人数**，口径为 `COALESCE(user_id, open_id)`。

关注服务号时用户多半还没打开过 H5、还没建号，所以那条事件只有 openId。
授权成功时写入的 `OPEN_H5` 同时带 userId 和 openId，查询时用 COALESCE
把两者归成同一个人，否则同一个人会被算成两个，关注到打开的转化率会凭空高出一截。

## 四项核心指标

| 指标 | 口径 | 数据源 |
| --- | --- | --- |
| 访问到付费总转化率 | `CONFIRM_PAYMENT` 去重人数 ÷ `OPEN_H5` 去重人数 | 事件表 |
| 主播接单率 | 已接单订单数 ÷ 已付款订单数 | **订单表** |
| 平均接单时长 | 已接单订单的 `paidAt → acceptedAt` 平均值 | **订单表** |
| 7 日复购率 | `REPURCHASE_7D` 去重人数 ÷ `COMPLETE_ORDER` 去重人数 | 事件表 |

**接单率与接单时长刻意从订单表算，不从事件表。** 订单表才是权威，事件可能因为
埋点失败而缺失；而接单率是 proposal 里的否决指标，不能有误差。

分母为零时指标返回 `null` 而不是 0——「没有人访问」和「访问了但零转化」是两回事，
混在一起会让复盘时得出完全错误的结论。看板上显示为「—」。

## 接单率作为否决指标

接单率低于阈值（默认 80%，运营可在看板上调）时：

- 看板顶部出现告警条
- 接单率数字标红
- 总转化率标灰，并注明「参考价值受限」

原因见 proposal：接单率偏低会把「供给没跟上」误记成「产品没转化」。
这种情况下优化产品是南辕北辙，该做的是排查主播接单情况。

## 发现入口归因

每个发现入口有自己的来源标识与点击事件。粉丝由某入口进入主播详情时，详情事件带上
同一个来源；下单时这个来源**快照写进订单**（`service_order.entry_source`），
确认收款事件据此归因——那是运营在后台点的，那一刻早已没有「粉丝从哪来」的上下文，
与价格、分成比例同理必须在下单那一刻钉死。

| 入口 | 来源标识 | 入口点击事件 |
| --- | --- | --- |
| 品类 Tab | `CATEGORY_TAB` | `ENTRY_CATEGORY_TAB` |
| 主播列表 | `TALENT_LIST` | `ENTRY_TALENT_LIST` |
| 人气店员 | `POPULAR` | `ENTRY_POPULAR` |
| 九宫格 | `GRID` | `ENTRY_GRID` |
| 运营横幅 | `BANNER` | `ENTRY_BANNER` |
| 试音选人 | `AUDITION` | `ENTRY_AUDITION` |
| 随机匹配 | `RANDOM_MATCH` | `ENTRY_RANDOM_MATCH` |
| 心动墙 | `CRUSH_WALL` | `ENTRY_CRUSH_WALL` |
| 动态广场 | `FEED` | `ENTRY_FEED` |
| 榜单 | `LEADERBOARD` | `ENTRY_LEADERBOARD` |
| 收藏列表 | `FAVORITES` | `ENTRY_FAVORITES` |
| 站内聊天 | `CHAT` | `ENTRY_CHAT` |
| 直接访问 | `DIRECT` | —（没有可点击的入口） |

几条必须守住的口径：

- **来源列永不为空。** 认不出来、没传、分享链接直接进的，一律记 `DIRECT`（直接访问）。
  允许为空会让拆分表各行之和小于总数，而少掉的那部分没人知道去了哪。
- **`TALENT_LIST` 不在验收点名的 11 个入口之内，是额外加的。** 首页品类 Tab 未选中时
  的默认列表也是真实入口，把它归进 `DIRECT` 会让「直接访问」混进大量并非来自分享的人，
  那一行就不能用来衡量分享效果了。
- **每人每节点只归因一次。** 同一个人可能先从人气店员进详情、又从榜单进一次；
  按「来源 × 人」各算一次，拆分表之和就会大于总数。归因归给该周期内最早的那条事件
  （时间相同再按 id 定序，保证结果可复现）。于是「从人气店员进详情的人数」
  读作「**首次**进详情来自人气店员的人数」。
- **没有数据的入口展示为零，不隐藏那一行。** 「上线一个月一个人没带来」恰恰是最该被
  看见的结论。拆分表从枚举出发补零，而不是从查询结果出发列行。
- **入口点击事件不进漏斗。** `EventKey` 是漏斗节点、顺序即漏斗顺序，把十三个入口塞进去
  会让漏斗凭空多出十三级。入口事件自带 key，单独走 `trackEntryClick` 写入。

拆分表的三级为 `VIEW_TALENT_DETAIL` → `SUBMIT_ORDER` → `CONFIRM_PAYMENT`，
另附入口自身的点击数——点了入口却没进详情也是信息。

## 不预聚合

全部指标用 SQL 现算（design D6）。月事件量在十万以内，预聚合的复杂度远大于收益，
而且预聚合口径一旦写错，错误会被固化下来难以察觉。真到了需要预聚合的量级，
说明业务已经成立，那时再加也不迟。

## 采集失败不影响业务

`AnalyticsService.track` 吞掉所有异常，并且以 `REQUIRES_NEW` 自开事务。

自开事务不是性能优化而是正确性要求：若与主事务共用，埋点抛出的异常会把主事务
标记为 rollback-only，外层 catch 住也救不回来，下单最终仍会失败。
`AnalyticsResilienceTest` 就是为了把那种实现挡在门外。
