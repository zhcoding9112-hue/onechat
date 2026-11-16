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

    /**
     * 按入口来源拆分的各节点去重人数。
     *
     * 关键在那个 ROW_NUMBER：同一个人可能先从人气店员进了详情，又从榜单进了一次，
     * 若按 (来源, 人) 各算一次，拆分表各行之和会大于总数——而「各行之和等于总数」
     * 正是拆分表唯一有用的性质。所以**每人每节点只归因一次**，归给该周期内
     * 最早的那条事件（occurred_at 相同再按 id 定序，保证结果稳定可复现）。
     *
     * 这样的口径下，「从人气店员进详情的人数」读作「首次进详情来自人气店员的人数」。
     */
    @Select("""
            SELECT entry_source AS entrySource, event_key AS eventKey, COUNT(*) AS uv
            FROM (
              SELECT event_key, entry_source,
                     ROW_NUMBER() OVER (
                       PARTITION BY event_key, COALESCE(CAST(user_id AS CHAR), open_id)
                       ORDER BY occurred_at, id) AS rn
              FROM analytics_event
              WHERE occurred_at >= #{from} AND occurred_at < #{to}
            ) t
            WHERE t.rn = 1
            GROUP BY entry_source, event_key
            """)
    List<Map<String, Object>> countByEntrySource(@Param("from") LocalDateTime from,
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
