package com.guild.checkout.talent;

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
import com.guild.checkout.talent.admin.TalentAdminService;
import com.guild.checkout.user.AppUser;
import com.guild.checkout.user.AppUserService;
import com.guild.checkout.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * talent-workspace spec 的场景测试（tasks 8.1 - 8.6、8.8）。
 */
@SpringBootTest
@Transactional
class TalentWorkspaceScenarioTest {

    @Autowired private WebApplicationContext context;
    @Autowired private OrderService orderService;
    @Autowired private ServiceOrderMapper orderMapper;
    @Autowired private AppUserService userService;
    @Autowired private TalentAdminService talentAdminService;
    @Autowired private ServiceCatalogAdminService catalogService;
    @Autowired private TalentPriceAdminService priceService;
    @Autowired private CommissionAdminService commissionService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private MockHttpSession fan;
    private MockHttpSession talentSession;
    private MockHttpSession otherTalentSession;
    private long fanId;
    private long operatorId;
    private long talentId;
    private long talentUserId;
    private long specId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        commissionService.setDefault(3000);

        AppUser fanUser = newUser("wf_");
        fanId = fanUser.getId();
        fan = sessionFor(fanUser);
        AppUser op = newUser("wo_");
        operatorId = op.getId();
        userService.grantRole(operatorId, UserRole.OPERATOR);

        ServiceItem item = catalogService.createItem("ws_" + System.nanoTime(), "情感倾听", null, 0);
        ServiceSpec spec = catalogService.createSpec(item.getId(), "m30", "30分钟", 30, 30);
        specId = spec.getId();

        Talent talent = talentAdminService.create("小雨", null, null, 0);
        talentAdminService.setListed(talent.getId(), true);
        talentId = talent.getId();
        priceService.setPrice(talentId, specId, 1500, true);

        AppUser talentUser = newUser("wt_");
        talentAdminService.linkUser(talentId, talentUser.getId());
        talentUserId = talentUser.getId();
        talentSession = sessionFor(talentUser);

