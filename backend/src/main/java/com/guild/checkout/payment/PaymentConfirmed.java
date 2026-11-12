package com.guild.checkout.payment;

import java.time.LocalDateTime;

/**
 * 收款成功事件。订单侧只认这一个入站事件，对具体收款手段无感知。
 *
 * @param orderRef    订单号
 * @param paidCents   实收金额，单位：分
 * @param provider    产出本事件的支付方式标识
 * @param externalRef 该支付方式侧的唯一流水标识。人工收款用运营填的流水号或系统生成的确认号，
 *                    微信支付用其交易单号。与 orderRef、provider 共同构成幂等键。
 * @param operatorId  触发者 app_user.id；由异步回调触发时为空
 * @param occurredAt  收款发生时间
 * @param note        备注，如实收与应付不一致时的差异说明
 */
public record PaymentConfirmed(
        String orderRef,
        long paidCents,
        String provider,
        String externalRef,
        Long operatorId,
        LocalDateTime occurredAt,
        String note) {

    public PaymentConfirmed {
        if (orderRef == null || orderRef.isBlank()) {
            throw new IllegalArgumentException("orderRef 不能为空");
        }
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider 不能为空");
        }
        if (externalRef == null || externalRef.isBlank()) {
            throw new IllegalArgumentException("externalRef 不能为空：没有它就无法保证幂等");
        }
        if (paidCents < 0) {
            throw new IllegalArgumentException("实收金额不得为负");
        }
        if (occurredAt == null) {
            occurredAt = LocalDateTime.now();
        }
    }
}
