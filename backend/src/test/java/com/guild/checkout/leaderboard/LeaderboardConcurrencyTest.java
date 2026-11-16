package com.guild.checkout.leaderboard;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.admin.TalentAdminService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 榜单累计的并发正确性（tasks 8.1）。
 *
 * 刻意不加 @Transactional：丢更新是并发提交下的现象，测试事务会把线程隔离开，
 * 行锁与 ON DUPLICATE KEY UPDATE 根本不会被真正触发到。
 */
@SpringBootTest
class LeaderboardConcurrencyTest {

    private static final String PERIOD_KEY = "2000-01-01";

    @Autowired private LeaderboardTotalMapper totalMapper;
    @Autowired private TalentAdminService talentAdminService;

    private Long talentId;

    @BeforeEach
    void setUp() {
        Talent t = talentAdminService.create(new TalentAdminService.ProfileForm(
                "并发榜单主播" + System.nanoTime(), null, null, 0, null, null, null, null));
        talentId = t.getId();
    }

    @AfterEach
    void tearDown() {
        totalMapper.delete(Wrappers.<LeaderboardTotal>lambdaQuery()
                .eq(LeaderboardTotal::getPeriodKey, PERIOD_KEY)
                .eq(LeaderboardTotal::getTalentId, talentId));
        if (talentId != null) {
            talentAdminService.setListed(talentId, false);
        }
    }

    @Test
    @DisplayName("8.1 32 个线程并发累计同一行，总额与笔数一分不差")
    void 并发累计不丢更新() throws Exception {
        int threads = 32;
        long each = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    totalMapper.accumulate(
                            PeriodType.DAY.name(), PERIOD_KEY, talentId, each, 1);
                } catch (Exception e) {
                    // 并发首次累计若死锁或插重，会在这里现形
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(failures.get()).isZero();
        assertThat(totalMapper.selectCount(Wrappers.<LeaderboardTotal>lambdaQuery()
                .eq(LeaderboardTotal::getPeriodKey, PERIOD_KEY)
                .eq(LeaderboardTotal::getTalentId, talentId))).isEqualTo(1);

        LeaderboardTotal total = totalMapper.selectOne(Wrappers.<LeaderboardTotal>lambdaQuery()
                .eq(LeaderboardTotal::getPeriodType, PeriodType.DAY.name())
                .eq(LeaderboardTotal::getPeriodKey, PERIOD_KEY)
                .eq(LeaderboardTotal::getTalentId, talentId));
        assertThat(total.getAmountCents()).isEqualTo(threads * each);
        assertThat(total.getTipCount()).isEqualTo(threads);
    }

    @Test
    @DisplayName("8.1 并发的增与减混在一起，最终值仍等于代数和")
    void 并发增减结果正确() throws Exception {
        totalMapper.accumulate(PeriodType.DAY.name(), PERIOD_KEY, talentId, 10_000, 100);

        int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            // 一半加 200、一半减 100
            boolean add = i % 2 == 0;
            pool.submit(() -> {
                try {
                    start.await();
                    totalMapper.accumulate(PeriodType.DAY.name(), PERIOD_KEY, talentId,
                            add ? 200 : -100, add ? 1 : -1);
                } catch (Exception ignored) {
                    // 失败会体现在最终值上
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        LeaderboardTotal total = totalMapper.selectOne(Wrappers.<LeaderboardTotal>lambdaQuery()
                .eq(LeaderboardTotal::getPeriodType, PeriodType.DAY.name())
                .eq(LeaderboardTotal::getPeriodKey, PERIOD_KEY)
                .eq(LeaderboardTotal::getTalentId, talentId));
        // 10000 + 16*200 - 16*100 = 11600
        assertThat(total.getAmountCents()).isEqualTo(11_600);
        assertThat(total.getTipCount()).isEqualTo(100);
    }
}
