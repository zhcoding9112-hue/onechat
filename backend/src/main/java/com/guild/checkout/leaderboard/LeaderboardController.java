package com.guild.checkout.leaderboard;

import com.guild.checkout.auth.PublicApi;
import com.guild.checkout.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 打赏榜单。
 *
 * 返回体刻意只含主播侧字段：榜单 MUST NOT 展示任何打赏人的身份信息，
 * 所以 fan_user_id 这类字段在服务层就没被查出来，不是在这里过滤掉的。
 */
@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private static final int DEFAULT_LIMIT = 20;

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    public record BoardView(String periodType, String periodTypeLabel, String periodKey,
                            boolean current, List<LeaderboardService.Row> rows) {
    }

    @GetMapping
    public ApiResponse<BoardView> board(@RequestParam(defaultValue = "DAY") String type,
                                        @RequestParam(required = false) String period,
                                        @RequestParam(defaultValue = "20") int limit) {
        PeriodType periodType;
        try {
            periodType = PeriodType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            periodType = PeriodType.DAY;
        }
        String key = period == null || period.isBlank() ? periodType.currentKey() : period;
        List<LeaderboardService.Row> rows = leaderboardService.top(
                periodType, key, limit <= 0 ? DEFAULT_LIMIT : limit);
        return ApiResponse.ok(new BoardView(periodType.name(), periodType.label(), key,
                key.equals(periodType.currentKey()), rows));
    }

    /** 三种周期各自的当前标识，供前端展示「今日 / 本周 / 本月」。 */
    @GetMapping("/periods")
    @PublicApi
    public ApiResponse<Map<String, String>> periods() {
        return ApiResponse.ok(Map.of(
                PeriodType.DAY.name(), PeriodType.DAY.currentKey(),
                PeriodType.WEEK.name(), PeriodType.WEEK.currentKey(),
                PeriodType.MONTH.name(), PeriodType.MONTH.currentKey()));
    }
}
