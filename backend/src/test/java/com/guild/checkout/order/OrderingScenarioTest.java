package com.guild.checkout.order;

import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.common.exception.ForbiddenException;
import com.guild.checkout.pricing.PricingException;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ordering spec 的场景测试，逐条对应规格中的 Scenario。
 */
@SpringBootTest
@Transactional
class OrderingScenarioTest {

    @Autowired private OrderService orderService;
    @Autowired private OrderStateService stateService;
    @Autowired private ServiceOrderMapper orderMapper;
    @Autowired private AppUserService userService;
    @Autowired private TalentAdminService talentAdminService;
    @Autowired private ServiceCatalogAdminService catalogService;
    @Autowired private TalentPriceAdminService priceService;
    @Autowired private CommissionAdminService commissionService;

    private long fanId;
    private long otherFanId;
    private long operatorId;
    private long talentId;
    private long talentUserId;
    private long specId;

    @BeforeEach
    void setUp() {
        commissionService.setDefault(3000);
        fanId = newUser("sf_").getId();
        otherFanId = newUser("so_").getId();
        operatorId = newUser("sp_").getId();
        userService.grantRole(operatorId, UserRole.OPERATOR);

        ServiceItem item = catalogService.createItem("sc_" + System.nanoTime(), "情感倾听", null, 0);
        ServiceSpec spec = catalogService.createSpec(item.getId(), "m30", "30分钟", 30, 30);
        specId = spec.getId();

        Talent talent = talentAdminService.create("小雨", null, null, 0);
        talentAdminService.setListed(talent.getId(), true);
        talentId = talent.getId();
        priceService.setPrice(talentId, specId, 1500, true);

        AppUser talentUser = newUser("st_");
        talentAdminService.linkUser(talentId, talentUser.getId());
        talentUserId = talentUser.getId();
    }

    // ===== Requirement: 指定主播下单 =====

    @Test
    @DisplayName("Scenario: 成功创建订单 — 记录主播、项目、档位、数量、金额与联系方式")
    void 成功创建订单() {
        ServiceOrder order = create("wxid_abc", null, 2);

        assertThat(order.statusEnum()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getTalentId()).isEqualTo(talentId);
        assertThat(order.getServiceSpecId()).isEqualTo(specId);
        assertThat(order.getQuantity()).isEqualTo(2);
        assertThat(order.getPayableCents()).isEqualTo(3000);
        assertThat(order.getContact()).isEqualTo("wxid_abc");
        // 进入支付环节：有待支付截止时间
        assertThat(order.getPayDeadline()).isNotNull();
    }

