package com.guild.checkout.credit;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CreditAccountMapper extends BaseMapper<CreditAccount> {

    /**
     * 开户，已有则什么也不改。
     *
     * 为什么是 INSERT ... ON DUPLICATE KEY UPDATE 而不是「先查后插、捕获重复键」：
     * InnoDB 里一条撞上唯一索引的普通 INSERT 会先对既存那行加 S 锁再报重复键。
     * 如果这条 INSERT 和随后的余额 UPDATE 在同一个事务里，持着 S 锁的事务紧接着
     * 要对同一行升 X 锁，而另一个线程也持着该行的 S 锁，于是互相等成环——
     * 实测 16 线程并发首次变动必现 DeadlockLoserDataAccessException。
     * ON DUPLICATE KEY UPDATE 在冲突时直接取 X 锁，并发线程排队而不是成环。
     *
     * 也试过把开户切到 REQUIRES_NEW 的独立事务，死锁确实没了，但每个请求在
     * 变动期间要同时占两条连接，16 并发就把默认连接池抽干了。单条语句两者都躲开。
     */
    @Update("""
            INSERT INTO credit_account (user_id, balance)
            VALUES (#{userId}, 0)
            ON DUPLICATE KEY UPDATE version = version
            """)
    int openIfAbsent(@Param("userId") long userId);

    /**
     * 原子地变动余额，并在同一条语句里拦住会把余额压到负数的扣减。
     *
     * 关键在 {@code balance + delta >= 0} 这个条件写在 SQL 里而不是应用层：
     * UPDATE 对该行做当前读并持行锁，并发的第二个事务会等第一个提交后再基于
     * 新余额判断，所以不会超扣。若判断放在应用层（先 SELECT 再比较再 UPDATE），
     * 两个事务都会读到同样的旧余额并各自认为扣得起。
     *
     * @return 影响行数；0 表示余额不足（或账户不存在），调用方据此拒绝
     */
    @Update("""
            UPDATE credit_account
               SET balance = balance + #{delta},
                   version = version + 1
             WHERE user_id = #{userId}
               AND balance + #{delta} >= 0
            """)
    int applyDelta(@Param("userId") long userId, @Param("delta") long delta);
}
