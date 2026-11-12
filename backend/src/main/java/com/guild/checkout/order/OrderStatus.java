package com.guild.checkout.order;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 订单状态与合法跃迁。
 *
 * 正常路径：待支付 → 待确认收款 → 待接单 → 已接单 → 已完成。
 * 终止态：已超时、已取消、已退款，进入后不可再流转。
 *
 * 「待确认收款」来自 payment 规格：粉丝上传凭证后进入该状态等待运营对账，
 * 且它不受待支付超时关闭的影响——人已经付了钱，不能因为运营没及时对账就把单关掉。
 */
public enum OrderStatus {

    /** 已创建，等待粉丝付款并上传凭证。 */
    PENDING_PAYMENT("待支付"),

    /** 粉丝已上传凭证，等待运营核对到账。 */
    PENDING_CONFIRM("待确认收款"),

    /** 已确认收款，等待主播接单。 */
    PENDING_ACCEPT("待接单"),

    /** 主播已接单，服务在站外进行。 */
    ACCEPTED("已接单"),

    /** 主播已标记完成。 */
    COMPLETED("已完成"),

    /** 待接单超时未被接单，进入人工退款队列。 */
    TIMEOUT("已超时"),

    /** 已取消：待支付超时自动关闭，或运营人工取消。 */
    CANCELLED("已取消"),

    /** 已退款。 */
    REFUNDED("已退款");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 合法跃迁表。没有列出的跃迁一律拒绝。 */
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            PENDING_PAYMENT, EnumSet.of(PENDING_CONFIRM, PENDING_ACCEPT, CANCELLED),
            // 驳回凭证会退回待支付；确认收款进待接单
            PENDING_CONFIRM, EnumSet.of(PENDING_PAYMENT, PENDING_ACCEPT, CANCELLED),
            PENDING_ACCEPT, EnumSet.of(ACCEPTED, TIMEOUT, CANCELLED),
            ACCEPTED, EnumSet.of(COMPLETED, CANCELLED),
            // 已完成后只剩人工退款这一条出路（纠纷处理）
            COMPLETED, EnumSet.of(REFUNDED),
            TIMEOUT, EnumSet.of(REFUNDED),
            CANCELLED, EnumSet.of(REFUNDED),
            REFUNDED, EnumSet.noneOf(OrderStatus.class));

    /** 终止态：不再有任何出路，或只剩退款。 */
    private static final Set<OrderStatus> TERMINAL =
            EnumSet.of(COMPLETED, TIMEOUT, CANCELLED, REFUNDED);

    /** 等待中的状态：有超时出路，需要被超时调度器关注。 */
    private static final Set<OrderStatus> WAITING =
            EnumSet.of(PENDING_PAYMENT, PENDING_CONFIRM, PENDING_ACCEPT);

    public boolean canTransitionTo(OrderStatus target) {
        return target != null && TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public Set<OrderStatus> allowedTargets() {
        return Set.copyOf(TRANSITIONS.getOrDefault(this, Set.of()));
    }

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean isWaiting() {
        return WAITING.contains(this);
    }

    /** 钱已到账：这类订单若被终止，必须进入退款流程。 */
    public boolean isPaid() {
        return this == PENDING_ACCEPT || this == ACCEPTED || this == COMPLETED || this == TIMEOUT;
    }
}
