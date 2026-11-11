package com.guild.checkout.pricing;

/**
 * 分账拆分结果：公会分成与主播应得，单位均为分。
 *
 * 不变式：{@code guildCents + talentCents == amountCents}，任何取整都不得破坏它。
 */
public record CommissionSplit(long amountCents, int rateBp, long guildCents, long talentCents) {

    public CommissionSplit {
        if (guildCents + talentCents != amountCents) {
            throw new IllegalStateException(
                    "分账金额之和与来源金额不符: %d + %d != %d".formatted(guildCents, talentCents, amountCents));
        }
    }
}
