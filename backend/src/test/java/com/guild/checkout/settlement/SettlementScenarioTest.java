package com.guild.checkout.settlement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guild.checkout.auth.WechatAuthController;
import com.guild.checkout.order.OrderCreateCommand;
import com.guild.checkout.order.OrderService;
import com.guild.checkout.order.OrderStatus;
import com.guild.checkout.order.ServiceOrder;
import com.guild.checkout.order.ServiceOrderMapper;
import com.guild.checkout.pricing.ServiceItem;
import com.guild.checkout.pricing.ServiceSpec;
import com.guild.checkout.pricing.admin.CommissionAdminService;
import com.guild.checkout.pricing.admin.ServiceCatalogAdminService;
import com.guild.checkout.pricing.admin.TalentPriceAdminService;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * settlement spec 的场景测试（tasks 10.2 - 10.8）。
 */
@SpringBootTest
@Transactional
class SettlementScenarioTest {

    @Autowired private WebApplicationContext context;
    @Autowired private SettlementService settlementService;
    @Autowired private SettlementReportService reportService;
    @Autowired private SettlementEntryMapper entryMapper;
    @Autowired private OrderService orderService;
    @Autowired private ServiceOrderMapper orderMapper;
    @Autowired private TipService tipService;
    @Autowired private AppUserService userService;
    @Autowired private TalentAdminService talentAdminService;
    @Autowired private ServiceCatalogAdminService catalogService;
    @Autowired private TalentPriceAdminService priceService;
    @Autowired private CommissionAdminService commissionService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private MockHttpSession operator;
    private long fanId;
    private long operatorId;
    private long talentId;
    private long talentUserId;
    private long specId;
    private String period;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        commissionService.setDefault(3000);
        period = YearMonth.now().toString();

        fanId = newUser("sf_").getId();
        AppUser op = newUser("so_");
        operatorId = op.getId();
        userService.grantRole(operatorId, UserRole.OPERATOR);
        operator = sessionFor(op);

        ServiceItem item = catalogService.createItem("st_" + System.nanoTime(), "情感倾听", null, 0);
        ServiceSpec spec = catalogService.createSpec(item.getId(), "m30", "30分钟", 30, 30);
        specId = spec.getId();

        Talent talent = talentAdminService.create(new TalentAdminService.ProfileForm("小雨", null, null, 0, null, null, null, null));
        talentAdminService.setListed(talent.getId(), true);
        talentId = talent.getId();
        priceService.setPrice(talentId, specId, 10000, true);

