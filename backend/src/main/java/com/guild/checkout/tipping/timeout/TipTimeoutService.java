package com.guild.checkout.tipping.timeout;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.tipping.TipRecord;
import com.guild.checkout.tipping.TipRecordMapper;
import com.guild.checkout.tipping.TipService;
import com.guild.checkout.tipping.TipStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 打赏的支付超时回收。
 *
 * 打赏一直没有这个东西：创建时写了 payDeadline，到点却没有任何代码去关它，
 * 粉丝发起又不付款的记录会永远挂在运营后台的待处理队列里。
 *
 * 与订单的差别：
 *   - 只有一种超时。打赏没有接单环节，确认收款后直接计为已完成。
 *   - 没有延时队列，只有兜底扫表。打赏超时不影响任何人的体验——没人在等它，
 *     晚几分钟关掉没有代价，不值得为它再引一套 Redis 延时队列。
 *   - 终态是「已取消」而不是「已超时」。钱根本没收到，没有要退的东西；
 *     这与订单侧「待支付超时 → 已取消」的处理是一致的。
 */
@Service
public class TipTimeoutService {

    private static final Logger log = LoggerFactory.getLogger(TipTimeoutService.class);

    static final String TIMEOUT_REASON = "支付超时，系统自动关闭";

    private final TipRecordMapper tipMapper;
    private final TipService tipService;

    public TipTimeoutService(TipRecordMapper tipMapper, TipService tipService) {
        this.tipMapper = tipMapper;
        this.tipService = tipService;
    }

    public enum Result {
        /** 已关闭。 */
        HANDLED,
        /** 还没到期。 */
        NOT_DUE,
        /** 记录不存在，或已经不是待支付。 */
        SKIPPED,
        /** 到期瞬间被别的操作改掉了（例如运营刚好确认了收款）。 */
        RACED
    }

    /** 已过期但仍处于待支付的打赏。 */
    public List<TipRecord> overdue(int limit) {
        return tipMapper.selectList(Wrappers.<TipRecord>lambdaQuery()
                .eq(TipRecord::getStatus, TipStatus.PENDING_PAYMENT.name())
                .isNotNull(TipRecord::getPayDeadline)
                .le(TipRecord::getPayDeadline, LocalDateTime.now())
                .orderByAsc(TipRecord::getPayDeadline)
                .last("limit " + limit));
    }

    /**
     * 关掉一笔超时的打赏。
     *
     * 已上传凭证（待确认收款）的不关——与订单侧同一条规则：人已经把钱付了，
     * 只是还没对上账，不能因为时限到了就把它关掉。
     */
    public Result expire(long tipId) {
        TipRecord tip = tipMapper.selectById(tipId);
        if (tip == null || tip.statusEnum() != TipStatus.PENDING_PAYMENT) {
            return Result.SKIPPED;
        }
        if (tip.getPayDeadline() == null || tip.getPayDeadline().isAfter(LocalDateTime.now())) {
            return Result.NOT_DUE;
        }

        try {
            tipService.cancel(tipId, TIMEOUT_REASON);
        } catch (BusinessException ex) {
            // 最常见的情形：运营刚好在这一瞬间确认了收款
            log.info("打赏 {} 超时处置未生效，状态已被其他操作改变: {}",
                    tip.getTipRef(), ex.getMessage());
            return Result.RACED;
        }
        log.info("打赏 {} 支付超时，已自动关闭", tip.getTipRef());
        return Result.HANDLED;
    }
}
