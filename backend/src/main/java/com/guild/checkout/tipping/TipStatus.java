package com.guild.checkout.tipping;

/**
 * 打赏状态。
 *
 * 比订单少了待接单、已接单、已完成与已超时——打赏不需要主播做任何事，
 * 支付确认后直接计为已完成（tipping 规格明确要求）。
 */
public enum TipStatus {

    PENDING_PAYMENT("待支付"),
    PENDING_CONFIRM("待确认收款"),
    COMPLETED("已完成"),
    CANCELLED("已取消"),
    REFUNDED("已退款");

    private final String label;

    TipStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == REFUNDED;
    }
}
