package com.guild.checkout.order;

import java.time.LocalDateTime;

/**
 * 订单视图。
 *
 * 联系方式只给该单主播与运营看——它是粉丝的微信号，对无关的人就是个人信息泄露。
 */
public record OrderView(
        Long id,
        String orderRef,
        Long talentId,
        String talentNickname,
        String serviceItemName,
        String serviceSpecName,
        long unitPriceCents,
        int quantity,
        /** 订单原价（未用券前）。 */
        long originalCents,
        /** 券抵扣金额，0 表示没用券。 */
        long couponDiscountCents,
        /** 粉丝实付 = 原价 - 抵扣。 */
        long payableCents,
        Long paidCents,
        String status,
        String statusLabel,
        /** 仅该单主播与运营可见，其余情况为 null。 */
        String contact,
        String remark,
        LocalDateTime payDeadline,
        LocalDateTime acceptDeadline,
        LocalDateTime paidAt,
        LocalDateTime acceptedAt,
        LocalDateTime completedAt,
        LocalDateTime closedAt,
        String closeReason,
        LocalDateTime createdAt,
        /** 该状态下当前查看者可以执行的操作，前端据此决定按钮。 */
        java.util.List<String> actions) {
}
