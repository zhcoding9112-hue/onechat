package com.guild.checkout.order;

import com.guild.checkout.analytics.EntrySource;

/**
 * 下单入参。
 *
 * @param fanUserId     下单粉丝
 * @param talentId      指定的主播，不提供系统派单
 * @param serviceSpecId 档位
 * @param quantity      数量
 * @param contact       粉丝的站外联系方式，必填——主播据此在站外发起服务
 * @param remark        附加需求备注
 * @param userCouponId  使用的券（user_coupon.id），不用券传 null。单笔限一张
 * @param entrySource   粉丝是从哪个入口来的。为空按直接访问记
 */
public record OrderCreateCommand(
        long fanUserId,
        long talentId,
        long serviceSpecId,
        int quantity,
        String contact,
        String remark,
        Long userCouponId,
        EntrySource entrySource) {

    /** 不带归因的下单。来源按直接访问记。 */
    public OrderCreateCommand(long fanUserId, long talentId, long serviceSpecId,
                              int quantity, String contact, String remark,
                              Long userCouponId) {
        this(fanUserId, talentId, serviceSpecId, quantity, contact, remark,
                userCouponId, EntrySource.DIRECT);
    }

    /** 不用券的下单。 */
    public OrderCreateCommand(long fanUserId, long talentId, long serviceSpecId,
                              int quantity, String contact, String remark) {
        this(fanUserId, talentId, serviceSpecId, quantity, contact, remark, null);
    }

    public EntrySource entrySourceOrDirect() {
        return entrySource == null ? EntrySource.DIRECT : entrySource;
    }
}
