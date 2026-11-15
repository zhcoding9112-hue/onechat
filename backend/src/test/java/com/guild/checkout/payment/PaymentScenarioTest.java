package com.guild.checkout.payment;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * payment spec 的场景测试（tasks 6.3 / 6.5 / 6.6 / 6.8 / 6.9）。
 */
@SpringBootTest
@Transactional
class PaymentScenarioTest {

    @Autowired private WebApplicationContext context;
    @Autowired private PaymentService paymentService;
    @Autowired private OrderService orderService;
    @Autowired private ServiceOrderMapper orderMapper;
    @Autowired private PaymentRecordMapper recordMapper;
    @Autowired private PaymentProofMapper proofMapper;
    @Autowired private PaymentSettingMapper settingMapper;
    @Autowired private AppUserService userService;
    @Autowired private TalentAdminService talentAdminService;
    @Autowired private ServiceCatalogAdminService catalogService;
    @Autowired private TalentPriceAdminService priceService;
    @Autowired private CommissionAdminService commissionService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private MockHttpSession fan;
    private MockHttpSession otherFan;
    private MockHttpSession operator;
    private long fanId;
    private long operatorId;
    private long talentId;
    private long specId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        commissionService.setDefault(3000);

        AppUser fanUser = newUser("pf_");
        fanId = fanUser.getId();
        fan = sessionFor(fanUser);
        otherFan = sessionFor(newUser("po_"));
        AppUser op = newUser("pp_");
        operatorId = op.getId();
        userService.grantRole(operatorId, UserRole.OPERATOR);
        operator = sessionFor(op);

        ServiceItem item = catalogService.createItem("pay_" + System.nanoTime(), "情感倾听", null, 0);
        ServiceSpec spec = catalogService.createSpec(item.getId(), "m30", "30分钟", 30, 30);
        specId = spec.getId();

        Talent talent = talentAdminService.create(new TalentAdminService.ProfileForm("小雨", null, null, 0, null, null, null, null));
        talentAdminService.setListed(talent.getId(), true);
        talentId = talent.getId();
        priceService.setPrice(talentId, specId, 1500, true);

