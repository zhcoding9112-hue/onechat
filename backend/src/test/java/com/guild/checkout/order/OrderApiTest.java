package com.guild.checkout.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guild.checkout.auth.WechatAuthController;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 订单接口（tasks 5.5 / 5.7 / 5.8 / 5.9），以及 2.5 的越权访问防护。
 */
@SpringBootTest
@Transactional
class OrderApiTest {

    @Autowired private WebApplicationContext context;
    @Autowired private AppUserService userService;
    @Autowired private TalentAdminService talentAdminService;
    @Autowired private ServiceCatalogAdminService catalogService;
    @Autowired private TalentPriceAdminService priceService;
    @Autowired private CommissionAdminService commissionService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ServiceOrderMapper orderMapper;

    private MockMvc mockMvc;
    private MockHttpSession fan;
    private MockHttpSession otherFan;
    private MockHttpSession talentSession;
    private MockHttpSession otherTalentSession;
    private MockHttpSession operator;
    private long talentId;
    private long specId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        commissionService.setDefault(3000);

        fan = sessionFor(newUser("fan_", null));
        otherFan = sessionFor(newUser("other_", null));
        operator = sessionFor(newUser("op_", UserRole.OPERATOR));

        ServiceItem item = catalogService.createItem("ord_" + System.nanoTime(), "情感倾听", null, 0);
        ServiceSpec spec = catalogService.createSpec(item.getId(), "m30", "30分钟", 30, 30);
        specId = spec.getId();

        Talent talent = talentAdminService.create(new TalentAdminService.ProfileForm("小雨", null, null, 0, null, null, null, null));
        talentAdminService.setListed(talent.getId(), true);
        talentId = talent.getId();
        priceService.setPrice(talentId, specId, 1500, true);

        AppUser talentUser = newUser("talent_", null);
        talentAdminService.linkUser(talentId, talentUser.getId());
        talentSession = sessionFor(talentUser);

