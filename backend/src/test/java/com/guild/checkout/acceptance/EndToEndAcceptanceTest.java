package com.guild.checkout.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guild.checkout.analytics.AnalyticsEvent;
import com.guild.checkout.analytics.AnalyticsEventMapper;
import com.guild.checkout.analytics.EventKey;
import com.guild.checkout.analytics.FunnelService;
import com.guild.checkout.auth.WechatAuthController;
import com.guild.checkout.order.OrderStatus;
import com.guild.checkout.order.ServiceOrder;
import com.guild.checkout.order.ServiceOrderMapper;
import com.guild.checkout.order.timeout.OrderTimeoutService;
import com.guild.checkout.payment.PaymentConfirmed;
import com.guild.checkout.payment.PaymentInstruction;
import com.guild.checkout.payment.PaymentProvider;
import com.guild.checkout.payment.PaymentService;
import com.guild.checkout.pricing.ServiceItem;
import com.guild.checkout.pricing.ServiceSpec;
import com.guild.checkout.pricing.admin.CommissionAdminService;
import com.guild.checkout.pricing.admin.ServiceCatalogAdminService;
import com.guild.checkout.pricing.admin.TalentPriceAdminService;
import com.guild.checkout.settlement.SettlementReportService;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.admin.TalentAdminService;
import com.guild.checkout.tipping.TipRecord;
import com.guild.checkout.tipping.TipService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 集成验收（tasks 第 14 组）。
 *
 * 每个测试走通一条完整链路，全部经过真实 HTTP 接口，不走捷径调 service。
 * 这些测试是「系统作为一个整体是否成立」的最后一道检查。
 */
@SpringBootTest
@Transactional
class EndToEndAcceptanceTest {

    @Autowired private WebApplicationContext context;
    @Autowired private AppUserService userService;
    @Autowired private TalentAdminService talentAdminService;
    @Autowired private ServiceCatalogAdminService catalogService;
    @Autowired private TalentPriceAdminService priceService;
    @Autowired private CommissionAdminService commissionService;
    @Autowired private ServiceOrderMapper orderMapper;
    @Autowired private AnalyticsEventMapper eventMapper;
    @Autowired private OrderTimeoutService timeoutService;
    @Autowired private PaymentService paymentService;
    @Autowired private TipService tipService;
    @Autowired private SettlementReportService reportService;
    @Autowired private FunnelService funnelService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private MockHttpSession fan;
    private MockHttpSession talentSession;
    private MockHttpSession operator;
    private long fanId;
    private long operatorId;
    private long talentId;
    private long talentUserId;
    private long specId;
    private long serviceItemId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        commissionService.setDefault(3000);
        // 构造数据集要可核算，先清出确定的基线（随事务回滚）
        eventMapper.delete(com.baomidou.mybatisplus.core.toolkit.Wrappers.emptyWrapper());
        orderMapper.delete(com.baomidou.mybatisplus.core.toolkit.Wrappers.emptyWrapper());

        AppUser fanUser = newUser("e2e_fan_");
        fanId = fanUser.getId();
        fan = sessionFor(fanUser);
        AppUser op = newUser("e2e_op_");
        operatorId = op.getId();
        userService.grantRole(operatorId, UserRole.OPERATOR);
        operator = sessionFor(op);

        ServiceItem item = catalogService.createItem("e2e_" + System.nanoTime(), "情感倾听", null, 0);
        serviceItemId = item.getId();
        ServiceSpec spec = catalogService.createSpec(item.getId(), "m30", "30分钟", 30, 30);
        specId = spec.getId();

        Talent talent = talentAdminService.create("小雨", null, null, 0);
        talentAdminService.setListed(talent.getId(), true);
        talentId = talent.getId();
        priceService.setPrice(talentId, specId, 10000, true);

