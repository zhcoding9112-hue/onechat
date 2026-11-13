package com.guild.checkout.order.timeout;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guild.checkout.auth.WechatAuthController;
import com.guild.checkout.order.OrderCreateCommand;
import com.guild.checkout.order.OrderDeadlineProvider;
import com.guild.checkout.order.OrderOperatorType;
import com.guild.checkout.order.OrderService;
import com.guild.checkout.order.OrderStateService;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 超时处置（tasks 7.2 - 7.7）。
 */
@SpringBootTest
@Transactional
class OrderTimeoutScenarioTest {

    @Autowired private WebApplicationContext context;
    @Autowired private OrderService orderService;
    @Autowired private OrderStateService stateService;
    @Autowired private OrderTimeoutService timeoutService;
    @Autowired private OrderDeadlineProvider deadlines;
    @Autowired private ServiceOrderMapper orderMapper;
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

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        commissionService.setDefault(3000);

        fanId = newUser("tf_").getId();
        AppUser op = newUser("to_");
        operatorId = op.getId();
        userService.grantRole(operatorId, UserRole.OPERATOR);
        operator = sessionFor(op);

        ServiceItem item = catalogService.createItem("to_" + System.nanoTime(), "情感倾听", null, 0);
        ServiceSpec spec = catalogService.createSpec(item.getId(), "m30", "30分钟", 30, 30);
        specId = spec.getId();

        Talent talent = talentAdminService.create("小雨", null, null, 0);
        talentAdminService.setListed(talent.getId(), true);
        talentId = talent.getId();
        priceService.setPrice(talentId, specId, 1500, true);

