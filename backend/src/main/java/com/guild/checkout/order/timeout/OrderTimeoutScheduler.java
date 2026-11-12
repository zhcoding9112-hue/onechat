package com.guild.checkout.order.timeout;

import com.guild.checkout.order.ServiceOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 超时调度。
 *
 * 两层机制，缺一不可（design D4）：
 *   1. 延时队列每 10 秒取一次到期成员 —— 精度到秒级，接单超时直接影响粉丝体验。
 *   2. 兜底扫表每 5 分钟捞一次 —— Redis 重启或入队失败时，订单不会永久卡在等待态。
 *      只靠队列的话，最糟的故障形态是"用户付了钱、系统再不管它"。
 *
 * 处置本身是幂等的：同一订单被两层机制同时捞到，乐观锁保证只有一次生效。
 */
@Component
@ConditionalOnProperty(name = "order.timeout.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class OrderTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutScheduler.class);

    /** 单轮处置上限，避免积压时一次占满事务。 */
    private static final int BATCH = 200;

    private final DelayQueue payQueue;
    private final DelayQueue acceptQueue;
    private final OrderTimeoutService timeoutService;

    public OrderTimeoutScheduler(@Qualifier("payTimeoutQueue") DelayQueue payQueue,
                                 @Qualifier("acceptTimeoutQueue") DelayQueue acceptQueue,
                                 OrderTimeoutService timeoutService) {
        this.payQueue = payQueue;
        this.acceptQueue = acceptQueue;
        this.timeoutService = timeoutService;
    }

    /** 延时队列：每 10 秒一轮。 */
    @Scheduled(fixedDelayString = "${order.timeout.queue-interval-ms:10000}")
    public void drainQueues() {
        drain(payQueue, true);
        drain(acceptQueue, false);
    }

    /** 兜底扫表：每 5 分钟一轮。 */
    @Scheduled(fixedDelayString = "${order.timeout.scan-interval-ms:300000}")
    public void scanOverdue() {
        int handled = 0;
        for (ServiceOrder order : timeoutService.overduePayments(BATCH)) {
            if (timeoutService.expirePayment(order.getId()) == OrderTimeoutService.Result.HANDLED) {
                handled++;
            }
        }
        for (ServiceOrder order : timeoutService.overdueAccepts(BATCH)) {
            if (timeoutService.expireAccept(order.getId()) == OrderTimeoutService.Result.HANDLED) {
                handled++;
            }
        }
        if (handled > 0) {
            // 这条日志出现就说明队列漏了，值得查
            log.warn("兜底扫表处置了 {} 笔超时订单，说明延时队列有遗漏", handled);
        }
    }

    private void drain(DelayQueue queue, boolean payment) {
        Set<Long> due = queue.pollDue(BATCH);
        for (Long orderId : due) {
            try {
                if (payment) {
                    timeoutService.expirePayment(orderId);
                } else {
                    timeoutService.expireAccept(orderId);
                }
            } catch (RuntimeException ex) {
                // 单笔失败不能让整轮停摆；兜底扫表会再捞一次
                log.error("超时处置订单 {} 失败", orderId, ex);
            }
        }
    }
}
