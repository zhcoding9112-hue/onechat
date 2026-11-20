package com.guild.checkout.tipping;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.auth.RequireRole;
import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.TalentService;
import com.guild.checkout.user.AppUser;
import com.guild.checkout.user.AppUserMapper;
import com.guild.checkout.user.UserDisplay;
import com.guild.checkout.user.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 运营侧的打赏处置与金额配置。 */
@RestController
@RequestMapping("/api/admin/tips")
@RequireRole(UserRole.OPERATOR)
public class TipAdminController {

    private final TipService tipService;
    private final TipRecordMapper tipMapper;
    private final TalentService talentService;
    private final AppUserMapper userMapper;

    public TipAdminController(TipService tipService,
                              TipRecordMapper tipMapper,
                              TalentService talentService,
                              AppUserMapper userMapper) {
        this.tipService = tipService;
        this.tipMapper = tipMapper;
        this.talentService = talentService;
        this.userMapper = userMapper;
    }

    public record ConfirmRequest(
            @Min(value = 0, message = "实收金额不得为负") long paidCents,
            @Size(max = 255) String note) {
    }

    public record LimitRequest(Long minCents, Long maxCents) {
    }

    public record RefundRequest(String reason) {
    }

    public record CancelRequest(
            @NotBlank(message = "取消原因为必填")
            @Size(max = 255, message = "取消原因不超过 255 字符") String reason) {
    }

    /**
     * 运营看到的一笔打赏。
     *
     * 比实体多了主播与粉丝昵称：运营要对账、要处理纠纷，光有 id 没法用。
     * 留言按 TipService.requireReadable 的规则对运营开放。
     */
    public record TipAdminView(
            Long id,
            String tipRef,
            Long fanUserId,
            String fanNickname,
            Long talentId,
            String talentNickname,
            long amountCents,
            Long paidCents,
            Integer commissionRateBp,
            Long guildCents,
            Long talentCents,
            String status,
            String statusLabel,
            String message,
            LocalDateTime payDeadline,
            LocalDateTime paidAt,
            LocalDateTime closedAt,
            String closeReason,
            LocalDateTime createdAt) {
    }

    @GetMapping
    public ApiResponse<List<TipAdminView>> list(@RequestParam(required = false) String status) {
        var query = Wrappers.<TipRecord>lambdaQuery();
        if (status != null && !status.isBlank()) {
            query.eq(TipRecord::getStatus, TipStatus.valueOf(status).name());
        }
        List<TipRecord> tips = tipMapper.selectList(query.orderByDesc(TipRecord::getId));
        if (tips.isEmpty()) {
            return ApiResponse.ok(List.of());
        }

        // 昵称一次批量查出来，不在循环里逐条查
        Map<Long, String> fanNames = userMapper.selectBatchIds(
                        tips.stream().map(TipRecord::getFanUserId).filter(java.util.Objects::nonNull)
                                .distinct().toList())
                .stream()
                .collect(Collectors.toMap(AppUser::getId,
                        u -> UserDisplay.nickname(u.getNickname(), u.getId())));

        return ApiResponse.ok(tips.stream().map(t -> {
            Talent talent = talentService.find(t.getTalentId());
            TipStatus st = t.statusEnum();
            return new TipAdminView(
                    t.getId(), t.getTipRef(),
                    t.getFanUserId(),
                    fanNames.getOrDefault(t.getFanUserId(),
                            UserDisplay.nickname(null, t.getFanUserId())),
                    t.getTalentId(), talent == null ? null : talent.getNickname(),
                    t.getAmountCents(), t.getPaidCents(),
                    t.getCommissionRateBp(), t.getGuildCents(), t.getTalentCents(),
                    st.name(), st.label(), t.getMessage(),
                    t.getPayDeadline(), t.getPaidAt(), t.getClosedAt(), t.getCloseReason(),
                    t.getCreatedAt());
        }).toList());
    }

    @PutMapping("/{tipId}/confirm")
    public ApiResponse<TipRecord> confirm(@PathVariable long tipId,
                                          @Valid @RequestBody ConfirmRequest req) {
        return ApiResponse.ok(tipService.confirmPayment(tipId, req.paidCents(), req.note()));
    }

    /** 标记已退款：台账冲销，榜单按这笔打赏原本计入的周期扣回。 */
    @PutMapping("/{tipId}/refund")
    public ApiResponse<TipRecord> refund(@PathVariable long tipId,
                                         @RequestBody RefundRequest req) {
        return ApiResponse.ok(tipService.markRefunded(tipId, req.reason()));
    }

    /**
     * 取消一笔还没收款的打赏。
     *
     * 打赏没有超时回收——OrderTimeoutScheduler 只处理服务订单，打赏的 payDeadline
     * 到了也没人管。粉丝发起后不付款的记录会一直挂在待支付里，只能靠这里清掉。
     */
    @PutMapping("/{tipId}/cancel")
    public ApiResponse<TipRecord> cancel(@PathVariable long tipId,
                                         @Valid @RequestBody CancelRequest req) {
        return ApiResponse.ok(tipService.cancel(tipId, req.reason().trim()));
    }

    @GetMapping("/limits")
    public ApiResponse<TipSetting> limits() {
        return ApiResponse.ok(tipService.requireSetting());
    }

    @PutMapping("/limits")
    public ApiResponse<TipSetting> updateLimits(@RequestBody LimitRequest req) {
        return ApiResponse.ok(tipService.updateSetting(req.minCents(), req.maxCents()));
    }
}
