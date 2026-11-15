package com.guild.checkout.analytics;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AnalyticsEventMapper extends BaseMapper<AnalyticsEvent> {

    /**
     * 各节点的去重人数。
     *
     * 去重口径是 user_id；关注服务号时用户可能还没建号，所以回退到 open_id。
     * COALESCE 把两者统一成一个标识，避免同一个人被算成两个。
     */
    @Select("""
            SELECT event_key AS eventKey, COUNT(DISTINCT COALESCE(CAST(user_id AS CHAR), open_id)) AS uv
            FROM analytics_event
            WHERE occurred_at >= #{from} AND occurred_at < #{to}
            GROUP BY event_key
            """)
    List<Map<String, Object>> countDistinctByKey(@Param("from") LocalDateTime from,
                                                 @Param("to") LocalDateTime to);

    /** 按主播拆分的各节点去重人数。 */
    @Select("""
            SELECT talent_id AS talentId, event_key AS eventKey,
                   COUNT(DISTINCT COALESCE(CAST(user_id AS CHAR), open_id)) AS uv
            FROM analytics_event
            WHERE occurred_at >= #{from} AND occurred_at < #{to} AND talent_id IS NOT NULL
            GROUP BY talent_id, event_key
            """)
    List<Map<String, Object>> countByTalent(@Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to);

    /** 按服务项目拆分。 */
    @Select("""
            SELECT service_item_id AS serviceItemId, event_key AS eventKey,
                   COUNT(DISTINCT COALESCE(CAST(user_id AS CHAR), open_id)) AS uv
            FROM analytics_event
            WHERE occurred_at >= #{from} AND occurred_at < #{to} AND service_item_id IS NOT NULL
            GROUP BY service_item_id, event_key
            """)
    List<Map<String, Object>> countByServiceItem(@Param("from") LocalDateTime from,
                                                 @Param("to") LocalDateTime to);
}
