# 部署清单

对应 `tasks.md` 的 13.2、13.3，以及 2.2 的测试号验证。这三项互相咬合，
有了服务器和备案域名就能一起做掉。

**先读「零、关键路径」再动手。** 有一件事必须今天就启动，否则后面全部白等。

---

## 零、关键路径：域名备案

**ICP 备案是唯一能让整个工期白做的外部因素**（design.md 的风险清单第一条）。

- 国内服务器必须备案，没有备案的域名连 80/443 都打不开
- 备案周期通常 7–20 个工作日，各省不同
- 微信网页授权要求的「网页授权域名」必须是**已备案的域名**，且不能带端口

**今天就提交备案**，不要等开发完再办。备案期间：

- 开发继续用内网穿透（ngrok、frp、cpolar 都行）配合微信**测试号**联调
- 测试号不要求备案域名，这是它存在的意义

备案批下来之前，下面的步骤只有「三、服务器环境」可以先做。

---

## 一、开通前的清单

| 事项 | 状态 | 谁去办 | 备注 |
| --- | --- | --- | --- |
| 域名 + ICP 备案 | ⬜ | 你 | 关键路径，见上 |
| 云服务器 | ⬜ | 你 | 2 核 4G 足够，见「三」 |
| 服务号**认证** | ⬜ | 你 | 未认证的服务号没有网页授权接口 |
| 微信测试号 | ⬜ | 你 | 联调用，立刻可申请，无需备案 |
| 微信支付商户号 | ⬜ | 你 | 不阻塞上线，人工收款先跑 |
| 公会对公账户信息 | ⬜ | 你 | 上线后填进后台，不写进代码 |
| 分成比例与各档位定价 | ⬜ | 你 + 公司 | 上线前填进后台即可 |

> 服务号必须是**已认证**的。未认证服务号拿不到 `snsapi_userinfo` 网页授权，
> 整套身份体系就立不起来。认证需要企业资质 + 300 元/年。

---

## 二、先拿测试号把 2.2 做掉

这一步**不需要备案域名**，现在就能做，不必等服务器。

### 1. 申请测试号

微信公众平台 → 开发 → 开发者工具 → 公众平台测试帐号。
记下 `appID` 和 `appsecret`。

### 2. 起内网穿透

```bash
# 以 cpolar 为例，把本机 8080 暴露到公网
cpolar http 8080
```

拿到形如 `https://xxxx.r6.cpolar.top` 的地址。

### 3. 填网页授权域名

测试号页面 →「网页服务」→「网页帐号」→「修改」→ 填入上一步的域名，
**只填域名，不带 `https://`、不带端口、不带路径**。

### 4. 启动应用

```bash
export SPRING_PROFILES_ACTIVE=local
export WECHAT_APP_ID=<测试号 appID>
export WECHAT_APP_SECRET=<测试号 appsecret>
export APP_BASE_URL=https://xxxx.r6.cpolar.top
java -jar build/private-checkout.jar
```

配了 `WECHAT_APP_ID` 后，系统会自动从授权桩切到真实实现，启动日志里会看到
`微信网页授权使用真实实现`。

### 5. 用微信扫码关注测试号，然后在微信里打开

```
https://xxxx.r6.cpolar.top/
```

应当跳到微信授权页 → 同意 → 回到首页。

### 6. 验证

```bash
mysql -u root private_checkout -e \
  "SELECT id, open_id, nickname, roles, created_at FROM app_user ORDER BY id DESC LIMIT 3;"
```

库里出现一条 `open_id` 以 `o` 开头（不是 `stub_`）的新记录，就是 2.2 通过了。

### 排查

| 现象 | 原因 |
| --- | --- |
| `redirect_uri 参数错误` | 网页授权域名填错，或带了协议/端口 |
| 跳到「请在微信中打开」 | 用了普通浏览器，必须在微信里打开 |
| `invalid appid` | AppID 填错，或用的是订阅号的 |
| 授权后回到首页仍未登录 | Cookie 被拦；检查是否 HTTPS 与 `SESSION_COOKIE_SECURE` 不匹配 |