        AppUser talentUser = newUser("st_u_");
        talentAdminService.linkUser(talentId, talentUser.getId());
        talentUserId = talentUser.getId();
    }

    // ===== Requirement: 基于快照计算分账 =====

    @Test
    @DisplayName("Scenario: 订单计入分账 — 含金额、比例、公会分成与主播应得")
    void 订单计入分账() {
        ServiceOrder order = completeOrder();

        SettlementEntry entry = settlementService.findBySource(
                SettlementEntry.SOURCE_ORDER, order.getId());

        assertThat(entry).isNotNull();
        assertThat(entry.getSourceRef()).isEqualTo(order.getOrderRef());
        assertThat(entry.getAmountCents()).isEqualTo(10000);
        assertThat(entry.getCommissionRateBp()).isEqualTo(3000);
        assertThat(entry.getGuildCents()).isEqualTo(3000);
        assertThat(entry.getTalentCents()).isEqualTo(7000);
        assertThat(entry.isActive()).isTrue();
    }

    @Test
    @DisplayName("打赏完成时也记一笔分账")
    void 打赏计入分账() {
        TipRecord tip = tipService.create(fanId, talentId, 8800, null);
        tipService.confirmPayment(tip.getId(), 8800, null);

        SettlementEntry entry = settlementService.findBySource(
                SettlementEntry.SOURCE_TIP, tip.getId());

        assertThat(entry).isNotNull();
        // 文档示例：8800 × 30% = 2640，主播得 6160
        assertThat(entry.getGuildCents()).isEqualTo(2640);
        assertThat(entry.getTalentCents()).isEqualTo(6160);
    }

    @Test
    @DisplayName("Scenario: 配置变更不影响既有分账")
    void 配置变更不影响既有分账() {
        ServiceOrder order = completeOrder();
        SettlementEntry before = settlementService.findBySource(
                SettlementEntry.SOURCE_ORDER, order.getId());

        commissionService.setOverride(talentId, 6000);
        priceService.setPrice(talentId, specId, 99900, true);

        SettlementEntry after = entryMapper.selectById(before.getId());
        assertThat(after.getAmountCents()).isEqualTo(10000);
        assertThat(after.getCommissionRateBp()).isEqualTo(3000);
        assertThat(after.getGuildCents()).isEqualTo(3000);
        assertThat(after.getTalentCents()).isEqualTo(7000);
    }

    @Test
    @DisplayName("同一来源重复记账是幂等的，只产生一条")
    void 重复记账幂等() {
        ServiceOrder order = completeOrder();

        settlementService.recordOrder(orderMapper.selectById(order.getId()));
        settlementService.recordOrder(orderMapper.selectById(order.getId()));

        assertThat(settlementService.allEntries(period, talentId)).hasSize(1);
    }

    // ===== Requirement: 退款不计入分账 =====

    @Test
    @DisplayName("Scenario: 退款订单不计入分账 — 已生成则冲销并留痕")
    void 退款订单冲销() {
        ServiceOrder order = completeOrder();
        assertThat(settlementService.findBySource(
                SettlementEntry.SOURCE_ORDER, order.getId()).isActive()).isTrue();

        orderService.markRefunded(order.getId(), operatorId, "粉丝投诉，全额退款");

        SettlementEntry reversed = settlementService.findBySource(
                SettlementEntry.SOURCE_ORDER, order.getId());
        assertThat(reversed.isActive()).isFalse();
        assertThat(reversed.getStatus()).isEqualTo(SettlementEntry.REVERSED);
        assertThat(reversed.getReversedAt()).isNotNull();
        assertThat(reversed.getReverseReason()).isEqualTo("粉丝投诉，全额退款");

        // 记录还在，只是不计入合计——删掉就查不到这笔钱曾经算过账
        assertThat(settlementService.allEntries(period, talentId)).hasSize(1);
        assertThat(settlementService.activeEntries(period, talentId)).isEmpty();
    }

    @Test
    @DisplayName("从未记账的订单退款时不报错")
    void 未记账订单退款不报错() {
        ServiceOrder order = paidOrder();
        ServiceOrder o = orderMapper.selectById(order.getId());
        o.setStatusEnum(OrderStatus.TIMEOUT);
        orderMapper.updateById(o);

        orderService.markRefunded(order.getId(), operatorId, "超时退款");

        assertThat(settlementService.findBySource(
                SettlementEntry.SOURCE_ORDER, order.getId())).isNull();
    }

    @Test
    @DisplayName("冲销后的记录不再计入报表合计")
    void 冲销后不计入报表() {
        completeOrder();
        ServiceOrder refunded = completeOrder();
        orderService.markRefunded(refunded.getId(), operatorId, "退款");

        var row = reportService.monthly(period).stream()
                .filter(r -> r.talentId().equals(talentId)).findFirst().orElseThrow();

        assertThat(row.orderCount()).isEqualTo(1);
        assertThat(row.orderAmountCents()).isEqualTo(10000);
        assertThat(row.talentCents()).isEqualTo(7000);
    }

    // ===== Requirement: 按主播按周期的结算报表 =====

    @Test
    @DisplayName("Scenario: 生成月度结算报表 — 订单与打赏分列并给出分成")
    void 生成月度报表() {
        completeOrder();
        completeOrder();
        TipRecord tip = tipService.create(fanId, talentId, 8800, null);
        tipService.confirmPayment(tip.getId(), 8800, null);

        var row = reportService.monthly(period).stream()
                .filter(r -> r.talentId().equals(talentId)).findFirst().orElseThrow();

        assertThat(row.orderAmountCents()).isEqualTo(20000);
        assertThat(row.tipAmountCents()).isEqualTo(8800);
        assertThat(row.orderCount()).isEqualTo(2);
        assertThat(row.tipCount()).isEqualTo(1);
        // 公会 3000×2 + 2640 = 8640；主播 7000×2 + 6160 = 20160
        assertThat(row.guildCents()).isEqualTo(8640);
        assertThat(row.talentCents()).isEqualTo(20160);
        // 不变式：公会 + 主播 = 订单金额 + 打赏金额
        assertThat(row.guildCents() + row.talentCents())
                .isEqualTo(row.orderAmountCents() + row.tipAmountCents());
    }

    @Test
    @DisplayName("Scenario: 周期内无数据的主播以零值成行而非缺行")
    void 无数据主播零值成行() {
        Talent idle = talentAdminService.create(new TalentAdminService.ProfileForm("没有单的主播", null, null, 0, null, null, null, null));

        var rows = reportService.monthly(period);
        var row = rows.stream().filter(r -> r.talentId().equals(idle.getId()))
                .findFirst().orElseThrow(() -> new AssertionError("无流水的主播应当成行"));

        assertThat(row.orderAmountCents()).isZero();
        assertThat(row.tipAmountCents()).isZero();
        assertThat(row.guildCents()).isZero();
        assertThat(row.talentCents()).isZero();
        assertThat(row.orderCount()).isZero();
    }

    @Test
    @DisplayName("其他周期的流水不计入本期")
    void 跨周期不串账() {
        ServiceOrder order = completeOrder();
        // 把这笔账挪到上个月
        SettlementEntry entry = settlementService.findBySource(
                SettlementEntry.SOURCE_ORDER, order.getId());
        entry.setOccurredAt(LocalDateTime.now().minusMonths(1));
        entryMapper.updateById(entry);

        var row = reportService.monthly(period).stream()
                .filter(r -> r.talentId().equals(talentId)).findFirst().orElseThrow();
        assertThat(row.orderCount()).isZero();

        String lastMonth = YearMonth.now().minusMonths(1).toString();
        var lastRow = reportService.monthly(lastMonth).stream()
                .filter(r -> r.talentId().equals(talentId)).findFirst().orElseThrow();
        assertThat(lastRow.orderCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("非法周期格式被拒绝")
    void 非法周期被拒() {
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> reportService.monthly("2026/11"))
                .isInstanceOf(com.guild.checkout.common.exception.BusinessException.class);
    }

    // ===== Requirement: 导出 =====

    @Test
    @DisplayName("Scenario: 导出报表 — CSV 带 BOM，金额为元")
    void 导出报表() throws Exception {
        completeOrder();

        MvcResult result = mockMvc.perform(
                        get("/api/admin/settlement/report/export?period=" + period).session(operator))
                .andExpect(status().isOk())
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        String csv = new String(body, StandardCharsets.UTF_8);

        assertThat(csv).startsWith("﻿");
        assertThat(csv).contains("主播ID,主播昵称,周期,服务订单金额");
        assertThat(csv).contains("小雨");
        // 10000 分导出为 100.00 元
        assertThat(csv).contains("100.00").contains("70.00").contains("30.00");
        assertThat(result.getResponse().getHeader("Content-Disposition"))
                .contains("settlement-" + period + ".csv");
    }

    @Test
    @DisplayName("明细导出包含已冲销的记录供追溯")
    void 明细导出含冲销记录() {
        ServiceOrder order = completeOrder();
        orderService.markRefunded(order.getId(), operatorId, "退款原因");

        String csv = new String(reportService.exportDetailCsv(period, talentId), StandardCharsets.UTF_8);

        assertThat(csv).contains("已冲销").contains("退款原因").contains(order.getOrderRef());
    }

    @Test
    @DisplayName("昵称含逗号时 CSV 被正确转义")
    void CSV转义() {
        assertThat(SettlementReportService.csv("小雨,温柔")).isEqualTo("\"小雨,温柔\"");
        assertThat(SettlementReportService.csv("带\"引号")).isEqualTo("\"带\"\"引号\"");
        assertThat(SettlementReportService.csv("普通")).isEqualTo("普通");
        assertThat(SettlementReportService.csv(null)).isEmpty();
    }

    // ===== Requirement: 不提供站内提现 =====

    @Test
    @DisplayName("Scenario: 运营标记已结 — 只记录标记与时间，不发生资金动作")
    void 标记已结清() throws Exception {
        completeOrder();

        mockMvc.perform(put("/api/admin/settlement/marks/" + talentId + "/" + period)
                        .session(operator).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"已微信转账\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settledAt").isNotEmpty())
                .andExpect(jsonPath("$.data.operatorId").value(operatorId));

        // 报表上体现为已结清
        var row = reportService.monthly(period).stream()
                .filter(r -> r.talentId().equals(talentId)).findFirst().orElseThrow();
        assertThat(row.settled()).isTrue();

        // 台账金额完全没动
        assertThat(settlementService.activeEntries(period, talentId).get(0).getTalentCents())
                .isEqualTo(7000);
    }

    @Test
    @DisplayName("重复标记结清是幂等的")
    void 重复标记幂等() {
        settlementService.markSettled(talentId, period, operatorId, null);
        var second = settlementService.markSettled(talentId, period, operatorId, null);

        assertThat(second).isNotNull();
        assertThat(settlementService.marksOf(period).stream()
                .filter(m -> m.getTalentId().equals(talentId)).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("结清标记可撤销，标错了要能改回来")
    void 可撤销结清标记() throws Exception {
        settlementService.markSettled(talentId, period, operatorId, null);

        mockMvc.perform(delete("/api/admin/settlement/marks/" + talentId + "/" + period)
                .session(operator)).andExpect(status().isOk());

        assertThat(settlementService.findMark(talentId, period)).isNull();
    }

    @Test
    @DisplayName("主播收入只读，响应中没有任何提现或结算状态变更入口")
    void 主播收入只读() throws Exception {
        completeOrder();
        MockHttpSession talentSession = new MockHttpSession();
        talentSession.setAttribute(WechatAuthController.SESSION_USER_ID, talentUserId);

        JsonNode income = dataOf(mockMvc.perform(
                get("/api/talent/workspace/income").session(talentSession)).andReturn());

        assertThat(income.get("totalCents").asLong()).isEqualTo(7000);
        assertThat(income.toString())
                .doesNotContain("withdraw").doesNotContain("提现").doesNotContain("settled");
    }

    @Test
    @DisplayName("非运营访问结算接口一律无权限")
    void 非运营不能看结算() throws Exception {
        MockHttpSession fanSession = sessionFor(userService.requireById(fanId));

        mockMvc.perform(get("/api/admin/settlement/report?period=" + period).session(fanSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/settlement/marks/" + talentId + "/" + period)
                        .session(fanSession).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    // ===== 文档里的手算示例 =====

    @ParameterizedTest
    @DisplayName("docs/settlement.md 的手算示例与系统输出一致")
    @CsvSource({
            "10000, 3000, 3000, 7000",
            "9999,  3000, 2999, 7000",
            "1,     5000,    0,    1",
            "3333,  3333, 1110, 2223",
            "8800,  3000, 2640, 6160",
    })
    void 文档示例可复算(long amount, int rateBp, long expectedGuild, long expectedTalent) {
        // 文档里有「1 分」这样的极端示例，放开打赏金额限制以便验取整公式本身
        tipService.updateSetting(1L, 100_000_000L);
        commissionService.setOverride(talentId, rateBp);
        TipRecord tip = tipService.create(fanId, talentId, amount, null);
        tipService.confirmPayment(tip.getId(), amount, null);

        SettlementEntry entry = settlementService.findBySource(
                SettlementEntry.SOURCE_TIP, tip.getId());

        assertThat(entry.getGuildCents()).as("公会分成").isEqualTo(expectedGuild);
        assertThat(entry.getTalentCents()).as("主播应得").isEqualTo(expectedTalent);
        assertThat(entry.getGuildCents() + entry.getTalentCents()).isEqualTo(amount);
    }

    @Test
    @DisplayName("分转元的展示与文档一致")
    void 分转元() {
        assertThat(SettlementReportService.yuan(10000)).isEqualTo("100.00");
        assertThat(SettlementReportService.yuan(9999)).isEqualTo("99.99");
        assertThat(SettlementReportService.yuan(1)).isEqualTo("0.01");
        assertThat(SettlementReportService.yuan(0)).isEqualTo("0.00");
        assertThat(SettlementReportService.yuan(-150)).isEqualTo("-1.50");
    }

    // ---------- 辅助 ----------

    private ServiceOrder paidOrder() {
        ServiceOrder order = orderService.create(new OrderCreateCommand(
                fanId, talentId, specId, 1, "wxid_test", null));
        return orderService.confirmPayment(order.getId(), 10000, operatorId, null);
    }

    private ServiceOrder completeOrder() {
        ServiceOrder order = paidOrder();
        orderService.accept(order.getId(), talentUserId);
        return orderService.complete(order.getId(), talentUserId);
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
