package com.guild.checkout.tipping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guild.checkout.auth.WechatAuthController;
import com.guild.checkout.common.exception.BusinessException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * tipping spec 的场景测试（tasks 9.2 - 9.8）。
 */
@SpringBootTest
@Transactional
class TippingScenarioTest {

    @Autowired private WebApplicationContext context;
    @Autowired private TipService tipService;
    @Autowired private TipRecordMapper tipMapper;
    @Autowired private AppUserService userService;
    @Autowired private TalentAdminService talentAdminService;
    @Autowired private ServiceCatalogAdminService catalogService;
    @Autowired private TalentPriceAdminService priceService;
    @Autowired private CommissionAdminService commissionService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private MockHttpSession fan;
    private MockHttpSession otherFan;
    private MockHttpSession talentSession;
    private MockHttpSession otherTalentSession;
    private MockHttpSession operator;
    private long fanId;
    private long talentId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        commissionService.setDefault(3000);
        tipService.updateSetting(100L, 200000L);

        AppUser fanUser = newUser("tipf_");
        fanId = fanUser.getId();
        fan = sessionFor(fanUser);
        otherFan = sessionFor(newUser("tipo_"));
        AppUser op = newUser("tipp_");
        userService.grantRole(op.getId(), UserRole.OPERATOR);
        operator = sessionFor(op);

        ServiceItem item = catalogService.createItem("tip_" + System.nanoTime(), "情感倾听", null, 0);
        ServiceSpec spec = catalogService.createSpec(item.getId(), "m30", "30分钟", 30, 30);

        Talent talent = talentAdminService.create(new TalentAdminService.ProfileForm("小雨", null, null, 0, null, null, null, null));
        talentAdminService.setListed(talent.getId(), true);
        talentId = talent.getId();
        priceService.setPrice(talentId, spec.getId(), 1500, true);

        AppUser talentUser = newUser("tipt_");
        talentAdminService.linkUser(talentId, talentUser.getId());
        talentSession = sessionFor(talentUser);

