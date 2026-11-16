package com.guild.checkout.feed;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 点赞数与评论数。
 *
 * 不把计数冗余在 feed_post 上：冗余列要在点赞、取消点赞、评论、下架评论、
 * 下架动态每条路径上维护，漏一条就与真实行数对不上，而对不上没人会发现。
 * 一页十来条动态，一条带索引的分组查询完全够用；真到了扛不住的量级，
 * 再加缓存层也还有一份可对账的真值。
 */
@Mapper
public interface FeedCountMapper {

    @Select("""
            <script>
            SELECT post_id AS postId, COUNT(*) AS c
            FROM feed_like
            WHERE post_id IN
              <foreach item="id" collection="postIds" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY post_id
            </script>
            """)
    List<Map<String, Object>> likeCounts(@Param("postIds") List<Long> postIds);

    /** 只统计可见评论：下架的评论不该还在计数里。 */
    @Select("""
            <script>
            SELECT post_id AS postId, COUNT(*) AS c
            FROM feed_comment
            WHERE status = 'VISIBLE' AND post_id IN
              <foreach item="id" collection="postIds" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY post_id
            </script>
            """)
    List<Map<String, Object>> commentCounts(@Param("postIds") List<Long> postIds);

    /** 当前用户在这批动态里点过赞的那些。 */
    @Select("""
            <script>
            SELECT post_id
            FROM feed_like
            WHERE user_id = #{userId} AND post_id IN
              <foreach item="id" collection="postIds" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<Long> likedPostIds(@Param("userId") long userId, @Param("postIds") List<Long> postIds);
}