        AppUser talentUser = newUser("tt_");
        talentAdminService.linkUser(talentId, talentUser.getId());
        talentUserId = talentUser.getId();
    }

    // ===== 7.2 待支付超时 =====

    @Test
    @DisplayName("7.2 超期未上传凭证的订单被置为已取消，原因记为支付超时")
    void 待支付超时被取消() {
        ServiceOrder order = newOrder();
        expirePayDeadline(order);

        assertThat(timeoutService.expirePayment(order.getId()))
                .isEqualTo(OrderTimeoutService.Result.HANDLED);

        ServiceOrder closed = orderMapper.selectById(order.getId());
        assertThat(closed.statusEnum()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(closed.getCloseReason()).isEqualTo(OrderTimeoutService.PAY_TIMEOUT_REASON);
        assertThat(closed.getClosedAt()).isNotNull();
    }

    @Test
    @DisplayName("7.2 已上传凭证的订单到期后不被关闭")
    void 已上传凭证的订单不被关闭() {
        ServiceOrder order = newOrder();
        // 上传凭证 → 待确认收款
        stateService.transition(order, OrderStatus.PENDING_CONFIRM,
                OrderOperatorType.FAN, fanId, "上传凭证", null);
        expirePayDeadline(order);

        assertThat(timeoutService.expirePayment(order.getId()))
                .isEqualTo(OrderTimeoutService.Result.SKIPPED);

        // 人可能已经付了钱，不能因为运营没及时对账就把单关掉
        assertThat(orderMapper.selectById(order.getId()).statusEnum())
                .isEqualTo(OrderStatus.PENDING_CONFIRM);
    }

    @Test
    @DisplayName("7.2 未到期的待支付订单不被处置")
    void 未到期不处置() {
        ServiceOrder order = newOrder();

        assertThat(timeoutService.expirePayment(order.getId()))
                .isEqualTo(OrderTimeoutService.Result.NOT_DUE);
        assertThat(orderMapper.selectById(order.getId()).statusEnum())
                .isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("7.2 超时处置写入状态变更日志，触发者为系统")
    void 超时处置留痕() {
        ServiceOrder order = newOrder();
        expirePayDeadline(order);
        timeoutService.expirePayment(order.getId());

        var history = stateService.history(order.getId());
        var last = history.get(history.size() - 1);
        assertThat(last.getToStatus()).isEqualTo("CANCELLED");
        assertThat(last.getOperatorType()).isEqualTo("SYSTEM");
        assertThat(last.getOperatorId()).isNull();
        assertThat(last.getReason()).isEqualTo(OrderTimeoutService.PAY_TIMEOUT_REASON);
    }

    // ===== 7.3 待接单超时 =====

    @Test
    @DisplayName("7.3 待接单超时置为已超时并出现在运营待退款队列")
    void 待接单超时进入退款队列() throws Exception {
        ServiceOrder order = paidOrder();
        expireAcceptDeadline(order);

        assertThat(timeoutService.expireAccept(order.getId()))
                .isEqualTo(OrderTimeoutService.Result.HANDLED);

        ServiceOrder timedOut = orderMapper.selectById(order.getId());
        assertThat(timedOut.statusEnum()).isEqualTo(OrderStatus.TIMEOUT);
        assertThat(timedOut.getCloseReason()).isEqualTo(OrderTimeoutService.ACCEPT_TIMEOUT_REASON);

        JsonNode queue = dataOf(mockMvc.perform(
                get("/api/admin/orders/refund-queue").session(operator)).andReturn());
        assertThat(idsOf(queue)).contains(order.getId());
    }

    @Test
    @DisplayName("7.3 超时前被接单则不再触发超时处置")
    void 超时前被接单() {
        ServiceOrder order = paidOrder();
        orderService.accept(order.getId(), talentUserId);

        expireAcceptDeadline(order);
        assertThat(timeoutService.expireAccept(order.getId()))
                .isEqualTo(OrderTimeoutService.Result.SKIPPED);

        assertThat(orderMapper.selectById(order.getId()).statusEnum())
                .isEqualTo(OrderStatus.ACCEPTED);
    }

    // ===== 7.4 并发安全 =====

    @Test
    @DisplayName("7.4 接单与超时同时发生时只有一方生效")
    void 接单与超时并发() {
        ServiceOrder order = paidOrder();

        // 主播在时限内抢先接单成功
        orderService.accept(order.getId(), talentUserId);
        // 随后超时调度器才轮到这一笔，此时时限已过
        expireAcceptDeadline(order);

        assertThat(timeoutService.expireAccept(order.getId()))
                .isIn(OrderTimeoutService.Result.SKIPPED, OrderTimeoutService.Result.RACED);
        assertThat(orderMapper.selectById(order.getId()).statusEnum())
                .isEqualTo(OrderStatus.ACCEPTED);

        // 反向：超时先生效，则接单被拒
        ServiceOrder other = paidOrder();
        expireAcceptDeadline(other);
        assertThat(timeoutService.expireAccept(other.getId()))
                .isEqualTo(OrderTimeoutService.Result.HANDLED);
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> orderService.accept(other.getId(), talentUserId))
                .isInstanceOf(com.guild.checkout.common.exception.BusinessException.class);
        assertThat(orderMapper.selectById(other.getId()).statusEnum())
                .isEqualTo(OrderStatus.TIMEOUT);
    }

    @Test
    @DisplayName("7.4 同一订单被处置两次只生效一次")
    void 重复处置只生效一次() {
        ServiceOrder order = paidOrder();
        expireAcceptDeadline(order);

        assertThat(timeoutService.expireAccept(order.getId()))
                .isEqualTo(OrderTimeoutService.Result.HANDLED);
        assertThat(timeoutService.expireAccept(order.getId()))
                .isEqualTo(OrderTimeoutService.Result.SKIPPED);

        // 日志里只有一条超时记录
        long timeoutLogs = stateService.history(order.getId()).stream()
                .filter(e -> "TIMEOUT".equals(e.getToStatus())).count();
        assertThat(timeoutLogs).isEqualTo(1);
    }

    // ===== 7.5 兜底扫表 =====

    @Test
    @DisplayName("7.5 兜底扫表能捞到已过期的待支付与待接单订单")
    void 兜底扫表能捞到过期订单() {
        ServiceOrder pending = newOrder();
        expirePayDeadline(pending);
        ServiceOrder waiting = paidOrder();
        expireAcceptDeadline(waiting);
        ServiceOrder fresh = newOrder();

        assertThat(timeoutService.overduePayments(100))
                .extracting(ServiceOrder::getId)
                .contains(pending.getId())
                .doesNotContain(fresh.getId());
        assertThat(timeoutService.overdueAccepts(100))
                .extracting(ServiceOrder::getId)
                .contains(waiting.getId());
    }

    @Test
    @DisplayName("7.5 已上传凭证的过期订单不出现在待支付扫描结果中")
    void 扫表不捞待确认收款的订单() {
        ServiceOrder order = newOrder();
        stateService.transition(order, OrderStatus.PENDING_CONFIRM,
                OrderOperatorType.FAN, fanId, null, null);
        expirePayDeadline(order);

        assertThat(timeoutService.overduePayments(100))
                .extracting(ServiceOrder::getId)
                .doesNotContain(order.getId());
    }

    @Test
    @DisplayName("7.5 清空 Redis 队列后，过期订单仍能被扫表处置")
    void 清空队列后仍能处置() {
        ServiceOrder order = paidOrder();
        expireAcceptDeadline(order);
        // 不走队列，直接模拟兜底扫表的路径
        var overdue = timeoutService.overdueAccepts(100);
        assertThat(overdue).extracting(ServiceOrder::getId).contains(order.getId());

        for (ServiceOrder o : overdue) {
            timeoutService.expireAccept(o.getId());
        }
        assertThat(orderMapper.selectById(order.getId()).statusEnum())
                .isEqualTo(OrderStatus.TIMEOUT);
    }

    // ===== 7.6 时限配置 =====

    @Test
    @DisplayName("7.6 修改时限后新订单按新时限，计时中的订单沿用原时限")
    void 修改时限不影响在途订单() {
        deadlines.update(30, 15);
        ServiceOrder before = newOrder();
        LocalDateTime originalDeadline = before.getPayDeadline();

        deadlines.update(120, 60);
        ServiceOrder after = newOrder();

        // 在途订单的到期时间完全没变。
        // DATETIME(3) 对微秒是四舍五入而非截断，所以用容差比较而不是直接相等；
        // 这里要验的是"没有被改成 120 分钟后"，毫秒级差异无关紧要。
        assertThat(orderMapper.selectById(before.getId()).getPayDeadline())
                .isCloseTo(originalDeadline,
                        org.assertj.core.api.Assertions.within(5, java.time.temporal.ChronoUnit.MILLIS));
        // 新订单按新时限，明显更晚
        assertThat(after.getPayDeadline()).isAfter(originalDeadline.plusMinutes(60));
    }

    @Test
    @DisplayName("7.6 接单时限从确认收款那一刻起算，且存的是绝对时间戳")
    void 接单时限从收款起算() {
        deadlines.update(null, 15);
        ServiceOrder order = newOrder();
        assertThat(order.getAcceptDeadline()).isNull();

        LocalDateTime beforeConfirm = LocalDateTime.now();
        ServiceOrder paid = orderService.confirmPayment(order.getId(), 1500, operatorId, null);

        assertThat(paid.getAcceptDeadline())
                .isAfter(beforeConfirm.plusMinutes(14))
                .isBefore(beforeConfirm.plusMinutes(16));
    }

    @Test
    @DisplayName("7.6 时限必须为正数，过长的值被拒绝")
    void 非法时限被拒() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> deadlines.update(0, null))
                .isInstanceOf(com.guild.checkout.common.exception.BusinessException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> deadlines.update(null, -5))
                .isInstanceOf(com.guild.checkout.common.exception.BusinessException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> deadlines.update(60 * 24 * 8, null))
                .isInstanceOf(com.guild.checkout.common.exception.BusinessException.class);
    }

    @Test
    @DisplayName("7.6 运营可通过接口读改时限")
    void 时限接口可读改() throws Exception {
        mockMvc.perform(get("/api/admin/orders/deadlines").session(operator))
                .andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/admin/orders/deadlines").session(operator)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"payMinutes\":45,\"acceptMinutes\":20}"))
                .andExpect(status().isOk());

        assertThat(deadlines.payMinutes()).isEqualTo(45);
        assertThat(deadlines.acceptMinutes()).isEqualTo(20);
    }

    // ---------- 辅助 ----------

    private ServiceOrder newOrder() {
        return orderService.create(new OrderCreateCommand(
                fanId, talentId, specId, 1, "wxid_test", null));
    }

    private ServiceOrder paidOrder() {
        ServiceOrder order = newOrder();
        return orderService.confirmPayment(order.getId(), 1500, operatorId, null);
    }

    /** 把到期时间拨到过去，模拟时间流逝。 */
    private void expirePayDeadline(ServiceOrder order) {
        ServiceOrder o = orderMapper.selectById(order.getId());
        o.setPayDeadline(LocalDateTime.now().minusMinutes(1));
        orderMapper.updateById(o);
    }

    private void expireAcceptDeadline(ServiceOrder order) {
        ServiceOrder o = orderMapper.selectById(order.getId());
        o.setAcceptDeadline(LocalDateTime.now().minusMinutes(1));
        orderMapper.updateById(o);
    }

    private JsonNode dataOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private static java.util.List<Long> idsOf(JsonNode array) {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        array.forEach(n -> ids.add(n.get("id").asLong()));
        return ids;
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
