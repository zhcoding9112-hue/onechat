package com.guild.checkout.tipping.timeout;

import com.guild.checkout.tipping.TipRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 打赏超时扫表。
 *
 * 只有扫表、没有延时队列，理由见 {@link TipTimeoutService} 的类注释。
 * 开关与订单侧分开，便于单独在测试里关掉。
 */
@Component
@ConditionalOnProperty(name = "tip.timeout.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class TipTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(TipTimeoutScheduler.class);

    /** 单轮处置上限，避免积压时一次占满事务。 */
    private static final int BATCH = 200;

    private final TipTimeoutService timeoutService;

    public TipTimeoutScheduler(TipTimeoutService timeoutService) {
        this.timeoutService = timeoutService;
    }

    @Scheduled(fixedDelayString = "${tip.timeout.scan-interval-ms:60000}")
    public void scanOverdue() {
        int handled = 0;
        for (TipRecord tip : timeoutService.overdue(BATCH)) {
            if (timeoutService.expire(tip.getId()) == TipTimeoutService.Result.HANDLED) {
                handled++;
            }
        }
        if (handled > 0) {
            log.info("关闭了 {} 笔超时未付款的打赏", handled);
        }
    }
}