---

## 三、服务器环境（13.2）

备案没下来也能先做这一步。

### 配置建议

2 核 4G、40G 系统盘。本期是 10 位主播、数千粉丝、月订单数百笔，
这个配置有两个数量级的余量。

### 安装

以 Ubuntu 22.04 为例：

```bash
# JDK 17
sudo apt update && sudo apt install -y openjdk-17-jre-headless
java -version   # 应显示 17.x

# MySQL 8
sudo apt install -y mysql-server
sudo mysql_secure_installation

# Redis
sudo apt install -y redis-server
sudo systemctl enable --now redis-server
redis-cli ping   # 应返回 PONG

# Nginx
sudo apt install -y nginx
```

### 建库与建用户

**不要用 root 跑应用。**

```sql
CREATE DATABASE private_checkout
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'pc_app'@'localhost' IDENTIFIED BY '<强密码>';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON private_checkout.* TO 'pc_app'@'localhost';
FLUSH PRIVILEGES;
```

> `CREATE / ALTER / INDEX` 是 Flyway 做迁移必需的。不给 `DROP`：
> 应用正常运行不需要删表，不给就不会误删。

### 目录

```bash
sudo mkdir -p /opt/private-checkout/{bin,uploads,backups,logs}
sudo useradd -r -s /bin/false pcapp
sudo chown -R pcapp:pcapp /opt/private-checkout
```

---

## 四、部署应用

### 1. 本机构建并上传

```bash
./scripts/build.sh
scp build/private-checkout.jar 服务器:/tmp/
scp -r scripts 服务器:/tmp/
```

### 2. 放到位

```bash
sudo mv /tmp/private-checkout.jar /opt/private-checkout/bin/
sudo mv /tmp/scripts /opt/private-checkout/
sudo chown -R pcapp:pcapp /opt/private-checkout
```

### 3. 写环境变量文件

```bash
sudo tee /opt/private-checkout/env > /dev/null <<'EOF'
SPRING_PROFILES_ACTIVE=prod

DB_HOST=127.0.0.1
DB_NAME=private_checkout
DB_USER=pc_app
DB_PASSWORD=<强密码>

REDIS_HOST=127.0.0.1
REDIS_PORT=6379

WECHAT_APP_ID=<正式服务号 AppID>
WECHAT_APP_SECRET=<正式服务号 AppSecret>
APP_BASE_URL=https://你的域名

# 走 HTTPS 后必须置 true，否则会话 Cookie 可能被中间人读走
SESSION_COOKIE_SECURE=true

STORAGE_ROOT=/opt/private-checkout/uploads
EOF

sudo chmod 600 /opt/private-checkout/env
sudo chown pcapp:pcapp /opt/private-checkout/env
```

> `SPRING_PROFILES_ACTIVE=prod` 很重要。在 prod 下若漏配 `WECHAT_APP_ID`，
> 应用会**直接启动失败**而不是退化成「任何人都能以任意 openid 登录」的授权桩。
> 这是故意的——宁可起不来，也不能悄悄变成谁都能登录。

### 4. systemd 服务

```bash
sudo tee /etc/systemd/system/private-checkout.service > /dev/null <<'EOF'
[Unit]
Description=Private Checkout
After=network.target mysql.service redis-server.service

[Service]
Type=simple
User=pcapp
WorkingDirectory=/opt/private-checkout
EnvironmentFile=/opt/private-checkout/env
ExecStart=/usr/bin/java -Xms512m -Xmx1g -jar /opt/private-checkout/bin/private-checkout.jar
Restart=on-failure
RestartSec=5
StandardOutput=append:/opt/private-checkout/logs/app.log
StandardError=append:/opt/private-checkout/logs/app.log

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now private-checkout
sudo systemctl status private-checkout
```

### 5. 确认起来了

