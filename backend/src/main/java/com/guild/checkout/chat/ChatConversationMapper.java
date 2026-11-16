package com.guild.checkout.chat;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface ChatConversationMapper extends BaseMapper<ChatConversation> {

    /**
     * 会话不存在就建，已存在就什么也不做。
     *
     * 用单条 INSERT ... ON DUPLICATE KEY UPDATE 而不是「先查后插」：
     * 先查后插在并发下会同时判定「不存在」，然后一方撞唯一索引失败——
     * 粉丝连点两下聊天就可能看到一次报错。
     *
     * 也不写成「插入失败再捕获 DuplicateKeyException」：那条路径会先在已存在的行上
     * 加 S 锁，紧接着的更新要 X 锁，两个并发请求互等就是死锁（第 6 组元子账户踩过）。
     */
    @Update("""
            INSERT INTO chat_conversation (fan_user_id, talent_id, last_message_at)
            VALUES (#{fanUserId}, #{talentId}, CURRENT_TIMESTAMP(3))
            ON DUPLICATE KEY UPDATE talent_id = talent_id
            """)
    int openIfAbsent(@Param("fanUserId") long fanUserId, @Param("talentId") long talentId);

    /**
     * 建完之后把会话读回来。必须是加锁读（FOR SHARE），不能用普通的 selectOne。
     *
     * MyS"""L 默认的 REPEATABLE READ 下，事务的读视图在**第一次普通读**时就定格了。
     * open() 先查了一次「有没有现成会话」，视图就停在那一刻；紧接着真正插入这行的
     * 是另一个并发事务，它提交得再早，也在我们的视图之外——再普通读一次仍然查不到，
     * 于是「会话创建失败」。加锁读不走读视图，读的是最新已提交版本，才拿得到。
     *
     * 这不是理论风险：16 线程同时发起会话时，有 3 个拿到了 null。
     */
    @Select("""
            SELECT * FROM chat_conversation
             WHERE fan_user_id = #{fanUserId} AND talent_id = #{talentId}
             FOR SHARE
            """)
    ChatConversation findForUpdate(@Param("fanUserId") long fanUserId,
                                   @Param("talentId") long talentId);

    /**
     * 发消息后前移会话的排序位置。
     *
     * 带 last_message_id 的递增条件：两条消息并发落库时，后插入的那条（id 更大）
     * 才是真正的「最后一条」，不能被先完成事务的那条覆盖回去。
     */
    @Update("""
            UPDATE chat_conversation
               SET last_message_id = #{messageId},
                   last_message_at = #{at}
             WHERE id = #{id}
               AND (last_message_id IS NULL OR last_message_id < #{messageId})
            """)
    int touchLastMessage(@Param("id") long id,
                         @Param("messageId") long messageId,
                         @Param("at") LocalDateTime at);

    /**
     * 前移粉丝侧的已读水位线。
     *
     * GREATEST 保证只增不减：两个标签页同时打开会话时，后到的那个请求
     * 可能带着更旧的水位线，直接赋值会把已读数又变回未读。
     */
    @Update("""
            UPDATE chat_conversation
               SET fan_read_until = GREATEST(fan_read_until, #{upTo})
             WHERE id = #{id}
            """)
    int markFanRead(@Param("id") long id, @Param("upTo") long upTo);

    /** 前移主播侧的已读水位线。理由同 {@link #markFanRead}。 */
    @Update("""
            UPDATE chat_conversation
               SET talent_read_until = GREATEST(talent_read_until, #{upTo})
             WHERE id = #{id}
            """)
    int markTalentRead(@Param("id") long id, @Param("upTo") long upTo);
}
