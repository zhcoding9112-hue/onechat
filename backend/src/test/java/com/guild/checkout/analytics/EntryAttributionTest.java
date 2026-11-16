package com.guild.checkout.analytics;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.auth.WechatAuthController;
import com.guild.checkout.order.OrderCreateCommand;
import com.guild.checkout.order.OrderService;
import com.guild.checkout.order.ServiceOrder;
import com.guild.checkout.order.ServiceOrderMapper;
import com.guild.checkout.pricing.ServiceItem;
import com.guild.checkout.pricing.ServiceSpec;
import com.guild.checkout.pricing.admin.CommissionAdminService;
import com.guild.checkout.pricing.admin.ServiceCatalogAdminService;
import com.guild.checkout.pricing.admin.TalentPriceAdminService;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.admin.TalentAdminService;
import com.guild.checkout.user.AppUser;
import com.guild.checkout.user.AppUserService;
import com.guild.checkout.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 入口归因与按入口拆分漏斗（tasks 11.1 - 11.5）。
 *
 * 与 FunnelScenarioTest 同样在事务内清空事件与订单：按入口核对数字，
 * 必须有确定的基线，否则「之和等于总数」这种断言根本无从验起。
 */
@SpringBootTest
@Transactional
class EntryAttributionTest {

    @Autowired private WebApplicationContext context;
    @Autowired private AnalyticsService analytics;
    @Autowired private FunnelService funnelService;
    @Autowired private AnalyticsEventMapper eventMapper;
    @Autowired private OrderService orderService;
    @Autowired private ServiceOrderMapper orderMapper;
    @Autowired private AppUserService userService;
    @Autowired private TalentAdminService talentAdminService;
    @Autowired private ServiceCatalogAdminService catalogService;
    @Autowired private TalentPriceAdminService priceService;
    @Autowired private CommissionAdminService commissionService;

    private MockMvc mockMvc;
    private MockHttpSession fan;
    private long fanId;
    private long otherFanId;
    private long operatorId;
    private long talentId;
    private long specId;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        commissionService.setDefault(3000);
        today = LocalDate.now();
        eventMapper.delete(Wrappers.emptyWrapper());
        orderMapper.delete(Wrappers.emptyWrapper());

        AppUser fanUser = newUser("ef_");
        fanId = fanUser.getId();
        fan = sessionFor(fanUser);
        otherFanId = newUser("eo_").getId();
        AppUser op = newUser("ep_");
        operatorId = op.getId();
        userService.grantRole(operatorId, UserRole.OPERATOR);

        ServiceItem item = catalogService.createItem("en_" + System.nanoTime(), "情感倾听", null, 0);
        ServiceSpec spec = catalogService.createSpec(item.getId(), "m30", "30分钟", 30, 30);
        specId = spec.getId();

