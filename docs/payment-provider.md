# 新增一种支付方式

本文说明接入一种新支付方式需要实现什么、改动落在哪里。**订单模块不在改动范围内**——
如果你发现自己需要动 `com.guild.checkout.order` 下的任何文件，说明抽象被绕过了，请先回来看这篇。

## 契约

支付方式与订单之间只有两个接触面：

| 方向 | 契约 | 位置 |
| --- | --- | --- |
| 订单 → 支付 | `PaymentProvider.instruct()` 产出付款指引 | `payment/PaymentProvider.java` |
| 支付 → 订单 | `PaymentConfirmed` 事件送进 `PaymentService.confirm()` | `payment/PaymentConfirmed.java` |

`PaymentProvider` 只有三个方法，刻意不含任何具体支付方式的概念：

```java
String code();                                          // 方式标识
String displayName();                                   // 展示名
PaymentInstruction instruct(String orderRef, long payableCents);  // 怎么引导用户去付
```

不同方式要传给前端的东西形状差异很大（人工收款是账户与收款码，微信支付是一组签名参数），
所以 `PaymentInstruction` 用 `kind` + `payload` 承载，前端按 `kind` 分支渲染：

- `DISPLAY_AND_UPLOAD`：展示信息，用户线下付款后回来上传凭证
- `INVOKE_SDK`：调起支付控件，结果由异步回调送达

## 接入步骤

### 1. 实现 `PaymentProvider`

```java
@Component
public class WechatPayProvider implements PaymentProvider {
    public String code() { return "wechat"; }
    public String displayName() { return "微信支付"; }
    public PaymentInstruction instruct(String orderRef, long payableCents) {
        // 调统一下单，把 JSAPI 参数放进 payload
        return new PaymentInstruction("wechat", Kind.INVOKE_SDK, Map.of(
                "appId", ..., "timeStamp", ..., "nonceStr", ..., "package", ...,
                "signType", "RSA", "paySign", ...));
    }
}
```

标了 `@Component` 就会被 `PaymentService` 自动收集，不需要注册到任何地方。

### 2. 把回调收敛成 `PaymentConfirmed`

```java
@PostMapping("/api/payment/wechat/callback")
public String callback(@RequestBody String body) {
    // 验签、解密
    paymentService.confirm(new PaymentConfirmed(
            outTradeNo,          // orderRef
            payerTotal,          // paidCents，单位分
            "wechat",            // provider
            transactionId,       // externalRef：微信交易单号
            null,                // operatorId：异步回调没有操作者
            successTime,
            null));
    return "SUCCESS";
}
```

**`externalRef` 必须是该支付方式侧的唯一流水标识**。它与 `orderRef`、`provider` 共同构成
`payment_record` 的唯一索引，是幂等的唯一依靠。微信支付的回调天然会重投，没有它一定会重复入账。

幂等靠唯一索引冲突而不是「先查再写」：并发重投下先查再写挡不住。

### 3. 在 `payment_setting` 里加一行并启用

```sql
INSERT INTO payment_setting (provider, enabled, tips) VALUES ('wechat', 0, '...');
```

先以 `enabled = 0` 上线，灰度时由运营在后台打开。停用某方式不会让已创建的待支付订单失效，
只是粉丝端不再展示它。

### 4. 前端按 `kind` 加一个分支

粉丝端支付页读 `instruction.kind`，`INVOKE_SDK` 时调 `WeixinJSBridge`。
`DISPLAY_AND_UPLOAD` 的现有分支不需要改。

## 不需要改的地方

- `order` 包下的任何文件。订单状态机、超时处置、分账计算都不知道支付方式的存在。
- `PaymentService.confirm()` 本身。它只认事件，不关心事件从哪来。
- 结算与分账。它们读的是订单上的快照字段。

## 切换演练

上线微信支付时按 design.md 的 Migration Plan 走：

1. 两种方式并存，`manual` 保持启用
2. 观察一周，确认回调幂等与对账无误
3. 把 `manual` 降级为仅运营可见的兜底通道
4. 出问题就在后台关掉 `wechat`，退回纯人工收款，订单侧无需改动与发版
