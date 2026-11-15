package com.guild.checkout.survey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guild.checkout.auth.WechatAuthController;
import com.guild.checkout.order.OrderCreateCommand;
import com.guild.checkout.order.OrderService;
import com.guild.checkout.order.ServiceOrder;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ordering 规格「订单完成后的满意度回访」的场景测试（tasks 11.2 - 11.5、8.7）。
 */
@SpringBootTest
@Transactional
class SurveyScenarioTest {

    @Autowired private WebApplicationContext context;
    @Autowired private SurveyService surveyService;
    @Autowired private OrderService orderService;
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
    private MockHttpSession operator;
    private long fanId;
    private long operatorId;
    private long talentId;
    private long talentUserId;
    private long specId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        commissionService.setDefault(3000);

        AppUser fanUser = newUser("qf_");
        fanId = fanUser.getId();
        fan = sessionFor(fanUser);
        otherFan = sessionFor(newUser("qo_"));
        AppUser op = newUser("qp_");
        operatorId = op.getId();
        userService.grantRole(operatorId, UserRole.OPERATOR);
        operator = sessionFor(op);

        ServiceItem item = catalogService.createItem("sv_" + System.nanoTime(), "情感倾听", null, 0);
        ServiceSpec spec = catalogService.createSpec(item.getId(), "m30", "30分钟", 30, 30);
        specId = spec.getId();

        Talent talent = talentAdminService.create(new TalentAdminService.ProfileForm("小雨", null, null, 0, null, null, null, null));
        talentAdminService.setListed(talent.getId(), true);
        talentId = talent.getId();
        priceService.setPrice(talentId, specId, 1500, true);