        Talent other = talentAdminService.create("阿澈", null, null, 0);
        talentAdminService.setListed(other.getId(), true);
        AppUser otherUser = newUser("wx_");
        talentAdminService.linkUser(other.getId(), otherUser.getId());
        otherTalentSession = sessionFor(otherUser);
    }

    // ===== Requirement: 主播身份态入口 =====

    @Test
    @DisplayName("Scenario: 主播进入工作台 — 默认停在待接单列表")
    void 主播进入工作台() throws Exception {
        mockMvc.perform(get("/api/talent/workspace/me").session(talentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.talentId").value(talentId))
                .andExpect(jsonPath("$.data.nickname").value("小雨"));

        mockMvc.perform(get("/api/talent/workspace/pending-orders").session(talentSession))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Scenario: 粉丝看不到工作台入口 — 会话里 talent 为 false 且接口无权限")
    void 粉丝看不到工作台() throws Exception {
        mockMvc.perform(get("/api/session/me").session(fan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.talent").value(false));

        mockMvc.perform(get("/api/talent/workspace/me").session(fan))
                .andExpect(status().isForbidden());
    }

    // ===== Requirement: 待接单提醒与列表 =====

    @Test
    @DisplayName("Scenario: 展示剩余接单时间 — 带 acceptDeadline 供前端倒计时")
    void 展示剩余接单时间() throws Exception {
        paidOrder();

        JsonNode list = dataOf(mockMvc.perform(
                get("/api/talent/workspace/pending-orders").session(talentSession)).andReturn());

        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("acceptDeadline").isNull()).isFalse();
        assertThat(list.get(0).get("actions").toString()).contains("ACCEPT");
    }

    @Test
    @DisplayName("Scenario: 剩余时间为零的订单不再展示接单按钮")
    void 超时订单不给接单按钮() throws Exception {
        ServiceOrder order = paidOrder();
        ServiceOrder o = orderMapper.selectById(order.getId());
        o.setAcceptDeadline(LocalDateTime.now().minusMinutes(1));
        orderMapper.updateById(o);

        JsonNode list = dataOf(mockMvc.perform(
                get("/api/talent/workspace/pending-orders").session(talentSession)).andReturn());

        assertThat(list.get(0).get("actions").toString()).doesNotContain("ACCEPT");

        // 即便前端硬点，服务端也拒绝
        mockMvc.perform(put("/api/talent/workspace/orders/" + order.getId() + "/accept")
                        .session(talentSession))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Scenario: 无待接单订单 — 空列表而非错误")
    void 无待接单订单() throws Exception {
        mockMvc.perform(get("/api/talent/workspace/pending-orders").session(talentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ===== Requirement: 接单与标记完成操作 =====

    @Test
    @DisplayName("Scenario: 工作台内接单 — 转入进行中并展示联系方式与备注")
    void 工作台内接单() throws Exception {
        ServiceOrder order = paidOrderWith("wxid_fan", "晚上九点后");

        JsonNode accepted = dataOf(mockMvc.perform(
                put("/api/talent/workspace/orders/" + order.getId() + "/accept").session(talentSession))
                .andExpect(status().isOk()).andReturn());

        assertThat(accepted.get("contact").asText()).isEqualTo("wxid_fan");
        assertThat(accepted.get("remark").asText()).isEqualTo("晚上九点后");

        // 从待接单移到进行中
        assertThat(dataOf(mockMvc.perform(
                get("/api/talent/workspace/pending-orders").session(talentSession)).andReturn()))
                .isEmpty();
        assertThat(dataOf(mockMvc.perform(
                get("/api/talent/workspace/active-orders").session(talentSession)).andReturn()))
                .hasSize(1);
    }

    @Test
    @DisplayName("Scenario: 工作台内标记完成 — 移至历史列表")
    void 工作台内标记完成() throws Exception {
        ServiceOrder order = paidOrder();
        mockMvc.perform(put("/api/talent/workspace/orders/" + order.getId() + "/accept")
                .session(talentSession)).andExpect(status().isOk());

        mockMvc.perform(put("/api/talent/workspace/orders/" + order.getId() + "/complete")
                        .session(talentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        assertThat(dataOf(mockMvc.perform(
                get("/api/talent/workspace/active-orders").session(talentSession)).andReturn()))
                .isEmpty();
        assertThat(dataOf(mockMvc.perform(
                get("/api/talent/workspace/history-orders").session(talentSession)).andReturn()))
                .hasSize(1);
    }

    @Test
    @DisplayName("Scenario: 只能操作自己的订单 — 他人接单或完成一律无权限")
    void 只能操作自己的订单() throws Exception {
        ServiceOrder order = paidOrder();

        mockMvc.perform(put("/api/talent/workspace/orders/" + order.getId() + "/accept")
                        .session(otherTalentSession))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/talent/workspace/orders/" + order.getId() + "/accept")
                .session(talentSession)).andExpect(status().isOk());

        mockMvc.perform(put("/api/talent/workspace/orders/" + order.getId() + "/complete")
                        .session(otherTalentSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("他人的订单也不出现在自己的列表里")
    void 列表只含自己的订单() throws Exception {
        paidOrder();

        assertThat(dataOf(mockMvc.perform(
                get("/api/talent/workspace/pending-orders").session(otherTalentSession)).andReturn()))
                .isEmpty();
    }

    // ===== Requirement: 在线状态开关 =====

    @Test
    @DisplayName("Scenario: 切换为离线 — 粉丝端随即展示离线，且仍可下单")
    void 切换为离线() throws Exception {
        mockMvc.perform(put("/api/talent/workspace/online/true").session(talentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.online").value(true));

        mockMvc.perform(put("/api/talent/workspace/online/false").session(talentSession))
                .andExpect(status().isOk());

        JsonNode detail = dataOf(mockMvc.perform(
                get("/api/talents/" + talentId).session(fan)).andReturn());
        assertThat(detail.get("online").asBoolean()).isFalse();
        // 离线不阻止下单
        assertThat(detail.get("available").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Scenario: 状态保持 — 退出后重新进入仍是上次设置的状态")
    void 在线状态保持() throws Exception {
        mockMvc.perform(put("/api/talent/workspace/online/true").session(talentSession))
                .andExpect(status().isOk());

        // 全新会话，相当于退出后重新进入
        MockHttpSession fresh = new MockHttpSession();
        fresh.setAttribute(WechatAuthController.SESSION_USER_ID, talentUserId);

        mockMvc.perform(get("/api/talent/workspace/me").session(fresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.online").value(true));
    }

    // ===== Requirement: 只读订单与收入记录 =====

    @Test
    @DisplayName("Scenario: 查看收入 — 展示已完成订单的应得金额合计与明细，无提现入口")
    void 查看收入() throws Exception {
        completeOrder();
        completeOrder();

        JsonNode income = dataOf(mockMvc.perform(
                get("/api/talent/workspace/income").session(talentSession)).andReturn());

        // 1500 分 × 70%（公会抽 30%）= 1050，两单共 2100
        assertThat(income.get("totalCents").asLong()).isEqualTo(2100);
        assertThat(income.get("completedOrderCount").asInt()).isEqualTo(2);
        assertThat(income.get("entries")).hasSize(2);
        assertThat(income.get("entries").get(0).get("talentCents").asLong()).isEqualTo(1050);
        // 响应里不含任何提现或状态变更字段
        assertThat(income.toString())
                .doesNotContain("withdraw").doesNotContain("提现").doesNotContain("settle");
    }

    @Test
    @DisplayName("收入只计已完成订单，超时与退款不计入")
    void 收入只计已完成() throws Exception {
        completeOrder();

        ServiceOrder timedOut = paidOrder();
        ServiceOrder o = orderMapper.selectById(timedOut.getId());
        o.setStatusEnum(OrderStatus.TIMEOUT);
        orderMapper.updateById(o);

        JsonNode income = dataOf(mockMvc.perform(
                get("/api/talent/workspace/income").session(talentSession)).andReturn());

        assertThat(income.get("completedOrderCount").asInt()).isEqualTo(1);
        assertThat(income.get("totalCents").asLong()).isEqualTo(1050);
    }

    @Test
    @DisplayName("收入金额取订单上的快照，改分成比例不影响已完成订单")
    void 收入用快照金额() throws Exception {
        completeOrder();

        commissionService.setOverride(talentId, 6000);

        JsonNode income = dataOf(mockMvc.perform(
                get("/api/talent/workspace/income").session(talentSession)).andReturn());
        assertThat(income.get("totalCents").asLong()).isEqualTo(1050);
        assertThat(income.get("entries").get(0).get("commissionRateBp").asInt()).isEqualTo(3000);
    }

    @Test
    @DisplayName("没有收入时返回零值与空明细，而非错误")
    void 无收入时空状态() throws Exception {
        mockMvc.perform(get("/api/talent/workspace/income").session(talentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCents").value(0))
                .andExpect(jsonPath("$.data.entries.length()").value(0));
    }

    @Test
    @DisplayName("Scenario: 查看历史订单 — 含已完成、已超时与已退款及各自金额")
    void 查看历史订单() throws Exception {
        completeOrder();
        ServiceOrder timedOut = paidOrder();
        ServiceOrder o = orderMapper.selectById(timedOut.getId());
        o.setStatusEnum(OrderStatus.TIMEOUT);
        orderMapper.updateById(o);

        JsonNode history = dataOf(mockMvc.perform(
                get("/api/talent/workspace/history-orders").session(talentSession)).andReturn());

        assertThat(history).hasSize(2);
        assertThat(history.toString()).contains("已完成").contains("已超时");
        history.forEach(n -> assertThat(n.get("payableCents").asLong()).isEqualTo(1500));
    }

    @Test
    @DisplayName("有主播角色但未关联档案时，工作台一律无权限")
    void 有角色无档案不可用() throws Exception {
        AppUser orphan = newUser("orphan_");
        userService.grantRole(orphan.getId(), UserRole.TALENT);

        mockMvc.perform(get("/api/talent/workspace/income").session(sessionFor(orphan)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("未关联")));
    }

    // ---------- 辅助 ----------

    private ServiceOrder paidOrder() {
        return paidOrderWith("wxid_test", null);
    }

    private ServiceOrder paidOrderWith(String contact, String remark) {
        ServiceOrder order = orderService.create(new OrderCreateCommand(
                fanId, talentId, specId, 1, contact, remark));
        return orderService.confirmPayment(order.getId(), 1500, operatorId, null);
    }

    private void completeOrder() {
        ServiceOrder order = paidOrder();
        orderService.accept(order.getId(), talentUserId);
        orderService.complete(order.getId(), talentUserId);
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
