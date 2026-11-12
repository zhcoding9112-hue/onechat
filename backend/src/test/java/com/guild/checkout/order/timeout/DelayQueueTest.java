package com.guild.checkout.order.timeout;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 延时队列（task 7.1）。
 */
@SpringBootTest
class DelayQueueTest {

    @Autowired private StringRedisTemplate redis;

    private DelayQueue queue;

    @BeforeEach
    void setUp() {
        // 每个测试用独立的 key，彼此不干扰
        String key = "test:delay:" + System.nanoTime();
        queue = new DelayQueue(redis, key);
    }

    @Test
    void 已到期的成员能被取出() {
        queue.schedule(1001L, LocalDateTime.now().minusSeconds(5));

        Set<Long> due = queue.pollDue(10);

        assertThat(due).containsExactly(1001L);
    }

    @Test
    void 未到期的成员不会被取出() {
        queue.schedule(1002L, LocalDateTime.now().plusMinutes(10));

        assertThat(queue.pollDue(10)).isEmpty();
        // 仍在队列里等着
        assertThat(queue.size()).isEqualTo(1);
    }

    @Test
    void 到期与未到期混在一起时只取到期的() {
        queue.schedule(2001L, LocalDateTime.now().minusSeconds(30));
        queue.schedule(2002L, LocalDateTime.now().minusSeconds(10));
        queue.schedule(2003L, LocalDateTime.now().plusMinutes(5));

        Set<Long> due = queue.pollDue(10);

        assertThat(due).containsExactlyInAnyOrder(2001L, 2002L);
        assertThat(queue.size()).isEqualTo(1);
    }

    @Test
    void 取出后即从队列移除不会被重复取到() {
        queue.schedule(3001L, LocalDateTime.now().minusSeconds(1));

        assertThat(queue.pollDue(10)).containsExactly(3001L);
        assertThat(queue.pollDue(10)).isEmpty();
        assertThat(queue.size()).isZero();
    }

    @Test
    void 重复入队会更新到期时间而非产生两条() {
        queue.schedule(4001L, LocalDateTime.now().plusMinutes(10));
        Double first = queue.scoreOf(4001L);

        queue.schedule(4001L, LocalDateTime.now().minusSeconds(1));

        assertThat(queue.size()).isEqualTo(1);
        assertThat(queue.scoreOf(4001L)).isLessThan(first);
        assertThat(queue.pollDue(10)).containsExactly(4001L);
    }

    @Test
    void 移除后不再被取出() {
        queue.schedule(5001L, LocalDateTime.now().minusSeconds(1));

        queue.cancel(5001L);

        assertThat(queue.pollDue(10)).isEmpty();
        assertThat(queue.scoreOf(5001L)).isNull();
    }

    @Test
    void 移除不存在的成员不报错() {
        queue.cancel(99999L);
        assertThat(queue.size()).isZero();
    }

    @Test
    void 到期时间为空时不入队() {
        queue.schedule(6001L, null);
        assertThat(queue.size()).isZero();
    }

    @Test
    void 限制单轮取出数量() {
        for (long i = 1; i <= 20; i++) {
            queue.schedule(7000L + i, LocalDateTime.now().minusSeconds(i));
        }

        Set<Long> first = queue.pollDue(5);

        assertThat(first).hasSize(5);
        assertThat(queue.size()).isEqualTo(15);
    }

    @Test
    void 按到期时间先后取出() {
        queue.schedule(8003L, LocalDateTime.now().minusSeconds(1));
        queue.schedule(8001L, LocalDateTime.now().minusSeconds(30));
        queue.schedule(8002L, LocalDateTime.now().minusSeconds(10));

        // 最早到期的先被处置，避免积压时新单插队
        assertThat(queue.pollDue(2)).containsExactly(8001L, 8002L);
    }
}
