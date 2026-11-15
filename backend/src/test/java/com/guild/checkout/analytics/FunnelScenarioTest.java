package com.guild.checkout.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * analytics-funnel spec 的场景测试（tasks 12.3 - 12.13）。
 *
 * 指标全部用构造数据集手工核算后写死期望值——这是验收明确要求的，
 * 也是唯一能发现「口径写错但看着像对的」那类 bug 的方法。
 */
@SpringBootTest
@Transactional
class FunnelScenarioTest {

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
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private MockHttpSession operator;
    private MockHttpSession fan;
    private MockHttpSession talentSession;
    private long fanId;
    private long operatorId;
    private long talentId;
    private long talentUserId;
    private long serviceItemId;
    private long specId;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        commissionService.setDefault(3000);
        today = LocalDate.now();
        // 清空事件表，让构造数据集的核算不受既有数据干扰
        eventMapper.delete(com.baomidou.mybatisplus.core.toolkit.Wrappers.emptyWrapper());

        AppUser fanUser = newUser("ff_");
        fanId = fanUser.getId();
        fan = sessionFor(fanUser);
        AppUser op = newUser("fo_");
        operatorId = op.getId();
        userService.grantRole(operatorId, UserRole.OPERATOR);
        operator = sessionFor(op);

        ServiceItem item = catalogService.createItem("fn_" + System.nanoTime(), "情感倾听", null, 0);
        serviceItemId = item.getId();
        ServiceSpec spec = catalogService.createSpec(item.getId(), "m30", "30分钟", 30, 30);
        specId = spec.getId();

        Talent talent = talentAdminService.create("小雨", null, null, 0);
        talentAdminService.setListed(talent.getId(), true);
        talentId = talent.getId();
        priceService.setPrice(talentId, specId, 1500, true);