    @Test
    @DisplayName("Scenario: 联系方式缺失 — 拒绝创建并提示必填")
    void 联系方式缺失被拒() {
        long before = countOrders();

        assertThatThrownBy(() -> create("   ", null, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("联系方式");

        assertThat(countOrders()).isEqualTo(before);
    }

    @Test
    @DisplayName("Scenario: 携带备注下单 — 备注随订单保存")
    void 携带备注下单() {
        ServiceOrder order = create("wxid_abc", "晚上九点后", 1);
        assertThat(orderMapper.selectById(order.getId()).getRemark()).isEqualTo("晚上九点后");
    }

    @Test
    @DisplayName("不提供由系统分配主播的下单方式：主播不存在或已下架一律拒绝")
    void 必须指定可约的主播() {
        talentAdminService.setListed(talentId, false);
        assertThatThrownBy(() -> create("wxid_abc", null, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("暂不可约");
    }

    // ===== Requirement: 订单状态流转 =====

    @Test
    @DisplayName("Scenario: 上传凭证后进入待确认收款")
    void 上传凭证后进入待确认收款() {
        ServiceOrder order = create("wxid_abc", null, 1);

        stateService.transition(order, OrderStatus.PENDING_CONFIRM,
                OrderOperatorType.FAN, fanId, "上传凭证", null);

        assertThat(orderMapper.selectById(order.getId()).statusEnum())
                .isEqualTo(OrderStatus.PENDING_CONFIRM);
    }

    @Test
    @DisplayName("Scenario: 支付确认后进入待接单")
    void 支付确认后进入待接单() {
        ServiceOrder order = create("wxid_abc", null, 1);

        ServiceOrder paid = orderService.confirmPayment(order.getId(), 1500, operatorId, null);

        assertThat(paid.statusEnum()).isEqualTo(OrderStatus.PENDING_ACCEPT);
        assertThat(paid.getPaidAt()).isNotNull();
        // 接单时限从确认收款这一刻起算
        assertThat(paid.getAcceptDeadline()).isNotNull();
    }

    @Test
    @DisplayName("Scenario: 凭证被驳回后退回待支付")
    void 凭证被驳回后退回待支付() {
        ServiceOrder order = create("wxid_abc", null, 1);
        stateService.transition(order, OrderStatus.PENDING_CONFIRM,
                OrderOperatorType.FAN, fanId, null, null);

        stateService.transition(order, OrderStatus.PENDING_PAYMENT,
                OrderOperatorType.OPERATOR, operatorId, "流水中未找到到账", null);

        assertThat(orderMapper.selectById(order.getId()).statusEnum())
                .isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("Scenario: 非法状态跃迁被拒绝 — 对待支付订单请求标记完成")
    void 非法跃迁被拒绝() {
        ServiceOrder order = create("wxid_abc", null, 1);

        assertThatThrownBy(() -> orderService.complete(order.getId(), talentUserId))
                .isInstanceOf(BusinessException.class);

        assertThat(orderMapper.selectById(order.getId()).statusEnum())
                .isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("Scenario: 状态变更留痕 — 记录前后状态、时间与触发者，运营可查完整记录")
    void 状态变更留痕() {
        ServiceOrder order = create("wxid_abc", null, 1);
        orderService.confirmPayment(order.getId(), 1500, operatorId, null);
        orderService.accept(order.getId(), talentUserId);
        orderService.complete(order.getId(), talentUserId);

        List<OrderStatusLog> history = stateService.history(order.getId());

        assertThat(history).hasSize(4);
        assertThat(history).extracting(OrderStatusLog::getToStatus)
                .containsExactly("PENDING_PAYMENT", "PENDING_ACCEPT", "ACCEPTED", "COMPLETED");
        assertThat(history).allSatisfy(e -> {
            assertThat(e.getOccurredAt()).isNotNull();
            assertThat(e.getOperatorType()).isNotBlank();
        });
        assertThat(history.get(1).getFromStatus()).isEqualTo("PENDING_PAYMENT");
    }

    // ===== Requirement: 主播接单 =====

    @Test
    @DisplayName("Scenario: 主播接单成功 — 状态变更并记录接单时间")
    void 主播接单成功() {
        ServiceOrder order = paid();

        ServiceOrder accepted = orderService.accept(order.getId(), talentUserId);

        assertThat(accepted.statusEnum()).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(accepted.getAcceptedAt()).isNotNull();
    }

    @Test
    @DisplayName("Scenario: 他人尝试接单 — 无权限")
    void 他人接单被拒() {
        ServiceOrder order = paid();

        assertThatThrownBy(() -> orderService.accept(order.getId(), otherFanId))
                .isInstanceOf(ForbiddenException.class);

        assertThat(orderMapper.selectById(order.getId()).statusEnum())
                .isEqualTo(OrderStatus.PENDING_ACCEPT);
    }

    @Test
    @DisplayName("Scenario: 对已超时订单接单 — 拒绝并提示已超时")
    void 对已超时订单接单被拒() {
        ServiceOrder order = paid();
        stateService.transition(order, OrderStatus.TIMEOUT,
                OrderOperatorType.SYSTEM, null, "接单超时", null);

        assertThatThrownBy(() -> orderService.accept(order.getId(), talentUserId))
                .isInstanceOf(BusinessException.class);
    }

    // ===== Requirement: 标记服务完成 =====

    @Test
    @DisplayName("Scenario: 主播标记完成 — 记录完成时间")
    void 主播标记完成() {
        ServiceOrder order = accepted();

        ServiceOrder done = orderService.complete(order.getId(), talentUserId);

        assertThat(done.statusEnum()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(done.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("Scenario: 粉丝侧查看已完成订单 — 展示内容、金额、主播与完成时间")
    void 粉丝查看已完成订单() {
        ServiceOrder order = accepted();
        orderService.complete(order.getId(), talentUserId);

        ServiceOrder stored = orderMapper.selectById(order.getId());
        assertThat(stored.getServiceItemName()).isEqualTo("情感倾听");
        assertThat(stored.getServiceSpecName()).isEqualTo("30分钟");
        assertThat(stored.getPayableCents()).isEqualTo(1500);
        assertThat(stored.getTalentId()).isEqualTo(talentId);
        assertThat(stored.getCompletedAt()).isNotNull();
    }

    // ===== Requirement: 运营人工取消与退款 =====

    @Test
    @DisplayName("Scenario: 运营处理超时订单退款 — 记录操作者、时间与原因")
    void 运营处理超时退款() {
        ServiceOrder order = paid();
        stateService.transition(order, OrderStatus.TIMEOUT,
                OrderOperatorType.SYSTEM, null, "接单超时", null);

        ServiceOrder refunded = orderService.markRefunded(order.getId(), operatorId, "已线下退款");

        assertThat(refunded.statusEnum()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(refunded.getCloseReason()).isEqualTo("已线下退款");

        OrderStatusLog last = lastLog(order.getId());
        assertThat(last.getOperatorType()).isEqualTo("OPERATOR");
        assertThat(last.getOperatorId()).isEqualTo(operatorId);
        assertThat(last.getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("Scenario: 退款操作幂等 — 重复标记不重复变更也不重复留痕")
    void 退款幂等() {
        ServiceOrder order = paid();
        stateService.transition(order, OrderStatus.TIMEOUT,
                OrderOperatorType.SYSTEM, null, null, null);

        orderService.markRefunded(order.getId(), operatorId, "已线下退款");
        int afterFirst = stateService.history(order.getId()).size();

        orderService.markRefunded(order.getId(), operatorId, "已线下退款");

        assertThat(stateService.history(order.getId())).hasSize(afterFirst);
        assertThat(orderMapper.selectById(order.getId()).statusEnum())
                .isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    @DisplayName("Scenario: 取消需填写原因 — 未填写则拒绝")
    void 取消需填原因() {
        ServiceOrder order = create("wxid_abc", null, 1);

        assertThatThrownBy(() -> orderService.cancel(order.getId(), operatorId, "  "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("原因");

        assertThat(orderMapper.selectById(order.getId()).statusEnum())
                .isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    // ===== 快照不随配置漂移 =====

    @Test
    @DisplayName("下单后调价与调分成，订单金额与分账均不变")
    void 订单快照不随配置漂移() {
        ServiceOrder order = create("wxid_abc", null, 1);
        long payable = order.getPayableCents();
        long guild = order.getGuildCents();
        long talentCents = order.getTalentCents();

        priceService.setPrice(talentId, specId, 9900, true);
        commissionService.setOverride(talentId, 6000);

        ServiceOrder stored = orderMapper.selectById(order.getId());
        assertThat(stored.getPayableCents()).isEqualTo(payable);
        assertThat(stored.getGuildCents()).isEqualTo(guild);
        assertThat(stored.getTalentCents()).isEqualTo(talentCents);
        assertThat(stored.getGuildCents() + stored.getTalentCents()).isEqualTo(payable);
    }

    @Test
    @DisplayName("档位停用后既有订单不受影响，但无法再下新单")
    void 停用档位不影响既有订单() {
        ServiceOrder order = create("wxid_abc", null, 1);

        priceService.setEnabled(talentId, specId, false);

        assertThat(orderMapper.selectById(order.getId()).getPayableCents()).isEqualTo(1500);
        assertThatThrownBy(() -> create("wxid_abc", null, 1))
                .isInstanceOf(PricingException.class);
    }

    // ---------- 辅助 ----------

    private ServiceOrder create(String contact, String remark, int quantity) {
        return orderService.create(new OrderCreateCommand(
                fanId, talentId, specId, quantity, contact, remark));
    }

    private ServiceOrder paid() {
        ServiceOrder order = create("wxid_abc", null, 1);
        return orderService.confirmPayment(order.getId(), 1500, operatorId, null);
    }

    private ServiceOrder accepted() {
        ServiceOrder order = paid();
        return orderService.accept(order.getId(), talentUserId);
    }

    private OrderStatusLog lastLog(long orderId) {
        List<OrderStatusLog> history = stateService.history(orderId);
        return history.get(history.size() - 1);
    }

    private long countOrders() {
        return orderMapper.selectCount(com.baomidou.mybatisplus.core.toolkit.Wrappers.emptyWrapper());
    }

    private AppUser newUser(String prefix) {
        return userService.registerOrUpdate(prefix + System.nanoTime(), null, prefix, null);
    }
}
