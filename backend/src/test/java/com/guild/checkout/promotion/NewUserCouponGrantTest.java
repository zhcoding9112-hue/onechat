package com.guild.checkout.promotion;

import com.guild.checkout.user.AppUser;
import com.guild.checkout.user.AppUserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 新人券：注册即发。
 *
 * **刻意不加 @Transactional**：发券挂在 AFTER_COMMIT 上，测试方法若包在一个
 * 不提交的事务里，监听器根本不会跑，用例会「通过」但什么也没验到。
 * 代价是要自己收尾，@AfterEach 里把造出来的数据删掉。
 */
@SpringBootTest
@org.springframework.test.context.event.RecordApplicationEvents
class NewUserCouponGrantTest {

    @Autowired private AppUserService userService;
    @Autowired private CouponService couponService;
    @Autowired private CouponMapper couponMapper;
    @Autowired private UserCouponMapper userCouponMapper;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired private org.springframework.test.context.event.ApplicationEvents events;

    private final List<Long> createdUsers = new java.util.ArrayList<>();
    private final List<Long> createdCoupons = new java.util.ArrayList<>();

    @org.junit.jupiter.api.AfterEach
    void cleanUp() {
        for (Long userId : createdUsers) {
            jdbc.update("DELETE FROM user_coupon WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM app_user WHERE id = ?", userId);
        }
        for (Long couponId : createdCoupons) {
            jdbc.update("DELETE FROM user_coupon WHERE coupon_id = ?", couponId);
            jdbc.update("DELETE FROM coupon WHERE id = ?", couponId);
        }
        createdUsers.clear();
        createdCoupons.clear();
    }

    @Test
    @DisplayName("标了注册即发的券，新用户一建号就拿到")
    void 新用户自动拿到券() {
        Coupon coupon = newCoupon("注册即发券", true);

        AppUser user = register("nu_auto_");

        assertThat(couponIdsOf(user.getId())).contains(coupon.getId());
    }

    @Test
    @DisplayName("没标的券不发")
    void 没标的券不发() {
        Coupon plain = newCoupon("普通券", false);

        AppUser user = register("nu_plain_");

        assertThat(couponIdsOf(user.getId())).doesNotContain(plain.getId());
    }

    @Test
    @DisplayName("停用的券不发")
    void 停用的券不发() {
        Coupon coupon = newCoupon("停用的注册券", true);
        // CouponService 没有停用方法，直接改库——这条验的是发券的筛选条件，不是停用流程
        jdbc.update("UPDATE coupon SET enabled = 0 WHERE id = ?", coupon.getId());

        AppUser user = register("nu_off_");

        assertThat(couponIdsOf(user.getId())).doesNotContain(coupon.getId());
    }

    @Test
    @DisplayName("已过期的券不发 — 发了也用不了，只会让券包里多一张废券")
    void 过期的券不发() {
        Coupon expired = couponService.create(new CouponService.CouponForm(
                "过期的注册券", 300L, 2000L, null,
                LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(1), true));
        createdCoupons.add(expired.getId());

        AppUser user = register("nu_exp_");

        assertThat(couponIdsOf(user.getId())).doesNotContain(expired.getId());
    }

    /**
     * 再次授权只是刷新资料，不该再走一遍发券。
     *
     * 只断言「张数没变」是不够的：唯一索引本来就会挡住第二张，
     * 所以即便每次登录都发一遍事件，张数照样是 1，用例会假通过。
     * 这里直接数 UserRegistered 事件的条数——建号发一次、再次授权一次都不发。
     */
    @Test
    @DisplayName("老用户再次授权不发注册事件，也不会重复拿券")
    void 再次授权不重复发() {
        Coupon coupon = newCoupon("注册即发券", true);

        AppUser user = register("nu_again_");
        assertThat(countOf(user.getId(), coupon.getId())).isEqualTo(1);
        long afterRegister = eventCount();
        assertThat(afterRegister).as("建号应当发一次").isEqualTo(1);

        userService.registerOrUpdate(user.getOpenId(), null, "换了个昵称", null);

        assertThat(eventCount())
                .as("再次授权不该再发注册事件")
                .isEqualTo(afterRegister);
        assertThat(countOf(user.getId(), coupon.getId())).isEqualTo(1);
    }

    private long eventCount() {
        return events.stream(com.guild.checkout.user.event.UserRegistered.class).count();
    }

    @Test
    @DisplayName("开关能关掉：关掉之后注册的人就不再拿到")
    void 关掉之后不再发() {
        Coupon coupon = newCoupon("注册即发券", true);

        AppUser before = register("nu_before_");
        assertThat(couponIdsOf(before.getId())).contains(coupon.getId());

        couponService.setAutoGrantOnRegister(coupon.getId(), false);

        AppUser after = register("nu_after_");
        assertThat(couponIdsOf(after.getId())).doesNotContain(coupon.getId());
    }

    // ---------- 辅助 ----------

    private Coupon newCoupon(String name, boolean autoGrant) {
        Coupon coupon = couponService.create(new CouponService.CouponForm(
                name, 300L, 2000L, null,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusYears(1), autoGrant));
        createdCoupons.add(coupon.getId());
        return coupon;
    }

    private AppUser register(String prefix) {
        AppUser user = userService.registerOrUpdate(
                prefix + System.nanoTime(), null, prefix, null);
        createdUsers.add(user.getId());
        return user;
    }

    private List<Long> couponIdsOf(long userId) {
        return userCouponMapper.selectList(
                        com.baomidou.mybatisplus.core.toolkit.Wrappers.<UserCoupon>lambdaQuery()
                                .eq(UserCoupon::getUserId, userId))
                .stream().map(UserCoupon::getCouponId).toList();
    }

    private long countOf(long userId, long couponId) {
        return couponIdsOf(userId).stream().filter(id -> id == couponId).count();
    }
}
