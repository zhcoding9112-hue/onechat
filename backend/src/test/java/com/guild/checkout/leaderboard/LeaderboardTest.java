package com.guild.checkout.leaderboard;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.admin.TalentAdminService;
import com.guild.checkout.tipping.TipRecord;
import com.guild.checkout.tipping.TipService;
import com.guild.checkout.tipping.TipStatus;
import com.guild.checkout.user.AppUser;
import com.guild.checkout.user.AppUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 打赏榜单（tasks 8.1 - 8.4）。
 */
@SpringBootTest
@Transactional
class LeaderboardTest {

    @Autowired private LeaderboardService leaderboardService;
    @Autowired private LeaderboardTotalMapper totalMapper;
    @Autowired private LeaderboardSnapshotMapper snapshotMapper;
    @Autowired private TipService tipService;
    @Autowired private TalentAdminService talentAdminService;
    @Autowired private AppUserService userService;
    @Autowired private JdbcTemplate jdbc;

    private long fanId;
    private long talentA;
    private long talentB;
    private long talentC;
    private long talentD;

    @BeforeEach
    void setUp() {
        fanId = newUser("lf_").getId();
        talentA = newTalent("榜一");
        talentB = newTalent("榜二");
        talentC = newTalent("榜三");
        talentD = newTalent("榜四");
    }

    private AppUser newUser(String prefix) {
        return userService.registerOrUpdate(prefix + System.nanoTime(), null, "测试用户", null);
    }

    private long newTalent(String nickname) {
        Talent t = talentAdminService.create(new TalentAdminService.ProfileForm(
                nickname + System.nanoTime(), null, null, 0, null, null, null, null));
        talentAdminService.setListed(t.getId(), true);
        return t.getId();
    }

    /** 打赏并确认收款，即计入榜单。 */
    private TipRecord tipped(long talentId, long amountCents) {
        TipRecord tip = tipService.create(fanId, talentId, amountCents, null);
        return tipService.confirmPayment(tip.getId(), amountCents, null);
    }

    private String todayKey() {
        return PeriodType.DAY.currentKey();
    }

    // ===== 8.1 累计可增可减 =====

    @Test
    @DisplayName("8.1 确认收款后三种周期的累计都增加")
    void 收款后三种周期都计入() {
        tipped(talentA, 5000);

        for (PeriodType type : PeriodType.values()) {
            assertThat(leaderboardService.amountOf(type, type.currentKey(), talentA))
                    .as("%s 的累计", type).isEqualTo(5000);
        }
    }