```bash
curl -s http://127.0.0.1:8080/actuator/health
```

应返回 `"status":"UP"`，且 `db` 与 `redis` 两个组件都是 `UP`。
首次启动时 Flyway 会自动建全部表，日志里能看到 `Migrating schema ... to version "11"`。

---

## 五、Nginx 与 HTTPS

**微信网页授权要求 HTTPS**，这不是可选项。

### 1. 证书

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d 你的域名
```

certbot 会自动改 Nginx 配置并装好自动续期。

### 2. 站点配置

```bash
sudo tee /etc/nginx/sites-available/private-checkout > /dev/null <<'EOF'
server {
    listen 80;
    server_name 你的域名;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name 你的域名;

    # certbot 会填好这两行
    # ssl_certificate     /etc/letsencrypt/live/你的域名/fullchain.pem;
    # ssl_certificate_key /etc/letsencrypt/live/你的域名/privkey.pem;

    # 凭证与试听音频会走这里，放宽一点
    client_max_body_size 10m;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        # 少了这行，应用会以为自己在 http 下，Secure Cookie 不下发，会话建不起来
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
EOF

sudo ln -sf /etc/nginx/sites-available/private-checkout /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
```

### 3. 让应用认识反向代理

在 `/opt/private-checkout/env` 里加一行：

```
SERVER_FORWARD_HEADERS_STRATEGY=framework
```

这让应用认 Nginx 传来的 `X-Forwarded-Proto`，从而知道自己其实在 HTTPS 下。

> **但别指望它解决回调问题。** 微信回调地址是从 `APP_BASE_URL` 拼出来的，
> 不是从请求推导的；会话 Cookie 的 Secure 标志由 `SESSION_COOKIE_SECURE` 显式控制。
> 这两项才是上线后最容易出事的地方：
>
> | 症状 | 真正要改的 |
> | --- | --- |
> | 微信报 `redirect_uri 参数错误`，或回调跳到内网地址 | `APP_BASE_URL` 没填成公网 HTTPS 地址 |
> | 授权完成但仍是未登录状态 | `SESSION_COOKIE_SECURE` 与实际协议不匹配 |
>
> `SERVER_FORWARD_HEADERS_STRATEGY` 是正确性上的卫生习惯，不是这两个问题的解药。

### 4. 验证

```bash
curl -I https://你的域名/
curl -s https://你的域名/api/session/me
```

首页应返回 200，接口应返回 `UNAUTHENTICATED`（未登录是正常的）。

---

## 六、微信公众平台配置（13.3）

全部在「微信公众平台 → 设置与开发」里做。

| 配置项 | 位置 | 填什么 |
| --- | --- | --- |
| 网页授权域名 | 接口权限 → 网页授权 → 修改 | `你的域名`（不带协议、端口、路径） |
| JS 接口安全域名 | 公众号设置 → 功能设置 | 同上 |
| 业务域名 | 公众号设置 → 功能设置 | 同上 |
| IP 白名单 | 基本配置 → IP 白名单 | 服务器公网 IP |

### 域名校验文件

填网页授权域名时，微信会要求你下载一个 `MP_verify_xxxx.txt` 并放到网站根目录。

```bash
sudo mkdir -p /opt/private-checkout/wellknown
sudo cp MP_verify_xxxx.txt /opt/private-checkout/wellknown/
```

然后在 Nginx 的 server 块里加：

```nginx
location ~ ^/MP_verify_.*\.txt$ {
    root /opt/private-checkout/wellknown;
}
```

`sudo nginx -t && sudo systemctl reload nginx` 后，微信那边点「确定」即可。

> **IP 白名单最容易漏。** 漏了的表现是：网页授权能跳转，但换取 openid 时
> 微信返回 `errcode 40164`，应用日志里会看到「换取 access_token 失败」。

---

## 七、上线前核对

逐项打勾，全部通过再放量。

### 系统

- [ ] `curl -s https://你的域名/actuator/health` 返回 `UP`，且 `db`、`redis` 都是 `UP`
- [ ] 首页能在**微信里**打开
- [ ] 用普通浏览器打开，看到「请在微信中打开」引导页
- [ ] 微信里完成一次真实授权，库里 `app_user` 新增记录（这就是 2.2）
- [ ] 日志里没有 `授权桩` 字样（有就说明 AppID 没配上）

### 数据

- [ ] 后台 →「服务与价格」：服务项目与档位已建好
- [ ] 每位主播的每个档位都配了价格（没配价的档位粉丝端不显示）
- [ ] 后台 →「分成比例」：全局默认已按公司谈定的比例填好
- [ ] 后台 → 支付设置：**公会对公收款信息已填**（不填粉丝会看到「收款信息尚未配置」）
- [ ] 主播档案已建、已上架、已关联各自的微信号
- [ ] 让每位主播在微信里打开一次，确认能进工作台

### 跑一笔真单

**用真实的小额订单走一遍**，这比任何测试都有说服力：

1. 建一个 1 元的测试档位，下单
2. 真的转 1 元到对公账户
3. 上传凭证 → 运营在后台确认收款
4. 主播接单 → 标记完成
5. 粉丝评分
6. 后台看结算报表，金额对得上
7. 看板上这一笔的各节点都在
8. 做完把测试档位停用

### 备份

```bash
sudo crontab -u pcapp -e
# 加一行，每天凌晨三点备份
0 3 * * * BACKUP_DIR=/opt/private-checkout/backups DB_USER=pc_app DB_PASSWORD=<密码> /opt/private-checkout/scripts/backup-db.sh >> /opt/private-checkout/logs/backup.log 2>&1
```

- [ ] 手动跑一次 `backup-db.sh`，确认产出了 `.sql.gz`
- [ ] 跑一次 `restore-db.sh`，确认能恢复出库并用它启动应用

---

## 八、日常运维

```bash
# 看状态
sudo systemctl status private-checkout

# 看日志
sudo tail -f /opt/private-checkout/logs/app.log

# 重启
sudo systemctl restart private-checkout
```

### 发新版本

```bash
# 本机
./scripts/build.sh
scp build/private-checkout.jar 服务器:/tmp/

# 服务器
sudo cp /opt/private-checkout/bin/private-checkout.jar \
        /opt/private-checkout/bin/private-checkout.jar.bak
sudo mv /tmp/private-checkout.jar /opt/private-checkout/bin/
sudo chown pcapp:pcapp /opt/private-checkout/bin/private-checkout.jar
sudo systemctl restart private-checkout
curl -s http://127.0.0.1:8080/actuator/health
```

**发版前先备份数据库。** 新版本可能带数据库迁移，迁移是不可逆的。

### 回滚

```bash
sudo mv /opt/private-checkout/bin/private-checkout.jar.bak \
        /opt/private-checkout/bin/private-checkout.jar
sudo systemctl restart private-checkout
```

> 回滚只能回退代码，**回退不了数据库迁移**。若新版本加了表或列，旧版本一般仍能启动
> （它不认识新东西，但也不会去碰）；若新版本改了既有列的类型或约束，旧版本可能起不来。
> 这种情况下只能从备份恢复，所以发版前的那次备份是认真的，不是走过场。

---

## 九、接微信支付时

不在本次上线范围内，2–3 周后商户号下来再做。流程见
[`payment-provider.md`](payment-provider.md)，要点：

1. 实现 `WechatPayProvider`，标 `@Component` 即自动生效
2. 回调收敛成 `PaymentConfirmed` 事件，`externalRef` 填微信交易单号
3. 在后台先以停用状态上线，灰度时再打开
4. 观察一周，确认回调幂等与对账无误，再把人工收款降级为兜底
5. 出问题就在后台关掉微信支付，退回纯人工收款，**订单侧无需改动与发版**

订单模块在整个过程中一行都不用动——这一点有测试在构建里守着。
