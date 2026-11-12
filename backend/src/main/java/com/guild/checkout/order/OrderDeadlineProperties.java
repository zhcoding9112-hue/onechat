package com.guild.checkout.order;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 订单时限配置。
 *
 * 这两个值可由运营在后台调整，但订单在创建时会把当时的时限折算成绝对时间戳写入自身，
 * 因此调整只影响之后创建的订单，已在计时中的订单沿用其创建时的时限（ordering 规格要求）。
 *
 * @param payMinutes    待支付有效期（分钟）
 * @param acceptMinutes 接单时限（分钟）
 */
@ConfigurationProperties(prefix = "order.deadline")
public record OrderDeadlineProperties(Integer payMinutes, Integer acceptMinutes) {

    public OrderDeadlineProperties {
        if (payMinutes == null || payMinutes <= 0) {
            payMinutes = 30;
        }
        if (acceptMinutes == null || acceptMinutes <= 0) {
            acceptMinutes = 15;
        }
    }
}
