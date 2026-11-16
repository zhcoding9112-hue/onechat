package com.guild.checkout.tipping;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.auth.RequireRole;
import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.user.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 运营侧的打赏处置与金额配置。 */
@RestController
@RequestMapping("/api/admin/tips")
@RequireRole(UserRole.OPERATOR)
public class TipAdminController {

    private final TipService tipService;
    private final TipRecordMapper tipMapper;

    public TipAdminController(TipService tipService, TipRecordMapper tipMapper) {
        this.tipService = tipService;
        this.tipMapper = tipMapper;
    }

    public record ConfirmRequest(
            @Min(value = 0, message = "实收金额不得为负") long paidCents,
            @Size(max = 255) String note) {
    }

    public record LimitRequest(Long minCents, Long maxCents) {
    }

    public record RefundRequest(String reason) {
    }

    @GetMapping
    public ApiResponse<List<TipRecord>> list(@RequestParam(required = false) String status) {
        var query = Wrappers.<TipRecord>lambdaQuery();
        if (status != null && !status.isBlank()) {
            query.eq(TipRecord::getStatus, TipStatus.valueOf(status).name());
        }
        return ApiResponse.ok(tipMapper.selectList(query.orderByDesc(TipRecord::getId)));
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

    @GetMapping("/limits")
    public ApiResponse<TipSetting> limits() {
        return ApiResponse.ok(tipService.requireSetting());
    }

    @PutMapping("/limits")
    public ApiResponse<TipSetting> updateLimits(@RequestBody LimitRequest req) {
        return ApiResponse.ok(tipService.updateSetting(req.minCents(), req.maxCents()));
    }
}
