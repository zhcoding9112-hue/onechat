package com.guild.checkout.talent;

import org.apache.ibatis.annotations.Mapper;
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
                       @Param("limit") int limit,
                       @Param("offset") int offset);

    @Select("<script>SELECT COUNT(*) " + FILTER + "</script>")
    long countMatching(@Param("categoryCode") String categoryCode,
                       @Param("gender") String gender,
                       @Param("level") Integer level);
}
