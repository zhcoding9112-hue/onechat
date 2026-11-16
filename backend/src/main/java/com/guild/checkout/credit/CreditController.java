package com.guild.checkout.credit;

import com.guild.checkout.auth.CurrentUserHolder;
import com.guild.checkout.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 粉丝端元子：只读。
 *
 * 这里刻意只有 GET。本阶段元子不开放充值、购买、兑换与提现，所以不存在对应的
 * POST——入口不是「前端藏起来」，而是后端没有这个接口。
 */
@RestController
@RequestMapping("/api/credits")
public class CreditController {

    private final CreditService creditService;

    public CreditController(CreditService creditService) {
        this.creditService = creditService;
    }

    /** 一条流水。不透出操作者，粉丝不需要也不应该看到是哪个运营动的账。 */
    public record TransactionView(Long id, long delta, long balanceAfter,
                                  String type, String typeLabel,
                                  String reason, LocalDateTime createdAt) {
    }

    public record TransactionPage(List<TransactionView> items, long total, boolean hasMore) {
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Long>> balance() {
        long userId = CurrentUserHolder.requireUserId();
        return ApiResponse.ok(Map.of("balance", creditService.balanceOf(userId)));
    }

    @GetMapping("/me/transactions")
    public ApiResponse<TransactionPage> transactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        long userId = CurrentUserHolder.requireUserId();
        int limit = Math.min(Math.max(size, 1), 100);
        List<CreditTransaction> rows = creditService.listTransactions(userId, page, limit);
        long total = creditService.countTransactions(userId);

        List<TransactionView> items = rows.stream().map(tx -> new TransactionView(
                tx.getId(), tx.getDelta(), tx.getBalanceAfter(),
                tx.getType(), label(tx.getType()), tx.getReason(), tx.getCreatedAt())).toList();
        boolean hasMore = (long) Math.max(page, 0) * limit + items.size() < total;
        return ApiResponse.ok(new TransactionPage(items, total, hasMore));
    }

    private static String label(String type) {
        if (type == null) {
            return "变动";
        }
        return switch (type) {
            case "GRANT" -> "发放";
            case "ADJUST" -> "调整";
            case "DEDUCT" -> "消耗";
            default -> "变动";
        };
    }
}
