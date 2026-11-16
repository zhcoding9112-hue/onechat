package com.guild.checkout.chat;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 未读计数。
 *
 * 不在 chat_conversation 上放一列「未读数」：那样要在发消息、打开会话、
 * 运营下架消息每条路径上加加减减，漏一条就与实际消息对不上，而对不上没人会发现。
 * 这里按「比已读水位线新、且不是我发的、且仍可见」现算，错不了。
 *
 * 一条会话列表十几行，一条带 (conversation_id, id) 索引的分组查询足够。
 */
@Mapper
public interface ChatUnreadMapper {

    /** 粉丝在自己每个会话里的未读数。只返回有未读的会话。 */
    @Select("""
            SELECT c.id AS conversationId, COUNT(m.id) AS c
            FROM chat_conversation c
            JOIN chat_message m
              ON m.conversation_id = c.id
             AND m.status = 'VISIBLE'
             AND m.sender_role = 'TALENT'
             AND m.id > c.fan_read_until
            WHERE c.fan_user_id = #{fanUserId}
            GROUP BY c.id
            """)
    List<Map<String, Object>> fanUnreadByConversation(@Param("fanUserId") long fanUserId);

    /** 粉丝的全局未读总数，供底部导航的红点使用。 */
    @Select("""
            SELECT COUNT(m.id)
            FROM chat_conversation c
            JOIN chat_message m
              ON m.conversation_id = c.id
             AND m.status = 'VISIBLE'
             AND m.sender_role = 'TALENT'
             AND m.id > c.fan_read_until
            WHERE c.fan_user_id = #{fanUserId}
            """)
    long fanUnreadTotal(@Param("fanUserId") long fanUserId);

    /** 主播在自己每个会话里的未读数。 */
    @Select("""
            SELECT c.id AS conversationId, COUNT(m.id) AS c
            FROM chat_conversation c
            JOIN chat_message m
              ON m.conversation_id = c.id
             AND m.status = 'VISIBLE'
             AND m.sender_role = 'FAN'
             AND m.id > c.talent_read_until
            WHERE c.talent_id = #{talentId}
            GROUP BY c.id
            """)
    List<Map<String, Object>> talentUnreadByConversation(@Param("talentId") long talentId);

    /** 主播的全局未读总数。 */
    @Select("""
            SELECT COUNT(m.id)
            FROM chat_conversation c
            JOIN chat_message m
              ON m.conversation_id = c.id
             AND m.status = 'VISIBLE'
             AND m.sender_role = 'FAN'
             AND m.id > c.talent_read_until
            WHERE c.talent_id = #{talentId}
            """)
    long talentUnreadTotal(@Param("talentId") long talentId);
}
