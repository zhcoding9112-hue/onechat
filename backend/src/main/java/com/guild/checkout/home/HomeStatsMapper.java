package com.guild.checkout.home;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 首页区块需要的聚合查询。
 *
 * 这些查询跨订单、打赏与主播表，不属于任何单一领域的 Mapper，故单独放一处。
 */
@Mapper
public interface HomeStatsMapper {

    /**
     * 人气主播：按窗口期内的成交单量倒序。
     *
     * 「成交」口径为已付款且未退款：退款单不应给主播带来人气。
     * 用 LEFT JOIN 而非 INNER JOIN，保证窗口期内零成交的已上架主播也能进入候选，
     * 否则新签约主播会因为没有历史单量而永远进不了人气位。
     */
    @Select("""
            SELECT t.id
            FROM talent t
            LEFT JOIN service_order o
              ON o.talent_id = t.id
             AND o.paid_at IS NOT NULL
             AND o.status <> 'REFUNDED'
             AND o.paid_at >= #{since}
            WHERE t.listed = 1
            GROUP BY t.id, t.sort_order
            ORDER BY COUNT(o.id) DESC, t.sort_order ASC, t.id ASC
            LIMIT #{limit}
            """)
    List<Long> findPopularTalentIds(@Param("since") LocalDateTime since, @Param("limit") int limit);

    /**
     * 打赏跑马灯的数据源。
     *
     * 刻意只取主播昵称与金额：跑马灯 MUST NOT 暴露打赏人身份，
     * 在 SQL 这一层就不把 fan_user_id 查出来，比在上层记得过滤更可靠。
     */
    @Select("""
            SELECT t.nickname AS nickname, r.amount_cents AS amountCents
            FROM tip_record r
            JOIN talent t ON t.id = r.talent_id
            WHERE r.status = 'COMPLETED'
            ORDER BY r.paid_at DESC, r.id DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> findRecentTips(@Param("limit") int limit);
}
