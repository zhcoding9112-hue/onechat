package com.guild.checkout.feed;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.admin.TalentAdminService;
import com.guild.checkout.user.AppUser;
import com.guild.checkout.user.AppUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 点赞与举报的并发幂等（tasks 9.3 / 9.6）。
 *
 * 刻意不加 @Transactional：幂等靠唯一索引，而测试事务会把线程隔离开，
 * 唯一索引根本不会被触发到。因此自行清理本用例造出来的数据。
 */
@SpringBootTest
class FeedConcurrencyTest {

    @Autowired private FeedService feedService;
    @Autowired private ReportService reportService;
    @Autowired private FeedPostMapper postMapper;
    @Autowired private FeedLikeMapper likeMapper;
    @Autowired private ContentReportMapper reportMapper;
    @Autowired private TalentAdminService talentAdminService;
    @Autowired private AppUserService userService;

    private Long postId;
    private Long talentId;
    private long fanId;

    @BeforeEach
    void setUp() {
        Talent t = talentAdminService.create(new TalentAdminService.ProfileForm(
                "并发动态主播" + System.nanoTime(), null, null, 0, null, null, null, null));
        talentAdminService.setListed(t.getId(), true);
        talentId = t.getId();
        fanId = userService.registerOrUpdate(
                "fcc_" + System.nanoTime(), null, "并发粉丝", null).getId();
        postId = feedService.publish(talentId, "并发测试动态", null).getId();
    }

    @AfterEach
    void tearDown() {
        if (postId != null) {
            likeMapper.delete(Wrappers.<FeedLike>lambdaQuery().eq(FeedLike::getPostId, postId));
            reportMapper.delete(Wrappers.<ContentReport>lambdaQuery()
                    .eq(ContentReport::getTargetType, ContentReport.TARGET_POST)
                    .eq(ContentReport::getTargetId, postId));
            postMapper.deleteById(postId);
        }
        if (talentId != null) {
            talentAdminService.setListed(talentId, false);
        }
    }

    @Test
    @DisplayName("9.3 32 个线程同时点赞同一条动态，只计一次")
    void 并发点赞只计一次() throws Exception {
        int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    feedService.like(fanId, postId);
                } catch (Exception e) {
                    // 唯一索引冲突必须在服务内部被吞掉，冒到这里就算失败
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
        assertThat(feedService.likeCount(postId)).isEqualTo(1);
    }

    @Test
    @DisplayName("9.6 同一人并发举报同一对象只产生一条")
    void 并发举报只产生一条() throws Exception {
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    reportService.report(fanId, ContentReport.TARGET_POST, postId, "并发举报");
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
        List<ContentReport> all = reportService.onTarget(ContentReport.TARGET_POST, postId);
        assertThat(all).hasSize(1);
    }
}
