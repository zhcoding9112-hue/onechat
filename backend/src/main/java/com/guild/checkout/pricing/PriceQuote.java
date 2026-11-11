package com.guild.checkout.pricing;

/**
 * 下单瞬间固定下来的计价结果。
 *
 * 本对象是不可变的，一经产出即写入订单。此后无论运营怎么改价格或分成比例，
 * 已成立的订单都按这里的取值结算——这是 service-pricing 与 settlement 两份规格共同要求的。
 *
 * @param talentId       主播档案 id
 * @param serviceItemId  服务项目 id
 * @param serviceSpecId  档位 id
 * @param serviceItemName 项目名称快照，便于历史订单在项目改名后仍显示当时的名称
 * @param serviceSpecName 档位名称快照
 * @param unitPriceCents 单价快照，单位分
 * @param quantity       数量
 * @param payableCents   应付金额，单位分
 * @param commissionRateBp 分成比例快照，基点
 * @param guildCents     公会分成，单位分
 * @param talentCents    主播应得，单位分
 */
public record PriceQuote(
        Long talentId,
        Long serviceItemId,
        Long serviceSpecId,
        String serviceItemName,
        String serviceSpecName,
        long unitPriceCents,
        int quantity,
        long payableCents,
        int commissionRateBp,
        long guildCents,
        long talentCents) {

    public PriceQuote {
        if (quantity <= 0) {
            throw new IllegalArgumentException("数量必须大于零: " + quantity);
        }
        if (unitPriceCents < 0) {
            throw new IllegalArgumentException("单价不得为负: " + unitPriceCents);
        }
        if (payableCents != unitPriceCents * quantity) {
            throw new IllegalStateException("应付金额与单价数量不符");
        }
        if (guildCents + talentCents != payableCents) {
            throw new IllegalStateException("分账金额之和与应付金额不符");
        }
    }
}
