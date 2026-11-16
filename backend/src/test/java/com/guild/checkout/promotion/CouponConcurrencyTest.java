package com.guild.checkout.promotion;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.user.AppUser;
import com.guild.checkout.user.AppUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 券的并发幂等（tasks 7.3 / 7.5）。
 *
 * 刻意不加 @Transactional：重复领取与重复占用都是并发提交下的现象，
 * 测试事务会把线程隔离开，唯一索引与条件 UPDATE 根本不会被触发到。
 */
@SpringBootTest
class CouponConcurrencyTest {

    @Autowired private CouponService couponService;
    @Autowired private UserCouponMapper userCouponMapper;
    @Autowired private CouponMapper couponMapper;
    @Autowired private AppUserService userService;

    private Long couponId;

    @AfterEach
    void tearDown() {
        if (couponId != null) {
            userCouponMapper.delete(Wrappers.<UserCoupon>lambdaQuery()
                    .eq(UserCoupon::getCouponId, couponId));
            couponMapper.deleteById(couponId);
        }
    }

    private Coupon newCoupon() {
        Coupon c = couponService.create(new CouponService.CouponForm(
                "并发测试券", 2000L, 10000L, null,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30)));
        couponId = c.getId();
        return c;
    }

    @Test
    @DisplayName("7.3 32 个线程同时领取同一张券，只领到一张")
    void 并发重复领取只产生一张() throws Exception {
        Coupon coupon = newCoupon();
        AppUser fan = userService.registerOrUpdate("ccc_" + System.nanoTime(), null, "并发粉丝", null);

        int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger succeeded = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    couponService.claim(fan.getId(), coupon.getId());
                    succeeded.incrementAndGet();
                } catch (Exception e) {
                    // 「已领取过」是预期的拒绝
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(succeeded.get()).isEqualTo(1);
        assertThat(userCouponMapper.selectCount(Wrappers.<UserCoupon>lambdaQuery()
                .eq(UserCoupon::getCouponId, coupon.getId())
                .eq(UserCoupon::getUserId, fan.getId()))).isEqualTo(1);
    }

    @Test
    @DisplayName("7.5 16 个线程同时占用同一张券，只有一个成功")
    void 并发占用只有一方成功() throws Exception {
        Coupon coupon = newCoupon();
        AppUser fan = userService.registerOrUpdate("ccl_" + System.nanoTime(), null, "并发粉丝", null);
        UserCoupon owned = couponService.claim(fan.getId(), coupon.getId());

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger succeeded = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            // 每个线程假装自己是不同的订单，订单 id 用一个不会与真实订单冲突的大数
            long fakeOrderId = 900_000_000L + i;
            pool.submit(() -> {
                try {
                    start.await();
                    couponService.lock(fan.getId(), owned.getId(), fakeOrderId);
                    succeeded.incrementAndGet();
                } catch (Exception e) {
                    // 「已被占用」是预期的拒绝
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        // 一张券只能被一笔订单占住，哪怕 16 笔订单同时来抢
        assertThat(succeeded.get()).isEqualTo(1);
        assertThat(userCouponMapper.selectById(owned.getId()).getStatus())
                .isEqualTo(UserCoupon.LOCKED);
    }
}
