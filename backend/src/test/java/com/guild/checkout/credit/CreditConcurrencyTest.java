package com.guild.checkout.credit;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
 * 并发扣减无超扣（tasks 6.1）。
 *
 * 刻意不加 @Transactional：超扣是并发提交下的现象，测试事务会把线程彼此隔离，
 * 行锁与条件 UPDATE 根本不会被真正触发到。因此自行清理本用例造出来的数据。
 */
@SpringBootTest
class CreditConcurrencyTest {

    private static final long FAN_ID = 999_000_012L;
    private static final long OPERATOR_ID = 999_000_013L;

    @Autowired private CreditService creditService;
    @Autowired private CreditAccountMapper accountMapper;
    @Autowired private CreditTransactionMapper transactionMapper;

    @BeforeEach
    void setUp() {
        clear();
    }

    @AfterEach
    void tearDown() {
        clear();
    }

    private void clear() {
        transactionMapper.delete(Wrappers.<CreditTransaction>lambdaQuery()
                .eq(CreditTransaction::getUserId, FAN_ID));
        accountMapper.delete(Wrappers.<CreditAccount>lambdaQuery()
                .eq(CreditAccount::getUserId, FAN_ID));
    }

    @Test
    @DisplayName("6.1 32 个线程各扣 10，余额 100 时只有 10 次成功且余额不为负")
    void 并发扣减无超扣() throws Exception {
        creditService.apply(FAN_ID, 100, CreditType.GRANT, "期初", OPERATOR_ID);

        int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    creditService.apply(FAN_ID, -10, CreditType.DEDUCT, "并发扣减", OPERATOR_ID);
                    succeeded.incrementAndGet();
                } catch (Exception e) {
                    rejected.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        // 100 元子最多扣 10 次，一次不多一次不少
        assertThat(succeeded.get()).isEqualTo(10);
        assertThat(rejected.get()).isEqualTo(threads - 10);
        assertThat(creditService.balanceOf(FAN_ID)).isZero();

        // 成功次数与流水条数必须对齐：多出来的流水意味着扣了却没记，或反之
        long deductRows = transactionMapper.selectCount(Wrappers.<CreditTransaction>lambdaQuery()
                .eq(CreditTransaction::getUserId, FAN_ID)
                .eq(CreditTransaction::getType, CreditType.DEDUCT.name()));
        assertThat(deductRows).isEqualTo(10);
    }

    @Test
    @DisplayName("6.1 并发首次变动只开出一个账户")
    void 并发开户只产生一个账户() throws Exception {
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    creditService.apply(FAN_ID, 1, CreditType.GRANT, "并发开户", OPERATOR_ID);
                } catch (Exception e) {
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
        assertThat(accountMapper.selectCount(Wrappers.<CreditAccount>lambdaQuery()
                .eq(CreditAccount::getUserId, FAN_ID))).isEqualTo(1);
        // 16 次各加 1，余额与流水都不能丢
        assertThat(creditService.balanceOf(FAN_ID)).isEqualTo(16);
        assertThat(creditService.countTransactions(FAN_ID)).isEqualTo(16);
    }
}
