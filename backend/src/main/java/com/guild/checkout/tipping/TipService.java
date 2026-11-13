package com.guild.checkout.tipping;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.common.exception.ForbiddenException;
import com.guild.checkout.common.exception.NotFoundException;
import com.guild.checkout.order.OrderDeadlineProvider;
import com.guild.checkout.order.OrderRefGenerator;
import com.guild.checkout.pricing.CommissionCalculator;
import com.guild.checkout.pricing.CommissionSplit;
import com.guild.checkout.pricing.PricingService;
import com.guild.checkout.settlement.SettlementEntry;
import com.guild.checkout.settlement.SettlementService;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.TalentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 打赏。
 *
 * 复用订单侧的两样东西：分成计算（同一套取整规则）与支付链路（同一个收款事件）。
 * 但不复用订单状态机——打赏没有接单、完成与超时，支付确认后直接计为已完成。
 */
@Service
public class TipService {

    private static final Logger log = LoggerFactory.getLogger(TipService.class);

    private static final int MAX_MESSAGE_LENGTH = 200;
    private static final int REF_RETRY = 5;

    private final TipRecordMapper tipMapper;
    private final TipSettingMapper settingMapper;
    private final OrderRefGenerator refGenerator;
    private final PricingService pricingService;
    private final TalentService talentService;
    private final OrderDeadlineProvider deadlines;
    private final SettlementService settlementService;

    public TipService(TipRecordMapper tipMapper,
                      TipSettingMapper settingMapper,
                      OrderRefGenerator refGenerator,
                      PricingService pricingService,
                      TalentService talentService,
                      OrderDeadlineProvider deadlines,
                      SettlementService settlementService) {
        this.tipMapper = tipMapper;
        this.settingMapper = settingMapper;
        this.refGenerator = refGenerator;
        this.pricingService = pricingService;
        this.talentService = talentService;
        this.deadlines = deadlines;
        this.settlementService = settlementService;
    }

    // ---------- 创建 ----------

    /**
     * 发起打赏。
     *
     * 分成比例在此刻快照固定，与服务订单同一套规则：此后改比例不影响这一笔。
     */
    @Transactional
    public TipRecord create(long fanUserId, long talentId, long amountCents, String message) {
        TipSetting setting = requireSetting();
        if (amountCents < setting.getMinCents() || amountCents > setting.getMaxCents()) {
            throw new BusinessException("打赏金额需在 %s 到 %s 元之间".formatted(
                    yuan(setting.getMinCents()), yuan(setting.getMaxCents())));
        }

        Talent talent = talentService.find(talentId);
        if (talent == null) {
            throw new NotFoundException("主播不存在");
        }
        if (!Boolean.TRUE.equals(talent.getListed())) {
            throw new BusinessException("该主播暂不可打赏");
        }

        int rateBp = pricingService.resolveCommissionRateBp(talentId);
        CommissionSplit split = CommissionCalculator.split(amountCents, rateBp);

        TipRecord tip = new TipRecord();
        tip.setFanUserId(fanUserId);
        tip.setTalentId(talentId);
        tip.setAmountCents(amountCents);
        tip.setCommissionRateBp(rateBp);
        tip.setGuildCents(split.guildCents());
        tip.setTalentCents(split.talentCents());
        tip.setStatusEnum(TipStatus.PENDING_PAYMENT);
        tip.setMessage(normalizeMessage(message));
        tip.setPayDeadline(LocalDateTime.now().plusMinutes(deadlines.payMinutes()));

        insertWithUniqueRef(tip);
        log.info("打赏 {} 已创建，粉丝 {} → 主播 {}，{} 分",
                tip.getTipRef(), fanUserId, talentId, amountCents);
        return tip;
    }

    private void insertWithUniqueRef(TipRecord tip) {
        for (int attempt = 1; ; attempt++) {
            tip.setTipRef(refGenerator.next());
            try {
                tipMapper.insert(tip);
                return;
            } catch (DuplicateKeyException ex) {
                if (attempt >= REF_RETRY) {
                    throw ex;
                }
            }
        }
    }

    // ---------- 状态推进 ----------

    /**
     * 确认收款。打赏不进入待接单，直接计为已完成，也不向主播推送接单提醒。
     */
    @Transactional
    public TipRecord confirmPayment(long tipId, long paidCents, String note) {
        TipRecord tip = require(tipId);
        TipStatus status = tip.statusEnum();
        if (status != TipStatus.PENDING_PAYMENT && status != TipStatus.PENDING_CONFIRM) {
            throw new BusinessException("打赏当前为%s，不能确认收款".formatted(status.label()));
        }

        tip.setStatusEnum(TipStatus.COMPLETED);
        tip.setPaidCents(paidCents);
        tip.setPaidAt(LocalDateTime.now());
        tip.setClosedAt(LocalDateTime.now());
        if (note != null && !note.isBlank()) {
            tip.setCloseReason(note.trim());
        }
        if (tipMapper.updateById(tip) == 0) {
            throw new BusinessException("打赏状态已被其他操作变更，请刷新后重试");
        }
        settlementService.recordTip(tip);
        log.info("打赏 {} 已收款 {} 分，直接计为已完成", tip.getTipRef(), paidCents);
        return tip;
    }

