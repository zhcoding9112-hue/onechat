package com.guild.checkout.level;

import com.guild.checkout.auth.CurrentUserHolder;
import com.guild.checkout.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 粉丝等级。 */
@RestController
@RequestMapping("/api/fan-levels")
public class FanLevelController {

    private final FanLevelService levelService;

    public FanLevelController(FanLevelService levelService) {
        this.levelService = levelService;
    }

    @GetMapping("/me")
    public ApiResponse<FanLevelService.FanLevel> mine() {
        return ApiResponse.ok(levelService.levelOf(CurrentUserHolder.requireUserId()));
    }

    /** 全部档位，供个人中心展示「还差多少到下一级」的刻度。 */
    @GetMapping
    public ApiResponse<List<FanLevelSetting>> tiers() {
        return ApiResponse.ok(levelService.thresholds());
    }
}
