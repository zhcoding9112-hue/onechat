# 公会私域收银台

微信服务号内的移动端 H5：粉丝指定主播下单付费，通话在站外交付，系统承担撮合、收款、
结算记账与履约追踪。粉丝端、主播工作台、运营后台共用一套前端工程与后端服务。

需求与规格见 `openspec/changes/add-private-checkout-mvp/`。

## 依赖版本

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17（本仓库固定） | 构建会校验，非 17 直接失败 |
| Maven | 3.9+ | 本机 3.9.16 |
| MySQL | 8.0+ | 本机 8.0.46 |
| Redis | 6.2+ | 本机 6.2.18 |
| Node.js | 20+ | 本机 24.16.0 |
| npm | 10+ | 本机 11.13.0 |

### JDK 必须显式指向 17

本机 Homebrew 的 `mvn` 在 `JAVA_HOME` 未设置时会默认用 Homebrew JDK（当前为 26），
而本项目固定 Java 17。**每个新终端在构建前先执行：**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

忘了也不会悄悄跑错：`maven-enforcer-plugin` 会让构建直接失败并提示这条命令。

## 建库

MySQL 与 Redis 需已启动（本机由 Homebrew 托管：`brew services start mysql@8.0 redis@6.2`）。

```bash
mysql -u root -e "CREATE DATABASE IF NOT EXISTS private_checkout DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
```

建完空库即可，表结构由 Flyway 在应用启动时自动迁移，不需要手工执行任何 DDL。

## 配置项

全部配置都有默认值，本机开发通常无需改动。需要覆盖时用环境变量：

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DB_HOST` | `127.0.0.1` | MySQL 主机 |
| `DB_PORT` | `3306` | MySQL 端口 |
| `DB_NAME` | `private_checkout` | 库名 |
| `DB_USER` | `root` | 用户名 |
| `DB_PASSWORD` | 空 | 密码 |
| `REDIS_HOST` | `127.0.0.1` | Redis 主机 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `REDIS_DB` | `0` | Redis 库号 |
| `REDIS_PASSWORD` | 空 | Redis 密码 |
| `VITE_API_TARGET` | `http://127.0.0.1:8080` | 前端开发代理指向的后端地址 |
| `SPRING_PROFILES_ACTIVE` | `local` | 本机开发用 `local`；生产必须覆盖 |
| `SESSION_COOKIE_SECURE` | `false` | 生产走 HTTPS 时置 `true` |
| `SERVER_FORWARD_HEADERS_STRATEGY` | `none` | 置于 Nginx 之后时设 `framework` |
| `WECHAT_APP_ID` | 空 | 服务号 AppID |
| `WECHAT_APP_SECRET` | 空 | 服务号 AppSecret |
| `WECHAT_OAUTH_SCOPE` | `snsapi_userinfo` | 授权作用域 |
| `APP_BASE_URL` | `http://127.0.0.1:5173` | 微信回调所用的对外基地址 |

### 微信网页授权

未配置 `WECHAT_APP_ID` / `WECHAT_APP_SECRET` 时，本机会使用**授权桩**：任何 code 都被当作 openid
直接登录，用于不依赖微信也能跑通全链路。桩只在 profile 为 `local` / `test` / `dev` 时允许启用；
其他 profile 下缺配置会在启动时直接失败，**不会**退化成「谁都能登录」。

接真实服务号（或测试号）时：

```bash
export SPRING_PROFILES_ACTIVE=local
export WECHAT_APP_ID=<你的 AppID>
export WECHAT_APP_SECRET=<你的 AppSecret>
export APP_BASE_URL=<公网可达的基地址，需与公众平台配置的网页授权域名一致>
```

注意 `APP_BASE_URL` 必须是微信能回跳的公网地址。本机联调需用内网穿透，
且该域名要填进公众平台的「网页授权域名」，否则微信会拒绝回调。

本地私有配置写在 `backend/src/main/resources/application-local.yml`，该文件已被 `.gitignore` 忽略。

