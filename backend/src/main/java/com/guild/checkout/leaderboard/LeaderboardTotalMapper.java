package com.guild.checkout.leaderboard;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface LeaderboardTotalMapper extends BaseMapper<LeaderboardTotal> {

    /**
     * 累加（delta 可负）。没有该行就建一行。
     *
     * 一条语句完成「没有就建、有就加」，理由与元子开户完全相同：
     * 先查后插在并发下要么插出两行，要么因重复键 INSERT 的 S 锁与随后 UPDATE 的
     * X 锁互相等成环而死锁。ON DUPLICATE KEY UPDATE 冲突时直接取 X 锁，线程排队。
     */
    @Update("""
            INSERT INTO leaderboard_total
                   (period_type, period_key, talent_id, amount_cents, tip_count)
            VALUES (#{periodType}, #{periodKey}, #{talentId}, #{amountDelta}, #{countDelta})
            ON DUPLICATE KEY UPDATE
                   amount_cents = amount_cents + #{amountDelta},
                   tip_count    = tip_count + #{countDelta}
            """)
    int accumulate(@Param("periodType") String periodType,
                   @Param("periodKey") String periodKey,
                   @Param("talentId") long talentId,
                   @Param("amountDelta") long amountDelta,
                   @Param("countDelta") int countDelta);
}
