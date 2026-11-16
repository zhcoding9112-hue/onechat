package com.guild.checkout.credit;

import com.guild.checkout.auth.CurrentUserHolder;
import com.guild.checkout.auth.RequireRole;
import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.user.AppUser;
import com.guild.checkout.user.AppUserService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/** 运营侧的元子发放与调整。整类要求运营角色。 */
@RestController
@RequestMapping("/api/admin/credits")
@RequireRole(UserRole.OPERATOR)
public class CreditAdminController {

    private final CreditService creditService;
    private final AppUserService userService;

    public CreditAdminController(CreditService creditService, AppUserService userService) {
        this.creditService = creditService;
        this.userService = userService;
    }

    /** 原因必填，这里由 @NotBlank 挡住；服务层还会再挡一次（直接调用服务的路径也要受约束）。 */
    public record GrantRequest(
            @NotNull(message = "发放数量为必填") Long amount,
            @NotBlank(message = "原因为必填")
            @Size(max = 255, message = "原因不超过 255 字符") String reason) {
    }

    public record AdjustRequest(
            @NotNull(message = "调整数量为必填") Long delta,
            @NotBlank(message = "原因为必填")
            @Size(max = 255, message = "原因不超过 255 字符") String reason) {
    }

    public record TransactionView(Long id, long delta, long balanceAfter, String type,
                                  String reason, Long operatorUserId, LocalDateTime createdAt) {
    }

    public record AccountView(Long userId, String nickname, long balance,
                              List<TransactionView> transactions) {
    }

    @GetMapping("/{userId}")
    public ApiResponse<AccountView> account(@PathVariable long userId,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        AppUser user = userService.requireById(userId);
        List<TransactionView> rows = creditService.listTransactions(userId, page, size).stream()
                .map(tx -> new TransactionView(tx.getId(), tx.getDelta(), tx.getBalanceAfter(),
                        tx.getType(), tx.getReason(), tx.getOperatorUserId(), tx.getCreatedAt()))
                .toList();
        return ApiResponse.ok(new AccountView(
                user.getId(), user.getNickname(), creditService.balanceOf(userId), rows));
    }

    /** 发放：只增不减，数量必须为正。 */
    @PostMapping("/{userId}/grant")
    public ApiResponse<TransactionView> grant(@PathVariable long userId,
                                              @Valid @RequestBody GrantRequest req) {
        userService.requireById(userId);
        long amount = req.amount();
        if (amount <= 0) {
            throw new BusinessException("发放数量必须为正数");
        }
        return ApiResponse.ok(toView(creditService.apply(
                userId, amount, CreditType.GRANT, req.reason(), CurrentUserHolder.requireUserId())));
    }

    /** 差错调整：可正可负。 */
    @PostMapping("/{userId}/adjust")
    public ApiResponse<TransactionView> adjust(@PathVariable long userId,
                                               @Valid @RequestBody AdjustRequest req) {
        userService.requireById(userId);
        return ApiResponse.ok(toView(creditService.apply(
                userId, req.delta(), CreditType.ADJUST, req.reason(),
                CurrentUserHolder.requireUserId())));
    }

    private TransactionView toView(CreditTransaction tx) {
        return new TransactionView(tx.getId(), tx.getDelta(), tx.getBalanceAfter(),
                tx.getType(), tx.getReason(), tx.getOperatorUserId(), tx.getCreatedAt());
    }
}
