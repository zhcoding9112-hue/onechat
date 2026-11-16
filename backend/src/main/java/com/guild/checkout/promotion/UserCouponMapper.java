package com.guild.checkout.promotion;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface UserCouponMapper extends BaseMapper<UserCoupon> {

    /**
     * 占用一张券：仅当它当前确实可用时才成功。
     *
     * 判断写进 WHERE 而不是应用层，理由与元子扣减相同：
     * 「先查状态是 AVAILABLE、再 UPDATE 成 LOCKED」在并发下会让同一张券被两笔订单占用，
     * 两个事务都会读到 AVAILABLE。这条语句对该行当前读并加锁，第二个线程只会影响 0 行。
     *
     * @return 影响行数；0 表示这张券已经被占用、已核销或已过期
     */
    @Update("""
            UPDATE user_coupon
               SET status = 'LOCKED', order_id = #{orderId}
             WHERE id = #{userCouponId}
               AND user_id = #{userId}
               AND status = 'AVAILABLE'
               AND expires_at > #{now}
            """)
    int lockForOrder(@Param("userCouponId") long userCouponId,
                     @Param("userId") long userId,
                     @Param("orderId") long orderId,
                     @Param("now") LocalDateTime now);

    /** 核销：占用它的那笔订单确认收款。 */
    @Update("""
            UPDATE user_coupon
               SET status = 'USED', used_at = #{now}
             WHERE order_id = #{orderId}
               AND status = 'LOCKED'
            """)
    int redeemByOrder(@Param("orderId") long orderId, @Param("now") LocalDateTime now);
}
