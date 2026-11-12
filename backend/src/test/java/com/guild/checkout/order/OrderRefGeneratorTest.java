package com.guild.checkout.order;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class OrderRefGeneratorTest {

    @Test
    void 长度固定18位且全为数字() {
        OrderRefGenerator generator = new OrderRefGenerator();
        for (int i = 0; i < 1000; i++) {
            String ref = generator.next();
            assertThat(ref).hasSize(18).containsOnlyDigits();
            assertThat(OrderRefGenerator.isWellFormed(ref)).isTrue();
        }
    }

    @Test
    void 前缀为当前时间便于与流水比对() {
        Clock fixed = Clock.fixed(
                Instant.parse("2026-11-12T09:30:45Z"), ZoneId.of("Asia/Shanghai"));
        OrderRefGenerator generator = new OrderRefGenerator(fixed);

        String ref = generator.next();

        // 2026-11-12 17:30:45 北京时间
        assertThat(ref).startsWith("261112173045");
        assertThat(ref).hasSize(18);
    }

    @Test
    void 并发生成一万个订单号无重复() throws Exception {
        OrderRefGenerator generator = new OrderRefGenerator();
        int threads = 16;
        int perThread = 625; // 16 × 625 = 10000
        Set<String> refs = ConcurrentHashMap.newKeySet();
        AtomicInteger generated = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        refs.add(generator.next());
                        generated.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(generated.get()).isEqualTo(10_000);
        assertThat(refs).hasSize(10_000);
    }

    @Test
    void 固定时钟下连续生成十万个也不重复() {
        // 时间前缀完全相同的极端情况：唯一性必须由后缀本身保证，而不是靠时间推进
        Clock fixed = Clock.fixed(Instant.parse("2026-11-12T09:30:45Z"), ZoneId.systemDefault());
        OrderRefGenerator generator = new OrderRefGenerator(fixed);

        Set<String> refs = new java.util.HashSet<>();
        int n = 100_000;
        for (int i = 0; i < n; i++) {
            refs.add(generator.next());
        }
        assertThat(refs).hasSize(n);
    }

    @Test
    void 相邻订单号在数值上不相邻以免暴露成交量() {
        Clock fixed = Clock.fixed(Instant.parse("2026-11-12T09:30:45Z"), ZoneId.systemDefault());
        OrderRefGenerator generator = new OrderRefGenerator(fixed);

        int adjacent = 0;
        long previous = Long.parseLong(generator.next().substring(12));
        for (int i = 0; i < 500; i++) {
            long current = Long.parseLong(generator.next().substring(12));
            if (Math.abs(current - previous) <= 10) {
                adjacent++;
            }
            previous = current;
        }
        assertThat(adjacent).as("相邻订单号不应是连号").isZero();
    }

    @Test
    void 格式校验拒绝非法单号() {
        assertThat(OrderRefGenerator.isWellFormed(null)).isFalse();
        assertThat(OrderRefGenerator.isWellFormed("")).isFalse();
        assertThat(OrderRefGenerator.isWellFormed("12345")).isFalse();
        assertThat(OrderRefGenerator.isWellFormed("26111217304512345")).isFalse();   // 17 位
        assertThat(OrderRefGenerator.isWellFormed("2611121730451234567")).isFalse(); // 19 位
        assertThat(OrderRefGenerator.isWellFormed("26111217304512345a")).isFalse();  // 含字母
        assertThat(OrderRefGenerator.isWellFormed("261112173045123456")).isTrue();
    }
}