## 启动

### 后端（端口 8080）

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17) && cd backend && mvn clean package && java -jar target/private-checkout.jar
```

验证：

```bash
curl -s http://127.0.0.1:8080/actuator/health
```

应返回 `"status":"UP"`，且 `db` 与 `redis` 两个组件均为 `UP`。

### 前端（端口 5173）

```bash
cd frontend && npm install && npm run dev
```

打开 **http://127.0.0.1:5173** ，建议用浏览器的移动端视口（375×812）查看。
前端的 `/api` 请求由 Vite 代理转发到后端，不存在跨域问题。

#### 在手机微信里看

手机连同一个 Wi-Fi，微信里打开 `http://<本机局域网IP>:5173`（IP 看 `npm run dev` 启动时
打印的 Network 那行，或 `ipconfig getifaddr en0`）。

授权回调会跟着请求进来的地址走——用桩的时候按请求自己的 Host 回跳，
所以 `localhost` / `127.0.0.1` / 局域网 IP 三种进法都能走通，不用改配置。
（配了真实 AppID 之后就只认 `APP_BASE_URL`，微信后台登记的网页授权域名必须和它一致。）

打不开时按这个顺序查：

1. 手机和电脑是不是同一个 Wi-Fi
2. `ipconfig getifaddr en0` 看 IP 变没变（DHCP 会换）
3. 系统设置 → 网络 → 防火墙，开着的话放行 node 和 java
4. `curl -I http://<局域网IP>:5173/` 在电脑上先试，200 才说明服务本身是通的

> **桌面浏览器为什么能进得去。** 后端有一道 UA 门禁：授权入口只认 UA 里的
> `MicroMessenger`（或微信开发者工具的 `wechatdevtools`），其余一律跳「请在微信中打开」。
> 所以 Vite 的 `/api` 代理在**开发期**把转发出去的 User-Agent 换成了微信的
> （见 `frontend/vite.config.ts` 里的 `fakeWechatUserAgent`）。
> 请求本来就带微信标记时不覆盖，真机和开发者工具联调不受影响。
> 这段只挂在 `server.proxy` 上，生产由 nginx 转发，不经过它。
>
> UA 只骗得过「引导去微信打开」这个体验判断，不是安全边界——
> 身份仍然由微信授权建立，本机能一路走完是因为还开着授权桩（见上面的配置项）。

### 生产构建

```bash
./scripts/build.sh
```

一条命令产出 `build/private-checkout.jar`：前端静态文件已打进去，
`java -jar` 后同时提供 API 与页面，不需要单独部署 Nginx 托管前端。

### 备份与恢复

```bash
./scripts/backup-db.sh
```

建议在服务器上装成每日任务。恢复演练：

```bash
./scripts/restore-db.sh backups/private_checkout-<时间戳>.sql.gz
```

默认恢复到 `private_checkout_restore` 而不是覆盖生产库，脚本会拒绝直接覆盖。

