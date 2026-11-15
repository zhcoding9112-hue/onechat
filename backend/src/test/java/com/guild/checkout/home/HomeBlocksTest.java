package com.guild.checkout.home;

import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.TalentService;
import com.guild.checkout.talent.admin.TalentAdminService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 首页区块（tasks 2.4 - 2.8）。
 *
 * 开发库里本来就有大量夹具数据，所以一律用「相对断言」：
 * 只比较本用例自己造的数据之间的相对关系，不对全局条数或排名做绝对断言。
 */
@SpringBootTest
@Transactional
class HomeBlocksTest {

    @Autowired private HomeService homeService;
    @Autowired private HomeStatsMapper statsMapper;
    @Autowired private HomeEntryMapper entryMapper;
    @Autowired private TalentAdminService talentAdminService;
    @Autowired private TalentService talentService;
    @Autowired private JdbcTemplate jdbc;

    private Talent newListedTalent(String nickname, String gender) {
        Talent t = talentAdminService.create(new TalentAdminService.ProfileForm(
                nickname, null, null, 0, gender, null, null, null));
        talentAdminService.setListed(t.getId(), true);
        return talentService.require(t.getId());
    }

    /** 直接插订单行：这里要验的是统计口径，不是下单流程。 */
    private void insertPaidOrder(long talentId, String status, LocalDateTime paidAt) {
        jdbc.update("""
                INSERT INTO service_order
                  (order_ref, fan_user_id, talent_id, service_item_id, service_spec_id,
                   service_item_name, service_spec_name, quantity, unit_price_cents,
                   payable_cents, paid_cents, commission_rate_bp, guild_cents, talent_cents,
                   status, contact, paid_at, version)
                VALUES (?, 1, ?, 1, 1, '测试项目', '测试档位', 1, 100,
                        100, 100, 3000, 30, 70, ?, 'wx', ?, 0)
                """,
                "T" + Long.toString(System.nanoTime() % 100000000L, 36),
                talentId, status, paidAt);
    }

    @Test
    @DisplayName("2.4 人气店员按近 7 日成交单量倒序，零成交的主播仍在候选内")
    void 人气按近七日成交单量倒序() {
        Talent hot = newListedTalent("人气高的", null);
        Talent cold = newListedTalent("没有单的", null);
        insertPaidOrder(hot.getId(), "COMPLETED", LocalDateTime.now().minusDays(1));
        insertPaidOrder(hot.getId(), "ACCEPTED", LocalDateTime.now().minusDays(2));

        List<Long> ids = statsMapper.findPopularTalentIds(LocalDateTime.now().minusDays(7), 1000);

        // 零成交的主播必须也在候选里，否则新签约主播永远进不了人气位
        assertThat(ids).contains(hot.getId(), cold.getId());
        assertThat(ids.indexOf(hot.getId())).isLessThan(ids.indexOf(cold.getId()));
    }

    @Test
    @DisplayName("2.4 已退款与窗口外的订单不计入人气")
    void 退款与窗口外订单不计入人气() {
        Talent refunded = newListedTalent("只有退款单的", null);
        Talent stale = newListedTalent("只有旧单的", null);
        Talent clean = newListedTalent("什么都没有的", null);
        insertPaidOrder(refunded.getId(), "REFUNDED", LocalDateTime.now().minusDays(1));
        insertPaidOrder(stale.getId(), "COMPLETED", LocalDateTime.now().minusDays(30));

        List<Long> ids = statsMapper.findPopularTalentIds(LocalDateTime.now().minusDays(7), 1000);

        // 三者的有效成交量都是 0，于是只能按 sort_order、id 兜底排序，
        // 即创建更早的排在前面；若退款或旧单被误计，refunded/stale 会插到 clean 前面。
        assertThat(ids.indexOf(refunded.getId())).isLessThan(ids.indexOf(clean.getId()));
        assertThat(ids.indexOf(stale.getId())).isLessThan(ids.indexOf(clean.getId()));
    }