        Talent talent = talentAdminService.create(new TalentAdminService.ProfileForm(
                "归因主播" + System.nanoTime(), null, null, 0, null, null, null, null));
        talentAdminService.setListed(talent.getId(), true);
        talentId = talent.getId();
        priceService.setPrice(talentId, specId, 1500, true);
    }

    private AppUser newUser(String prefix) {
        return userService.registerOrUpdate(prefix + System.nanoTime(), null, "测试用户", null);
    }

    private MockHttpSession sessionFor(AppUser user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(WechatAuthController.SESSION_USER_ID, user.getId());
        return session;
    }

    // ===== 11.1 入口事件枚举 =====

    @Test
    @DisplayName("11.1 事件枚举覆盖 tasks 点名的全部 11 个发现入口")
    void 枚举覆盖全部入口() {
        List<String> labels = EntrySource.all().stream().map(EntrySource::label).toList();
        assertThat(labels).contains(
                "品类 Tab", "人气店员", "九宫格", "运营横幅", "试音选人", "随机匹配",
                "心动墙", "动态广场", "榜单", "收藏列表", "站内聊天");
        // 直接访问必须单独成项，不能和「未知」混为一谈
        assertThat(labels).contains("直接访问");
    }

    @Test
    @DisplayName("11.1 每个入口有自己的事件键，且互不相同")
    void 每个入口独立事件键() {
        List<String> keys = EntrySource.all().stream().map(EntrySource::eventKey).toList();
        assertThat(keys).doesNotHaveDuplicates().hasSize(EntrySource.values().length);
        assertThat(EntrySource.byEventKey("ENTRY_POPULAR")).isEqualTo(EntrySource.POPULAR);
        assertThat(EntrySource.byEventKey("VIEW_TALENT_DETAIL")).isNull();
    }

    @Test
    @DisplayName("11.1 入口点击事件可上报并落库，来源与事件键一致")
    void 入口点击可上报() throws Exception {
        track("ENTRY_CRUSH_WALL", null);

        List<AnalyticsEvent> events = eventMapper.selectList(null);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventKey()).isEqualTo("ENTRY_CRUSH_WALL");
        assertThat(events.get(0).getEntrySource()).isEqualTo("CRUSH_WALL");
        assertThat(events.get(0).getUserId()).isEqualTo(fanId);
    }

    @Test
    @DisplayName("11.1 入口事件不混进漏斗：漏斗级数不因新增入口而变化")
    void 入口事件不混进漏斗() {
        track分析(EventKey.OPEN_H5, EntrySource.POPULAR, fanId);
        analytics.trackEntryClick(EntrySource.POPULAR, fanId, talentId);

        int steps = funnelService.overview(today, today).funnel().size();
        assertThat(steps).isEqualTo(EventKey.values().length);
    }

    // ===== 11.2 来源归因 =====

    @Test
    @DisplayName("11.2 从人气店员进详情，事件来源标记为人气店员")
    void 从人气店员进详情() throws Exception {
        track("VIEW_TALENT_DETAIL", "POPULAR");

        AnalyticsEvent e = eventMapper.selectList(Wrappers.<AnalyticsEvent>lambdaQuery()
                .eq(AnalyticsEvent::getEventKey, "VIEW_TALENT_DETAIL")).get(0);
        assertThat(e.getEntrySource()).isEqualTo("POPULAR");
        assertThat(EntrySource.of(e.getEntrySource()).label()).isEqualTo("人气店员");
    }

    @Test
    @DisplayName("11.2 下单把来源快照在订单上，确认收款时据此归因")
    void 收款事件按订单上的快照归因() {
        ServiceOrder order = orderService.create(new OrderCreateCommand(
                fanId, talentId, specId, 1, "wx_fan", null, null, EntrySource.LEADERBOARD));
        assertThat(order.getEntrySource()).isEqualTo("LEADERBOARD");

        orderService.confirmPayment(order.getId(), 1500, operatorId, null);

        AnalyticsEvent paid = eventMapper.selectList(Wrappers.<AnalyticsEvent>lambdaQuery()
                .eq(AnalyticsEvent::getEventKey, "CONFIRM_PAYMENT")).get(0);
        assertThat(paid.getEntrySource()).isEqualTo("LEADERBOARD");
    }

    // ===== 11.3 直接访问 =====

    @Test
    @DisplayName("11.3 不带来源的进入记为直接访问")
    void 不带来源记为直接访问() throws Exception {
        track("VIEW_TALENT_DETAIL", null);

        AnalyticsEvent e = eventMapper.selectList(null).get(0);
        assertThat(e.getEntrySource()).isEqualTo("DIRECT");
    }

    @Test
    @DisplayName("11.3 认不出的来源按直接访问记，不拒绝上报")
    void 未知来源不拒绝() throws Exception {
        track("VIEW_TALENT_DETAIL", "SOME_FUTURE_ENTRY");

        assertThat(eventMapper.selectList(null).get(0).getEntrySource()).isEqualTo("DIRECT");
    }

    @Test
    @DisplayName("11.3 不带来源的下单，订单快照也是直接访问")
    void 不带来源的下单() {
        ServiceOrder order = orderService.create(new OrderCreateCommand(
                fanId, talentId, specId, 1, "wx_fan", null));
        assertThat(order.getEntrySource()).isEqualTo("DIRECT");
    }

    @Test
    @DisplayName("11.3 直接访问在拆分表中单独成列")
    void 直接访问单独成列() throws Exception {
        track("VIEW_TALENT_DETAIL", null);

        FunnelService.EntryBreakdown direct = row("DIRECT");
        assertThat(direct.label()).isEqualTo("直接访问");
        assertThat(direct.uvByKey().get("VIEW_TALENT_DETAIL")).isEqualTo(1);
    }

    @Test
    @DisplayName("11.3 事件表里的来源列永远不为空")
    void 来源列永不为空() {
        track分析(EventKey.OPEN_H5, null, fanId);
        analytics.track(EventKey.VIEW_TALENT_LIST, fanId);

        assertThat(eventMapper.selectList(null))
                .allSatisfy(e -> assertThat(e.getEntrySource()).isNotBlank());
    }

    // ===== 11.4 按入口拆分 =====

    @Test
    @DisplayName("11.4 各入口的三个节点人数之和与总数一致")
    void 各入口之和等于总数() {
        // 甲：人气店员进详情 → 下单 → 收款
        track分析(EventKey.VIEW_TALENT_DETAIL, EntrySource.POPULAR, fanId);
        ServiceOrder a = orderService.create(new OrderCreateCommand(
                fanId, talentId, specId, 1, "wx_a", null, null, EntrySource.POPULAR));
        orderService.confirmPayment(a.getId(), 1500, operatorId, null);

        // 乙：榜单进详情 → 下单，但没付款
        track分析(EventKey.VIEW_TALENT_DETAIL, EntrySource.LEADERBOARD, otherFanId);
        orderService.create(new OrderCreateCommand(
                otherFanId, talentId, specId, 1, "wx_b", null, null, EntrySource.LEADERBOARD));

        // 丙：直接访问只看了详情
        long thirdFan = newUser("et_").getId();
        track分析(EventKey.VIEW_TALENT_DETAIL, null, thirdFan);

        List<FunnelService.EntryBreakdown> rows = funnelService.byEntrySource(today, today);
        Map<String, Long> total = totalsOf(rows);

        assertThat(total.get("VIEW_TALENT_DETAIL")).isEqualTo(3);
        assertThat(total.get("SUBMIT_ORDER")).isEqualTo(2);
        assertThat(total.get("CONFIRM_PAYMENT")).isEqualTo(1);

        // 与不分来源的总漏斗逐级对齐
        for (FunnelService.FunnelStep step : funnelService.overview(today, today).funnel()) {
            if (total.containsKey(step.eventKey())) {
                assertThat(total.get(step.eventKey()))
                        .as("入口拆分之和应等于总漏斗的 %s", step.eventKey())
                        .isEqualTo(step.uv());
            }
        }
    }

    @Test
    @DisplayName("11.4 同一人从两个入口进详情只归因一次，之和不会大于总数")
    void 同一人多入口只归因一次() {
        track分析(EventKey.VIEW_TALENT_DETAIL, EntrySource.POPULAR, fanId);
        track分析(EventKey.VIEW_TALENT_DETAIL, EntrySource.LEADERBOARD, fanId);

        List<FunnelService.EntryBreakdown> rows = funnelService.byEntrySource(today, today);
        assertThat(totalsOf(rows).get("VIEW_TALENT_DETAIL")).isEqualTo(1);
        // 归给最早的那个入口
        assertThat(row("POPULAR").uvByKey().get("VIEW_TALENT_DETAIL")).isEqualTo(1);
        assertThat(row("LEADERBOARD").uvByKey().get("VIEW_TALENT_DETAIL")).isZero();

        // 总漏斗那一级也是 1 个人，两边对得上
        long overviewUv = funnelService.overview(today, today).funnel().stream()
                .filter(s -> s.eventKey().equals("VIEW_TALENT_DETAIL"))
                .findFirst().orElseThrow().uv();
        assertThat(overviewUv).isEqualTo(1);
    }

    @Test
    @DisplayName("11.4 同一人同一入口看多次也只算一个人")
    void 同一人多次只算一次() {
        track分析(EventKey.VIEW_TALENT_DETAIL, EntrySource.FEED, fanId);
        track分析(EventKey.VIEW_TALENT_DETAIL, EntrySource.FEED, fanId);
        track分析(EventKey.VIEW_TALENT_DETAIL, EntrySource.FEED, fanId);

        assertThat(row("FEED").uvByKey().get("VIEW_TALENT_DETAIL")).isEqualTo(1);
    }

    @Test
    @DisplayName("11.4 拆分表同时给出入口自身的点击数")
    void 拆分表含入口点击数() {
        analytics.trackEntryClick(EntrySource.AUDITION, fanId, talentId);
        analytics.trackEntryClick(EntrySource.AUDITION, otherFanId, talentId);
        // 点了入口却没进详情：这一行会是「点击 2、进详情 0」
        FunnelService.EntryBreakdown audition = row("AUDITION");
        assertThat(audition.uvByKey().get("ENTRY_AUDITION")).isEqualTo(2);
        assertThat(audition.uvByKey().get("VIEW_TALENT_DETAIL")).isZero();
    }

    // ===== 11.5 空行不隐藏 =====

    @Test
    @DisplayName("11.5 周期内无事件的入口仍然出现在拆分表中，值为零")
    void 无数据入口展示为零() {
        track分析(EventKey.VIEW_TALENT_DETAIL, EntrySource.POPULAR, fanId);

        List<FunnelService.EntryBreakdown> rows = funnelService.byEntrySource(today, today);
        // 每个入口一行，一个不少
        assertThat(rows).hasSize(EntrySource.values().length);
        assertThat(rows).extracting(FunnelService.EntryBreakdown::source)
                .containsExactlyElementsOf(EntrySource.all().stream().map(Enum::name).toList());

        FunnelService.EntryBreakdown empty = row("CRUSH_WALL");
        assertThat(empty.uvByKey().values()).allSatisfy(v -> assertThat(v).isZero());
        // 是零，不是 null——前端据此渲染数字，null 会显示成空白
        assertThat(empty.uvByKey().get("SUBMIT_ORDER")).isNotNull().isZero();
    }

    @Test
    @DisplayName("11.5 完全没有事件时，拆分表仍是完整的全零表")
    void 完全无数据时仍是全零表() {
        List<FunnelService.EntryBreakdown> rows = funnelService.byEntrySource(today, today);
        assertThat(rows).hasSize(EntrySource.values().length);
        assertThat(rows).allSatisfy(r ->
                assertThat(r.uvByKey().values()).allSatisfy(v -> assertThat(v).isZero()));
    }

    // ===== 辅助 =====

    private void track(String eventKey, String entrySource) throws Exception {
        String body = entrySource == null
                ? "{\"eventKey\":\"%s\",\"talentId\":%d}".formatted(eventKey, talentId)
                : "{\"eventKey\":\"%s\",\"entrySource\":\"%s\",\"talentId\":%d}"
                        .formatted(eventKey, entrySource, talentId);
        mockMvc.perform(post("/api/analytics/track").session(fan)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private void track分析(EventKey key, EntrySource source, long userId) {
        analytics.track(AnalyticsService.Track.of(key).from(source).user(userId).talent(talentId));
    }

    private FunnelService.EntryBreakdown row(String source) {
        return funnelService.byEntrySource(today, today).stream()
                .filter(r -> r.source().equals(source)).findFirst().orElseThrow();
    }

    private static Map<String, Long> totalsOf(List<FunnelService.EntryBreakdown> rows) {
        return FunnelService.ENTRY_STEPS.stream().collect(java.util.stream.Collectors.toMap(
                EventKey::name,
                k -> rows.stream().mapToLong(r -> r.uvByKey().getOrDefault(k.name(), 0L)).sum()));
    }
}
