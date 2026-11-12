package com.guild.checkout.order.timeout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 基于 Redis ZSet 的延时队列。
 *
 * score 存到期时间的毫秒时间戳，member 存订单 id。取出时按 score 范围查，
 * 因此"取出已到期的"是一次 O(log n + m) 的范围查询，不需要轮询全部成员。
 *
 * 本队列只负责"到期提醒"，不保证可靠投递：Redis 重启或写入失败都会丢成员。
 * 可靠性由数据库侧的兜底扫表保证（见 {@code OrderTimeoutScanner}）——
 * 订单的到期时间本来就存在订单行上，队列只是让处置更及时。
 */
public class DelayQueue {

    private static final Logger log = LoggerFactory.getLogger(DelayQueue.class);

    private final StringRedisTemplate redis;
    private final String key;

    public DelayQueue(StringRedisTemplate redis, String key) {
        this.redis = redis;
        this.key = key;
    }

    /** 入队。同一成员重复入队会更新其到期时间。 */
    public void schedule(long orderId, LocalDateTime deadline) {
        if (deadline == null) {
            return;
        }
        try {
            redis.opsForZSet().add(key, String.valueOf(orderId), toScore(deadline));
        } catch (RuntimeException ex) {
            // 入队失败不能让下单失败：兜底扫表会捞回来
            log.warn("延时队列入队失败，订单 {} 将依赖兜底扫表: {}", orderId, ex.getMessage());
        }
    }

    /** 移除。订单提前离开等待态时调用，避免队列里堆积无效成员。 */
    public void cancel(long orderId) {
        try {
            redis.opsForZSet().remove(key, String.valueOf(orderId));
        } catch (RuntimeException ex) {
            log.warn("延时队列移除失败，订单 {}: {}", orderId, ex.getMessage());
        }
    }

    /**
     * 取出已到期的成员，最多 limit 个，并把它们从队列中移除。
     *
     * 先查后删之间有竞态，但重复取出是安全的：处置侧以数据库乐观锁兜底，
     * 同一订单被处置两次时只有一次生效。
     */
    public Set<Long> pollDue(int limit) {
        Set<Long> ids = new LinkedHashSet<>();
        try {
            Set<ZSetOperations.TypedTuple<String>> due = redis.opsForZSet()
                    .rangeByScoreWithScores(key, 0, nowScore(), 0, limit);
            if (due == null || due.isEmpty()) {
                return ids;
            }
            for (ZSetOperations.TypedTuple<String> tuple : due) {
                String member = tuple.getValue();
                if (member == null) {
                    continue;
                }
                // 删除成功才算取到，保证同一成员不会被两个实例同时拿走
                Long removed = redis.opsForZSet().remove(key, member);
                if (removed != null && removed > 0) {
                    ids.add(Long.parseLong(member));
                }
            }
        } catch (RuntimeException ex) {
            log.warn("延时队列取出失败，本轮跳过，兜底扫表仍会处置: {}", ex.getMessage());
        }
        return ids;
    }

    /** 当前队列长度，仅用于观测。 */
    public long size() {
        try {
            Long size = redis.opsForZSet().zCard(key);
            return size == null ? 0 : size;
        } catch (RuntimeException ex) {
            return -1;
        }
    }

    /** 某成员的到期时间，不存在时返回 null。仅用于测试与排查。 */
    public Double scoreOf(long orderId) {
        try {
            return redis.opsForZSet().score(key, String.valueOf(orderId));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static double toScore(LocalDateTime deadline) {
        return deadline.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static double nowScore() {
        return Instant.now().toEpochMilli();
    }
}
