package com.guild.checkout.leaderboard;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.TalentService;
import com.guild.checkout.tipping.TipRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 打赏榜单。
 *
 * 归期按打赏的**收款时间**，不是按当前时间：一笔上月收款、本月退款的打赏，
 * 要从上月的账上减掉。若按当前时间归期，本月会凭空多出一笔负数，上月则永远是错的。
 * 这样每个周期的累计都只由属于它的打赏构成，也不会被减成负数。
 *
 * 快照一经固化就不再变。已固化周期事后发生退款，累计值会变而快照不变——
 * 把已经公示过的名次事后改掉，等于让排名翻案。
 */
@Service
public class LeaderboardService {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardService.class);

    private final LeaderboardTotalMapper totalMapper;
    private final LeaderboardSnapshotMapper snapshotMapper;
    private final TalentService talentService;

    public LeaderboardService(LeaderboardTotalMapper totalMapper,
                              LeaderboardSnapshotMapper snapshotMapper,
                              TalentService talentService) {
        this.totalMapper = totalMapper;
        this.snapshotMapper = snapshotMapper;
        this.talentService = talentService;
    }

    /** 榜单上的一行。刻意不含任何打赏人字段。 */
    public record Row(int rank, Long talentId, String nickname, String coverUrl,
                      long amountCents, int tipCount) {
    }

    // ---------- 累计 ----------

    /** 打赏确认收款后计入三种周期的累计。 */
    @Transactional
    public void recordTip(TipRecord tip) {
        apply(tip, +1);
    }

    /** 打赏退款后从累计中扣除，扣的是它当初计入的那个周期。 */
    @Transactional
    public void reverseTip(TipRecord tip) {
        apply(tip, -1);
    }

    private void apply(TipRecord tip, int sign) {
        LocalDateTime at = tip.getPaidAt();
        if (at == null) {
            // 没收过款的打赏从来没进过榜，退款也就无从扣起
            return;
        }
        long amount = tip.getAmountCents() * sign;
        for (PeriodType type : PeriodType.values()) {
            totalMapper.accumulate(type.name(), type.keyOf(at), tip.getTalentId(), amount, sign);
        }
        log.info("榜单累计 {} 主播 {} {} 分", sign > 0 ? "计入" : "扣除", tip.getTalentId(), Math.abs(amount));
    }

    // ---------- 读取 ----------

    /**
     * 某周期的榜单。
     *
     * 读历史周期时顺手把它固化（惰性，与试音需求的过期关闭同理）：
     * 所有在意历史榜单的路径都会先读一次，不存在「该固化却没人固化」的窗口，
     * 多一个定时任务只会多一处会漏跑的地方。
     */
    @Transactional
    public List<Row> top(PeriodType type, String periodKey, int limit) {
        String key = periodKey == null || periodKey.isBlank() ? type.currentKey() : periodKey;
        int size = Math.min(Math.max(limit, 1), 100);

        if (!key.equals(type.currentKey())) {
            freeze(type, key);
            return fromSnapshot(type, key, size);
        }
        return fromTotals(type, key, size);
    }

    private List<Row> fromTotals(PeriodType type, String key, int limit) {
        List<LeaderboardTotal> totals = totalMapper.selectList(
                Wrappers.<LeaderboardTotal>lambdaQuery()
                        .eq(LeaderboardTotal::getPeriodType, type.name())
                        .eq(LeaderboardTotal::getPeriodKey, key)
                        // 退成 0 或负数的行不该出现在榜上
                        .gt(LeaderboardTotal::getAmountCents, 0)
                        .orderByDesc(LeaderboardTotal::getAmountCents)
                        .orderByAsc(LeaderboardTotal::getTalentId)
                        .last("LIMIT " + limit));

        Map<Long, Talent> talents = talentService.findByIds(
                totals.stream().map(LeaderboardTotal::getTalentId).toList());

        List<Row> rows = new ArrayList<>();
        for (LeaderboardTotal t : totals) {
            Talent talent = talents.get(t.getTalentId());
            if (talent == null) {
                continue;
            }
            rows.add(new Row(rows.size() + 1, talent.getId(), talent.getNickname(),
                    talent.getCoverUrl(), t.getAmountCents(), t.getTipCount()));
        }
        return rows;
    }

    private List<Row> fromSnapshot(PeriodType type, String key, int limit) {
        List<LeaderboardSnapshot> rows = snapshotMapper.selectList(
                Wrappers.<LeaderboardSnapshot>lambdaQuery()
                        .eq(LeaderboardSnapshot::getPeriodType, type.name())
                        .eq(LeaderboardSnapshot::getPeriodKey, key)
                        .orderByAsc(LeaderboardSnapshot::getRankNo)
                        .last("LIMIT " + limit));

        Map<Long, Talent> talents = talentService.findByIds(
                rows.stream().map(LeaderboardSnapshot::getTalentId).toList());

        return rows.stream().map(s -> {
            Talent talent = talents.get(s.getTalentId());
            return new Row(s.getRankNo(), s.getTalentId(),
                    // 昵称取快照里的：主播事后改名，历史榜单仍显示当时的名字
                    s.getNickname(), talent == null ? null : talent.getCoverUrl(),
                    s.getAmountCents(), s.getTipCount());
        }).toList();
    }

    // ---------- 固化 ----------

    /**
     * 固化某个已结束周期的名次。幂等：已固化过就什么也不做。
     *
     * @return 本次写入的行数；0 表示此前已固化（或该周期无数据）
     */
    @Transactional
    public int freeze(PeriodType type, String periodKey) {
        if (periodKey.equals(type.currentKey())) {
            // 当前周期还在变，固化它等于提前宣布结果
            throw new IllegalArgumentException("不能固化尚未结束的周期: " + periodKey);
        }
        if (isFrozen(type, periodKey)) {
            return 0;
        }

        List<LeaderboardTotal> totals = totalMapper.selectList(
                Wrappers.<LeaderboardTotal>lambdaQuery()
                        .eq(LeaderboardTotal::getPeriodType, type.name())
                        .eq(LeaderboardTotal::getPeriodKey, periodKey)
                        .gt(LeaderboardTotal::getAmountCents, 0)
                        .orderByDesc(LeaderboardTotal::getAmountCents)
                        .orderByAsc(LeaderboardTotal::getTalentId));
        if (totals.isEmpty()) {
            return 0;
        }

        Map<Long, Talent> talents = talentService.findByIds(
                totals.stream().map(LeaderboardTotal::getTalentId).toList());

        int rank = 0;
        int written = 0;
        for (LeaderboardTotal t : totals) {
            Talent talent = talents.get(t.getTalentId());
            if (talent == null) {
                continue;
            }
            rank++;
            LeaderboardSnapshot snapshot = new LeaderboardSnapshot();
            snapshot.setPeriodType(type.name());
            snapshot.setPeriodKey(periodKey);
            snapshot.setTalentId(t.getTalentId());
            snapshot.setRankNo(rank);
            snapshot.setAmountCents(t.getAmountCents());
            snapshot.setTipCount(t.getTipCount());
            snapshot.setNickname(talent.getNickname());
            snapshot.setFrozenAt(LocalDateTime.now());
            try {
                snapshotMapper.insert(snapshot);
                written++;
            } catch (DuplicateKeyException e) {
                // 另一个请求刚固化过同一周期，正是想要的结果
            }
        }
        if (written > 0) {
            log.info("已固化 {} {} 的榜单，共 {} 行", type, periodKey, written);
        }
        return written;
    }

    public boolean isFrozen(PeriodType type, String periodKey) {
        return snapshotMapper.selectCount(Wrappers.<LeaderboardSnapshot>lambdaQuery()
                .eq(LeaderboardSnapshot::getPeriodType, type.name())
                .eq(LeaderboardSnapshot::getPeriodKey, periodKey)) > 0;
    }

    /** 某周期某主播的累计，供核对。 */
    public long amountOf(PeriodType type, String periodKey, long talentId) {
        LeaderboardTotal total = totalMapper.selectOne(Wrappers.<LeaderboardTotal>lambdaQuery()
                .eq(LeaderboardTotal::getPeriodType, type.name())
                .eq(LeaderboardTotal::getPeriodKey, periodKey)
                .eq(LeaderboardTotal::getTalentId, talentId));
        return total == null ? 0 : total.getAmountCents();
    }
}
