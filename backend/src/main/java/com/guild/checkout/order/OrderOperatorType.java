package com.guild.checkout.order;

/** 状态变更的触发者类型。 */
public enum OrderOperatorType {
    /** 下单粉丝。 */
    FAN,
    /** 该订单指定的主播。 */
    TALENT,
    /** 运营人员。 */
    OPERATOR,
    /** 系统自动触发，如超时处置。 */
    SYSTEM
}
