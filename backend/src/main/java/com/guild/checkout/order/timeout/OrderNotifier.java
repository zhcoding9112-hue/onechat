package com.guild.checkout.order.timeout;

import com.guild.checkout.order.ServiceOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 订单相关的通知。
 *
 * 上线期先落日志：服务号模板消息需要已认证服务号与模板审批，而那两项还在办。
 * 通知的触发点与内容在这里定好，接入模板消息时只改这个类的实现，
 * 超时处置与订单流转都不需要动。
 */
@Component
public class OrderNotifier {

    private static final Logger log = LoggerFactory.getLogger(OrderNotifier.class);

    /** 待接单：提醒主播去接。 */
    public void notifyPendingAccept(ServiceOrder order) {
        log.info("[通知·主播] 订单 {} 已收款，请在时限内接单，截止 {}",
                order.getOrderRef(), order.getAcceptDeadline());
    }

    /** 支付超时：告知粉丝订单已关闭。 */
    public void notifyPaymentTimeout(ServiceOrder order) {
        log.info("[通知·粉丝] 订单 {} 因超时未支付已自动取消", order.getOrderRef());
    }

    /** 接单超时：双方都要通知，粉丝还要告知将安排退款。 */
    public void notifyAcceptTimeout(ServiceOrder order) {
        log.info("[通知·粉丝] 订单 {} 未被接单，我们将为你安排退款", order.getOrderRef());
        log.info("[通知·主播] 订单 {} 因超时未接单已关闭", order.getOrderRef());
    }
}
