package com.guild.checkout.promotion;

import com.guild.checkout.auth.CurrentUserHolder;
import com.guild.checkout.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/** 粉丝侧的券包与领取。 */
@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    /**
     * 券包里的一张券。
     *
     * bucket 是给前端分栏用的三态：AVAILABLE 可用 / USED 已使用 / EXPIRED 已过期。
     * 被未支付订单占用（LOCKED）的券归入「已使用」——它既不能再用于别的单，
     * 放在「可用」里会让粉丝以为还能再用一次。
     */
    public record WalletItem(Long userCouponId, Long couponId, String name,
                             long amountCents, long thresholdCents,
                             Long serviceItemId, LocalDateTime expiresAt,
                             String bucket, String statusLabel, String remark) {
    }

    @GetMapping("/mine")
    public ApiResponse<List<WalletItem>> wallet() {
        long userId = CurrentUserHolder.requireUserId();
        return ApiResponse.ok(couponService.wallet(userId).stream().map(CouponController::toItem).toList());
    }

    /** 某笔订单当前可用的券，供下单页选择。 */
    @GetMapping("/usable")
    public ApiResponse<List<WalletItem>> usable(@RequestParam long originalCents,
                                                @RequestParam(required = false) Long serviceItemId) {
        long userId = CurrentUserHolder.requireUserId();
        return ApiResponse.ok(couponService.usableFor(userId, originalCents, serviceItemId)
                .stream().map(CouponController::toItem).toList());
    }

    /** 领取。重复领取被拒绝。 */
    @PostMapping("/{couponId}/claim")
    public ApiResponse<WalletItem> claim(@PathVariable long couponId) {
        long userId = CurrentUserHolder.requireUserId();
        UserCoupon owned = couponService.claim(userId, couponId);
        return ApiResponse.ok(toItem(new CouponService.OwnedCoupon(
                owned, couponService.require(couponId))));
    }

    static WalletItem toItem(CouponService.OwnedCoupon o) {
        return new WalletItem(
                o.owned().getId(), o.coupon().getId(), o.coupon().getName(),
                o.coupon().getAmountCents(), o.coupon().getThresholdCents(),
                o.coupon().getServiceItemId(), o.owned().getExpiresAt(),
                bucketOf(o.owned().getStatus()), labelOf(o.owned().getStatus()),
                o.owned().getRemark());
    }

    /** 四个内部状态折成粉丝看到的三栏。 */
    static String bucketOf(String status) {
        return switch (status) {
            case UserCoupon.AVAILABLE -> "AVAILABLE";
            case UserCoupon.LOCKED, UserCoupon.USED -> "USED";
            default -> "EXPIRED";
        };
    }

    static String labelOf(String status) {
        return switch (status) {
            case UserCoupon.AVAILABLE -> "可用";
            case UserCoupon.LOCKED -> "使用中";
            case UserCoupon.USED -> "已使用";
            default -> "已过期";
        };
    }
}
