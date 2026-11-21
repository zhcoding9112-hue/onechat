package com.guild.checkout.promotion;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.user.event.UserRegistered;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 新人券：注册即发。
 *
 * 为什么是 AFTER_COMMIT：
 *   - 建号的事务若回滚，不该留下一张发给不存在用户的券；
 *   - 更重要的是反过来——**发券失败绝不能连累注册**。一个人因为券表出问题
 *     而登不进来，比少拿一张券严重得多，所以这里把异常吃掉只记日志。
 *
 * 发哪些券由 coupon.auto_grant_on_register 决定，不在代码里写死券 id：
 * 哪张算「新人券」是运营的决定，而且可能同时有多张。
 */
@Component
public class NewUserCouponGranter {

    private static final Logger log = LoggerFactory.getLogger(NewUserCouponGranter.class);

    private final CouponMapper couponMapper;
    private final CouponService couponService;

    public NewUserCouponGranter(CouponMapper couponMapper, CouponService couponService) {
        this.couponMapper = couponMapper;
        this.couponService = couponService;
    }

    @TransactionalEventListener
    public void onUserRegistered(UserRegistered event) {
        try {
            List<Coupon> coupons = autoGrantable();
            if (coupons.isEmpty()) {
                return;
            }
            for (Coupon coupon : coupons) {
                couponService.grantTo(coupon.getId(), List.of(event.userId()));
            }
            log.info("新用户 {} 自动获得 {} 张券", event.userId(), coupons.size());
        } catch (RuntimeException ex) {
            // 注册已经提交了，这里再抛也改变不了结果，只会污染调用栈
            log.error("给新用户 {} 发新人券失败，注册本身不受影响", event.userId(), ex);
        }
    }

    /**
     * 可以自动发放的券：开着、标了注册即发、且还没过期。
     *
     * 不卡 validFrom：提前排好的活动券，让这期间注册的人先拿着，到点自然能用，
     * 比「注册早了就永远错过」合理。
     */
    private List<Coupon> autoGrantable() {
        return couponMapper.selectList(Wrappers.<Coupon>lambdaQuery()
                .eq(Coupon::getAutoGrantOnRegister, true)
                .eq(Coupon::getEnabled, true)
                .gt(Coupon::getValidTo, LocalDateTime.now()));
    }
}
