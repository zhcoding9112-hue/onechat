package com.guild.checkout.settlement;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.order.ServiceOrder;
import com.guild.checkout.tipping.TipRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 分账记账。
 *
 * 记账只认来源上的快照，不读当前配置——这是 service-pricing 与 settlement 两份规格
 * 共同要求的「调价/调比例不影响历史订单」。
 *
 * 幂等靠 (source_type, source_id) 唯一索引：订单完成事件若被重放，第二次会撞唯一键
 * 并被识别为已记账，而不是记出第二笔。
 */
@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    private final SettlementEntryMapper entryMapper;
    private final SettlementMarkMapper markMapper;

    public SettlementService(SettlementEntryMapper entryMapper, SettlementMarkMapper markMapper) {
        this.entryMapper = entryMapper;
        this.markMapper = markMapper;
    }

    // ---------- 记账 ----------

    /**
     * 订单完成时记一笔分账。重复调用是幂等的。
     *
     * 金额基数取**原价**而不是实付：券的抵扣由公会承担，主播分成一分不少。
     * 抵扣额单独记在 promoCostCents 上，报表据此单列促销成本——
     * 报表只读台账不回查订单表（见本类开头），所以这个数必须在记账时就落下来。
     */
    @Transactional
    public SettlementEntry recordOrder(ServiceOrder order) {
        long promoCost = order.getCouponDiscountCents() == null ? 0 : order.getCouponDiscountCents();
        return record(SettlementEntry.SOURCE_ORDER, order.getId(), order.getOrderRef(),
                order.getTalentId(), order.getOriginalCents(), order.getCommissionRateBp(),
                order.getGuildCents(), order.getTalentCents(), promoCost,
                order.getCompletedAt() == null ? LocalDateTime.now() : order.getCompletedAt());
    }

    /** 打赏完成时记一笔分账。 */
    @Transactional
    public SettlementEntry recordTip(TipRecord tip) {
        // 打赏不参与促销，促销成本恒为 0
        return record(SettlementEntry.SOURCE_TIP, tip.getId(), tip.getTipRef(),
                tip.getTalentId(), tip.getAmountCents(), tip.getCommissionRateBp(),
                tip.getGuildCents(), tip.getTalentCents(), 0L,
                tip.getPaidAt() == null ? LocalDateTime.now() : tip.getPaidAt());
    }

    private SettlementEntry record(String sourceType, Long sourceId, String sourceRef,
                                   Long talentId, Long amountCents, Integer rateBp,
                                   Long guildCents, Long talentCents, Long promoCostCents,
                                   LocalDateTime occurredAt) {
        if (guildCents + talentCents != amountCents) {
            // 来源数据本身就不平，记进台账只会把错误固化
            throw new IllegalStateException(
                    "来源 %s/%s 的分账之和与金额不符：%d + %d != %d"
                            .formatted(sourceType, sourceRef, guildCents, talentCents, amountCents));
        }

        SettlementEntry entry = new SettlementEntry();
        entry.setSourceType(sourceType);
        entry.setSourceId(sourceId);
        entry.setSourceRef(sourceRef);
        entry.setTalentId(talentId);
        entry.setAmountCents(amountCents);
        entry.setCommissionRateBp(rateBp);
        entry.setGuildCents(guildCents);
        entry.setTalentCents(talentCents);
        entry.setPromoCostCents(promoCostCents == null ? 0L : promoCostCents);
        entry.setStatus(SettlementEntry.ACTIVE);
        entry.setOccurredAt(occurredAt);

        try {
            entryMapper.insert(entry);
            log.info("已记分账 {}/{}，主播 {} 应得 {} 分", sourceType, sourceRef, talentId, talentCents);
            return entry;
        } catch (DuplicateKeyException ex) {
            // 同一来源已记过账，返回既有记录即可
            SettlementEntry existing = findBySource(sourceType, sourceId);
            if (existing == null) {
                throw ex;
            }
            return existing;
        }
    }

    /**
     * 冲销。退款时调用，不删除记录——删掉就查不到这笔钱曾经算过账。
     * 来源从未记账时静默返回，便于调用方无脑调用。
     */
    @Transactional
    public SettlementEntry reverse(String sourceType, Long sourceId, String reason) {
        SettlementEntry entry = findBySource(sourceType, sourceId);
        if (entry == null) {
            return null;
        }
        if (!entry.isActive()) {
            return entry;
        }
        entry.setStatus(SettlementEntry.REVERSED);
        entry.setReversedAt(LocalDateTime.now());
        entry.setReverseReason(reason);
        entryMapper.updateById(entry);
        log.info("已冲销分账 {}/{}，原因：{}", sourceType, entry.getSourceRef(), reason);
        return entry;
    }

    public SettlementEntry findBySource(String sourceType, Long sourceId) {
        return entryMapper.selectOne(Wrappers.<SettlementEntry>lambdaQuery()
                .eq(SettlementEntry::getSourceType, sourceType)
                .eq(SettlementEntry::getSourceId, sourceId));
    }

    // ---------- 报表 ----------

    /** 某主播某周期的结算汇总。 */
    public record TalentStatement(
            Long talentId,
            String talentNickname,
            String period,
            long orderAmountCents,
            long tipAmountCents,
            long guildCents,
            long talentCents,
            /** 促销成本（券抵扣），由公会承担，不从主播应得里扣。 */
            long promoCostCents,
            int orderCount,
            int tipCount,
            boolean settled,
            LocalDateTime settledAt) {
    }

    /** 周期内的全部有效分账。已冲销的不计入。 */
    public List<SettlementEntry> activeEntries(String period, Long talentId) {
        YearMonth month = parsePeriod(period);
        LocalDateTime from = month.atDay(1).atStartOfDay();
        LocalDateTime to = month.plusMonths(1).atDay(1).atStartOfDay();

        var query = Wrappers.<SettlementEntry>lambdaQuery()
                .eq(SettlementEntry::getStatus, SettlementEntry.ACTIVE)
                .ge(SettlementEntry::getOccurredAt, from)
                .lt(SettlementEntry::getOccurredAt, to);
        if (talentId != null) {
            query.eq(SettlementEntry::getTalentId, talentId);
        }
        return entryMapper.selectList(query.orderByAsc(SettlementEntry::getOccurredAt));
    }

    /** 某主播某周期的全部分账，含已冲销的，供运营追溯。 */
    public List<SettlementEntry> allEntries(String period, Long talentId) {
        YearMonth month = parsePeriod(period);
        LocalDateTime from = month.atDay(1).atStartOfDay();
        LocalDateTime to = month.plusMonths(1).atDay(1).atStartOfDay();

        var query = Wrappers.<SettlementEntry>lambdaQuery()
                .ge(SettlementEntry::getOccurredAt, from)
                .lt(SettlementEntry::getOccurredAt, to);
        if (talentId != null) {
            query.eq(SettlementEntry::getTalentId, talentId);
        }
        return entryMapper.selectList(query.orderByAsc(SettlementEntry::getOccurredAt));
    }

    // ---------- 结清标记 ----------

    /**
     * 标记某周期某主播已线下结清。
     * 只记录标记与时间，不发生任何资金动作（settlement 规格明确要求）。
     */
    @Transactional
    public SettlementMark markSettled(long talentId, String period, long operatorId, String note) {
        parsePeriod(period);
        SettlementMark existing = findMark(talentId, period);
        if (existing != null) {
            return existing;
        }
        SettlementMark mark = new SettlementMark();
        mark.setTalentId(talentId);
        mark.setPeriod(period);
        mark.setSettledAt(LocalDateTime.now());
        mark.setOperatorId(operatorId);
        mark.setNote(note);
        try {
            markMapper.insert(mark);
        } catch (DuplicateKeyException ex) {
            return findMark(talentId, period);
        }
        log.info("主播 {} 的 {} 周期已标记线下结清，操作者 {}", talentId, period, operatorId);
        return mark;
    }

    /** 撤销结清标记。标错了要能改回来。 */
    @Transactional
    public void unmarkSettled(long talentId, String period) {
        SettlementMark mark = findMark(talentId, period);
        if (mark != null) {
            markMapper.deleteById(mark.getId());
        }
    }

    public SettlementMark findMark(long talentId, String period) {
        return markMapper.selectOne(Wrappers.<SettlementMark>lambdaQuery()
                .eq(SettlementMark::getTalentId, talentId)
                .eq(SettlementMark::getPeriod, period));
    }

    public List<SettlementMark> marksOf(String period) {
        return markMapper.selectList(
                Wrappers.<SettlementMark>lambdaQuery().eq(SettlementMark::getPeriod, period));
    }

    // ---------- 辅助 ----------

    /** 周期格式 yyyy-MM。 */
    public static YearMonth parsePeriod(String period) {
        try {
            return YearMonth.parse(period);
        } catch (DateTimeParseException | NullPointerException ex) {
            throw new BusinessException("周期格式应为 yyyy-MM，收到：" + period);
        }
    }

    public static String periodOf(LocalDateTime time) {
        return YearMonth.from(time.toLocalDate()).toString();
    }

    public static String periodOf(LocalDate date) {
        return YearMonth.from(date).toString();
    }
}
