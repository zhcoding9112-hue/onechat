package com.guild.checkout.talent;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 粉丝端主播信息流的筛选与分页查询。
 *
 * 不走 MyBatis-Plus 分页拦截器：它自 3.5.9 起需要额外的 jsqlparser 构件，
 * 而这里只需要一条带条件的 LIMIT/OFFSET，显式写出来比引入依赖更划算，
 * 也让「按品类筛选」的 EXISTS 子查询一眼可读。
 */
@Mapper
public interface TalentQueryMapper {

    String FILTER = """
            FROM talent t
            WHERE t.listed = 1
            <if test="keyword != null">
              AND LOCATE(#{keyword}, t.nickname) > 0
            </if>
            <if test="gender != null">
              AND t.gender = #{gender}
            </if>
            <if test="level != null">
              AND t.level = #{level}
            </if>
            <if test="categoryCode != null">
              AND EXISTS (
                SELECT 1 FROM talent_price p
                JOIN service_spec s ON s.id = p.service_spec_id AND s.enabled = 1
                JOIN service_item i ON i.id = s.service_item_id AND i.enabled = 1
                WHERE p.talent_id = t.id AND p.enabled = 1 AND i.code = #{categoryCode}
              )
            </if>
            """;

    @Select("<script>SELECT t.id " + FILTER
            + " ORDER BY t.sort_order ASC, t.id ASC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Long> findIds(@Param("categoryCode") String categoryCode,
                       @Param("gender") String gender,
                       @Param("level") Integer level,
                       @Param("keyword") String keyword,
                       @Param("limit") int limit,
                       @Param("offset") int offset);

    @Select("<script>SELECT COUNT(*) " + FILTER + "</script>")
    long countMatching(@Param("categoryCode") String categoryCode,
                       @Param("gender") String gender,
                       @Param("level") Integer level,
                       @Param("keyword") String keyword);

    /**
     * 随机匹配一位主播。
     *
     * 用 ORDER BY RAND() 而不是「先 count 再按随机 offset 取」：后者要两次查询，
     * 且两次之间候选集可能变化，取到空行。当前候选集规模（百级）下 RAND() 的
     * 代价可以忽略；真到了需要优化的量级，再换成按主键区间抽样。
     *
     * onlineOnly 与 excludeIds 只在匹配路径用，故不并入上面的 FILTER。
     *
     * 必须关掉一级缓存：同一个 SqlSession 内，相同语句加相同参数会直接复用
     * 缓存行，ORDER BY RAND() 压根不会被重新执行，连续匹配会拿到同一个人。
     * 生产上每个请求是独立 session 所以不显形，但在一次事务里连续匹配就会中招。
     */
    @Select("""
            <script>
            SELECT t.id
            FROM talent t
            WHERE t.listed = 1
            <if test="gender != null">
              AND t.gender = #{gender}
            </if>
            <if test="level != null">
              AND t.level = #{level}
            </if>
            <if test="onlineOnly">
              AND t.online = 1
            </if>
            <if test="categoryCode != null">
              AND EXISTS (
                SELECT 1 FROM talent_price p
                JOIN service_spec s ON s.id = p.service_spec_id AND s.enabled = 1
                JOIN service_item i ON i.id = s.service_item_id AND i.enabled = 1
                WHERE p.talent_id = t.id AND p.enabled = 1 AND i.code = #{categoryCode}
              )
            </if>
            <if test="excludeOrderedFor != null">
              AND NOT EXISTS (
                SELECT 1 FROM service_order o
                WHERE o.talent_id = t.id AND o.fan_user_id = #{excludeOrderedFor}
              )
            </if>
            <if test="excludeIds != null and excludeIds.size() > 0">
              AND t.id NOT IN
              <foreach collection="excludeIds" item="eid" open="(" separator="," close=")">#{eid}</foreach>
            </if>
            ORDER BY RAND()
            LIMIT 1
            </script>
            """)
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    Long findRandomId(@Param("categoryCode") String categoryCode,
                      @Param("gender") String gender,
                      @Param("level") Integer level,
                      @Param("onlineOnly") boolean onlineOnly,
                      @Param("excludeOrderedFor") Long excludeOrderedFor,
                      @Param("excludeIds") java.util.List<Long> excludeIds);
}
