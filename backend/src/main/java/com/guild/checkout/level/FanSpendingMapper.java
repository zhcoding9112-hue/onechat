package com.guild.checkout.level;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 粉丝累计已确认消费。
 *
 * 不另建累计表而是查原表求和：累计表要在下单、收款、退款、超时每条路径上维护，
 * 漏一条就会与真实流水对不上，而对不上的表没人会发现。这里数据量在数百到数千笔，
 * 两条带索引的 SUM 完全够用；真到了扛不住的量级再加累计表也不迟，
 * 那时它至少有一份可以对账的真值。
 */
@Mapper
public interface FanSpendingMapper {

    /**
     * 订单侧：已确认收款且未退款的实付合计。
     *
     * 取实付而不是原价——「消费金额」是粉丝真花出去的钱，用券抵掉的那部分他没花。
     */
    @Select("""
            SELECT COALESCE(SUM(payable_cents), 0)
            FROM service_order
            WHERE fan_user_id = #{userId}
              AND paid_at IS NOT NULL
              AND status <> 'REFUNDED'
            """)
    long orderSpending(@Param("userId") long userId);

    /** 打赏侧：已完成的金额合计。 */
    @Select("""
            SELECT COALESCE(SUM(amount_cents), 0)
            FROM tip_record
            WHERE fan_user_id = #{userId}
              AND status = 'COMPLETED'
            """)
    long tipSpending(@Param("userId") long userId);
}