        Talent other = talentAdminService.create(new TalentAdminService.ProfileForm("阿澈", null, null, 0, null, null, null, null));
        talentAdminService.setListed(other.getId(), true);
        AppUser otherTalentUser = newUser("otalent_", null);
        talentAdminService.linkUser(other.getId(), otherTalentUser.getId());
        otherTalentSession = sessionFor(otherTalentUser);
    }

    // ---------- 5.5 下单 ----------

    @Test
    @DisplayName("5.5 成功下单，价格与分成被快照写入")
    void 成功下单() throws Exception {
        JsonNode order = createOrder(2, "wxid_xiaoyu", "晚上九点后");

        assertThat(order.get("orderRef").asText()).hasSize(18);
        assertThat(order.get("unitPriceCents").asLong()).isEqualTo(1500);
        assertThat(order.get("quantity").asInt()).isEqualTo(2);
        assertThat(order.get("payableCents").asLong()).isEqualTo(3000);
        assertThat(order.get("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(order.get("statusLabel").asText()).isEqualTo("待支付");
        assertThat(order.get("payDeadline").isNull()).isFalse();
        assertThat(order.get("remark").asText()).isEqualTo("晚上九点后");

        ServiceOrder stored = orderMapper.selectById(order.get("id").asLong());
        assertThat(stored.getCommissionRateBp()).isEqualTo(3000);
        assertThat(stored.getGuildCents()).isEqualTo(900);
        assertThat(stored.getTalentCents()).isEqualTo(2100);
        assertThat(stored.getGuildCents() + stored.getTalentCents()).isEqualTo(stored.getPayableCents());
    }

    @Test
    @DisplayName("5.5 联系方式缺失时拒绝创建")
    void 联系方式缺失被拒() throws Exception {
        long before = countOrders();
        mockMvc.perform(post("/api/orders").session(fan)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"talentId\":" + talentId + ",\"serviceSpecId\":" + specId
                                + ",\"quantity\":1,\"contact\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAM"))
                .andExpect(jsonPath("$.errors[?(@.field=='contact')]").exists());

        // 比差值而非绝对值：库里可能有其他来源的订单
        assertThat(countOrders()).isEqualTo(before);
    }

    @Test
    @DisplayName("5.5 对已下架主播下单被拒绝")
    void 对已下架主播下单被拒() throws Exception {
        talentAdminService.setListed(talentId, false);

        mockMvc.perform(post("/api/orders").session(fan)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1, "wxid_x", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("暂不可约")));
    }

    @Test
    @DisplayName("5.5 未配价的档位下单被拒绝")
    void 未配价档位下单被拒() throws Exception {
        priceService.setEnabled(talentId, specId, false);

        mockMvc.perform(post("/api/orders").session(fan)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1, "wxid_x", null)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("5.5 下单后立即有一条创建日志")
    void 下单后有创建日志() throws Exception {
        long orderId = createOrder(1, "wxid_x", null).get("id").asLong();

        JsonNode history = dataOf(mockMvc.perform(
                get("/api/orders/" + orderId + "/history").session(fan)).andReturn());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("fromStatus").isNull()).isTrue();
        assertThat(history.get(0).get("toStatus").asText()).isEqualTo("PENDING_PAYMENT");
    }

    // ---------- 5.7 接单 ----------

    @Test
    @DisplayName("5.7 主播接单成功并看到联系方式与备注")
    void 主播接单成功() throws Exception {
        long orderId = paidOrder("wxid_xiaoyu", "晚上九点后");

        JsonNode pending = dataOf(mockMvc.perform(
                get("/api/talent/workspace/pending-orders").session(talentSession)).andReturn());
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).get("actions").toString()).contains("ACCEPT");

        JsonNode accepted = dataOf(mockMvc.perform(
                put("/api/talent/workspace/orders/" + orderId + "/accept").session(talentSession))
                .andExpect(status().isOk()).andReturn());

        assertThat(accepted.get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(accepted.get("contact").asText()).isEqualTo("wxid_xiaoyu");
        assertThat(accepted.get("remark").asText()).isEqualTo("晚上九点后");
        assertThat(orderMapper.selectById(orderId).getAcceptedAt()).isNotNull();
    }

    @Test
    @DisplayName("5.7 他人尝试接单返回无权限")
    void 他人接单被拒() throws Exception {
        long orderId = paidOrder("wxid_x", null);

        mockMvc.perform(put("/api/talent/workspace/orders/" + orderId + "/accept")
                        .session(otherTalentSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(orderMapper.selectById(orderId).statusEnum()).isEqualTo(OrderStatus.PENDING_ACCEPT);
    }

    @Test
    @DisplayName("5.7 粉丝不能调用接单接口")
    void 粉丝不能接单() throws Exception {
        long orderId = paidOrder("wxid_x", null);

        mockMvc.perform(put("/api/talent/workspace/orders/" + orderId + "/accept").session(fan))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("5.7 对未收款订单接单被拒绝")
    void 对未收款订单接单被拒() throws Exception {
        long orderId = createOrder(1, "wxid_x", null).get("id").asLong();

        mockMvc.perform(put("/api/talent/workspace/orders/" + orderId + "/accept")
                        .session(talentSession))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_REJECTED"));
    }

    @Test
    @DisplayName("5.7 超过接单时限后接单被拒绝")
    void 超时后接单被拒() throws Exception {
        long orderId = paidOrder("wxid_x", null);
        // 把接单时限拨到过去
        ServiceOrder order = orderMapper.selectById(orderId);
        order.setAcceptDeadline(java.time.LocalDateTime.now().minusMinutes(1));
        orderMapper.updateById(order);

        mockMvc.perform(put("/api/talent/workspace/orders/" + orderId + "/accept")
                        .session(talentSession))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("接单时限")));

        // 剩余时间为零的订单也不再给 ACCEPT 操作
        JsonNode pending = dataOf(mockMvc.perform(
                get("/api/talent/workspace/pending-orders").session(talentSession)).andReturn());
        assertThat(pending.get(0).get("actions").toString()).doesNotContain("ACCEPT");
    }

    // ---------- 5.8 标记完成 ----------

    @Test
    @DisplayName("5.8 标记完成写入状态与完成时间")
    void 标记完成() throws Exception {
        long orderId = acceptedOrder();

        JsonNode done = dataOf(mockMvc.perform(
                put("/api/talent/workspace/orders/" + orderId + "/complete").session(talentSession))
                .andExpect(status().isOk()).andReturn());

        assertThat(done.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(orderMapper.selectById(orderId).getCompletedAt()).isNotNull();

        // 完成后粉丝侧出现评分入口与再次下单
        JsonNode fanView = dataOf(mockMvc.perform(
                get("/api/orders/" + orderId).session(fan)).andReturn());
        assertThat(fanView.get("actions").toString()).contains("RATE").contains("REORDER");
    }

    @Test
    @DisplayName("5.8 他人不能标记完成")
    void 他人不能标记完成() throws Exception {
        long orderId = acceptedOrder();

        mockMvc.perform(put("/api/talent/workspace/orders/" + orderId + "/complete")
                        .session(otherTalentSession))
                .andExpect(status().isForbidden());
    }

    // ---------- 运营订单列表：分页与搜索 ----------

    @Test
    @DisplayName("订单列表分页 — items 按页返回，total 是全量条数")
    void 订单列表分页() throws Exception {
        for (int i = 0; i < 3; i++) {
            createOrder(1, "wxid_page" + i, null);
        }

        JsonNode first = dataOf(mockMvc.perform(
                get("/api/admin/orders?status=PENDING_PAYMENT&page=0&size=2").session(operator))
                .andExpect(status().isOk()).andReturn());

        assertThat(first.get("items")).hasSize(2);
        assertThat(first.get("total").asLong()).isGreaterThanOrEqualTo(3);
        assertThat(first.get("hasMore").asBoolean()).isTrue();

        JsonNode far = dataOf(mockMvc.perform(
                get("/api/admin/orders?status=PENDING_PAYMENT&page=999&size=2").session(operator))
                .andExpect(status().isOk()).andReturn());
        assertThat(far.get("items")).isEmpty();
        assertThat(far.get("total").asLong()).isEqualTo(first.get("total").asLong());
        assertThat(far.get("hasMore").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("按订单号搜索 — 服务端精确命中那一单")
    void 按订单号搜索() throws Exception {
        JsonNode order = createOrder(1, "wxid_find", null);
        String ref = order.get("orderRef").asText();

        JsonNode hit = dataOf(mockMvc.perform(
                get("/api/admin/orders?keyword=" + ref).session(operator))
                .andExpect(status().isOk()).andReturn());

        assertThat(hit.get("total").asLong()).isEqualTo(1);
        assertThat(hit.get("items").get(0).get("id").asLong()).isEqualTo(order.get("id").asLong());
    }

    @Test
    @DisplayName("按粉丝微信号搜索 — 命中该粉丝的单")
    void 按微信号搜索() throws Exception {
        JsonNode order = createOrder(1, "wxid_unique_contact", null);

        JsonNode hit = dataOf(mockMvc.perform(
                get("/api/admin/orders?keyword=unique_contact").session(operator))
                .andExpect(status().isOk()).andReturn());

        assertThat(idsOfOrders(hit.get("items"))).contains(order.get("id").asLong());
    }

    @Test
    @DisplayName("按主播昵称搜索 — 昵称不在订单表上，仍要能搜到")
    void 按主播昵称搜索() throws Exception {
        JsonNode order = createOrder(1, "wxid_bytalent", null);
        // 本用例的主播在 setUp 里叫「小雨」。开发库里可能还有同名的，
        // 所以只断言「包含自己这一单」，不断言总数。

        JsonNode hit = dataOf(mockMvc.perform(
                get("/api/admin/orders?keyword=小雨").session(operator))
                .andExpect(status().isOk()).andReturn());

        assertThat(idsOfOrders(hit.get("items"))).contains(order.get("id").asLong());
    }

    @Test
    @DisplayName("搜不到的关键字 — 返回空页而不是全量")
    void 搜不到返回空页() throws Exception {
        createOrder(1, "wxid_x", null);

        JsonNode miss = dataOf(mockMvc.perform(
                get("/api/admin/orders?keyword=这串东西肯定不存在").session(operator))
                .andExpect(status().isOk()).andReturn());

        assertThat(miss.get("items")).isEmpty();
        assertThat(miss.get("total").asLong()).isZero();
    }

    /**
     * 通配符必须按字面处理。
     *
     * 探针设计成「只有当 % 被当成通配符才会命中」：微信号是 wxid_probeXYend，
     * 搜 "probe%end" —— 转义了就匹配不上（没有字面的 probe%end），
     * 不转义就会匹配上。再用 "probeXYend" 做对照，确认这一单确实搜得到，
     * 排除「其实是搜索整个坏掉了」这种假通过。
     */
    @Test
    @DisplayName("关键字里的百分号按字面处理，不当通配符")
    void 订单搜索百分号不当通配符() throws Exception {
        JsonNode order = createOrder(1, "wxid_probeXYend", null);

        JsonNode control = dataOf(mockMvc.perform(
                get("/api/admin/orders").param("keyword", "probeXYend").session(operator))
                .andExpect(status().isOk()).andReturn());
        assertThat(idsOfOrders(control.get("items")))
                .as("对照组：这一单本来就该被搜到")
                .contains(order.get("id").asLong());

        JsonNode wildcard = dataOf(mockMvc.perform(
                get("/api/admin/orders").param("keyword", "probe%end").session(operator))
                .andExpect(status().isOk()).andReturn());
        assertThat(idsOfOrders(wildcard.get("items")))
                .as("% 被当成通配符的话，这里会命中 probeXYend")
                .doesNotContain(order.get("id").asLong());
    }

    /** 下划线同理。微信号里下划线很常见，搜错了会多捞一堆。 */
    @Test
    @DisplayName("关键字里的下划线按字面处理，不当通配符")
    void 订单搜索下划线不当通配符() throws Exception {
        JsonNode order = createOrder(1, "wxidZprobe", null);

        JsonNode wildcard = dataOf(mockMvc.perform(
                get("/api/admin/orders").param("keyword", "wxid_probe").session(operator))
                .andExpect(status().isOk()).andReturn());
        assertThat(idsOfOrders(wildcard.get("items")))
                .as("_ 被当成通配符的话，这里会命中 wxidZprobe")
                .doesNotContain(order.get("id").asLong());
    }

    private static java.util.List<Long> idsOfOrders(JsonNode list) {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        list.forEach(n -> ids.add(n.get("id").asLong()));
        return ids;
    }

    // ---------- 5.9 运营取消与退款 ----------

    @Test
    @DisplayName("5.9 取消需填写原因")
    void 取消需填原因() throws Exception {
        long orderId = createOrder(1, "wxid_x", null).get("id").asLong();

        mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancel").session(operator)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='reason')]").exists());

        assertThat(orderMapper.selectById(orderId).statusEnum()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("5.9 取消记录操作者、时间与原因")
    void 取消留痕() throws Exception {
        long orderId = createOrder(1, "wxid_x", null).get("id").asLong();

        mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancel").session(operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"粉丝主动申请取消\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        ServiceOrder stored = orderMapper.selectById(orderId);
        assertThat(stored.getClosedAt()).isNotNull();
        assertThat(stored.getCloseReason()).isEqualTo("粉丝主动申请取消");

        JsonNode history = dataOf(mockMvc.perform(
                get("/api/orders/" + orderId + "/history").session(operator)).andReturn());
        JsonNode last = history.get(history.size() - 1);
        assertThat(last.get("operatorType").asText()).isEqualTo("OPERATOR");
        assertThat(last.get("reason").asText()).isEqualTo("粉丝主动申请取消");
    }

    @Test
    @DisplayName("5.9 重复标记退款不产生第二条记录")
    void 重复标记退款幂等() throws Exception {
        long orderId = paidOrder("wxid_x", null);
        // 先置为已超时，才能走退款
        ServiceOrder order = orderMapper.selectById(orderId);
        order.setStatusEnum(OrderStatus.TIMEOUT);
        orderMapper.updateById(order);

        mockMvc.perform(put("/api/admin/orders/" + orderId + "/refund").session(operator)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"超时退款\"}"))
                .andExpect(status().isOk());
        int afterFirst = historySize(orderId);

        mockMvc.perform(put("/api/admin/orders/" + orderId + "/refund").session(operator)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"超时退款\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"));

        assertThat(historySize(orderId)).isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("5.9 实收与应付不一致时必须填写差异说明")
    void 金额不一致需差异说明() throws Exception {
        long orderId = createOrder(1, "wxid_x", null).get("id").asLong();

        mockMvc.perform(put("/api/admin/payment/" + orderId + "/confirm").session(operator)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"paidCents\":1400}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("差异说明")));

        mockMvc.perform(put("/api/admin/payment/" + orderId + "/confirm").session(operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paidCents\":1400,\"note\":\"粉丝少付 1 元，已沟通补足\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.status").value("PENDING_ACCEPT"));

        // 实收金额记在订单上，运营视图可见
        assertThat(orderMapper.selectById(orderId).getPaidCents()).isEqualTo(1400);
    }

    @Test
    @DisplayName("5.9 待退款队列只含已超时订单")
    void 待退款队列() throws Exception {
        long timeoutOrder = paidOrder("wxid_a", null);
        ServiceOrder o = orderMapper.selectById(timeoutOrder);
        o.setStatusEnum(OrderStatus.TIMEOUT);
        o.setClosedAt(java.time.LocalDateTime.now());
        orderMapper.updateById(o);
        long normalOrder = paidOrder("wxid_b", null);

        JsonNode queue = dataOf(mockMvc.perform(
                get("/api/admin/orders/refund-queue").session(operator)).andReturn());

        var ids = new java.util.ArrayList<Long>();
        queue.forEach(n -> ids.add(n.get("id").asLong()));
        assertThat(ids).contains(timeoutOrder).doesNotContain(normalOrder);
    }

    // ---------- 2.5 越权访问防护 ----------

    @Test
    @DisplayName("2.5 请求他人订单详情返回无权限")
    void 请求他人订单详情被拒() throws Exception {
        long orderId = createOrder(1, "wxid_x", null).get("id").asLong();

        mockMvc.perform(get("/api/orders/" + orderId).session(otherFan))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("2.5 本人、该单主播与运营都可读，无关主播不可读")
    void 可读范围() throws Exception {
        long orderId = paidOrder("wxid_x", null);

        mockMvc.perform(get("/api/orders/" + orderId).session(fan)).andExpect(status().isOk());
        mockMvc.perform(get("/api/orders/" + orderId).session(talentSession)).andExpect(status().isOk());
        mockMvc.perform(get("/api/orders/" + orderId).session(operator)).andExpect(status().isOk());

        mockMvc.perform(get("/api/orders/" + orderId).session(otherTalentSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/orders/" + orderId).session(otherFan))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("2.5 状态轨迹同样受归属校验")
    void 轨迹也受归属校验() throws Exception {
        long orderId = createOrder(1, "wxid_x", null).get("id").asLong();

        mockMvc.perform(get("/api/orders/" + orderId + "/history").session(otherFan))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("2.5 按订单号查询同样受归属校验")
    void 按单号查询受归属校验() throws Exception {
        JsonNode order = createOrder(1, "wxid_x", null);
        String ref = order.get("orderRef").asText();

        mockMvc.perform(get("/api/orders/by-ref/" + ref).session(fan)).andExpect(status().isOk());
        mockMvc.perform(get("/api/orders/by-ref/" + ref).session(otherFan))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("2.5 粉丝端看不到自己订单的联系方式回显之外的他人数据")
    void 我的订单只含自己的单() throws Exception {
        createOrder(1, "wxid_mine", null);
        // 另一位粉丝下一单
        mockMvc.perform(post("/api/orders").session(otherFan)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1, "wxid_other", null)))
                .andExpect(status().isOk());

        JsonNode mine = dataOf(mockMvc.perform(get("/api/orders").session(fan)).andReturn());
        assertThat(mine).hasSize(1);
    }

    @Test
    @DisplayName("联系方式不暴露给下单粉丝之外的无关者，粉丝自己也不回显")
    void 联系方式可见范围() throws Exception {
        long orderId = paidOrder("wxid_secret", null);

        // 粉丝自己看：不回显（他本来就知道，回显只会增加泄露面）
        JsonNode fanView = dataOf(mockMvc.perform(
                get("/api/orders/" + orderId).session(fan)).andReturn());
        assertThat(fanView.get("contact").isNull()).isTrue();

        // 该单主播看：可见，否则无法联系粉丝
        JsonNode talentView = dataOf(mockMvc.perform(
                get("/api/orders/" + orderId).session(talentSession)).andReturn());
        assertThat(talentView.get("contact").asText()).isEqualTo("wxid_secret");
    }

    @Test
    @DisplayName("同时是运营的粉丝，看自己的订单仍是粉丝视图")
    void 运营看自己的订单是粉丝视图() throws Exception {
        // 公会很小，运营本人也会下单。若按角色优先判成运营，
        // 他自己的订单上「上传凭证」会消失，等于付不了钱。
        MvcResult result = mockMvc.perform(post("/api/orders").session(operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1, "wxid_op", null)))
                .andExpect(status().isOk())
                .andReturn();
        long orderId = dataOf(result).get("id").asLong();

        JsonNode view = dataOf(mockMvc.perform(
                get("/api/orders/" + orderId).session(operator)).andReturn());

        assertThat(view.get("actions").toString())
                .contains("UPLOAD_PROOF")
                .doesNotContain("CONFIRM_PAYMENT");
    }

    @Test
    @DisplayName("运营看他人的订单是运营视图")
    void 运营看他人订单是运营视图() throws Exception {
        long orderId = createOrder(1, "wxid_x", null).get("id").asLong();

        JsonNode view = dataOf(mockMvc.perform(
                get("/api/orders/" + orderId).session(operator)).andReturn());

        assertThat(view.get("actions").toString()).contains("CONFIRM_PAYMENT");
    }

    // ---------- 辅助 ----------

    private String body(int quantity, String contact, String remark) {
        return "{\"talentId\":" + talentId + ",\"serviceSpecId\":" + specId
                + ",\"quantity\":" + quantity + ",\"contact\":\"" + contact + "\""
                + (remark == null ? "" : ",\"remark\":\"" + remark + "\"") + "}";
    }

    private JsonNode createOrder(int quantity, String contact, String remark) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders").session(fan)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(quantity, contact, remark)))
                .andExpect(status().isOk())
                .andReturn();
        return dataOf(result);
    }

    private long paidOrder(String contact, String remark) throws Exception {
        long orderId = createOrder(1, contact, remark).get("id").asLong();
        mockMvc.perform(put("/api/admin/payment/" + orderId + "/confirm").session(operator)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"paidCents\":1500}"))
                .andExpect(status().isOk());
        return orderId;
    }

    private long acceptedOrder() throws Exception {
        long orderId = paidOrder("wxid_x", null);
        mockMvc.perform(put("/api/talent/workspace/orders/" + orderId + "/accept").session(talentSession))
                .andExpect(status().isOk());
        return orderId;
    }

    private int historySize(long orderId) throws Exception {
        return dataOf(mockMvc.perform(
                get("/api/orders/" + orderId + "/history").session(operator)).andReturn()).size();
    }

    private long countOrders() {
        return orderMapper.selectCount(com.baomidou.mybatisplus.core.toolkit.Wrappers.emptyWrapper());
    }

    private JsonNode dataOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private AppUser newUser(String prefix, UserRole extraRole) {
        AppUser user = userService.registerOrUpdate(prefix + System.nanoTime(), null, prefix, null);
        if (extraRole != null) {
            userService.grantRole(user.getId(), extraRole);
        }
        return user;
    }

    private MockHttpSession sessionFor(AppUser user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(WechatAuthController.SESSION_USER_ID, user.getId());
        return session;
    }
}