        // 人工收款配好收款信息
        PaymentSetting setting = paymentService.findSetting(ManualPaymentProvider.CODE);
        setting.setEnabled(true);
        setting.setAccountName("某某文化传媒有限公司");
        setting.setAccountNo("1234567890");
        setting.setBankName("某银行某支行");
        settingMapper.updateById(setting);
    }

    // ===== Requirement: 支付方式与订单流程解耦 =====

    @Test
    @DisplayName("Scenario: 人工收款确认驱动订单 — 转入待接单")
    void 人工收款确认驱动订单() {
        ServiceOrder order = newOrder();

        PaymentService.ConfirmResult result = confirm(order, 1500, "flow-001");

        assertThat(result.outcome()).isEqualTo(PaymentService.Outcome.ACCEPTED);
        assertThat(result.order().statusEnum()).isEqualTo(OrderStatus.PENDING_ACCEPT);
        assertThat(result.order().getAcceptDeadline()).isNotNull();
    }

    @Test
    @DisplayName("Scenario: 同一订单只认一次收款 — 第二次被忽略且留痕")
    void 同一订单只认一次收款() {
        ServiceOrder order = newOrder();

        confirm(order, 1500, "flow-001");
        PaymentService.ConfirmResult second = confirm(order, 1500, "flow-001");

        assertThat(second.outcome()).isEqualTo(PaymentService.Outcome.DUPLICATE);
        assertThat(orderMapper.selectById(order.getId()).statusEnum())
                .isEqualTo(OrderStatus.PENDING_ACCEPT);
        // 只记一次收款
        assertThat(paymentService.recordsOf(order.getId())).hasSize(1);
        // 但被忽略的事件留了痕
        assertThat(paymentService.eventLogsOf(order.getOrderRef()))
                .extracting(PaymentEventLog::getOutcome)
                .containsExactly(PaymentEventLog.ACCEPTED, PaymentEventLog.DUPLICATE);
    }

    @Test
    @DisplayName("不同流水号的第二次收款同样被忽略，因为订单已收款")
    void 已收款订单的新流水也被忽略() {
        ServiceOrder order = newOrder();
        confirm(order, 1500, "flow-001");

        PaymentService.ConfirmResult second = confirm(order, 1500, "flow-002");

        assertThat(second.outcome()).isEqualTo(PaymentService.Outcome.DUPLICATE);
        assertThat(paymentService.recordsOf(order.getId())).hasSize(1);
    }

    @Test
    @DisplayName("Scenario: 支付方式可配置启停 — 停用后粉丝端不再展示，已创建订单不失效")
    void 支付方式启停() throws Exception {
        ServiceOrder order = newOrder();

        // 启用时可取到指引
        JsonNode page = dataOf(mockMvc.perform(
                get("/api/orders/" + order.getId() + "/payment").session(fan)).andReturn());
        assertThat(page.get("providers")).hasSize(1);
        assertThat(page.get("instruction").get("provider").asText()).isEqualTo("manual");

        mockMvc.perform(put("/api/admin/payment/settings/manual/enabled/false").session(operator))
                .andExpect(status().isOk());

        // 停用后不再展示该方式
        assertThat(paymentService.enabledProviders()).isEmpty();
        // 已创建的待支付订单本身不失效
        assertThat(orderMapper.selectById(order.getId()).statusEnum())
                .isEqualTo(OrderStatus.PENDING_PAYMENT);
        // 仍可被运营确认收款，钱已经到账的单不该因为关了入口就作废
        assertThat(confirm(order, 1500, "flow-003").outcome())
                .isEqualTo(PaymentService.Outcome.ACCEPTED);
    }

    @Test
    @DisplayName("支付端口不含任何微信或人工收款的专有概念")
    void 支付端口保持中立() {
        // 以方法签名为准：端口只回答"是什么"与"怎么引导"，不暴露收款码或回调参数
        List<String> methods = java.util.Arrays.stream(PaymentProvider.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName).sorted().toList();
        assertThat(methods).containsExactly("code", "displayName", "instruct");

        List<String> eventFields = java.util.Arrays.stream(PaymentConfirmed.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList();
        assertThat(eventFields).containsExactly(
                "orderRef", "paidCents", "provider", "externalRef", "operatorId", "occurredAt", "note");
        assertThat(eventFields.toString().toLowerCase())
                .doesNotContain("wechat").doesNotContain("qr").doesNotContain("proof");
    }

    // ===== Requirement: 人工收款信息展示 =====

    @Test
    @DisplayName("Scenario: 展示收款信息 — 账户、应付金额与订单编号")
    void 展示收款信息() throws Exception {
        ServiceOrder order = newOrder();

        JsonNode page = dataOf(mockMvc.perform(
                get("/api/orders/" + order.getId() + "/payment").session(fan)).andReturn());

        assertThat(page.get("orderRef").asText()).isEqualTo(order.getOrderRef());
        assertThat(page.get("payableCents").asLong()).isEqualTo(1500);
        JsonNode payload = page.get("instruction").get("payload");
        assertThat(payload.get("accountName").asText()).isEqualTo("某某文化传媒有限公司");
        assertThat(payload.get("accountNo").asText()).isEqualTo("1234567890");
        assertThat(payload.get("tips").asText()).contains("上传支付凭证");
        assertThat(payload.get("ready").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Scenario: 运营更换收款码 — 此后进入支付环节的订单展示新信息")
    void 更换收款信息() throws Exception {
        mockMvc.perform(put("/api/admin/payment/settings/manual").session(operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountNo\":\"9999888877\",\"qrUrl\":\"/uploads/qr/new.png\"}"))
                .andExpect(status().isOk());

        ServiceOrder order = newOrder();
        JsonNode payload = dataOf(mockMvc.perform(
                get("/api/orders/" + order.getId() + "/payment").session(fan)).andReturn())
                .get("instruction").get("payload");

        assertThat(payload.get("accountNo").asText()).isEqualTo("9999888877");
        assertThat(payload.get("qrUrl").asText()).isEqualTo("/uploads/qr/new.png");
    }

    @Test
    @DisplayName("收款信息未配置时明确告知未就绪，而不是展示空白")
    void 收款信息未配置时标记未就绪() throws Exception {
        settingMapper.update(null, Wrappers.<PaymentSetting>lambdaUpdate()
                .eq(PaymentSetting::getProvider, ManualPaymentProvider.CODE)
                .set(PaymentSetting::getAccountNo, null)
                .set(PaymentSetting::getQrUrl, null));

        ServiceOrder order = newOrder();
        JsonNode payload = dataOf(mockMvc.perform(
                get("/api/orders/" + order.getId() + "/payment").session(fan)).andReturn())
                .get("instruction").get("payload");

        assertThat(payload.get("ready").asBoolean()).isFalse();
    }

    // ===== Requirement: 支付凭证上传 =====

    @Test
    @DisplayName("Scenario: 上传凭证 — 订单置为待确认收款并出现在待对账列表")
    void 上传凭证() throws Exception {
        ServiceOrder order = newOrder();

        mockMvc.perform(multipart("/api/orders/" + order.getId() + "/payment/proofs")
                        .file(image("proof.png")).session(fan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url", org.hamcrest.Matchers.startsWith("/uploads/payment-proof/")));

        assertThat(orderMapper.selectById(order.getId()).statusEnum())
                .isEqualTo(OrderStatus.PENDING_CONFIRM);

        JsonNode queue = dataOf(mockMvc.perform(
                get("/api/admin/payment/reconcile").session(operator)).andReturn());
        assertThat(idsOf(queue)).contains(order.getId());
    }

    @Test
    @DisplayName("Scenario: 重复上传凭证 — 全部保留，状态保持待确认收款")
    void 重复上传凭证() throws Exception {
        ServiceOrder order = newOrder();

        upload(order, "first.png");
        upload(order, "second.png");
        upload(order, "third.png");

        assertThat(proofMapper.selectList(Wrappers.<PaymentProof>lambdaQuery()
                .eq(PaymentProof::getOrderId, order.getId()))).hasSize(3);
        assertThat(orderMapper.selectById(order.getId()).statusEnum())
                .isEqualTo(OrderStatus.PENDING_CONFIRM);

        // 运营对账时能看到全部三张
        JsonNode queue = dataOf(mockMvc.perform(
                get("/api/admin/payment/reconcile").session(operator)).andReturn());
        JsonNode item = findById(queue, order.getId());
        assertThat(item.get("proofUrls")).hasSize(3);
    }

    @Test
    @DisplayName("Scenario: 凭证格式不符 — 拒绝上传，订单保持待支付")
    void 凭证格式不符() throws Exception {
        ServiceOrder order = newOrder();

        mockMvc.perform(multipart("/api/orders/" + order.getId() + "/payment/proofs")
                        .file(new MockMultipartFile("file", "x.exe",
                                "application/octet-stream", "x".getBytes()))
                        .session(fan))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("不支持的格式")));

        assertThat(orderMapper.selectById(order.getId()).statusEnum())
                .isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("他人不能为别人的订单上传凭证")
    void 他人不能上传凭证() throws Exception {
        ServiceOrder order = newOrder();

        mockMvc.perform(multipart("/api/orders/" + order.getId() + "/payment/proofs")
                        .file(image("p.png")).session(otherFan))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("已收款的订单不能再上传凭证")
    void 已收款不能再传凭证() throws Exception {
        ServiceOrder order = newOrder();
        confirm(order, 1500, "flow-001");

        mockMvc.perform(multipart("/api/orders/" + order.getId() + "/payment/proofs")
                        .file(image("p.png")).session(fan))
                .andExpect(status().isConflict());
    }

    // ===== Requirement: 运营对账确认 =====

    @Test
    @DisplayName("Scenario: 确认收款 — 记录实收金额、操作者与时间，订单转待接单")
    void 确认收款() throws Exception {
        ServiceOrder order = newOrder();
        upload(order, "p.png");

        mockMvc.perform(put("/api/admin/payment/" + order.getId() + "/confirm").session(operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paidCents\":1500,\"externalRef\":\"bank-20261112-001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.status").value("PENDING_ACCEPT"));

        List<PaymentRecord> records = paymentService.recordsOf(order.getId());
        assertThat(records).singleElement().satisfies(r -> {
            assertThat(r.getPaidCents()).isEqualTo(1500);
            assertThat(r.getOperatorId()).isEqualTo(operatorId);
            assertThat(r.getExternalRef()).isEqualTo("bank-20261112-001");
            assertThat(r.getOccurredAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("Scenario: 驳回凭证 — 订单回到待支付并展示驳回原因，粉丝可重传")
    void 驳回凭证() throws Exception {
        ServiceOrder order = newOrder();
        upload(order, "p.png");

        mockMvc.perform(put("/api/admin/payment/" + order.getId() + "/reject").session(operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"流水中未找到对应到账\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"));

        ServiceOrder back = orderMapper.selectById(order.getId());
        assertThat(back.statusEnum()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(back.getCloseReason()).isEqualTo("流水中未找到对应到账");

        // 粉丝可以重新上传
        upload(back, "again.png");
        assertThat(orderMapper.selectById(order.getId()).statusEnum())
                .isEqualTo(OrderStatus.PENDING_CONFIRM);
    }

    @Test
    @DisplayName("驳回原因为必填")
    void 驳回需填原因() throws Exception {
        ServiceOrder order = newOrder();
        upload(order, "p.png");

        mockMvc.perform(put("/api/admin/payment/" + order.getId() + "/reject").session(operator)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());

        assertThat(orderMapper.selectById(order.getId()).statusEnum())
                .isEqualTo(OrderStatus.PENDING_CONFIRM);
    }

    @Test
    @DisplayName("Scenario: 实收与应付不一致 — 必须填差异说明，且两个金额都保留")
    void 金额不一致需差异说明() throws Exception {
        ServiceOrder order = newOrder();
        upload(order, "p.png");

        mockMvc.perform(put("/api/admin/payment/" + order.getId() + "/confirm").session(operator)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"paidCents\":1400}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("差异说明")));

        mockMvc.perform(put("/api/admin/payment/" + order.getId() + "/confirm").session(operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paidCents\":1400,\"note\":\"少付 1 元，已沟通\"}"))
                .andExpect(status().isOk());

        ServiceOrder paid = orderMapper.selectById(order.getId());
        assertThat(paid.getPayableCents()).isEqualTo(1500);
        assertThat(paid.getPaidCents()).isEqualTo(1400);
        assertThat(paymentService.recordsOf(order.getId()).get(0).getNote()).contains("少付");
    }

    @Test
    @DisplayName("非运营访问对账接口一律无权限")
    void 非运营不能对账() throws Exception {
        ServiceOrder order = newOrder();

        mockMvc.perform(get("/api/admin/payment/reconcile").session(fan))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/payment/" + order.getId() + "/confirm").session(fan)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"paidCents\":1500}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("对不存在的订单发收款事件会被拒绝并留痕")
    void 不存在的订单被拒() {
        assertThatThrownBy(() -> paymentService.confirm(new PaymentConfirmed(
                "261112000000999999", 1500, "manual", "x", operatorId, LocalDateTime.now(), null)))
                .isInstanceOf(com.guild.checkout.common.exception.NotFoundException.class);
    }

    @Test
    @DisplayName("收款事件缺少流水标识时直接拒绝构造，幂等无从谈起")
    void 缺少流水标识被拒() {
        assertThatThrownBy(() -> new PaymentConfirmed(
                "261112000000999999", 1500, "manual", "  ", null, LocalDateTime.now(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("幂等");
    }

    // ---------- 辅助 ----------

    private ServiceOrder newOrder() {
        return orderService.create(new OrderCreateCommand(
                fanId, talentId, specId, 1, "wxid_test", null));
    }

    private PaymentService.ConfirmResult confirm(ServiceOrder order, long cents, String ref) {
        return paymentService.confirm(new PaymentConfirmed(
                order.getOrderRef(), cents, ManualPaymentProvider.CODE, ref,
                operatorId, LocalDateTime.now(), null));
    }

    private void upload(ServiceOrder order, String name) throws Exception {
        mockMvc.perform(multipart("/api/orders/" + order.getId() + "/payment/proofs")
                        .file(image(name)).session(fan))
                .andExpect(status().isOk());
    }

    private static MockMultipartFile image(String name) {
        return new MockMultipartFile("file", name, "image/png", "fake-image-bytes".getBytes());
    }

    private JsonNode dataOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private static List<Long> idsOf(JsonNode array) {
        List<Long> ids = new java.util.ArrayList<>();
        array.forEach(n -> ids.add(n.get("orderId").asLong()));
        return ids;
    }

    private static JsonNode findById(JsonNode array, long orderId) {
        for (JsonNode n : array) {
            if (n.get("orderId").asLong() == orderId) {
                return n;
            }
        }
        return null;
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