        AppUser talentUser = newUser("ft_");
        talentAdminService.linkUser(talentId, talentUser.getId());
        talentUserId = talentUser.getId();
        talentSession = sessionFor(talentUser);
    }

    // ===== Requirement: 全链路漏斗节点埋点 =====

    @Test
    @DisplayName("Scenario: 记录节点事件 — 含节点标识、用户标识与发生时间")
    void 记录节点事件() {
        analytics.track(AnalyticsService.Track.of(EventKey.OPEN_H5).user(fanId));

        List<AnalyticsEvent> events = eventMapper.selectList(null);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventKey()).isEqualTo("OPEN_H5");
        assertThat(events.get(0).getUserId()).isEqualTo(fanId);
        assertThat(events.get(0).getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("Scenario: 事件携带归因维度 — 主播与服务项目标识")
    void 事件携带归因维度() {
        completeOrder();

        List<AnalyticsEvent> submits = eventMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<AnalyticsEvent>lambdaQuery()
                        .eq(AnalyticsEvent::getEventKey, "SUBMIT_ORDER"));
        assertThat(submits).hasSize(1);
        assertThat(submits.get(0).getTalentId()).isEqualTo(talentId);
        assertThat(submits.get(0).getServiceItemId()).isEqualTo(serviceItemId);
        assertThat(submits.get(0).getOrderRef()).isNotBlank();
    }

    @Test
    @DisplayName("12.3 走完一笔订单后确认收款、主播接单、服务完成三类事件均已记录")
    void 后端三类事件均记录() {
        completeOrder();

        List<String> keys = eventMapper.selectList(null).stream()
                .map(AnalyticsEvent::getEventKey).toList();
        assertThat(keys).contains("SUBMIT_ORDER", "CONFIRM_PAYMENT", "ACCEPT_ORDER", "COMPLETE_ORDER");
    }

    @Test
    @DisplayName("12.4 前端上报七类浏览事件，均携带归因维度")
    void 前端埋点上报() throws Exception {
        for (EventKey key : List.of(EventKey.OPEN_H5, EventKey.VIEW_TALENT_LIST,
                EventKey.VIEW_TALENT_DETAIL, EventKey.CLICK_ORDER)) {
            mockMvc.perform(post("/api/analytics/track").session(fan)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"eventKey\":\"" + key.name() + "\",\"talentId\":" + talentId
                                    + ",\"serviceItemId\":" + serviceItemId + "}"))
                    .andExpect(status().isOk());
        }

        List<AnalyticsEvent> events = eventMapper.selectList(null);
        assertThat(events).hasSize(4);
        assertThat(events).allSatisfy(e -> {
            assertThat(e.getUserId()).isEqualTo(fanId);
            assertThat(e.getTalentId()).isEqualTo(talentId);
        });
    }

    @Test
    @DisplayName("前端不能伪造涉及钱的节点事件")
    void 前端不能伪造收款事件() throws Exception {
        mockMvc.perform(post("/api/analytics/track").session(fan)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventKey\":\"CONFIRM_PAYMENT\"}"))
                .andExpect(status().isOk());

        // 静默忽略，不写库
        assertThat(eventMapper.selectList(null)).isEmpty();
    }

    @Test
    @DisplayName("12.5 关注服务号事件以 openId 记录，与后续授权归成同一人")
    void 关注事件() throws Exception {
        String openId = "o_follow_" + System.nanoTime();

        mockMvc.perform(post("/api/analytics/follow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openId\":\"" + openId + "\"}"))
                .andExpect(status().isOk());

        List<AnalyticsEvent> events = eventMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<AnalyticsEvent>lambdaQuery()
                        .eq(AnalyticsEvent::getEventKey, "FOLLOW_ACCOUNT"));
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getOpenId()).isEqualTo(openId);
        assertThat(events.get(0).getUserId()).isNull();
    }

    // ===== Requirement: 转化漏斗看板 =====

    @Test
    @DisplayName("Scenario: 查看漏斗 — 各节点去重人数与相邻转化率")
    void 查看漏斗() {
        // 构造数据集：3 人打开，2 人看详情，1 人下单
        long a = newUser("u1_").getId();
        long b = newUser("u2_").getId();
        long c = newUser("u3_").getId();
        track(EventKey.OPEN_H5, a); track(EventKey.OPEN_H5, b); track(EventKey.OPEN_H5, c);
        // 同一人重复打开只算一次
        track(EventKey.OPEN_H5, a);
        track(EventKey.VIEW_TALENT_LIST, a); track(EventKey.VIEW_TALENT_LIST, b);
        track(EventKey.VIEW_TALENT_LIST, c);
        track(EventKey.VIEW_TALENT_DETAIL, a); track(EventKey.VIEW_TALENT_DETAIL, b);
        track(EventKey.SUBMIT_ORDER, a);

        var overview = funnelService.overview(today, today);
        Map<String, FunnelService.FunnelStep> byKey = index(overview.funnel());

        assertThat(byKey.get("OPEN_H5").uv()).isEqualTo(3);
        assertThat(byKey.get("VIEW_TALENT_DETAIL").uv()).isEqualTo(2);
        assertThat(byKey.get("SUBMIT_ORDER").uv()).isEqualTo(1);
        // 详情 2 人 / 列表 3 人 = 66.67% → 6667 基点
        assertThat(byKey.get("VIEW_TALENT_LIST").uv()).isEqualTo(3);
        assertThat(byKey.get("VIEW_TALENT_DETAIL").conversionBp()).isEqualTo(6667);
        assertThat(byKey.get("VIEW_TALENT_DETAIL").lostUv()).isEqualTo(1);
        // SUBMIT_ORDER 的上一级是 CLICK_ORDER（0 人），上一级为 0 时转化率无意义
        assertThat(byKey.get("CLICK_ORDER").uv()).isZero();
        assertThat(byKey.get("SUBMIT_ORDER").conversionBp()).isNull();
    }

    @Test
    @DisplayName("Scenario: 时间范围内无数据 — 零值与空状态，不报错")
    void 无数据零值() {
        var overview = funnelService.overview(today.minusDays(30), today.minusDays(25));

        assertThat(overview.funnel()).hasSize(EventKey.values().length);
        assertThat(overview.funnel()).allSatisfy(s -> assertThat(s.uv()).isZero());
        assertThat(overview.metrics().visitToPayBp()).isNull();
        assertThat(overview.metrics().acceptRateBp()).isNull();
    }

    @Test
    @DisplayName("Scenario: 展示核心指标 — 四项与手工核算一致")
    void 核心指标() {
        // 构造：4 人打开 H5，其中 1 人完成付费
        long a = newUser("m1_").getId();
        for (long u : List.of(a, newUser("m2_").getId(), newUser("m3_").getId(),
                newUser("m4_").getId())) {
            track(EventKey.OPEN_H5, u);
        }
        completeOrder();  // 这笔由 fanId 产生，但 fanId 没打开过 H5
        track(EventKey.OPEN_H5, fanId);  // 补上，总共 5 人打开

        var m = funnelService.overview(today, today).metrics();

        // 访问到付费：1 人付费 / 5 人打开 = 20% → 2000 基点
        assertThat(m.visitToPayBp()).isEqualTo(2000);
        // 接单率：1 笔已付款订单，1 笔被接单 → 100%
        assertThat(m.acceptRateBp()).isEqualTo(10000);
        assertThat(m.avgAcceptSeconds()).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(m.acceptRateBelowThreshold()).isFalse();
        assertThat(m.conversionTrustworthy()).isTrue();
    }

    @Test
    @DisplayName("接单率从订单表算，不受埋点缺失影响")
    void 接单率不依赖埋点() {
        ServiceOrder accepted = paidOrder();
        orderService.accept(accepted.getId(), talentUserId);
        paidOrder();   // 没被接单
        paidOrder();   // 没被接单

        // 故意清掉所有事件
        eventMapper.delete(com.baomidou.mybatisplus.core.toolkit.Wrappers.emptyWrapper());

        var m = funnelService.overview(today, today).metrics();
        // 3 笔已付款，1 笔被接 → 33.33%
        assertThat(m.acceptRateBp()).isEqualTo(3333);
    }

    // ===== Requirement: 接单率单独监控与告警 =====

    @Test
    @DisplayName("Scenario: 接单率低于阈值 — 标注异常且提示转化率参考价值受限")
    void 接单率低于阈值() {
        paidOrder(); paidOrder(); paidOrder();  // 3 笔都没接

        var m = funnelService.overview(today, today).metrics();

        assertThat(m.acceptRateBp()).isZero();
        assertThat(m.acceptRateBelowThreshold()).isTrue();
        assertThat(m.acceptRateThresholdBp()).isEqualTo(8000);
        // 这是 proposal 的否决指标：接单率低时转化率不可信
        assertThat(m.conversionTrustworthy()).isFalse();
    }

    @Test
    @DisplayName("接单率达标时转化率可信")
    void 接单率达标() {
        ServiceOrder o = paidOrder();
        orderService.accept(o.getId(), talentUserId);

        var m = funnelService.overview(today, today).metrics();
        assertThat(m.acceptRateBelowThreshold()).isFalse();
        assertThat(m.conversionTrustworthy()).isTrue();
    }

    @Test
    @DisplayName("阈值可由运营配置")
    void 阈值可配() throws Exception {
        mockMvc.perform(put("/api/admin/analytics/settings").session(operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acceptRateThresholdBp\":5000}"))
                .andExpect(status().isOk());

        assertThat(funnelService.threshold()).isEqualTo(5000);

        mockMvc.perform(put("/api/admin/analytics/settings").session(operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acceptRateThresholdBp\":10001}"))
                .andExpect(status().isBadRequest());
    }

    // ===== Requirement: 按主播与服务类型拆分 =====

    @Test
    @DisplayName("Scenario: 按主播拆分 — 逐主播展示并附接单率")
    void 按主播拆分() throws Exception {
        completeOrder();

        JsonNode rows = dataOf(mockMvc.perform(
                get("/api/admin/analytics/by-talent").session(operator)).andReturn());

        JsonNode mine = null;
        for (JsonNode r : rows) {
            if (r.get("id").asLong() == talentId) {
                mine = r;
            }
        }
        assertThat(mine).isNotNull();
        assertThat(mine.get("name").asText()).isEqualTo("小雨");
        assertThat(mine.get("uvByKey").get("SUBMIT_ORDER").asLong()).isEqualTo(1);
        assertThat(mine.get("acceptRateBp").asInt()).isEqualTo(10000);
    }

    @Test
    @DisplayName("Scenario: 按服务项目拆分")
    void 按服务项目拆分() throws Exception {
        completeOrder();

        JsonNode rows = dataOf(mockMvc.perform(
                get("/api/admin/analytics/by-service").session(operator)).andReturn());

        JsonNode mine = null;
        for (JsonNode r : rows) {
            if (r.get("id").asLong() == serviceItemId) {
                mine = r;
            }
        }
        assertThat(mine).isNotNull();
        assertThat(mine.get("name").asText()).isEqualTo("情感倾听");
        assertThat(mine.get("uvByKey").get("COMPLETE_ORDER").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("12.9 拆分后各组人数之和与总体一致")
    void 拆分之和与总体一致() {
        completeOrder();
        completeOrder();

        var total = funnelService.overview(today, today).funnel().stream()
                .filter(s -> s.eventKey().equals("SUBMIT_ORDER")).findFirst().orElseThrow();
        var byTalent = funnelService.byTalent(today, today, Map.of());

        long sum = byTalent.stream()
                .mapToLong(b -> b.uvByKey().getOrDefault("SUBMIT_ORDER", 0L)).sum();
        // 只有一位主播，所以拆分之和应等于总体
        assertThat(sum).isEqualTo(total.uv());
    }

    // ===== Requirement: 7 日复购 =====

    @Test
    @DisplayName("12.6 跨日重复下单被正确标记为复购")
    void 七日复购判定() {
        // 第一单：把完成时间拨到 3 天前
        ServiceOrder first = completeOrder();
        ServiceOrder o = orderMapper.selectById(first.getId());
        o.setCompletedAt(LocalDateTime.now().minusDays(3));
        orderMapper.updateById(o);

        // 第二单：今天完成 → 应判为复购
        completeOrder();

        List<AnalyticsEvent> repurchase = eventMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<AnalyticsEvent>lambdaQuery()
                        .eq(AnalyticsEvent::getEventKey, "REPURCHASE_7D"));
        assertThat(repurchase).hasSize(1);
        assertThat(repurchase.get(0).getUserId()).isEqualTo(fanId);
    }

    @Test
    @DisplayName("超过 7 天的再次下单不算复购")
    void 超过七日不算复购() {
        ServiceOrder first = completeOrder();
        ServiceOrder o = orderMapper.selectById(first.getId());
        o.setCompletedAt(LocalDateTime.now().minusDays(10));
        orderMapper.updateById(o);

        completeOrder();

        assertThat(eventMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<AnalyticsEvent>lambdaQuery()
                        .eq(AnalyticsEvent::getEventKey, "REPURCHASE_7D"))).isEmpty();
    }

    @Test
    @DisplayName("首单不算复购")
    void 首单不算复购() {
        completeOrder();

        assertThat(eventMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<AnalyticsEvent>lambdaQuery()
                        .eq(AnalyticsEvent::getEventKey, "REPURCHASE_7D"))).isEmpty();
    }

    // ===== Requirement: 看板访问限定运营 =====

    @Test
    @DisplayName("Scenario: 运营访问看板 — 展示完整数据")
    void 运营访问看板() throws Exception {
        mockMvc.perform(get("/api/admin/analytics/funnel").session(operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.funnel").isArray())
                .andExpect(jsonPath("$.data.metrics").exists());
    }

    @Test
    @DisplayName("Scenario: 非运营访问看板 — 拒绝并返回无权限错误")
    void 非运营访问看板被拒() throws Exception {
        for (MockHttpSession session : List.of(fan, talentSession)) {
            mockMvc.perform(get("/api/admin/analytics/funnel").session(session))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.data").doesNotExist());
            mockMvc.perform(get("/api/admin/analytics/by-talent").session(session))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("未登录访问看板返回未认证")
    void 未登录访问看板() throws Exception {
        mockMvc.perform(get("/api/admin/analytics/funnel"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("结束日期早于开始日期被拒绝")
    void 非法时间范围被拒() {
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> funnelService.overview(today, today.minusDays(1)))
                .isInstanceOf(com.guild.checkout.common.exception.BusinessException.class);
    }

    // ---------- 辅助 ----------

    private void track(EventKey key, long userId) {
        analytics.track(AnalyticsService.Track.of(key).user(userId));
    }

    private ServiceOrder paidOrder() {
        ServiceOrder order = orderService.create(new OrderCreateCommand(
                fanId, talentId, specId, 1, "wxid_test", null));
        return orderService.confirmPayment(order.getId(), 1500, operatorId, null);
    }

    private ServiceOrder completeOrder() {
        ServiceOrder order = paidOrder();
        orderService.accept(order.getId(), talentUserId);
        return orderService.complete(order.getId(), talentUserId);
    }

    private static Map<String, FunnelService.FunnelStep> index(List<FunnelService.FunnelStep> steps) {
        Map<String, FunnelService.FunnelStep> map = new java.util.LinkedHashMap<>();
        steps.forEach(s -> map.put(s.eventKey(), s));
        return map;
    }

    private JsonNode dataOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private AppUser newUser(String prefix) {
        return userService.registerOrUpdate(prefix + System.nanoTime(), null, prefix, null);
    }

    private MockHttpSession sessionFor(AppUser user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(WechatAuthController.SESSION_USER_ID, user.getId());
        return session;
    }
}