        AppUser talentUser = newUser("qt_");
        talentAdminService.linkUser(talentId, talentUser.getId());
        talentUserId = talentUser.getId();
        talentSession = sessionFor(talentUser);
    }

    // ===== Scenario: 粉丝提交评分 =====

    @Test
    @DisplayName("Scenario: 粉丝提交评分 — 记录并关联到订单与主播")
    void 粉丝提交评分() throws Exception {
        ServiceOrder order = completeOrder();

        mockMvc.perform(post("/api/orders/" + order.getId() + "/survey").session(fan)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"score\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(5));

        SatisfactionSurvey stored = surveyService.findByOrder(order.getId());
        assertThat(stored.getTalentId()).isEqualTo(talentId);
        assertThat(stored.getFanUserId()).isEqualTo(fanId);
        assertThat(stored.getOrderRef()).isEqualTo(order.getOrderRef());
    }

    @Test
    @DisplayName("Scenario: 其他粉丝浏览该主播详情页时不展示任何评分")
    void 评分不出现在主播详情页() throws Exception {
        ServiceOrder order = completeOrder();
        submit(order, 5);

        String detail = mockMvc.perform(get("/api/talents/" + talentId).session(otherFan))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(detail).get("data");

        assertThat(data.has("score")).isFalse();
        assertThat(data.has("rating")).isFalse();
        assertThat(data.has("average")).isFalse();
        assertThat(data.has("survey")).isFalse();

        // 列表页同样没有
        String list = mockMvc.perform(get("/api/talents").session(otherFan))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(list).get("data").get("items").get(0).has("score")).isFalse();
    }

    @Test
    @DisplayName("一笔订单至多一条评分，重复提交被拒")
    void 重复评分被拒() throws Exception {
        ServiceOrder order = completeOrder();
        submit(order, 5);

        mockMvc.perform(post("/api/orders/" + order.getId() + "/survey").session(fan)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"score\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("已经评过分")));

        assertThat(surveyService.findByOrder(order.getId()).getScore()).isEqualTo(5);
    }

    @Test
    @DisplayName("非下单本人不能评分")
    void 他人不能评分() throws Exception {
        ServiceOrder order = completeOrder();

        mockMvc.perform(post("/api/orders/" + order.getId() + "/survey").session(otherFan)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"score\":5}"))
                .andExpect(status().isForbidden());

        assertThat(surveyService.findByOrder(order.getId())).isNull();
    }

    @Test
    @DisplayName("未完成的订单不能评分")
    void 未完成不能评分() throws Exception {
        ServiceOrder order = paidOrder();

        mockMvc.perform(post("/api/orders/" + order.getId() + "/survey").session(fan)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"score\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("尚未完成")));
    }

    @Test
    @DisplayName("评分越界被拒绝")
    void 评分越界被拒() throws Exception {
        ServiceOrder order = completeOrder();

        for (String body : new String[]{"{\"score\":0}", "{\"score\":6}", "{\"score\":-1}"}) {
            mockMvc.perform(post("/api/orders/" + order.getId() + "/survey").session(fan)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(surveyService.findByOrder(order.getId())).isNull();
    }

    // ===== Scenario: 粉丝不提交评分 =====

    @Test
    @DisplayName("Scenario: 粉丝不提交评分 — 订单保持已完成，不受影响")
    void 不评分不影响订单() throws Exception {
        ServiceOrder order = completeOrder();

        assertThat(surveyService.findByOrder(order.getId())).isNull();
        mockMvc.perform(get("/api/orders/" + order.getId()).session(fan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("未评分的订单不参与均值计算")
    void 未评分不参与均值() {
        completeOrder();                 // 不评分
        submit(completeOrder(), 5);
        submit(completeOrder(), 3);

        SurveyService.Aggregate agg = surveyService.aggregateOf(talentId);

        assertThat(agg.count()).isEqualTo(2);
        assertThat(agg.average()).isEqualTo(4.0);
    }

    // ===== Scenario: 主播只能看到聚合评分 =====

    @Test
    @DisplayName("Scenario: 主播只能看到聚合评分 — 均值与分值分布")
    void 主播看到聚合评分() throws Exception {
        submit(completeOrder(), 5);
        submit(completeOrder(), 4);
        submit(completeOrder(), 5);

        JsonNode agg = dataOf(mockMvc.perform(
                get("/api/talent/workspace/survey").session(talentSession)).andReturn());

        assertThat(agg.get("count").asLong()).isEqualTo(3);
        // (5+4+5)/3 = 4.67
        assertThat(agg.get("average").asDouble()).isEqualTo(4.67);
        assertThat(agg.get("distribution").get("5").asLong()).isEqualTo(2);
        assertThat(agg.get("distribution").get("4").asLong()).isEqualTo(1);
        assertThat(agg.get("distribution").get("1").asLong()).isZero();
    }

    @Test
    @DisplayName("Scenario: 响应中不包含任何可定位到订单或粉丝的评分记录")
    void 主播响应不可追溯() throws Exception {
        ServiceOrder order = completeOrder();
        submit(order, 2);

        String body = mockMvc.perform(get("/api/talent/workspace/survey").session(talentSession))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 既不能出现订单号，也不能出现任何可定位的字段名
        assertThat(body).doesNotContain(order.getOrderRef());
        assertThat(body).doesNotContain(String.valueOf(fanId));
        JsonNode data = objectMapper.readTree(body).get("data");
        assertThat(data.has("orderId")).isFalse();
        assertThat(data.has("orderRef")).isFalse();
        assertThat(data.has("fanUserId")).isFalse();
        assertThat(data.has("details")).isFalse();
        assertThat(data.has("entries")).isFalse();
        // 只有这三个字段
        assertThat(data.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("count", "average", "distribution");
    }

    @Test
    @DisplayName("无评分时主播侧返回空状态而非零分")
    void 无评分时空状态() throws Exception {
        JsonNode agg = dataOf(mockMvc.perform(
                get("/api/talent/workspace/survey").session(talentSession)).andReturn());

        assertThat(agg.get("count").asLong()).isZero();
        // 0 分和"还没人评"是两回事，均值必须为空而不是 0
        assertThat(agg.get("average").isNull()).isTrue();
        assertThat(agg.get("distribution").get("3").asLong()).isZero();
    }

    @Test
    @DisplayName("主播只看得到自己的评分")
    void 主播只看自己的评分() throws Exception {
        submit(completeOrder(), 5);

        Talent other = talentAdminService.create(new TalentAdminService.ProfileForm("阿澈", null, null, 0, null, null, null, null));
        AppUser otherTalentUser = newUser("qx_");
        talentAdminService.linkUser(other.getId(), otherTalentUser.getId());

        JsonNode agg = dataOf(mockMvc.perform(
                get("/api/talent/workspace/survey").session(sessionFor(otherTalentUser))).andReturn());
        assertThat(agg.get("count").asLong()).isZero();
    }

    @Test
    @DisplayName("粉丝访问工作台满意度接口无权限")
    void 粉丝不能看工作台评分() throws Exception {
        mockMvc.perform(get("/api/talent/workspace/survey").session(fan))
                .andExpect(status().isForbidden());
    }

    // ===== Scenario: 运营查看评分 =====

    @Test
    @DisplayName("Scenario: 运营查看评分 — 逐单明细与均值")
    void 运营查看逐单明细() throws Exception {
        ServiceOrder first = completeOrder();
        submit(first, 5);
        submit(completeOrder(), 2);

        JsonNode data = dataOf(mockMvc.perform(
                get("/api/admin/talents/" + talentId + "/survey").session(operator)).andReturn());

        assertThat(data.get("aggregate").get("count").asLong()).isEqualTo(2);
        assertThat(data.get("aggregate").get("average").asDouble()).isEqualTo(3.5);

        JsonNode details = data.get("details");
        assertThat(details).hasSize(2);
        // 运营这边可以追溯到订单与粉丝
        assertThat(details.toString()).contains(first.getOrderRef());
        assertThat(details.get(0).has("fanUserId")).isTrue();
    }

    @Test
    @DisplayName("非运营不能看逐单明细")
    void 非运营不能看明细() throws Exception {
        submit(completeOrder(), 5);

        mockMvc.perform(get("/api/admin/talents/" + talentId + "/survey").session(fan))
                .andExpect(status().isForbidden());
        // 主播也不行——他只能走工作台那条聚合路径
        mockMvc.perform(get("/api/admin/talents/" + talentId + "/survey").session(talentSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("运营总览按主播给出各自聚合")
    void 运营总览() throws Exception {
        submit(completeOrder(), 4);

        JsonNode overview = dataOf(mockMvc.perform(
                get("/api/admin/survey/overview").session(operator)).andReturn());

        assertThat(overview.get(String.valueOf(talentId)).get("count").asLong()).isEqualTo(1);
        assertThat(overview.get(String.valueOf(talentId)).get("average").asDouble()).isEqualTo(4.0);
    }

    // ===== 粉丝自查 =====

    @Test
    @DisplayName("粉丝能查到自己给这笔订单打的分")
    void 粉丝可查自己的评分() throws Exception {
        ServiceOrder order = completeOrder();
        submit(order, 4);

        mockMvc.perform(get("/api/orders/" + order.getId() + "/survey").session(fan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(4));

        // 别人查同一笔订单拿不到内容
        mockMvc.perform(get("/api/orders/" + order.getId() + "/survey").session(otherFan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("均值按四舍五入保留两位小数")
    void 均值两位小数() {
        submit(completeOrder(), 5);
        submit(completeOrder(), 5);
        submit(completeOrder(), 4);

        // (5+5+4)/3 = 4.6666... → 4.67
        assertThat(surveyService.aggregateOf(talentId).average()).isEqualTo(4.67);
    }

    @Test
    @DisplayName("服务层直接调用也挡住越界评分")
    void 服务层挡住越界评分() {
        ServiceOrder order = completeOrder();
        assertThatThrownBy(() -> surveyService.submit(order.getId(), fanId, 6))
                .isInstanceOf(com.guild.checkout.common.exception.BusinessException.class);
        assertThatThrownBy(() -> surveyService.submit(order.getId(), fanId, 0))
                .isInstanceOf(com.guild.checkout.common.exception.BusinessException.class);
    }

    // ---------- 辅助 ----------

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

    private void submit(ServiceOrder order, int score) {
        surveyService.submit(order.getId(), fanId, score);
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