    @Test
    @DisplayName("8.1 多笔打赏累加，笔数同步增加")
    void 多笔累加() {
        tipped(talentA, 5000);
        tipped(talentA, 3000);

        assertThat(leaderboardService.amountOf(PeriodType.DAY, todayKey(), talentA)).isEqualTo(8000);
        LeaderboardTotal total = totalMapper.selectOne(Wrappers.<LeaderboardTotal>lambdaQuery()
                .eq(LeaderboardTotal::getPeriodType, PeriodType.DAY.name())
                .eq(LeaderboardTotal::getPeriodKey, todayKey())
                .eq(LeaderboardTotal::getTalentId, talentA));
        assertThat(total.getTipCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("8.1 累计值可减：退款后金额与笔数都回落")
    void 累计可减() {
        TipRecord first = tipped(talentA, 5000);
        tipped(talentA, 3000);

        tipService.markRefunded(first.getId(), "粉丝投诉");

        assertThat(leaderboardService.amountOf(PeriodType.DAY, todayKey(), talentA)).isEqualTo(3000);
        LeaderboardTotal total = totalMapper.selectOne(Wrappers.<LeaderboardTotal>lambdaQuery()
                .eq(LeaderboardTotal::getPeriodType, PeriodType.DAY.name())
                .eq(LeaderboardTotal::getPeriodKey, todayKey())
                .eq(LeaderboardTotal::getTalentId, talentA));
        assertThat(total.getTipCount()).isEqualTo(1);
    }

    // ===== 8.2 退款扣除 =====

    @Test
    @DisplayName("8.2 一笔已计入的打赏被退款后排名金额相应减少")
    void 退款后排名金额减少() {
        tipped(talentA, 10000);
        tipped(talentB, 6000);
        assertThat(ranksOf()).containsExactly(talentA, talentB);

        TipRecord big = tipService.create(fanId, talentB, 9000, null);
        tipService.confirmPayment(big.getId(), 9000, null);
        // B 现在 15000，超过 A
        assertThat(ranksOf()).containsExactly(talentB, talentA);

        tipService.markRefunded(big.getId(), "重复支付");
        // 退回 9000，B 回到 6000，名次换回来
        assertThat(leaderboardService.amountOf(PeriodType.DAY, todayKey(), talentB)).isEqualTo(6000);
        assertThat(ranksOf()).containsExactly(talentA, talentB);
    }

    @Test
    @DisplayName("8.2 未确认收款的打赏不计入榜单")
    void 未确认不计入() {
        tipService.create(fanId, talentA, 5000, null);

        assertThat(leaderboardService.amountOf(PeriodType.DAY, todayKey(), talentA)).isZero();
        assertThat(leaderboardService.top(PeriodType.DAY, todayKey(), 10)).isEmpty();
    }

    @Test
    @DisplayName("8.2 重复标记退款是幂等的，不会扣两次")
    void 重复退款不重复扣() {
        TipRecord tip = tipped(talentA, 5000);

        tipService.markRefunded(tip.getId(), "粉丝投诉");
        tipService.markRefunded(tip.getId(), "粉丝投诉");

        assertThat(leaderboardService.amountOf(PeriodType.DAY, todayKey(), talentA)).isZero();
    }

    @Test
    @DisplayName("8.2 退款扣的是打赏原本计入的那个周期，不是当前周期")
    void 退款扣回原周期() {
        TipRecord tip = tipped(talentA, 5000);

        // 把这笔打赏挪到上个月收款，并把累计也挪过去，模拟跨周期退款
        LocalDateTime lastMonth = LocalDateTime.now().minusMonths(1);
        String oldKey = PeriodType.MONTH.keyOf(lastMonth);
        String nowKey = PeriodType.MONTH.currentKey();
        jdbc.update("UPDATE tip_record SET paid_at = ? WHERE id = ?", lastMonth, tip.getId());
        jdbc.update("UPDATE leaderboard_total SET period_key = ? "
                + "WHERE period_type = 'MONTH' AND period_key = ? AND talent_id = ?",
                oldKey, nowKey, talentA);

        tipService.markRefunded(tip.getId(), "跨月退款");

        // 上月被扣回 0，本月没有被凭空减出负数
        assertThat(leaderboardService.amountOf(PeriodType.MONTH, oldKey, talentA)).isZero();
        assertThat(leaderboardService.amountOf(PeriodType.MONTH, nowKey, talentA)).isZero();
        LeaderboardTotal current = totalMapper.selectOne(Wrappers.<LeaderboardTotal>lambdaQuery()
                .eq(LeaderboardTotal::getPeriodType, PeriodType.MONTH.name())
                .eq(LeaderboardTotal::getPeriodKey, nowKey)
                .eq(LeaderboardTotal::getTalentId, talentA));
        assertThat(current).as("本月不该因为上月的退款而出现负数行").isNull();
    }

    // ===== 8.3 周期切换与固化 =====

    @Test
    @DisplayName("8.3 新周期从零开始")
    void 新周期从零开始() {
        tipped(talentA, 5000);
        String yesterday = PeriodType.DAY.keyOf(LocalDateTime.now().minusDays(1));

        assertThat(leaderboardService.amountOf(PeriodType.DAY, todayKey(), talentA)).isEqualTo(5000);
        assertThat(leaderboardService.amountOf(PeriodType.DAY, yesterday, talentA)).isZero();
    }

    @Test
    @DisplayName("8.3 固化后快照内容不再变化，哪怕累计值事后被退款改掉")
    void 固化后快照不变() {
        TipRecord tip = tipped(talentA, 5000);
        // 把这笔挪到昨天，构造一个已结束的周期
        String yesterday = movedToYesterday(tip, talentA);

        leaderboardService.freeze(PeriodType.DAY, yesterday);
        List<LeaderboardService.Row> frozen = leaderboardService.top(PeriodType.DAY, yesterday, 10);
        assertThat(frozen).hasSize(1);
        assertThat(frozen.get(0).amountCents()).isEqualTo(5000);

        tipService.markRefunded(tip.getId(), "事后退款");

        // 累计变了
        assertThat(leaderboardService.amountOf(PeriodType.DAY, yesterday, talentA)).isZero();
        // 快照没变
        assertThat(leaderboardService.top(PeriodType.DAY, yesterday, 10).get(0).amountCents())
                .isEqualTo(5000);
    }

    @Test
    @DisplayName("8.3 固化是幂等的，重复触发不产生第二份名次")
    void 固化幂等() {
        TipRecord tip = tipped(talentA, 5000);
        String yesterday = movedToYesterday(tip, talentA);

        assertThat(leaderboardService.freeze(PeriodType.DAY, yesterday)).isEqualTo(1);
        assertThat(leaderboardService.freeze(PeriodType.DAY, yesterday)).isZero();

        assertThat(snapshotMapper.selectCount(Wrappers.<LeaderboardSnapshot>lambdaQuery()
                .eq(LeaderboardSnapshot::getPeriodType, PeriodType.DAY.name())
                .eq(LeaderboardSnapshot::getPeriodKey, yesterday))).isEqualTo(1);
    }

    @Test
    @DisplayName("8.3 读历史周期会顺手固化它，不依赖定时任务")
    void 读历史即固化() {
        TipRecord tip = tipped(talentA, 5000);
        String yesterday = movedToYesterday(tip, talentA);

        assertThat(leaderboardService.isFrozen(PeriodType.DAY, yesterday)).isFalse();
        leaderboardService.top(PeriodType.DAY, yesterday, 10);
        assertThat(leaderboardService.isFrozen(PeriodType.DAY, yesterday)).isTrue();
    }

    @Test
    @DisplayName("8.3 当前周期不能被固化")
    void 当前周期不可固化() {
        assertThatThrownBy(() -> leaderboardService.freeze(PeriodType.DAY, todayKey()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("尚未结束");
    }

    @Test
    @DisplayName("8.3 快照保留固化时的昵称，主播改名不影响历史榜单")
    void 快照保留当时昵称() {
        TipRecord tip = tipped(talentA, 5000);
        String yesterday = movedToYesterday(tip, talentA);
        leaderboardService.freeze(PeriodType.DAY, yesterday);
        String original = leaderboardService.top(PeriodType.DAY, yesterday, 10).get(0).nickname();

        jdbc.update("UPDATE talent SET nickname = ? WHERE id = ?", "改了名字", talentA);

        assertThat(leaderboardService.top(PeriodType.DAY, yesterday, 10).get(0).nickname())
                .isEqualTo(original).isNotEqualTo("改了名字");
    }

    // ===== 8.4 展示规则 =====

    @Test
    @DisplayName("8.4 榜单按金额倒序，名次从 1 开始连续")
    void 名次连续且倒序() {
        tipped(talentA, 1000);
        tipped(talentB, 9000);
        tipped(talentC, 5000);
        tipped(talentD, 3000);

        List<LeaderboardService.Row> rows = leaderboardService.top(PeriodType.DAY, todayKey(), 10);

        assertThat(rows.stream().map(LeaderboardService.Row::talentId))
                .containsExactly(talentB, talentC, talentD, talentA);
        assertThat(rows.stream().map(LeaderboardService.Row::rank))
                .containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("8.4 榜单行里不含任何打赏人字段")
    void 榜单不含打赏人字段() {
        tipped(talentA, 5000);

        List<String> fields = java.util.Arrays.stream(
                        LeaderboardService.Row.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList();

        // 结构上就没有打赏人相关的字段，而不是靠记得在某处过滤
        assertThat(fields).containsExactlyInAnyOrder(
                "rank", "talentId", "nickname", "coverUrl", "amountCents", "tipCount");
        assertThat(fields).noneMatch(f -> f.toLowerCase().contains("fan")
                || f.toLowerCase().contains("user"));
    }

    @Test
    @DisplayName("8.4 本周期无任何已确认打赏时返回空榜单而不是报错")
    void 空榜单() {
        assertThat(leaderboardService.top(PeriodType.DAY, todayKey(), 10)).isEmpty();
    }

    @Test
    @DisplayName("8.4 金额退成 0 的主播不出现在榜上")
    void 退成零不上榜() {
        TipRecord tip = tipped(talentA, 5000);
        tipped(talentB, 3000);

        tipService.markRefunded(tip.getId(), "退款");

        assertThat(ranksOf()).containsExactly(talentB);
    }

    @Test
    @DisplayName("8.4 limit 生效，取前 N 名")
    void 限制条数() {
        tipped(talentA, 1000);
        tipped(talentB, 9000);
        tipped(talentC, 5000);

        assertThat(leaderboardService.top(PeriodType.DAY, todayKey(), 2)).hasSize(2);
    }

    // ---------- 辅助 ----------

    /** 把某笔打赏与它的日累计一起挪到昨天，构造一个已结束的周期。 */
    private String movedToYesterday(TipRecord tip, long talentId) {
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
        String key = PeriodType.DAY.keyOf(yesterday);
        jdbc.update("UPDATE tip_record SET paid_at = ? WHERE id = ?", yesterday, tip.getId());
        jdbc.update("UPDATE leaderboard_total SET period_key = ? "
                + "WHERE period_type = 'DAY' AND period_key = ? AND talent_id = ?",
                key, todayKey(), talentId);
        return key;
    }

    /** 当前日榜上的主播 id，按名次。 */
    private List<Long> ranksOf() {
        return leaderboardService.top(PeriodType.DAY, todayKey(), 20).stream()
                .map(LeaderboardService.Row::talentId)
                .filter(id -> id.equals(talentA) || id.equals(talentB)
                        || id.equals(talentC) || id.equals(talentD))
                .toList();
    }
}
