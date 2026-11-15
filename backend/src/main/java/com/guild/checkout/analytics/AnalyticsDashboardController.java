package com.guild.checkout.analytics;

import com.guild.checkout.auth.RequireRole;
import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.pricing.ServiceItem;
import com.guild.checkout.pricing.ServiceItemMapper;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.TalentService;
import com.guild.checkout.user.UserRole;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 转化漏斗看板。整类要求运营角色——粉丝与主播访问一律无权限。
 */
@RestController
@RequestMapping("/api/admin/analytics")
@RequireRole(UserRole.OPERATOR)
public class AnalyticsDashboardController {

    private final FunnelService funnelService;
    private final TalentService talentService;
    private final ServiceItemMapper itemMapper;

    public AnalyticsDashboardController(FunnelService funnelService,
                                        TalentService talentService,
                                        ServiceItemMapper itemMapper) {
        this.funnelService = funnelService;
        this.talentService = talentService;
        this.itemMapper = itemMapper;
    }

    /** 首屏：漏斗 + 四项核心指标。时间范围不传时默认最近 7 天。 */
    @GetMapping("/funnel")
    public ApiResponse<FunnelService.Overview> funnel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(6) : from;
        return ApiResponse.ok(funnelService.overview(start, end));
    }

    @GetMapping("/by-talent")
    public ApiResponse<List<FunnelService.Breakdown>> byTalent(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(6) : from;
        Map<Long, String> names = talentService.listAll().stream()
                .collect(Collectors.toMap(Talent::getId, Talent::getNickname,
                        (a, b) -> a, LinkedHashMap::new));
        return ApiResponse.ok(funnelService.byTalent(start, end, names));
    }

    @GetMapping("/by-service")
    public ApiResponse<List<FunnelService.Breakdown>> byService(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(6) : from;
        Map<Long, String> names = itemMapper.selectList(null).stream()
                .collect(Collectors.toMap(ServiceItem::getId, ServiceItem::getName,
                        (a, b) -> a, LinkedHashMap::new));
        return ApiResponse.ok(funnelService.byServiceItem(start, end, names));
    }

    public record ThresholdRequest(
            @Min(value = 0, message = "阈值不得小于 0 基点")
            @Max(value = 10000, message = "阈值不得大于 10000 基点") int acceptRateThresholdBp) {
    }

    @GetMapping("/settings")
    public ApiResponse<Map<String, Object>> settings() {
        return ApiResponse.ok(Map.of("acceptRateThresholdBp", funnelService.threshold()));
    }

    @PutMapping("/settings")
    public ApiResponse<AnalyticsSetting> updateSettings(
            @jakarta.validation.Valid @RequestBody ThresholdRequest req) {
        return ApiResponse.ok(funnelService.updateThreshold(req.acceptRateThresholdBp()));
    }
}
