package com.guild.checkout.analytics;

import com.guild.checkout.auth.RequireRole;
import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.common.exception.NotImplementedException;
import com.guild.checkout.user.UserRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 转化漏斗看板。整类接口要求运营角色，粉丝与主播访问一律无权限。
 *
 * 当前只落了路由与角色守卫；漏斗与核心指标由 analytics-funnel 相关任务填充。
 */
@RestController
@RequestMapping("/api/admin/analytics")
@RequireRole(UserRole.OPERATOR)
public class AnalyticsDashboardController {

    @GetMapping("/funnel")
    public ApiResponse<Void> funnel() {
        throw new NotImplementedException("转化漏斗看板尚未实现");
    }
}
