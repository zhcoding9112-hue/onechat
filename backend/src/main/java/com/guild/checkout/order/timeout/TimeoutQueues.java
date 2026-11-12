package com.guild.checkout.order.timeout;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 两条独立的延时队列：待支付与待接单。
 *
 * 分开而不是合并成一条带类型标记的队列，是因为两者的处置逻辑与时限完全不同，
 * 合并后每次取出都要再判断类型，没有任何收益。
 */
@Configuration
public class TimeoutQueues {

    public static final String PAY_KEY = "private-checkout:timeout:pay";
    public static final String ACCEPT_KEY = "private-checkout:timeout:accept";

    @Bean("payTimeoutQueue")
    public DelayQueue payTimeoutQueue(StringRedisTemplate redis) {
        return new DelayQueue(redis, PAY_KEY);
    }

    @Bean("acceptTimeoutQueue")
    public DelayQueue acceptTimeoutQueue(StringRedisTemplate redis) {
        return new DelayQueue(redis, ACCEPT_KEY);
    }
}
