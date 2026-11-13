package com.guild.checkout.tipping;

import com.guild.checkout.auth.CurrentUser;
import com.guild.checkout.auth.CurrentUserHolder;
import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.TalentService;
import com.guild.checkout.user.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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

/**
 * 粉丝端打赏。
 */
@RestController
@RequestMapping("/api/tips")
public class TipController {

    private final TipService tipService;
    private final TalentService talentService;

    public TipController(TipService tipService, TalentService talentService) {
        this.tipService = tipService;
        this.talentService = talentService;
    }

    public record CreateRequest(
            @NotNull(message = "请选择主播") Long talentId,
            @Min(value = 1, message = "打赏金额必须大于零") long amountCents,
            @Size(max = 200, message = "留言不超过 200 字") String message) {
    }

    public record TipView(
            Long id,
            String tipRef,
            Long talentId,
            String talentNickname,
            long amountCents,
            String status,
            String statusLabel,
            /** 仅目标主播与运营可见，其余为 null。 */
            String message,
            LocalDateTime payDeadline,
            LocalDateTime paidAt,
            LocalDateTime createdAt) {
    }

    public record TipLimits(long minCents, long maxCents) {
    }

    /** 打赏金额范围，粉丝端据此做输入提示。 */
    @GetMapping("/limits")
    public ApiResponse<TipLimits> limits() {
        TipSetting setting = tipService.requireSetting();
        return ApiResponse.ok(new TipLimits(setting.getMinCents(), setting.getMaxCents()));
    }

    /** 发起打赏。 */
    @PostMapping
    public ApiResponse<TipView> create(@Valid @RequestBody CreateRequest req) {
        long userId = CurrentUserHolder.requireUserId();
        TipRecord tip = tipService.create(
                userId, req.talentId(), req.amountCents(), req.message());
        return ApiResponse.ok(toView(tip, true));
    }

    /** 我的打赏记录。 */
    @GetMapping
    public ApiResponse<List<TipView>> myTips() {
        long userId = CurrentUserHolder.requireUserId();
        return ApiResponse.ok(tipService.listByFan(userId).stream()
                .map(t -> toView(t, true))
                .toList());
    }

    @GetMapping("/{tipId}")
    public ApiResponse<TipView> detail(@PathVariable long tipId) {
        CurrentUser current = CurrentUserHolder.require();
        TipRecord tip = tipService.require(tipId);
        tipService.requireReadable(tip, current.userId(), current.hasRole(UserRole.OPERATOR));
        // 打赏本人也能看到自己写的留言；无关者根本读不到这条记录
        return ApiResponse.ok(toView(tip, true));
    }

    private TipView toView(TipRecord tip, boolean withMessage) {
        Talent talent = talentService.find(tip.getTalentId());
        return new TipView(
                tip.getId(), tip.getTipRef(), tip.getTalentId(),
                talent == null ? null : talent.getNickname(),
                tip.getAmountCents(),
                tip.statusEnum().name(), tip.statusEnum().label(),
                withMessage ? tip.getMessage() : null,
                tip.getPayDeadline(), tip.getPaidAt(), tip.getCreatedAt());
    }
}