## 测试

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17) && cd backend && mvn test
```

后端测试依赖本机 MySQL 与 Redis 已启动。

### 本机调试需带微信标识

授权入口会校验客户端标识：非微信内置浏览器一律导向引导页，不发起授权。
用 curl 调试时请带上微信客户端标识：

```bash
curl -A "Mozilla/5.0 (iPhone) MicroMessenger/8.0.44" "http://127.0.0.1:8080/api/auth/wechat/authorize?redirect=%2F"
```

## 尚未验证的事项

- **部署相关的三项需要真实服务器**：`tasks.md` 的 13.2（云服务器环境与一次部署）、
  13.3（服务号网页授权域名与接口权限）、13.4 的「每日定时」部分。
  构建脚本、备份脚本与恢复演练都已在本机验证通过，缺的是把它们装到服务器上。
  逐步清单见 [`docs/deploy.md`](docs/deploy.md)——其中第二节可以**现在就做**，
  用微信测试号加内网穿透把 2.2 验掉，不必等备案和服务器。


- **微信网页授权尚未在真实测试号上跑通**。代码已完整实现（授权跳转、state 防重放、
  回调换取 openid、建号、建立会话、资料获取失败降级），并以假微信服务端做了单元测试覆盖，
  本机用授权桩走通了全链路。但 `tasks.md` 的 2.2 要求「在测试号环境完成一次授权后库中新增用户记录」，
  这一步需要微信测试号的 AppID/AppSecret 与一个可回跳的公网域名，尚未执行，因此 2.2 未勾选。


## 目录结构

```
backend/            Spring Boot 3 + MyBatis-Plus + Flyway
  src/main/java/com/guild/checkout/
    common/api/       统一响应结构 ApiResponse 与错误码 ErrorCode
    common/exception/ 业务异常体系
    common/web/       全局异常处理
    auth/             微信网页授权（跳转、回调、state 校验）
    auth/wechat/      微信授权端口、真实实现与本机桩
    session/          会话查询
    pricing/          服务目录、价格、分成配置与下单计价快照
    storage/          文件存储端口与本地实现（试听片段、后续的支付凭证）
    analytics/        埋点采集、转化漏斗与核心指标
    order/            订单状态机、订单号生成、状态推进与留痕
    payment/          支付端口、收款事件幂等消费、人工收款与对账
    settlement/       分账台账、结算报表与导出、线下结清标记
    survey/           满意度回访：粉丝提交、主播只见聚合、运营见逐单
    tipping/          打赏：复用分成与支付链路，不进订单状态流转
    talent/           主播档案、粉丝端目录、主播工作台
    user/             用户与角色位模型
  src/main/resources/db/migration/   Flyway 迁移脚本
frontend/           Vue 3 + Vite + TS + Vant 4 + Pinia
  src/api/            统一请求封装（自动带会话、统一错误提示、未认证重新授权）
  src/admin/          运营后台（/admin 路由，Element Plus 按需引入到异步分块）
  src/talent/         主播工作台（/talent 路由，同一工程的身份态）
  src/router/         路由
  src/stores/         Pinia
  src/utils/          金额与基点换算
  src/views/          粉丝端页面
docs/               接入文档
  deploy.md             部署清单（服务器、HTTPS、微信配置、上线核对）
  payment-provider.md   新增支付方式
  settlement.md         分账口径与取整规则
  analytics.md          埋点事件清单
  ops-runbook.md        运营手册（对账、退款、异常处置、月结）
