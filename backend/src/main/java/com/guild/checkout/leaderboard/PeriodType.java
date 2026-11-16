package com.guild.checkout.leaderboard;

import java.time.LocalDateTime;
import java.time.temporal.IsoFields;

/**
 * 榜单周期。
 *
 * 周期标识用字符串：日 2026-11-16、周 2026-W47、月 2026-11。
 * 比起存起止时间戳，字符串让「这笔属于哪个周期」成为一次纯函数计算，
 * 不必每次比较区间；也让唯一索引天然挡住同期重复累计。
 *
 * 周用 ISO 周（周一为一周之首，跨年周归属按 ISO 规则），
 * 否则 12 月 31 日那一周会在年末年初之间裂成两段。
 */
public enum PeriodType {

    DAY("日榜"),
    WEEK("周榜"),
    MONTH("月榜");

    private final String label;

    PeriodType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 某个时刻属于哪个周期。 */
    public String keyOf(LocalDateTime at) {
        return switch (this) {
            case DAY -> "%04d-%02d-%02d".formatted(at.getYear(), at.getMonthValue(), at.getDayOfMonth());
            case WEEK -> "%04d-W%02d".formatted(
                    at.get(IsoFields.WEEK_BASED_YEAR), at.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            case MONTH -> "%04d-%02d".formatted(at.getYear(), at.getMonthValue());
        };
    }

    /** 当前周期。 */
    public String currentKey() {
        return keyOf(LocalDateTime.now());
    }
}
