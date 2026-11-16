package com.guild.checkout.level;

import com.guild.checkout.auth.RequireRole;
import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.user.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 运营侧的粉丝等级门槛。整类要求运营角色。 */
@RestController
@RequestMapping("/api/admin/fan-levels")
@RequireRole(UserRole.OPERATOR)
public class FanLevelAdminController {

    private final FanLevelService levelService;

    public FanLevelAdminController(FanLevelService levelService) {
        this.levelService = levelService;
    }

    public record ThresholdRequest(
            @NotNull(message = "门槛为必填") Long thresholdCents,
            @Size(max = 32, message = "名称不超过 32 字符") String name) {
    }

    @GetMapping
    public ApiResponse<List<FanLevelSetting>> list() {
        return ApiResponse.ok(levelService.thresholds());
    }

    @PutMapping("/{level}")
    public ApiResponse<FanLevelSetting> update(@PathVariable int level,
                                               @Valid @RequestBody ThresholdRequest req) {
        return ApiResponse.ok(levelService.updateThreshold(level, req.thresholdCents(), req.name()));
    }
}
