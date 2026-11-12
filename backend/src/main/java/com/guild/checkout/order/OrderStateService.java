package com.guild.checkout.order;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * 订单状态推进的唯一入口。
 *
 * 所有状态变更都必须经过这里，由它统一做三件事：
 *   1. 校验跃迁合法（非法跃迁一律拒绝，状态保持不变）；
 *   2. 以乐观锁写回（接单与超时并发时只有一方生效）；
 *   3. 记录状态变更日志（前后状态、时间、触发者）。
 *
 * 任何绕过本服务直接改 status 的代码都会丢掉留痕与并发保护。
 */
@Service
public class OrderStateService {

    private static final Logger log = LoggerFactory.getLogger(OrderStateService.class);

    private final ServiceOrderMapper orderMapper;
    private final OrderStatusLogMapper logMapper;

    public OrderStateService(ServiceOrderMapper orderMapper, OrderStatusLogMapper logMapper) {
        this.orderMapper = orderMapper;
        this.logMapper = logMapper;
    }

    /** 状态推进失败：非法跃迁，或并发下已被别人改掉。 */
    public static class TransitionRejected extends BusinessException {
        public TransitionRejected(String message) {
            super(message);
        }
    }

    /**
     * 推进订单状态。
     *
     * @param order        当前读到的订单（其 version 用于乐观锁）
     * @param target       目标状态
     * @param operatorType 触发者类型
     * @param operatorId   触发者 app_user.id，系统触发传 null
     * @param reason       变更原因，可为空
     * @param mutator      在写回前对订单做的附加修改，如写入 acceptedAt
     * @throws TransitionRejected 跃迁非法，或乐观锁失败
     */
    @Transactional
    public ServiceOrder transition(ServiceOrder order,
                                   OrderStatus target,
                                   OrderOperatorType operatorType,
                                   Long operatorId,
                                   String reason,
                                   Consumer<ServiceOrder> mutator) {
        OrderStatus from = order.statusEnum();
        if (!from.canTransitionTo(target)) {
            throw new TransitionRejected(
                    "订单当前为%s，不能变更为%s".formatted(from.label(), target.label()));
        }

        order.setStatusEnum(target);
        if (target.isTerminal() && order.getClosedAt() == null) {
            order.setClosedAt(LocalDateTime.now());
        }
        if (reason != null && !reason.isBlank()) {
            order.setCloseReason(reason);
        }
        if (mutator != null) {
            mutator.accept(order);
        }

        // updateById 带 @Version 会自动加 version 条件；返回 0 说明这行已被别人改过。
        int affected = orderMapper.updateById(order);
        if (affected == 0) {
            throw new TransitionRejected("订单状态已被其他操作变更，请刷新后重试");
        }

        writeLog(order, from, target, operatorType, operatorId, reason);
        log.info("订单 {} 状态 {} → {}，触发者 {}",
                order.getOrderRef(), from, target, operatorType);
        return order;
    }

    /** 创建订单时的首条日志：没有前一状态。 */
    @Transactional
    public void recordCreation(ServiceOrder order, Long operatorId) {
        writeLog(order, null, order.statusEnum(), OrderOperatorType.FAN, operatorId, "创建订单");
    }

    /** 某订单的完整状态轨迹，按发生顺序。 */
    public List<OrderStatusLog> history(long orderId) {
        return logMapper.selectList(Wrappers.<OrderStatusLog>lambdaQuery()
                .eq(OrderStatusLog::getOrderId, orderId)
                .orderByAsc(OrderStatusLog::getId));
    }

    private void writeLog(ServiceOrder order, OrderStatus from, OrderStatus to,
                          OrderOperatorType operatorType, Long operatorId, String reason) {
        OrderStatusLog entry = new OrderStatusLog();
        entry.setOrderId(order.getId());
        entry.setOrderRef(order.getOrderRef());
        entry.setFromStatus(from == null ? null : from.name());
        entry.setToStatus(to.name());
        entry.setOperatorType(operatorType.name());
        entry.setOperatorId(operatorId);
        entry.setReason(reason);
        entry.setOccurredAt(LocalDateTime.now());
        logMapper.insert(entry);
    }
}