    /** 标记为待确认收款（粉丝上传凭证后）。 */
    @Transactional
    public TipRecord markPendingConfirm(long tipId) {
        TipRecord tip = require(tipId);
        if (tip.statusEnum() == TipStatus.PENDING_CONFIRM) {
            return tip;
        }
        if (tip.statusEnum() != TipStatus.PENDING_PAYMENT) {
            throw new BusinessException("打赏当前为%s，不能上传凭证".formatted(tip.statusEnum().label()));
        }
        tip.setStatusEnum(TipStatus.PENDING_CONFIRM);
        tipMapper.updateById(tip);
        return tip;
    }

    @Transactional
    public TipRecord cancel(long tipId, String reason) {
        TipRecord tip = require(tipId);
        if (tip.statusEnum().isTerminal()) {
            throw new BusinessException("打赏已是%s，不能取消".formatted(tip.statusEnum().label()));
        }
        tip.setStatusEnum(TipStatus.CANCELLED);
        tip.setClosedAt(LocalDateTime.now());
        tip.setCloseReason(reason);
        tipMapper.updateById(tip);
        settlementService.reverse(SettlementEntry.SOURCE_TIP, tipId, reason);
        return tip;
    }

    // ---------- 查询 ----------

    public TipRecord require(long tipId) {
        TipRecord tip = tipMapper.selectById(tipId);
        if (tip == null) {
            throw new NotFoundException("打赏记录不存在");
        }
        return tip;
    }

    public TipRecord findByRef(String tipRef) {
        return tipMapper.selectOne(Wrappers.<TipRecord>lambdaQuery().eq(TipRecord::getTipRef, tipRef));
    }

    public List<TipRecord> listByFan(long fanUserId) {
        return tipMapper.selectList(Wrappers.<TipRecord>lambdaQuery()
                .eq(TipRecord::getFanUserId, fanUserId)
                .orderByDesc(TipRecord::getId));
    }

    /** 某主播收到的已完成打赏。留言只在这里出现。 */
    public List<TipRecord> listCompletedByTalent(long talentId) {
        return tipMapper.selectList(Wrappers.<TipRecord>lambdaQuery()
                .eq(TipRecord::getTalentId, talentId)
                .eq(TipRecord::getStatus, TipStatus.COMPLETED.name())
                .orderByDesc(TipRecord::getId));
    }

    /**
     * 打赏的可读性：打赏本人、目标主播本人，或运营。
     * 其他粉丝一律不可读——留言是私信性质的。
     */
    public void requireReadable(TipRecord tip, Long userId, boolean operator) {
        if (operator || Objects.equals(tip.getFanUserId(), userId)) {
            return;
        }
        Talent talent = talentService.find(tip.getTalentId());
        if (talent != null && Objects.equals(talent.getUserId(), userId)) {
            return;
        }
        throw new ForbiddenException("无权查看该打赏");
    }

    public boolean isTargetTalent(TipRecord tip, Long userId) {
        if (userId == null) {
            return false;
        }
        Talent talent = talentService.find(tip.getTalentId());
        return talent != null && Objects.equals(talent.getUserId(), userId);
    }

    // ---------- 配置 ----------

    public TipSetting requireSetting() {
        TipSetting setting = settingMapper.selectOne(
                Wrappers.<TipSetting>lambdaQuery().eq(TipSetting::getSingletonKey, 1));
        if (setting == null) {
            throw new BusinessException("打赏金额配置缺失，请联系技术排查");
        }
        return setting;
    }

    @Transactional
    public TipSetting updateSetting(Long minCents, Long maxCents) {
        TipSetting setting = requireSetting();
        long min = minCents == null ? setting.getMinCents() : minCents;
        long max = maxCents == null ? setting.getMaxCents() : maxCents;
        if (min <= 0) {
            throw new BusinessException("最低金额必须大于零");
        }
        if (max < min) {
            throw new BusinessException("最高金额不得低于最低金额");
        }
        setting.setMinCents(min);
        setting.setMaxCents(max);
        settingMapper.updateById(setting);
        return setting;
    }

    private String normalizeMessage(String message) {
        if (message == null) {
            return null;
        }
        String trimmed = message.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_MESSAGE_LENGTH) {
            throw new BusinessException("留言不超过 " + MAX_MESSAGE_LENGTH + " 字");
        }
        return trimmed;
    }

    private static String yuan(long cents) {
        return "%d.%02d".formatted(cents / 100, Math.abs(cents % 100));
    }
}
