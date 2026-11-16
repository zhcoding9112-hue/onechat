package com.guild.checkout.settlement;

import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.TalentService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 结算报表。
 *
 * 报表只读台账，不碰订单与打赏表——台账已经把金额快照固定下来，
 * 回头去 join 业务表只会引入"报表跟着配置漂移"的风险。
 */
@Service
public class SettlementReportService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SettlementService settlementService;
    private final TalentService talentService;

    public SettlementReportService(SettlementService settlementService, TalentService talentService) {
        this.settlementService = settlementService;
        this.talentService = talentService;
    }

    /**
     * 某周期的结算报表，按主播逐行。
     *
     * 周期内没有任何流水的主播也会成行，各项为零——规格明确要求零值成行而非缺行，
     * 否则运营核对时会漏掉"这个月这位主播一单没有"的情况。
     */
    public List<SettlementService.TalentStatement> monthly(String period) {
        SettlementService.parsePeriod(period);

        Map<Long, long[]> sums = new LinkedHashMap<>();
        for (SettlementEntry e : settlementService.activeEntries(period, null)) {
            // [订单金额, 打赏金额, 公会分成, 主播应得, 订单数, 打赏数, 促销成本]
            long[] acc = sums.computeIfAbsent(e.getTalentId(), k -> new long[7]);
            boolean isOrder = SettlementEntry.SOURCE_ORDER.equals(e.getSourceType());
            acc[isOrder ? 0 : 1] += e.getAmountCents();
            acc[2] += e.getGuildCents();
            acc[3] += e.getTalentCents();
            acc[isOrder ? 4 : 5] += 1;
            acc[6] += e.getPromoCostCents() == null ? 0 : e.getPromoCostCents();
        }

        Map<Long, SettlementMark> marks = new LinkedHashMap<>();
        for (SettlementMark m : settlementService.marksOf(period)) {
            marks.put(m.getTalentId(), m);
        }

        List<SettlementService.TalentStatement> rows = new ArrayList<>();
        for (Talent talent : talentService.listAll()) {
            long[] acc = sums.getOrDefault(talent.getId(), new long[7]);
            SettlementMark mark = marks.get(talent.getId());
            rows.add(new SettlementService.TalentStatement(
                    talent.getId(), talent.getNickname(), period,
                    acc[0], acc[1], acc[2], acc[3], acc[6], (int) acc[4], (int) acc[5],
                    mark != null, mark == null ? null : mark.getSettledAt()));
        }
        return rows;
    }

    /**
     * 导出为 CSV。
     *
     * 选 CSV 而非 xlsx：财务拿到就能用表格软件打开，而生成 xlsx 要多引一个库，
     * 在月数百笔的体量下不值得。金额导出为元，保留两位小数，便于直接对账。
     */
    public byte[] exportCsv(String period) {
        StringBuilder sb = new StringBuilder();
        // BOM，否则 Excel 打开中文会乱码
        sb.append('﻿');
        sb.append("主播ID,主播昵称,周期,服务订单金额,打赏金额,公会分成,主播应得,促销成本,订单数,打赏数,是否已结清\n");

        for (SettlementService.TalentStatement row : monthly(period)) {
            sb.append(row.talentId()).append(',')
                    .append(csv(row.talentNickname())).append(',')
                    .append(row.period()).append(',')
                    .append(yuan(row.orderAmountCents())).append(',')
                    .append(yuan(row.tipAmountCents())).append(',')
                    .append(yuan(row.guildCents())).append(',')
                    .append(yuan(row.talentCents())).append(',')
                    .append(yuan(row.promoCostCents())).append(',')
                    .append(row.orderCount()).append(',')
                    .append(row.tipCount()).append(',')
                    .append(row.settled() ? "是" : "否").append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** 某主播某周期的逐笔明细导出，含已冲销的，供纠纷追溯。 */
    public byte[] exportDetailCsv(String period, long talentId) {
        StringBuilder sb = new StringBuilder();
        sb.append('﻿');
        sb.append("来源类型,来源单号,发生时间,金额,分成比例,公会分成,主播应得,促销成本,状态,冲销原因\n");

        for (SettlementEntry e : settlementService.allEntries(period, talentId)) {
            sb.append(SettlementEntry.SOURCE_ORDER.equals(e.getSourceType()) ? "服务订单" : "打赏")
                    .append(',').append(e.getSourceRef())
                    .append(',').append(e.getOccurredAt().format(TIME))
                    .append(',').append(yuan(e.getAmountCents()))
                    .append(',').append(rate(e.getCommissionRateBp()))
                    .append(',').append(yuan(e.getGuildCents()))
                    .append(',').append(yuan(e.getTalentCents()))
                    .append(',').append(yuan(e.getPromoCostCents() == null ? 0 : e.getPromoCostCents()))
                    .append(',').append(e.isActive() ? "有效" : "已冲销")
                    .append(',').append(csv(e.getReverseReason()))
                    .append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** 分转元，两位小数，全程整数运算。 */
    static String yuan(long cents) {
        boolean negative = cents < 0;
        long abs = Math.abs(cents);
        String text = "%d.%02d".formatted(abs / 100, abs % 100);
        return negative ? "-" + text : text;
    }

    static String rate(int bp) {
        return "%d.%02d%%".formatted(bp / 100, bp % 100);
    }

    /** CSV 转义：含逗号、引号或换行时加引号，内部引号翻倍。 */
    static String csv(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
