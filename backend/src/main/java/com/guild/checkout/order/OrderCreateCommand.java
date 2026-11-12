package com.guild.checkout.order;

/**
 * 下单入参。
 *
 * @param fanUserId     下单粉丝
 * @param talentId      指定的主播，不提供系统派单
 * @param serviceSpecId 档位
 * @param quantity      数量
 * @param contact       粉丝的站外联系方式，必填——主播据此在站外发起服务
 * @param remark        附加需求备注
 */
public record OrderCreateCommand(
        long fanUserId,
        long talentId,
        long serviceSpecId,
        int quantity,
        String contact,
        String remark) {
}