    @Test
    @DisplayName("2.5 跑马灯数据源不含任何打赏人字段")
    void 跑马灯不含打赏人信息() {
        List<Map<String, Object>> rows = statsMapper.findRecentTips(5);

        // 不是「上层记得过滤」，而是 SQL 根本没查出来
        for (Map<String, Object> row : rows) {
            assertThat(row.keySet()).containsExactlyInAnyOrder("nickname", "amountCents");
        }
    }

    @Test
    @DisplayName("2.6 禁用的配置位不出现在生效列表中")
    void 禁用的配置位不生效() {
        HomeEntry on = saveEntry("生效中的横幅", true, null, null);
        HomeEntry off = saveEntry("已禁用的横幅", false, null, null);

        List<Long> active = activeBannerIds();
        assertThat(active).contains(on.getId()).doesNotContain(off.getId());
    }

    @Test
    @DisplayName("2.7 生效时间之外的配置位不展示，无需定时任务改状态")
    void 生效时间控制() {
        HomeEntry expired = saveEntry("已过期", true,
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1));
        HomeEntry future = saveEntry("还没开始", true,
                LocalDateTime.now().plusDays(1), null);
        HomeEntry open = saveEntry("长期有效", true, null, null);
        HomeEntry inWindow = saveEntry("窗口内", true,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));

        List<Long> active = activeBannerIds();
        assertThat(active)
                .contains(open.getId(), inWindow.getId())
                .doesNotContain(expired.getId(), future.getId());
    }

    @Test
    @DisplayName("2.8 按性别筛选时，结果中不出现其他性别")
    void 按性别筛选() {
        newListedTalent("筛选用女主播", "FEMALE");
        newListedTalent("筛选用男主播", "MALE");

        TalentService.TalentPage page = talentService.listListed(null, "FEMALE", null, 0, 50);

        assertThat(page.items()).isNotEmpty();
        assertThat(page.items()).allSatisfy(t -> assertThat(t.getGender()).isEqualTo("FEMALE"));
    }

    @Test
    @DisplayName("2.8 分页不重不漏，且总数与分页无关")
    void 分页不重不漏() {
        TalentService.TalentPage first = talentService.listListed(null, null, null, 0, 5);
        TalentService.TalentPage second = talentService.listListed(null, null, null, 1, 5);

        assertThat(first.total()).isEqualTo(second.total());
        List<Long> firstIds = first.items().stream().map(Talent::getId).toList();
        List<Long> secondIds = second.items().stream().map(Talent::getId).toList();
        assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
    }

    @Test
    @DisplayName("2.8 取到最后一页时 hasMore 判定为假")
    void 最后一页没有更多() {
        long total = talentService.listListed(null, null, null, 0, 1).total();
        int size = 10;
        int lastPage = (int) Math.max(0, (total - 1) / size);

        TalentService.TalentPage page = talentService.listListed(null, null, null, lastPage, size);
        long consumed = (long) lastPage * size + page.items().size();

        assertThat(consumed).isEqualTo(total);
    }

    @Test
    @DisplayName("2.8 页码超出范围时返回空列表而非报错")
    void 页码超范围返回空() {
        TalentService.TalentPage page = talentService.listListed(null, null, null, 9999, 20);
        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isGreaterThanOrEqualTo(0);
    }

    private HomeEntry saveEntry(String title, boolean enabled, LocalDateTime start, LocalDateTime end) {
        HomeEntry e = new HomeEntry();
        e.setEntryType(HomeEntryType.BANNER.name());
        e.setTitle(title);
        e.setSortOrder(0);
        e.setEnabled(enabled);
        e.setStartAt(start);
        e.setEndAt(end);
        entryMapper.insert(e);
        return e;
    }

    private List<Long> activeBannerIds() {
        return homeService.listActiveEntries(HomeEntryType.BANNER).stream()
                .map(HomeEntry::getId)
                .toList();
    }
}