scripts/            构建与备份脚本
openspec/           需求规格与任务
```

## 约定

- 金额一律 `BIGINT` 存「分」，禁止浮点。
- 分成比例一律 `INT` 存「基点」（万分之一），禁止百分比小数。
- 价格与分成比例在下单瞬间快照写入订单，后续调整不影响历史订单。
- 不使用数据库外键，关联完整性由应用层保证。
- 每张表带 `created_at` / `updated_at`，类型 `DATETIME(3)`。
- 布尔语义一律 `TINYINT(1)`。MySQL 8 会报 `Integer display width is deprecated` 警告，
  这是预期的：去掉宽度会让 Connector/J 不再把它映射成 Boolean，故保留，该警告可忽略。
- **已执行过的迁移文件一律不得修改**，连注释也不行——Flyway 校验和会变，应用将拒绝启动。
  需要调整已有表结构时新增一个迁移，不要回头改旧的。
- **两个 `Long` 之间一律用 `Objects.equals` 而非 `==`**。`==` 比的是引用，对超出
  Integer 缓存范围（>127）的 id 恒为 false，会把本人误判成无关者。
- **带 `@Version` 的实体必须给版本号初值**（如 `private Integer version = 0;`）。
  MyBatis-Plus 的乐观锁拦截器在版本号为 null 时会跳过版本条件，并发保护会静默失效。
- **MyBatis-Plus 的 `updateById` 会跳过 null 字段**，所以「把某字段清空」用实体更新是无效的。
  凡是可以被置空的字段一律用 `lambdaUpdate().set(...)` 显式写。
- **mock 外部接口时，应答的 Content-Type 必须照抄对方的真实行为**，不要想当然用
  `application/json`。微信 `/sns/oauth2/*` 返回的是 JSON 内容，但 Content-Type 标的是
  `text/plain`；单测当初全部以 `application/json` 应答，恰好绕开了真实世界唯一的差异，
  于是测试全绿而真机授权必挂（`no suitable HttpMessageConverter found`）。
  同理，调外部接口的 `RestClient` / `RestTemplate` 构造应抽成一处供生产与单测共用，
  不要让测试自己 `RestClient.builder()` 另起一份配置。
- **Element Plus 只给运营后台用，不得在 `main.ts` 全局注册**。粉丝端是微信里的移动 H5，
  首屏体积直接影响转化率。全局注册会让首屏从 324 KB 涨到 1595 KB。
  后台组件通过 `unplugin-vue-components` 按需引入，随 `/admin` 路由进异步分块。
- **MyBatis-Plus 的驼峰转下划线不会在字母与数字之间加分隔**：`promoTag1` 映射出的列名是
  `promo_tag1` 而非 `promo_tag_1`。字段名以数字结尾时必须用 `@TableField` 显式声明列名，
  否则运行时报 `Unknown column`，编译期毫无提示。
- **设计令牌是粉丝端唯一的色值来源**，组件与页面一律引用 `--c-*`，不得写死色值。
  Vant 的主题变量在 `styles.css` 末尾统一映射到令牌；漏掉这层映射，改主色就只改到自己写的
  那部分样式，Vant 组件纹丝不动。衍生色（`--c-brand-deep` / `--c-brand-soft`）由
  `color-mix` 从主色派生，并保留静态兜底值供不支持 `color-mix` 的老 WebView 使用。
- **随机类查询必须关掉 MyBatis 一级缓存**。同一个 SqlSession 内，相同语句加相同参数
  会直接复用缓存行，`ORDER BY RAND()` 压根不会被重新执行。生产上每个请求是独立
  session 所以不显形，但在一次事务里连续匹配会一直拿到同一个人。
  解法是给该语句加 `@Options(flushCache = Options.FlushCachePolicy.TRUE)`。
- **`<script setup>` 的顶层不是模块作用域**。它是 setup 函数体，每个组件实例都会重新执行，
  在那里声明的 `let` 是实例私有的。需要跨实例共享的东西（如「同时只播一条试听」的登记）
  必须放进独立模块，否则代码看上去完全正确而约束根本没生效。
  本项目的试听互斥登记在 `frontend/src/utils/audioRegistry.ts`。
- **撞唯一索引的 INSERT 会先加 S 锁，别和同行的 UPDATE 放进同一个事务**。InnoDB 里一条
  重复键 INSERT 在报错前会对既存那行加 S 锁；若同一事务紧接着要 UPDATE 同一行（典型场景：
  「没账户就开户，然后改余额」），持 S 锁的事务要升 X 锁，而并发线程也持着该行的 S 锁，
  于是互相等成环。实测 16 线程并发首次变动必现 `DeadlockLoserDataAccessException`。
  解法是把开户写成 `INSERT ... ON DUPLICATE KEY UPDATE`：冲突时直接取 X 锁，线程排队而非成环。
  **不要**改用 `REQUIRES_NEW` 的独立事务来绕——死锁确实没了，但每个请求在变动期间同时占
  两条连接，默认连接池大小下并发一上来就抽干（现象是 `CannotCreateTransactionException`）。
- **余额这类不可为负的数，判断要写进 UPDATE 的 WHERE 里**。
  `UPDATE ... SET balance = balance + #{delta} WHERE balance + #{delta} >= 0` 是单条语句，
  MySQL 对该行当前读并持锁，并发事务排队后基于新值判断，不会超扣。
  写成「先 SELECT 余额、应用层比较、再 UPDATE」在并发下必然超扣：两个事务都读到同样的旧值。
  表上再加一条 CHECK 约束兜底，防止日后有人绕过服务层直接写库。
- **MySQL 的 CHECK 违反（error 3819）不是 `DataIntegrityViolationException`**。
  Spring 把它归到 `UncategorizedSQLException`，断言时按 `DataAccessException` 加约束名匹配。
- **订单的「原价」与「实付」必须是两个字段**。用券后，分成按原价算、收款按实付算：
  券的抵扣由公会承担，主播分成一分不少。本项目里 `payable_cents` 保持「粉丝要付的钱」
  这个含义不变（支付侧一行没改），新增 `original_cents` 作为分成基数，
  `coupon_discount_cents` 记抵扣额。台账 `settlement_entry` 也同步加了 `promo_cost_cents`，
  因为结算报表只读台账不回查订单表（见 V9 注释），促销成本必须在记账那一刻就落下来。
- **加字段的迁移要显式回填历史行**。`ADD COLUMN ... NOT NULL DEFAULT 0` 只对新行生效，
  已有行会拿到 0。`original_cents` 若留 0，历史订单的分成基数就全变成 0 了，
  所以迁移里跟一条 `UPDATE ... SET original_cents = payable_cents`。
- **券随订单状态走的钩子挂在状态机入口，不要逐个业务方法去挂**。取消、退款、支付超时、
  接单超时是四条不同的路径，逐个挂钩子迟早漏一条，而漏掉的那条会把粉丝的券永久锁死。
  本项目挂在 `OrderStateService.transition`——所有状态变更的唯一入口。
- **Vant 的 Radio 不渲染原生 input，外层 `<label>` 不会把点击转给它**。
  包一层 label 看着能点，实际只有那个小圆圈能点中，手机上命中区域小得离谱。
  整行可选要显式在容器上绑 `@click`。
- **按业务时间归期，不要按「当前时间」归期**。榜单累计按打赏的收款时间落到日/周/月，
  退款时也从同一个周期扣回。若按当前时间归期，一笔上月收款、本月退款的打赏会让本月
  凭空多出一笔负数，上月则永远是错的。按业务时间归期后，每个周期的累计只由属于它的
  流水构成，也就不会被减成负数。
- **已公示的历史结果固化后不再改**。榜单快照一经固化，即使该周期事后发生退款导致累计值
  变化，快照也不动——把已经公示过的名次事后改掉等于让排名翻案。固化靠
  `(period_type, period_key, talent_id)` 唯一索引保证幂等，并在读历史周期时惰性触发，
  不依赖定时任务（与试音需求的过期关闭同理）。
- **由可配参数派生的值即时算，不要落库**。粉丝等级按累计消费和运营可配的门槛实时算出。
  落库的等级在运营改门槛那一刻就全体过期，还得另想办法重算一遍；算出来的等级则永远与
  当前门槛一致，没有「忘了刷新」这回事。累计消费同理直接 SUM 原表，不另建累计表——
  累计表要在下单、收款、退款、超时每条路径上维护，漏一条就与真实流水对不上，而对不上
  没人会发现。到了扛不住的量级再加缓存层，那时至少有一份可对账的真值。
- **MyBatis-Plus 的 insert 不回写数据库默认值**。建表时 `created_at` / `updated_at` 都有
  `DEFAULT CURRENT_TIMESTAMP(3)`，库里的值一直是对的，但 insert 只会把自增主键回写进实体。
  于是「插入后直接把实体返回给调用方」的接口，响应里时间字段是 null——本项目里一度有五处
  这样的接口（发布评论、运营发放元子、提交满意度、上传支付凭证、创建打赏），
  直到有一条用例去断言发布时间才暴露。现在由 `common/db/AuditTimeFillHandler` 统一自动填充，
  实体上以 `@TableField(fill = ...)` 标注；`strictInsertFill` 只在字段为 null 时填，
  显式设过值的地方不受影响，表上的 DEFAULT 也保留作为绕过 ORM 写库时的兜底。
- **内容下架是软的**。动态与评论下架只改 status，原文与图片原样保留，另记
  `removed_by` / `removed_at` / `remove_reason` 三列。删行就查不到「这条曾经发过什么」，
  申诉与纠纷时无从追溯。重复下架不覆盖首次留痕——第二次操作者不该把第一次的记录抹掉。
  粉丝侧的每个读取路径都只认 VISIBLE；运营侧单独有一组读全集的方法。
- **列表页的计数按需分组查，不冗余在主表上**。点赞数、评论数要在点赞、取消点赞、评论、
  下架评论每条路径上维护，漏一条就与真实行数对不上，而对不上没人会发现。
  一页十来条内容，一条 `WHERE id IN (...) GROUP BY` 足够。
- **REPEATABLE READ 下，「先查一次 → 插入 → 再查一次」拿不到别人刚插的行**。
  MySQL 默认隔离级别下，事务的读视图在**第一次普通读**时就定格。会话的
  `open()` 先查了一次「有没有现成会话」，视图停在那一刻；真正插入这行的是另一个并发事务，
  它提交得再早也在视图之外，于是第二次普通读仍然查不到，`INSERT ... ON DUPLICATE KEY UPDATE`
  明明成功了却报「创建失败」。16 线程同时发起会话时有 3 个踩中。
  补回的那次读必须是加锁读（`FOR SHARE`）——加锁读不走读视图，读的是最新已提交版本。
- **已读状态用「水位线」记，不用未读计数列**。每侧只记「我读到哪条消息 id 为止」，
  未读 = 比水位线新、且不是我发的、且仍可见的消息数。计数列要在发消息、打开会话、
  运营下架消息每条路径上加减，漏一条就对不上；水位线只在打开会话这一处前移，
  算错了下次打开就自愈。水位线用 `GREATEST` 前移，保证只增不减——
  两个标签页同时开着时，迟到的那个请求会带着更旧的水位线，直接赋值会把已读变回未读。
- **会话列表的排序键反过来该冗余**。`last_message_at` 只在发消息这一条路径上更新，
  不存在多路径漏改的问题，而列表排序必须有一个可索引的列。同一毫秒内的两条消息靠时间
  分不出先后，所以再用单调递增的 `last_message_id` 兜底。下架一条消息不改排序键——
  下架不该让会话在列表里跳位置。
- **做不到的事要明确拒绝，而不是干脆不提供接口**。撤回消息永远失败，但接口仍然存在并
  返回一句说得出理由的拒绝；若没有这个路由，客户端拿到的是 404，分不清
  「没有这个功能」和「这条消息不存在」。
- **产品约束能落到数据层就别只落在文档里**。「不承载音视频通话」写成
  `CHECK (content_type IN ('TEXT','IMAGE'))`，而不是只写在 config.yaml 里等人记得。
- **按维度拆分漏斗，必须保证各行之和等于总数**。同一个人可能先从人气店员进详情、
  又从榜单进一次；按「来源 × 人」各算一次，拆分表之和就会大于总数，而那张表
  唯一有用的性质就是加得起来。本项目的口径是**每人每节点只归因一次**，
  归给该周期内最早的那条事件（时间相同再按 id 定序，保证可复现），
  用 `ROW_NUMBER() OVER (PARTITION BY event_key, 人 ORDER BY occurred_at, id)` 取第一条。
  于是「从人气店员进详情的人数」读作「首次进详情来自人气店员的人数」。
  看板上的合计也直接由拆分行加出，不另跑一次总数查询——两边各算一次迟早会对不上。
- **归因维度不可为空，没有来源就是「直接访问」**。允许 null 会让拆分表各行之和
  小于总数，而少掉的那部分没人知道去了哪。同理，首页默认列表单独算一个入口
  （`TALENT_LIST`）而不是并进 `DIRECT`——`DIRECT` 那一行是用来衡量分享链接效果的，
  混进首页随手点开的人就没法看了。
- **归因要在下单那一刻快照进订单**。确认收款是运营在后台点的，那一刻早已没有
  「粉丝是从哪个入口来的」这个上下文；与价格、分成比例同理，不快照就只能归成未知。
- **无数据的行展示为零，不隐藏**。「上线一个月一个人没带来」恰恰是最该被看见的结论。
  拆分表从枚举出发补零，而不是从查询结果出发列行。
- **入口事件不混进漏斗枚举**。`EventKey` 的顺序即漏斗顺序，把十三个入口塞进去会让
  漏斗凭空多出十三级。入口自带事件键（`ENTRY_POPULAR` 之类），单独一条写入路径。
- **设计 token 要真的是单一真源，重复出现的字面量就该收进去**。无头像时的占位渐变
  `linear-gradient(150deg, #e7eef0, #dbe4e8)` 一度散在八个页面里，警示底色 `#fff6f2`
  散在两处——散着迟早有一处改了别处没改。现在是 `--g-avatar` 与 `--c-danger-soft`。
  判断标准很简单：同一个字面量出现第三次，就该有名字。
- **换掉一个第三方组件时，先把它的插槽清点一遍**。自制的 `EmptyState` 替掉 Vant 的
  `Empty` 时漏了具名 `description` 插槽，于是「需要微信授权」「请在微信中打开」两页的
  说明文字整段消失——页面不报错、不溢出、布局也正常，只是从此没人知道出了什么事。
  视口回归扫不出这种问题，截图才能。换组件时逐个用法对一遍插槽与 props。
- **首屏体积要能离线量，不能只靠开浏览器数请求**。`frontend/scripts/first-screen.mjs`
  顺着 Vite manifest 的 `imports` 闭包算「打开首页要下载哪些 chunk」，
  只跟静态 imports、不跟 `dynamicImports`（那是别的路由）。数出来的字节数与真实浏览器
  按 resource timing 量的结果逐字节一致，因此可以放进构建做回归。
  直接数 `dist` 下所有 `.js` 是错的：运营后台、主播工作台与十几个懒加载路由
  一个字节也不会出现在粉丝打开首页时的下载里。
- **美术资产的来源要由构建守住，不能靠一次人工检查**。`AssetProvenanceTest` 扫描
  `frontend/public` 与 `frontend/src` 下的全部图片文件，出现任何不在 `docs/assets.md`
  清单里的文件就让构建失败。下次有人把一张图拖进 `public/`，构建会立刻问它从哪来。
- **产物里不该留指向第三方 CDN 的资源引用**。Vant 的图标字体 `@font-face` 带一条
  `//at.alicdn.com/...woff` 回退。实测现代浏览器用的是内联的 woff2，不会发这个请求，
  但只要它还在产物里，就意味着某些客户端会让粉丝的浏览器去连一个我们不控制的域名。
  `vite.config.ts` 的 `stripRemoteIconFont` 在打包时删掉它。
- **验收用例写完要反过来验一遍：把实现打坏，看它是不是真的会红**。第 14 组的八条
  链路用例一次全绿，这本身不说明任何事——用例可能根本没断言到要点上。
  逐条注入故障后它们确实红了：拿掉打赏退款的榜单回滚 → 「退款后榜单应减去该笔金额」
  失败（6600 vs 20000）；把入口归因的 `rn = 1` 改成 `rn >= 1` → 「入口 LEADERBOARD 的
  VIEW_TALENT_DETAIL」失败（1 vs 2）；把分成基数从原价改成实付 → 下单直接 500。
  没做过这一步的绿色用例，和没有用例的区别只是多了一份心理安慰。
- **同一个仓库里有两份「第 14 组」时，要在代码里说清楚**。上一个 change 与本次改版
  各有一组集成验收，任务号都是 14.1–14.5 但内容完全不同。两个测试类的 javadoc
  互相指明归属，免得有人按编号对照后以为哪边写错了。
