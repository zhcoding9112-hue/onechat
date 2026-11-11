package com.guild.checkout.pricing;

/**
 * 分账计算。
 *
 * 取整规则（design D3）：公会分成向下取整，余数归主播。
 * 这样做的理由有两条：
 *   1. 两项之和恒等于来源金额，财务永远对得平，不会出现一分钱的长期漂移。
 *   2. 不能整除时的那一分给主播而不是公会——主播对到手金额敏感，公会不在乎一分钱。
 *
 * 全程整数运算，不引入任何浮点。
 */
public final class CommissionCalculator {

    /** 基点分母：10000 基点 = 100%。 */
    public static final int BP_DENOMINATOR = 10_000;

    private CommissionCalculator() {
    }

    /**
     * 按比例拆分金额。
     *
     * @param amountCents 来源金额，单位分，不得为负
     * @param rateBp      公会分成比例，基点，取值 0 到 10000
     */
    public static CommissionSplit split(long amountCents, int rateBp) {
        if (amountCents < 0) {
            throw new IllegalArgumentException("金额不得为负: " + amountCents);
        }
        requireValidRate(rateBp);

        // 先乘后除保证精度；余数归主播，保证两项之和恒等于来源金额。
        long guildCents = multiplyDivideFloor(amountCents, rateBp, BP_DENOMINATOR);
        long talentCents = amountCents - guildCents;
        return new CommissionSplit(amountCents, rateBp, guildCents, talentCents);
    }

    /** 校验比例取值。 */
    public static void requireValidRate(int rateBp) {
        if (rateBp < 0 || rateBp > BP_DENOMINATOR) {
            throw new IllegalArgumentException("分成比例必须在 0 到 10000 基点之间: " + rateBp);
        }
    }

    /** 基点转百分比文案，仅用于展示。 */
    public static String formatRate(int rateBp) {
        requireValidRate(rateBp);
        if (rateBp % 100 == 0) {
            return rateBp / 100 + "%";
        }
        if (rateBp % 10 == 0) {
            return "%d.%d%%".formatted(rateBp / 100, rateBp / 10 % 10);
        }
        return "%d.%02d%%".formatted(rateBp / 100, rateBp % 100);
    }

    /**
     * 计算 floor(value * numerator / denominator)，用 128 位中间值避免溢出。
     */
    private static long multiplyDivideFloor(long value, long numerator, long denominator) {
        long high = Math.multiplyHigh(value, numerator);
        long low = value * numerator;
        if (high == 0 && low >= 0) {
            return low / denominator;
        }
        // 退回到大整数，极端金额下也不会错
        return java.math.BigInteger.valueOf(value)
                .multiply(java.math.BigInteger.valueOf(numerator))
                .divide(java.math.BigInteger.valueOf(denominator))
                .longValueExact();
    }
}
