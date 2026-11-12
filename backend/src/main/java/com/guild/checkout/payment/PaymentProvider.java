package com.guild.checkout.payment;

/**
 * 支付方式端口。
 *
 * 本接口刻意不出现任何与微信或人工收款相关的概念：没有"收款码"、没有"凭证"、
 * 没有"JSAPI 参数"。它只回答两件事——这个方式是什么、怎么引导用户去付。
 * 付成功之后统一由 {@link PaymentConfirmed} 事件进入订单侧。
 */
public interface PaymentProvider {

    /** 方式标识，写入收款记录，全局唯一且稳定。 */
    String code();

    /** 展示名称，给粉丝端选择支付方式时用。 */
    String displayName();

    /**
     * 为一笔待支付订单产出付款指引。
     *
     * @param orderRef     订单号
     * @param payableCents 应付金额，单位：分
     */
    PaymentInstruction instruct(String orderRef, long payableCents);
}
