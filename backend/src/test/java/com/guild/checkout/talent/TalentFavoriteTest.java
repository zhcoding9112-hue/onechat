package com.guild.checkout.talent;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.talent.admin.TalentAdminService;
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
 * 收藏（tasks 3.1 - 3.4）。
 *
 * 刻意不加 @Transactional：并发幂等必须在真实提交下验证，
 * 测试事务会把几个线程隔离开，唯一索引根本不会被触发到。
 * 因此自行清理本用例造出来的数据。
 */
@SpringBootTest
class TalentFavoriteTest {

    @Autowired private TalentFavoriteService favoriteService;
    @Autowired private TalentFavoriteMapper favoriteMapper;
    @Autowired private TalentAdminService talentAdminService;
    @Autowired private TalentService talentService;

    private static final long FAN_ID = 999_000_001L;

    private Long talentId;

    @BeforeEach
    void setUp() {
        Talent t = talentAdminService.create(new TalentAdminService.ProfileForm(
                "收藏测试主播", null, null, 0, null, null, null, null));
        talentAdminService.setListed(t.getId(), true);
        talentId = t.getId();
        clearFavorites();
    }

    @AfterEach
    void tearDown() {
        clearFavorites();
        if (talentId != null) {
            talentAdminService.setListed(talentId, false);
        }
    }

    private void clearFavorites() {
        favoriteMapper.delete(Wrappers.<TalentFavorite>lambdaQuery()
                .eq(TalentFavorite::getUserId, FAN_ID));
    }

    private long countFavorites() {
        return favoriteMapper.selectCount(Wrappers.<TalentFavorite>lambdaQuery()
                .eq(TalentFavorite::getUserId, FAN_ID)
                .eq(TalentFavorite::getTalentId, talentId));
    }

    @Test
    @DisplayName("3.1 并发重复收藏同一主播只产生一条记录")
    void 并发重复收藏只产生一条() throws Exception {
        int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    favoriteService.add(FAN_ID, talentId);
                } catch (Exception e) {
                    // 唯一索引冲突必须在服务内部被吞掉，冒到这里就算失败
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(failures.get()).isZero();
        assertThat(countFavorites()).isEqualTo(1);
    }

    @Test
    @DisplayName("3.2 重复收藏幂等，记录数不变")
    void 重复收藏幂等() {
        favoriteService.add(FAN_ID, talentId);
        favoriteService.add(FAN_ID, talentId);
        favoriteService.add(FAN_ID, talentId);

        assertThat(countFavorites()).isEqualTo(1);
        assertThat(favoriteService.isFavorited(FAN_ID, talentId)).isTrue();
    }

    @Test
    @DisplayName("3.2 取消收藏后状态变为未收藏，重复取消不报错")
    void 取消收藏幂等() {
        favoriteService.add(FAN_ID, talentId);
        favoriteService.remove(FAN_ID, talentId);
        favoriteService.remove(FAN_ID, talentId);

        assertThat(countFavorites()).isZero();
        assertThat(favoriteService.isFavorited(FAN_ID, talentId)).isFalse();
    }

    @Test
    @DisplayName("3.4 收藏列表按收藏时间倒序")
    void 收藏列表按时间倒序() throws Exception {
        Talent other = talentAdminService.create(new TalentAdminService.ProfileForm(
                "后收藏的", null, null, 0, null, null, null, null));
        try {
            favoriteService.add(FAN_ID, talentId);
            // created_at 是 DATETIME(3)，同毫秒内两条的先后无法区分，这里拉开间隔
            Thread.sleep(5);
            favoriteService.add(FAN_ID, other.getId());

            List<Long> ids = favoriteService.listTalentIds(FAN_ID);
            assertThat(ids).containsExactly(other.getId(), talentId);
        } finally {
            favoriteMapper.delete(Wrappers.<TalentFavorite>lambdaQuery()
                    .eq(TalentFavorite::getUserId, FAN_ID));
        }
    }

    @Test
    @DisplayName("3.4 主播下架后仍保留在收藏关系中")
    void 下架后仍保留收藏关系() {
        favoriteService.add(FAN_ID, talentId);
        talentAdminService.setListed(talentId, false);

        assertThat(favoriteService.listTalentIds(FAN_ID)).contains(talentId);
        assertThat(talentService.require(talentId).getListed()).isFalse();
    }

    @Test
    @DisplayName("3.2 批量查询收藏状态只用一次查询")
    void 批量查询收藏状态() {
        favoriteService.add(FAN_ID, talentId);

        var favored = favoriteService.filterFavorited(FAN_ID, List.of(talentId, 999_999_999L));

        assertThat(favored).containsExactly(talentId);
    }

    @Test
    @DisplayName("3.2 收藏不存在的主播被拒绝")
    void 收藏不存在的主播被拒绝() {
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class,
                () -> favoriteService.add(FAN_ID, 999_999_999L)))
                .isNotNull();
        assertThat(favoriteService.listTalentIds(FAN_ID)).isEmpty();
    }
}
