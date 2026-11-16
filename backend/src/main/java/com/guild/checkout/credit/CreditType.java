package com.guild.checkout.credit;

/** 元子流水类型。本阶段只记账，DEDUCT 仅由运营的负向调整产生。 */
public enum CreditType {
    /** 运营发放。 */
    GRANT,
    /** 运营差错调整，可正可负。 */
    ADJUST,
    /** 消耗。预留给日后可能开放的元子抵扣，当前没有任何业务链路会产生。 */
    DEDUCT
}
