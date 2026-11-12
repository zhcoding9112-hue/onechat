package com.guild.checkout.order.timeout;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.order.OrderOperatorType;
import com.guild.checkout.order.OrderStateService;
import com.guild.checkout.order.OrderStatus;
import com.guild.checkout.order.ServiceOrder;
import com.guild.checkout.order.ServiceOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时处置。
 *
 * 两条规则：
 *   - 待支付超期且未上传任何凭证 → 已取消，原因「支付超时」。
 *     已上传凭证的订单处于待确认收款，不受此规则影响：人可能已经付了钱，
 *     不能因为运营没及时对账就把单关掉。
 *   - 待接单超期 → 已超时，通知双方并进入人工退款队列。
 *
 * 每一笔处置都经过 {@link OrderStateService}，因而自带乐观锁与留痕：
 * 「主播接单」与「超时处置」并发时只有一方生效。
 */
@Service
public class OrderTimeoutService {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutService.class);

    public static final String PAY_TIMEOUT_REASON = "支付超时";
    public static final String ACCEPT_TIMEOUT_REASON = "主播未在时限内接单";

    private final ServiceOrderMapper orderMapper;
    private final OrderStateService stateService;
    private final OrderNotifier notifier;

    public OrderTimeoutService(ServiceOrderMapper orderMapper,
                               OrderStateService stateService,
                               OrderNotifier notifier) {
        this.orderMapper = orderMapper;
        this.stateService = stateService;
        this.notifier = notifier;
    }

    /** 处置结果。 */
    public enum Result {
        /** 已处置。 */
        HANDLED,
        /** 订单已不在该等待态，无需处置。 */
        SKIPPED,
        /** 尚未到期。 */
        NOT_DUE,
        /** 并发下被别人改掉了。 */
        RACED
    }

    /**
     * 处置一笔可能已超时的待支付订单。
     *
     * @return 处置结果
     */
    @Transactional
    public Result expirePayment(long orderId) {
        ServiceOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return Result.SKIPPED;
        }
        // 已上传凭证（待确认收款）的订单不被关闭——这是 payment 规格明确要求的
        if (order.statusEnum() != OrderStatus.PENDING_PAYMENT) {
            return Result.SKIPPED;
        }
        if (notDue(order.getPayDeadline())) {
            return Result.NOT_DUE;
        }

        try {
            stateService.transition(order, OrderStatus.CANCELLED,
                    OrderOperatorType.SYSTEM, null, PAY_TIMEOUT_REASON, null);
        } catch (OrderStateService.TransitionRejected ex) {
            log.info("待支付超时处置未生效，订单 {} 已被其他操作改变: {}",
                    order.getOrderRef(), ex.getMessage());
            return Result.RACED;
        }
        notifier.notifyPaymentTimeout(order);
        log.info("订单 {} 支付超时，已自动取消", order.getOrderRef());
        return Result.HANDLED;
    }

    /**
     * 处置一笔可能已超时的待接单订单。
     * 钱已到账，因此必须通知双方并进入人工退款队列。
     */
    @Transactional
    public Result expireAccept(long orderId) {
        ServiceOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return Result.SKIPPED;
        }
        if (order.statusEnum() != OrderStatus.PENDING_ACCEPT) {
            return Result.SKIPPED;
        }
        if (notDue(order.getAcceptDeadline())) {
            return Result.NOT_DUE;
        }

        try {
            stateService.transition(order, OrderStatus.TIMEOUT,
                    OrderOperatorType.SYSTEM, null, ACCEPT_TIMEOUT_REASON, null);
        } catch (OrderStateService.TransitionRejected ex) {
            // 最常见的情形：主播刚好在这一瞬间接了单
            log.info("接单超时处置未生效，订单 {} 已被其他操作改变: {}",
                    order.getOrderRef(), ex.getMessage());
            return Result.RACED;
        }
        notifier.notifyAcceptTimeout(order);
        log.info("订单 {} 接单超时，已置为已超时并进入待退款队列", order.getOrderRef());
        return Result.HANDLED;
    }

    /** 已过期但仍处于待支付的订单，供兜底扫表使用。 */
    public List<ServiceOrder> overduePayments(int limit) {
        return orderMapper.selectList(Wrappers.<ServiceOrder>lambdaQuery()
                .eq(ServiceOrder::getStatus, OrderStatus.PENDING_PAYMENT.name())
                .isNotNull(ServiceOrder::getPayDeadline)
                .le(ServiceOrder::getPayDeadline, LocalDateTime.now())
                .orderByAsc(ServiceOrder::getPayDeadline)
                .last("limit " + limit));
    }

    /** 已过期但仍处于待接单的订单，供兜底扫表使用。 */
    public List<ServiceOrder> overdueAccepts(int limit) {
        return orderMapper.selectList(Wrappers.<ServiceOrder>lambdaQuery()
                .eq(ServiceOrder::getStatus, OrderStatus.PENDING_ACCEPT.name())
                .isNotNull(ServiceOrder::getAcceptDeadline)
                .le(ServiceOrder::getAcceptDeadline, LocalDateTime.now())
                .orderByAsc(ServiceOrder::getAcceptDeadline)
                .last("limit " + limit));
    }

    private static boolean notDue(LocalDateTime deadline) {
        return deadline == null || LocalDateTime.now().isBefore(deadline);
    }
}