        Talent other = talentAdminService.create(new TalentAdminService.ProfileForm("阿澈", null, null, 0, null, null, null, null));
        talentAdminService.setListed(other.getId(), true);
        AppUser otherTalentUser = newUser("tipx_");
        talentAdminService.linkUser(other.getId(), otherTalentUser.getId());
        otherTalentSession = sessionFor(otherTalentUser);
    }

    // ===== Requirement: 对指定主播发起打赏 =====

    @Test
    @DisplayName("Scenario: 成功发起打赏 — 创建记录并进入支付环节，支付确认后标记已完成")
    void 成功发起打赏() throws Exception {
        JsonNode tip = create(5000, "谢谢你的陪伴");

        assertThat(tip.get("tipRef").asText()).hasSize(18);
        assertThat(tip.get("amountCents").asLong()).isEqualTo(5000);
        assertThat(tip.get("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(tip.get("payDeadline").isNull()).isFalse();

        mockMvc.perform(put("/api/admin/tips/" + tip.get("id").asLong() + "/confirm")
                        .session(operator).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paidCents\":5000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("Scenario: 金额超出允许范围 — 拒绝并提示范围")
    void 金额越界被拒() throws Exception {
        tipService.updateSetting(100L, 10000L);

        for (long amount : new long[]{50, 20000}) {
            mockMvc.perform(post("/api/tips").session(fan)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"talentId\":" + talentId + ",\"amountCents\":" + amount + "}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("之间")));
        }

        // 边界值允许
        create(100, null);
        create(10000, null);
    }

    @Test
    @DisplayName("Scenario: 对已下架主播打赏 — 拒绝创建")
    void 对已下架主播打赏被拒() throws Exception {
        talentAdminService.setListed(talentId, false);

        mockMvc.perform(post("/api/tips").session(fan)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"talentId\":" + talentId + ",\"amountCents\":5000}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("暂不可打赏")));
    }

    @Test
    @DisplayName("金额为零或负数被参数校验拦下")
    void 非法金额被拒() throws Exception {
        mockMvc.perform(post("/api/tips").session(fan)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"talentId\":" + talentId + ",\"amountCents\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAM"));
    }

    // ===== Requirement: 打赏携带留言 =====

    @Test
    @DisplayName("Scenario: 携带留言打赏 — 目标主播在工作台看得到")
    void 主播能看到留言() throws Exception {
        long tipId = create(5000, "谢谢你的陪伴").get("id").asLong();
        confirm(tipId, 5000);

        JsonNode tips = dataOf(mockMvc.perform(
                get("/api/talent/workspace/tips").session(talentSession)).andReturn());

        assertThat(tips).hasSize(1);
        assertThat(tips.get(0).get("message").asText()).isEqualTo("谢谢你的陪伴");
        assertThat(tips.get(0).get("amountCents").asLong()).isEqualTo(5000);
    }

    @Test
    @DisplayName("Scenario: 留言的可见范围 — 其他粉丝浏览主播详情页不展示任何留言")
    void 留言不出现在主播详情页() throws Exception {
        long tipId = create(5000, "这是一条私密留言").get("id").asLong();
        confirm(tipId, 5000);

        // 其他粉丝看主播详情
        String detail = mockMvc.perform(get("/api/talents/" + talentId).session(otherFan))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 不能简单地断言不含 "message"：那是统一响应结构自带的字段
        assertThat(detail).doesNotContain("这是一条私密留言");
        JsonNode data = objectMapper.readTree(detail).get("data");
        assertThat(data.has("tips")).isFalse();
        assertThat(data.has("messages")).isFalse();
        assertThat(data.toString()).doesNotContain("私密");

        // 主播列表同样不含留言
        String list = mockMvc.perform(get("/api/talents").session(otherFan))
                .andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContain("这是一条私密留言");
    }

    @Test
    @DisplayName("其他主播看不到不属于自己的打赏与留言")
    void 他人主播看不到留言() throws Exception {
        long tipId = create(5000, "私密留言").get("id").asLong();
        confirm(tipId, 5000);

        JsonNode tips = dataOf(mockMvc.perform(
                get("/api/talent/workspace/tips").session(otherTalentSession)).andReturn());
        assertThat(tips).isEmpty();

        mockMvc.perform(get("/api/tips/" + tipId).session(otherTalentSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("无关粉丝读不到他人的打赏记录")
    void 无关粉丝读不到打赏() throws Exception {
        long tipId = create(5000, "私密留言").get("id").asLong();

        mockMvc.perform(get("/api/tips/" + tipId).session(otherFan))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("留言超长被拒绝")
    void 留言超长被拒() throws Exception {
        String longMessage = "x".repeat(201);
        mockMvc.perform(post("/api/tips").session(fan)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TipController.CreateRequest(talentId, 5000, longMessage))))
                .andExpect(status().isBadRequest());
    }

    // ===== Requirement: 打赏与服务订单分开计量 =====

    @Test
    @DisplayName("Scenario: 打赏不需要接单 — 直接计为已完成，不进入待接单也不推送提醒")
    void 打赏不需要接单() throws Exception {
        long tipId = create(5000, null).get("id").asLong();
        confirm(tipId, 5000);

        TipRecord tip = tipMapper.selectById(tipId);
        assertThat(tip.statusEnum()).isEqualTo(TipStatus.COMPLETED);
        assertThat(tip.getPaidAt()).isNotNull();

        // 打赏不会出现在主播的待接单列表里
        assertThat(dataOf(mockMvc.perform(
                get("/api/talent/workspace/pending-orders").session(talentSession)).andReturn()))
                .isEmpty();
    }

    @Test
    @DisplayName("Scenario: 分别统计 — 收入里服务订单与打赏分列并给出合计")
    void 收入分别统计() throws Exception {
        long tipId = create(5000, null).get("id").asLong();
        confirm(tipId, 5000);

        JsonNode income = dataOf(mockMvc.perform(
                get("/api/talent/workspace/income").session(talentSession)).andReturn());

        // 5000 分 × 70% = 3500
        assertThat(income.get("tipCents").asLong()).isEqualTo(3500);
        assertThat(income.get("orderCents").asLong()).isZero();
        assertThat(income.get("totalCents").asLong()).isEqualTo(3500);
        assertThat(income.get("totalCents").asLong())
                .isEqualTo(income.get("orderCents").asLong() + income.get("tipCents").asLong());
    }

    @Test
    @DisplayName("未完成的打赏不计入收入")
    void 未完成打赏不计入收入() throws Exception {
        create(5000, null);

        JsonNode income = dataOf(mockMvc.perform(
                get("/api/talent/workspace/income").session(talentSession)).andReturn());
        assertThat(income.get("tipCents").asLong()).isZero();
    }

    // ===== Requirement: 打赏参与分成 =====

    @Test
    @DisplayName("Scenario: 打赏按快照比例分账 — 创建后改比例不影响这一笔")
    void 打赏按快照比例分账() {
        commissionService.setDefault(3000);
        TipRecord tip = tipService.create(fanId, talentId, 5000, null);

        assertThat(tip.getCommissionRateBp()).isEqualTo(3000);
        assertThat(tip.getGuildCents()).isEqualTo(1500);
        assertThat(tip.getTalentCents()).isEqualTo(3500);

        commissionService.setOverride(talentId, 6000);

        TipRecord stored = tipMapper.selectById(tip.getId());
        assertThat(stored.getCommissionRateBp()).isEqualTo(3000);
        assertThat(stored.getGuildCents()).isEqualTo(1500);
        assertThat(stored.getTalentCents()).isEqualTo(3500);

        // 之后的新打赏按新比例
        TipRecord fresh = tipService.create(fanId, talentId, 5000, null);
        assertThat(fresh.getCommissionRateBp()).isEqualTo(6000);
    }

    @Test
    @DisplayName("打赏分账与订单同一套取整规则：两项之和恒等于打赏金额")
    void 打赏分账不变式() {
        for (int rateBp : new int[]{0, 1234, 3333, 6667, 10000}) {
            commissionService.setOverride(talentId, rateBp);
            for (long amount : new long[]{100, 999, 5000, 12345, 199999}) {
                TipRecord tip = tipService.create(fanId, talentId, amount, null);
                assertThat(tip.getGuildCents() + tip.getTalentCents())
                        .as("比例 %d 金额 %d", rateBp, amount)
                        .isEqualTo(amount);
            }
        }
    }

    // ===== 配置 =====

    @Test
    @DisplayName("金额上下限可由运营配置，粉丝端据此提示")
    void 金额上下限可配() throws Exception {
        mockMvc.perform(put("/api/admin/tips/limits").session(operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minCents\":500,\"maxCents\":50000}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tips/limits").session(fan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.minCents").value(500))
                .andExpect(jsonPath("$.data.maxCents").value(50000));
    }

    @Test
    @DisplayName("上限低于下限的配置被拒绝")
    void 非法上下限被拒() {
        assertThatThrownBy(() -> tipService.updateSetting(10000L, 100L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("非运营不能改打赏金额配置")
    void 非运营不能改配置() throws Exception {
        mockMvc.perform(put("/api/admin/tips/limits").session(fan)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minCents\":1}"))
                .andExpect(status().isForbidden());
    }

    // ---------- 辅助 ----------

    private JsonNode create(long amountCents, String message) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tips").session(fan)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TipController.CreateRequest(talentId, amountCents, message))))
                .andExpect(status().isOk())
                .andReturn();
        return dataOf(result);
    }

    private void confirm(long tipId, long paidCents) throws Exception {
        mockMvc.perform(put("/api/admin/tips/" + tipId + "/confirm").session(operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paidCents\":" + paidCents + "}"))
                .andExpect(status().isOk());
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