        AppUser talentUser = newUser("e2e_tl_");
        talentAdminService.linkUser(talentId, talentUser.getId());
        talentUserId = talentUser.getId();
        talentSession = sessionFor(talentUser);
    }

    // ===== 14.1 主链路 =====

    @Test
    @DisplayName("14.1 浏览主播 → 下单 → 上传凭证 → 运营确认 → 主播接单 → 标记完成 → 粉丝评分 → 生成分账")
    void 完整主链路() throws Exception {
        // 浏览
        mockMvc.perform(get("/api/talents").session(fan)).andExpect(status().isOk());
        JsonNode detail = dataOf(mockMvc.perform(
                get("/api/talents/" + talentId).session(fan)).andReturn());
        assertThat(detail.get("available").asBoolean()).isTrue();
        long unitPrice = detail.get("services").get(0).get("specs").get(0).get("unitPriceCents").asLong();
        assertThat(unitPrice).isEqualTo(10000);

        // 下单
        JsonNode order = dataOf(mockMvc.perform(post("/api/orders").session(fan)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"talentId\":" + talentId + ",\"serviceSpecId\":" + specId
                                + ",\"quantity\":1,\"contact\":\"wxid_e2e\",\"remark\":\"晚上九点后\"}"))
                .andExpect(status().isOk()).andReturn());
        long orderId = order.get("id").asLong();
        assertThat(order.get("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(order.get("payableCents").asLong()).isEqualTo(10000);

        // 支付页 → 上传凭证
        JsonNode page = dataOf(mockMvc.perform(
                get("/api/orders/" + orderId + "/payment").session(fan)).andReturn());
        assertThat(page.get("payableCents").asLong()).isEqualTo(10000);

        mockMvc.perform(multipart("/api/orders/" + orderId + "/payment/proofs")
                        .file(new MockMultipartFile("file", "p.png", "image/png", "img".getBytes()))
                        .session(fan))
                .andExpect(status().isOk());
        assertThat(orderMapper.selectById(orderId).statusEnum()).isEqualTo(OrderStatus.PENDING_CONFIRM);

        // 运营对账 → 确认收款
        JsonNode queue = dataOf(mockMvc.perform(
                get("/api/admin/payment/reconcile").session(operator)).andReturn());
        assertThat(idsOf(queue, "orderId")).contains(orderId);

        mockMvc.perform(put("/api/admin/payment/" + orderId + "/confirm").session(operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paidCents\":10000,\"externalRef\":\"bank-e2e-001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_ACCEPT"));

        // 主播接单
        JsonNode accepted = dataOf(mockMvc.perform(
                put("/api/talent/workspace/orders/" + orderId + "/accept").session(talentSession))
                .andExpect(status().isOk()).andReturn());
        assertThat(accepted.get("contact").asText()).isEqualTo("wxid_e2e");
        assertThat(accepted.get("remark").asText()).isEqualTo("晚上九点后");

        // 标记完成
        mockMvc.perform(put("/api/talent/workspace/orders/" + orderId + "/complete").session(talentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        // 粉丝评分
        mockMvc.perform(post("/api/orders/" + orderId + "/survey").session(fan)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"score\":5}"))
                .andExpect(status().isOk());

        // 分账已生成且金额正确
        String period = YearMonth.now().toString();
        var row = reportService.monthly(period).stream()
                .filter(r -> r.talentId().equals(talentId)).findFirst().orElseThrow();
        assertThat(row.orderAmountCents()).isEqualTo(10000);
        assertThat(row.guildCents()).isEqualTo(3000);
        assertThat(row.talentCents()).isEqualTo(7000);
        assertThat(row.guildCents() + row.talentCents()).isEqualTo(row.orderAmountCents());

        // 各环节状态与时间戳齐全
        ServiceOrder stored = orderMapper.selectById(orderId);
        assertThat(stored.getPaidAt()).isNotNull();
        assertThat(stored.getAcceptedAt()).isNotNull();
        assertThat(stored.getCompletedAt()).isNotNull();
        assertThat(stored.getPaidCents()).isEqualTo(10000);
    }

    // ===== 14.2 超时链路 =====

    @Test
    @DisplayName("14.2 下单未付自动取消；付款后无人接单自动超时并入退款队列")
    void 两条超时链路() throws Exception {
        // 链路一：下单后不付款
        long unpaid = createOrder();
        expire(unpaid, true);
        assertThat(timeoutService.expirePayment(unpaid)).isEqualTo(OrderTimeoutService.Result.HANDLED);
        ServiceOrder cancelled = orderMapper.selectById(unpaid);
        assertThat(cancelled.statusEnum()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.getCloseReason()).isEqualTo(OrderTimeoutService.PAY_TIMEOUT_REASON);

        // 链路二：付款后无人接单
        long paid = createOrder();
        confirm(paid);
        expire(paid, false);
        assertThat(timeoutService.expireAccept(paid)).isEqualTo(OrderTimeoutService.Result.HANDLED);
        ServiceOrder timedOut = orderMapper.selectById(paid);
        assertThat(timedOut.statusEnum()).isEqualTo(OrderStatus.TIMEOUT);

        // 进入待退款队列
        JsonNode queue = dataOf(mockMvc.perform(
                get("/api/admin/orders/refund-queue").session(operator)).andReturn());
        assertThat(idsOf(queue, "id")).contains(paid).doesNotContain(unpaid);

        // 两条链路都留了痕
        JsonNode history = dataOf(mockMvc.perform(
                get("/api/orders/" + paid + "/history").session(operator)).andReturn());
        assertThat(history.toString()).contains("SYSTEM").contains("已超时");
    }

    @Test
    @DisplayName("14.2 已上传凭证的订单到期后不被关闭")
    void 已传凭证不被超时关闭() throws Exception {
        long orderId = createOrder();
        mockMvc.perform(multipart("/api/orders/" + orderId + "/payment/proofs")
                        .file(new MockMultipartFile("file", "p.png", "image/png", "img".getBytes()))
                        .session(fan))
                .andExpect(status().isOk());
        expire(orderId, true);

        assertThat(timeoutService.expirePayment(orderId)).isEqualTo(OrderTimeoutService.Result.SKIPPED);
        assertThat(orderMapper.selectById(orderId).statusEnum()).isEqualTo(OrderStatus.PENDING_CONFIRM);
    }

    // ===== 14.3 打赏链路 =====

    @Test
    @DisplayName("14.3 打赏链路走通，并在结算报表中与服务订单分列且合计正确")
    void 打赏链路与报表() throws Exception {
        // 一笔服务订单走到完成
        long orderId = createOrder();
        confirm(orderId);
        mockMvc.perform(put("/api/talent/workspace/orders/" + orderId + "/accept").session(talentSession));
        mockMvc.perform(put("/api/talent/workspace/orders/" + orderId + "/complete").session(talentSession));

        // 一笔打赏
        JsonNode tip = dataOf(mockMvc.perform(post("/api/tips").session(fan)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"talentId\":" + talentId + ",\"amountCents\":8800,\"message\":\"辛苦了\"}"))
                .andExpect(status().isOk()).andReturn());
        long tipId = tip.get("id").asLong();
        mockMvc.perform(put("/api/admin/tips/" + tipId + "/confirm").session(operator)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"paidCents\":8800}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        // 报表里两者分列且合计正确
        var row = reportService.monthly(YearMonth.now().toString()).stream()
                .filter(r -> r.talentId().equals(talentId)).findFirst().orElseThrow();
        assertThat(row.orderAmountCents()).isEqualTo(10000);
        assertThat(row.tipAmountCents()).isEqualTo(8800);
        assertThat(row.orderCount()).isEqualTo(1);
        assertThat(row.tipCount()).isEqualTo(1);
        // 公会 3000 + 2640 = 5640；主播 7000 + 6160 = 13160
        assertThat(row.guildCents()).isEqualTo(5640);
        assertThat(row.talentCents()).isEqualTo(13160);
        assertThat(row.guildCents() + row.talentCents())
                .isEqualTo(row.orderAmountCents() + row.tipAmountCents());

        // 导出里也分列
        String csv = new String(reportService.exportCsv(YearMonth.now().toString()),
                java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("100.00").contains("88.00");
    }

    // ===== 14.4 看板与手工核算 =====

    @Test
    @DisplayName("14.4 构造数据集下，看板全部节点与四项核心指标与手工核算完全一致")
    void 看板与手工核算一致() throws Exception {
        // 构造：5 人打开 H5，4 人看列表，3 人看详情，2 人点下单
        List<Long> visitors = List.of(
                newUser("v1_").getId(), newUser("v2_").getId(), newUser("v3_").getId(),
                newUser("v4_").getId(), fanId);
        for (int i = 0; i < visitors.size(); i++) {
            trackAs(visitors.get(i), EventKey.OPEN_H5);
            if (i < 4) trackAs(visitors.get(i), EventKey.VIEW_TALENT_LIST);
            if (i < 3) trackAs(visitors.get(i), EventKey.VIEW_TALENT_DETAIL);
            if (i < 2) trackAs(visitors.get(i), EventKey.CLICK_ORDER);
        }

        // 其中 fanId 真的下单并走到完成
        long orderId = createOrder();
        confirm(orderId);
        mockMvc.perform(put("/api/talent/workspace/orders/" + orderId + "/accept").session(talentSession));
        mockMvc.perform(put("/api/talent/workspace/orders/" + orderId + "/complete").session(talentSession));
        // 再来一笔不接单的，让接单率变成 1/2
        long unaccepted = createOrder();
        confirm(unaccepted);

        LocalDate today = LocalDate.now();
        var overview = funnelService.overview(today, today);
        Map<String, FunnelService.FunnelStep> byKey = index(overview.funnel());

        // 漏斗：手工核算
        assertThat(byKey.get("OPEN_H5").uv()).isEqualTo(5);
        assertThat(byKey.get("VIEW_TALENT_LIST").uv()).isEqualTo(4);
        assertThat(byKey.get("VIEW_TALENT_DETAIL").uv()).isEqualTo(3);
        assertThat(byKey.get("CLICK_ORDER").uv()).isEqualTo(2);
        assertThat(byKey.get("SUBMIT_ORDER").uv()).isEqualTo(1);   // 只有 fanId 真下单（2 笔同一人去重）
        assertThat(byKey.get("CONFIRM_PAYMENT").uv()).isEqualTo(1);
        assertThat(byKey.get("COMPLETE_ORDER").uv()).isEqualTo(1);

        // 相邻转化率：4/5=80%、3/4=75%、2/3=66.67%、1/2=50%
        assertThat(byKey.get("VIEW_TALENT_LIST").conversionBp()).isEqualTo(8000);
        assertThat(byKey.get("VIEW_TALENT_DETAIL").conversionBp()).isEqualTo(7500);
        assertThat(byKey.get("CLICK_ORDER").conversionBp()).isEqualTo(6667);
        assertThat(byKey.get("SUBMIT_ORDER").conversionBp()).isEqualTo(5000);
        assertThat(byKey.get("VIEW_TALENT_LIST").lostUv()).isEqualTo(1);

        // 四项核心指标：手工核算
        var m = overview.metrics();
        // 访问到付费：1 人付费 / 5 人打开 = 20%
        assertThat(m.visitToPayBp()).isEqualTo(2000);
        // 接单率：2 笔已付款，1 笔被接 = 50%
        assertThat(m.acceptRateBp()).isEqualTo(5000);
        // 50% < 80% 阈值 → 告警，且转化率标为不可信
        assertThat(m.acceptRateBelowThreshold()).isTrue();
        assertThat(m.conversionTrustworthy()).isFalse();
        assertThat(m.avgAcceptSeconds()).isNotNull();
        // 7 日复购：无复购
        assertThat(m.repurchaseBp()).isZero();

        // 拆分后各组之和与总体一致
        long sum = funnelService.byTalent(today, today, Map.of()).stream()
                .mapToLong(b -> b.uvByKey().getOrDefault("SUBMIT_ORDER", 0L)).sum();
        assertThat(sum).isEqualTo(byKey.get("SUBMIT_ORDER").uv());
    }

    // ===== 14.5 支付方式切换演练 =====

    /** 一个完全虚构的支付方式，代表将来要接的微信支付。 */
    private static final class StubPayProvider implements PaymentProvider {
        @Override public String code() { return "drill-pay"; }
        @Override public String displayName() { return "演练支付"; }
        @Override public PaymentInstruction instruct(String orderRef, long payableCents) {
            return new PaymentInstruction(code(), PaymentInstruction.Kind.INVOKE_SDK,
                    Map.of("orderRef", orderRef, "amount", payableCents));
        }
    }

    @Test
    @DisplayName("14.5 换一个 provider，订单流程行为与人工收款完全一致")
    void 支付方式切换演练() throws Exception {
        // 基准：走人工收款的订单
        long manual = createOrder();
        confirm(manual);
        ServiceOrder manualOrder = orderMapper.selectById(manual);

        // 演练：换成一个与人工收款毫无关系的 provider
        long drill = createOrder();
        ServiceOrder drillOrder = orderMapper.selectById(drill);
        StubPayProvider provider = new StubPayProvider();
        PaymentInstruction instruction = provider.instruct(
                drillOrder.getOrderRef(), drillOrder.getPayableCents());
        assertThat(instruction.kind()).isEqualTo(PaymentInstruction.Kind.INVOKE_SDK);

        var result = paymentService.confirm(new PaymentConfirmed(
                drillOrder.getOrderRef(), 10000, provider.code(),
                "drill-txn-0001", null, LocalDateTime.now(), null));
        assertThat(result.outcome()).isEqualTo(PaymentService.Outcome.ACCEPTED);

        // 两笔订单的后续行为完全一致
        ServiceOrder drillPaid = orderMapper.selectById(drill);
        assertThat(drillPaid.statusEnum()).isEqualTo(manualOrder.statusEnum());
        assertThat(drillPaid.getAcceptDeadline()).isNotNull();
        assertThat(drillPaid.getPaidCents()).isEqualTo(manualOrder.getPaidCents());
        assertThat(drillPaid.getGuildCents()).isEqualTo(manualOrder.getGuildCents());

        // 继续走完，与人工收款的单没有任何差别
        mockMvc.perform(put("/api/talent/workspace/orders/" + drill + "/accept").session(talentSession))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/talent/workspace/orders/" + drill + "/complete").session(talentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        // 分账也照常生成
        var row = reportService.monthly(YearMonth.now().toString()).stream()
                .filter(r -> r.talentId().equals(talentId)).findFirst().orElseThrow();
        assertThat(row.orderCount()).isEqualTo(1);
        assertThat(row.talentCents()).isEqualTo(7000);

        // 重复回调被识别为重复
        assertThat(paymentService.confirm(new PaymentConfirmed(
                drillPaid.getOrderRef(), 10000, provider.code(),
                "drill-txn-0001", null, LocalDateTime.now(), null)).outcome())
                .isEqualTo(PaymentService.Outcome.DUPLICATE);
    }

    // ---------- 辅助 ----------

    private long createOrder() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders").session(fan)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"talentId\":" + talentId + ",\"serviceSpecId\":" + specId
                                + ",\"quantity\":1,\"contact\":\"wxid_e2e\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return dataOf(result).get("id").asLong();
    }

    private void confirm(long orderId) throws Exception {
        mockMvc.perform(put("/api/admin/payment/" + orderId + "/confirm").session(operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paidCents\":10000,\"externalRef\":\"e2e-" + orderId + "\"}"))
                .andExpect(status().isOk());
    }

    private void expire(long orderId, boolean payDeadline) {
        ServiceOrder o = orderMapper.selectById(orderId);
        if (payDeadline) {
            o.setPayDeadline(LocalDateTime.now().minusMinutes(1));
        } else {
            o.setAcceptDeadline(LocalDateTime.now().minusMinutes(1));
        }
        orderMapper.updateById(o);
    }

    private void trackAs(long userId, EventKey key) {
        AnalyticsEvent e = new AnalyticsEvent();
        e.setEventKey(key.name());
        e.setUserId(userId);
        e.setTalentId(talentId);
        e.setServiceItemId(serviceItemId);
        e.setOccurredAt(LocalDateTime.now());
        eventMapper.insert(e);
    }

    private static Map<String, FunnelService.FunnelStep> index(List<FunnelService.FunnelStep> steps) {
        Map<String, FunnelService.FunnelStep> map = new java.util.LinkedHashMap<>();
        steps.forEach(s -> map.put(s.eventKey(), s));
        return map;
    }

    private static List<Long> idsOf(JsonNode array, String field) {
        List<Long> ids = new java.util.ArrayList<>();
        array.forEach(n -> ids.add(n.get(field).asLong()));
        return ids;
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
