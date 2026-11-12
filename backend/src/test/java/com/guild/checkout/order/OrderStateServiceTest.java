package com.guild.checkout.order;

import com.guild.checkout.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 状态推进与留痕（tasks 5.3 / 5.4）。
 */
@SpringBootTest
@Transactional
class OrderStateServiceTest {

    @Autowired private OrderStateService stateService;
    @Autowired private ServiceOrderMapper orderMapper;
    @Autowired private OrderRefGenerator refGenerator;

    private ServiceOrder order;

    @BeforeEach
    void setUp() {
        order = newOrder();
    }

    // ---------- 正常流程 ----------

    @Test
    void 走完全流程后日志条数与变更次数一致() {
        stateService.recordCreation(order, 100L);

        stateService.transition(order, OrderStatus.PENDING_CONFIRM,
                OrderOperatorType.FAN, 100L, "上传凭证", null);
        stateService.transition(order, OrderStatus.PENDING_ACCEPT,
                OrderOperatorType.OPERATOR, 200L, "确认收款",
                o -> o.setPaidAt(LocalDateTime.now()));
        stateService.transition(order, OrderStatus.ACCEPTED,
                OrderOperatorType.TALENT, 300L, null,
                o -> o.setAcceptedAt(LocalDateTime.now()));
        stateService.transition(order, OrderStatus.COMPLETED,
                OrderOperatorType.TALENT, 300L, null,
                o -> o.setCompletedAt(LocalDateTime.now()));

        List<OrderStatusLog> history = stateService.history(order.getId());

        // 1 条创建 + 4 次变更 = 5 条
        assertThat(history).hasSize(5);
        assertThat(history).extracting(OrderStatusLog::getToStatus).containsExactly(
                "PENDING_PAYMENT", "PENDING_CONFIRM", "PENDING_ACCEPT", "ACCEPTED", "COMPLETED");
        assertThat(history.get(0).getFromStatus()).isNull();
        assertThat(history.get(1).getFromStatus()).isEqualTo("PENDING_PAYMENT");

        ServiceOrder reloaded = orderMapper.selectById(order.getId());
        assertThat(reloaded.statusEnum()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(reloaded.getPaidAt()).isNotNull();
        assertThat(reloaded.getAcceptedAt()).isNotNull();
        assertThat(reloaded.getCompletedAt()).isNotNull();
    }

    @Test
    void 日志记录前后状态发生时间与触发者() {
        stateService.transition(order, OrderStatus.PENDING_CONFIRM,
                OrderOperatorType.FAN, 100L, "上传凭证", null);

        OrderStatusLog entry = stateService.history(order.getId()).get(0);
        assertThat(entry.getFromStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(entry.getToStatus()).isEqualTo("PENDING_CONFIRM");
        assertThat(entry.getOperatorType()).isEqualTo("FAN");
        assertThat(entry.getOperatorId()).isEqualTo(100L);
        assertThat(entry.getReason()).isEqualTo("上传凭证");
        assertThat(entry.getOccurredAt()).isNotNull();
        assertThat(entry.getOrderRef()).isEqualTo(order.getOrderRef());
    }

    @Test
    void 系统触发的变更不带操作者id() {
        stateService.transition(order, OrderStatus.PENDING_ACCEPT,
                OrderOperatorType.OPERATOR, 200L, null, null);
        stateService.transition(order, OrderStatus.TIMEOUT,
                OrderOperatorType.SYSTEM, null, "接单超时", null);

        OrderStatusLog last = lastLog();
        assertThat(last.getOperatorType()).isEqualTo("SYSTEM");
        assertThat(last.getOperatorId()).isNull();
        assertThat(last.getReason()).isEqualTo("接单超时");
    }

    // ---------- 非法跃迁 ----------

    @Test
    void 非法跃迁被拒绝且状态与日志都不变() {
        assertThatThrownBy(() -> stateService.transition(order, OrderStatus.COMPLETED,
                OrderOperatorType.TALENT, 300L, null, null))
                .isInstanceOf(OrderStateService.TransitionRejected.class)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("待支付")
                .hasMessageContaining("已完成");

        ServiceOrder reloaded = orderMapper.selectById(order.getId());
        assertThat(reloaded.statusEnum()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(stateService.history(order.getId())).isEmpty();
    }

    @Test
    void 对已完成订单接单被拒绝() {
        advanceTo(OrderStatus.COMPLETED);

        assertThatThrownBy(() -> stateService.transition(order, OrderStatus.ACCEPTED,
                OrderOperatorType.TALENT, 300L, null, null))
                .isInstanceOf(OrderStateService.TransitionRejected.class);

        assertThat(orderMapper.selectById(order.getId()).statusEnum())
                .isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void 终止态不可复活() {
        stateService.transition(order, OrderStatus.CANCELLED,
                OrderOperatorType.OPERATOR, 200L, "运营取消", null);

        for (OrderStatus target : List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PENDING_ACCEPT,
                OrderStatus.ACCEPTED, OrderStatus.COMPLETED)) {
            assertThatThrownBy(() -> stateService.transition(order, target,
                    OrderOperatorType.OPERATOR, 200L, null, null))
                    .isInstanceOf(OrderStateService.TransitionRejected.class);
        }
    }

    // ---------- 终止态与原因 ----------

    @Test
    void 进入终止态自动记录关闭时间与原因() {
        advanceTo(OrderStatus.PENDING_ACCEPT);
        stateService.transition(order, OrderStatus.TIMEOUT,
                OrderOperatorType.SYSTEM, null, "超过接单时限", null);

        ServiceOrder reloaded = orderMapper.selectById(order.getId());
        assertThat(reloaded.getClosedAt()).isNotNull();
        assertThat(reloaded.getCloseReason()).isEqualTo("超过接单时限");
    }

    @Test
    void 非终止态的变更不写关闭时间() {
        stateService.transition(order, OrderStatus.PENDING_CONFIRM,
                OrderOperatorType.FAN, 100L, null, null);

        assertThat(orderMapper.selectById(order.getId()).getClosedAt()).isNull();
    }

    // ---------- 并发 ----------

    @Test
    void 乐观锁保证接单与超时只有一方生效() {
        advanceTo(OrderStatus.PENDING_ACCEPT);

        // 另一个进程在接单发生之前读到的那一版。不能用 selectById 再读一次：
        // MyBatis 的一级缓存会在同一事务内返回同一个实例，根本构不成并发。
        ServiceOrder stale = staleCopyOf(order);

        // 主播先接单成功，version 自增
        stateService.transition(order, OrderStatus.ACCEPTED,
                OrderOperatorType.TALENT, 300L, null, null);

        // 超时处置拿着旧版本写回，必须被乐观锁挡下
        assertThatThrownBy(() -> stateService.transition(stale, OrderStatus.TIMEOUT,
                OrderOperatorType.SYSTEM, null, "接单超时", null))
                .isInstanceOf(OrderStateService.TransitionRejected.class)
                .hasMessageContaining("已被其他操作变更");

        assertThat(orderMapper.selectById(order.getId()).statusEnum())
                .isEqualTo(OrderStatus.ACCEPTED);
    }

    @Test
    void 乐观锁失败时不留下误导性的日志() {
        advanceTo(OrderStatus.PENDING_ACCEPT);
        ServiceOrder stale = staleCopyOf(order);

        stateService.transition(order, OrderStatus.ACCEPTED, OrderOperatorType.TALENT, 300L, null, null);
        int logsAfterAccept = stateService.history(order.getId()).size();

        assertThatThrownBy(() -> stateService.transition(stale, OrderStatus.TIMEOUT,
                OrderOperatorType.SYSTEM, null, null, null))
                .isInstanceOf(OrderStateService.TransitionRejected.class);

        // 失败的那次不应该留下日志
        assertThat(stateService.history(order.getId())).hasSize(logsAfterAccept);
    }

    @Test
    void 超时先发生时接单方被挡下() {
        advanceTo(OrderStatus.PENDING_ACCEPT);
        ServiceOrder stale = staleCopyOf(order);

        // 这回让超时先成功
        stateService.transition(order, OrderStatus.TIMEOUT,
                OrderOperatorType.SYSTEM, null, "接单超时", null);

        assertThatThrownBy(() -> stateService.transition(stale, OrderStatus.ACCEPTED,
                OrderOperatorType.TALENT, 300L, null, null))
                .isInstanceOf(OrderStateService.TransitionRejected.class);

        assertThat(orderMapper.selectById(order.getId()).statusEnum())
                .isEqualTo(OrderStatus.TIMEOUT);
    }

    // ---------- 辅助 ----------

    private void advanceTo(OrderStatus target) {
        List<OrderStatus> path = switch (target) {
            case PENDING_ACCEPT -> List.of(OrderStatus.PENDING_ACCEPT);
            case ACCEPTED -> List.of(OrderStatus.PENDING_ACCEPT, OrderStatus.ACCEPTED);
            case COMPLETED -> List.of(OrderStatus.PENDING_ACCEPT, OrderStatus.ACCEPTED, OrderStatus.COMPLETED);
            default -> throw new IllegalArgumentException("不支持: " + target);
        };
        for (OrderStatus step : path) {
            stateService.transition(order, step, OrderOperatorType.OPERATOR, 200L, null, null);
        }
    }

    /** 复制出一份与当前库中版本相同的订单对象，模拟另一进程早先读到的那一版。 */
    private ServiceOrder staleCopyOf(ServiceOrder source) {
        ServiceOrder copy = new ServiceOrder();
        copy.setId(source.getId());
        copy.setOrderRef(source.getOrderRef());
        copy.setStatus(source.getStatus());
        copy.setVersion(source.getVersion());
        copy.setPayableCents(source.getPayableCents());
        copy.setQuantity(source.getQuantity());
        return copy;
    }

    private OrderStatusLog lastLog() {
        List<OrderStatusLog> history = stateService.history(order.getId());
        return history.get(history.size() - 1);
    }

    private ServiceOrder newOrder() {
        ServiceOrder o = new ServiceOrder();
        o.setOrderRef(refGenerator.next());
        o.setFanUserId(100L);
        o.setTalentId(1L);
        o.setServiceItemId(1L);
        o.setServiceSpecId(1L);
        o.setServiceItemName("情感倾听");
        o.setServiceSpecName("30分钟");
        o.setUnitPriceCents(1500L);
        o.setQuantity(1);
        o.setPayableCents(1500L);
        o.setCommissionRateBp(3000);
        o.setGuildCents(450L);
        o.setTalentCents(1050L);
        o.setStatusEnum(OrderStatus.PENDING_PAYMENT);
        o.setContact("wxid_test");
        o.setPayDeadline(LocalDateTime.now().plusMinutes(30));
        orderMapper.insert(o);
        return o;
    }
}
