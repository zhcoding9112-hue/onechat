package com.guild.checkout.payment;

import java.util.Map;

/**
 * 付款指引：支付方式告诉粉丝端"接下来怎么付"。
 *
 * 不同支付方式需要传给前端的东西形状差异很大（人工收款是收款码与账户，
 * 微信支付 JSAPI 是一组签名参数），因此用 payload 承载，由前端按 kind 分支渲染。
 * 这样新增支付方式不需要改订单侧的任何契约。
 *
 * @param provider 支付方式标识
 * @param kind     指引类型：DISPLAY_AND_UPLOAD 展示信息并等待上传凭证；INVOKE_SDK 调起支付控件
 * @param payload  该类型所需的数据
 */
public record PaymentInstruction(String provider, Kind kind, Map<String, Object> payload) {

    public enum Kind {
        /** 展示收款信息，粉丝线下付款后回来上传凭证。 */
        DISPLAY_AND_UPLOAD,
        /** 调起支付控件，结果由异步回调送达。 */
        INVOKE_SDK
    }
}
