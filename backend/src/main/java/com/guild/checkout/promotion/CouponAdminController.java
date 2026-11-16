package com.guild.checkout.promotion;

import com.guild.checkout.auth.RequireRole;
import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.user.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 运营侧的券定义与发放。整类要求运营角色。 */
@RestController
@RequestMapping("/api/admin/coupons")
@RequireRole(UserRole.OPERATOR)
public class CouponAdminController {

    private final CouponService couponService;

    public CouponAdminController(CouponService couponService) {
        this.couponService = couponService;
    }

    public record CreateRequest(
            @NotBlank(message = "券名为必填")
            @Size(max = 64, message = "券名不超过 64 字符") String name,
            @NotNull(message = "面额为必填") Long amountCents,
            @NotNull(message = "门槛为必填") Long thresholdCents,
            Long serviceItemId,
            @NotNull(message = "生效时间为必填") LocalDateTime validFrom,
            @NotNull(message = "失效时间为必填") LocalDateTime validTo) {
    }

    /** 发放对象：userIds 为空表示全体粉丝。 */
    public record GrantRequest(List<Long> userIds) {
    }

    @GetMapping
    public ApiResponse<List<Coupon>> list() {
        return ApiResponse.ok(couponService.listCoupons());
    }

    @PostMapping
    public ApiResponse<Coupon> create(@Valid @RequestBody CreateRequest req) {
        return ApiResponse.ok(couponService.create(new CouponService.CouponForm(
                req.name(), req.amountCents(), req.thresholdCents(),
                req.serviceItemId(), req.validFrom(), req.validTo())));
    }

    @PostMapping("/{couponId}/grant")
    public ApiResponse<Map<String, Integer>> grant(@PathVariable long couponId,
                                                   @RequestBody(required = false) GrantRequest req) {
        int granted = req == null || req.userIds() == null || req.userIds().isEmpty()
                ? couponService.grantToAll(couponId)
                : couponService.grantTo(couponId, req.userIds());
        return ApiResponse.ok(Map.of("granted", granted));
    }
}
