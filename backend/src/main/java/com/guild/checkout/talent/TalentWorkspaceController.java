package com.guild.checkout.talent;

import com.guild.checkout.auth.RequireRole;
import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.common.exception.NotImplementedException;
import com.guild.checkout.user.UserRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 主播工作台。整类接口要求主播角色，粉丝访问一律无权限。
 *
 * 当前只落了路由与角色守卫；待接单列表、接单与标记完成、在线状态、收入与评分
 * 由 talent-workspace 相关任务填充。
 */
@RestController
@RequestMapping("/api/talent/workspace")
@RequireRole(UserRole.TALENT)
public class TalentWorkspaceController {

    @GetMapping("/pending-orders")
    public ApiResponse<Void> pendingOrders() {
        throw new NotImplementedException("待接单列表尚未实现");
    }
}
