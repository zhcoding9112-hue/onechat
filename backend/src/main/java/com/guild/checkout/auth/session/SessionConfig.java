package com.guild.checkout.auth.session;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * 会话存储。
 *
 * 续期语义：Spring Session 在每次请求结束时重写会话的最后访问时间并刷新 Redis 过期时间，
 * 因此有效期是"距上次访问"的滑动窗口，而不是"距登录"的固定窗口。粉丝只要在用就不会被登出。
 *
 * 序列化用 JSON 而非 JDK 原生：原生序列化的 payload 绑定类名，改个包名历史会话全部失效，
 * 升级时会把所有在线用户踢下线。
 */
@Configuration
public class SessionConfig {

    @Bean("springSessionDefaultRedisSerializer")
    public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        return new GenericJackson2JsonRedisSerializer();
    }
}
