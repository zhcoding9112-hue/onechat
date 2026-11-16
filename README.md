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

打开 http://localhost:5173 ，建议用浏览器的移动端视口（375×812）查看。
前端的 `/api` 请求由 Vite 代理转发到后端，不存在跨域问题。

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
