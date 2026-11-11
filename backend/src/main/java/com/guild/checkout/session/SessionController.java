package com.guild.checkout.session;

import com.guild.checkout.auth.CurrentUser;
import com.guild.checkout.auth.CurrentUserHolder;
import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.user.AppUser;
import com.guild.checkout.user.AppUserService;
import com.guild.checkout.user.UserDisplay;
import com.guild.checkout.user.UserRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 当前会话。未登录由鉴权拦截器统一拦下并返回未认证。
 */
@RestController
@RequestMapping("/api/session")
public class SessionController {

    private final AppUserService userService;

    public SessionController(AppUserService userService) {
        this.userService = userService;
    }

    public record CurrentUserView(
            Long userId,
            String nickname,
            String avatarUrl,
            /** 昵称头像是否为占位值，运营后台据此提示资料缺失。 */
            boolean placeholderProfile,
            List<String> roles,
            boolean talent,
            boolean operator) {
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserView> me() {
        CurrentUser current = CurrentUserHolder.require();
        AppUser user = userService.requireById(current.userId());
        String rawNickname = user == null ? null : user.getNickname();
        String rawAvatar = user == null ? null : user.getAvatarUrl();
        return ApiResponse.ok(new CurrentUserView(
                current.userId(),
                UserDisplay.nickname(rawNickname, current.userId()),
                UserDisplay.avatarUrl(rawAvatar),
                UserDisplay.isPlaceholderNickname(rawNickname),
                current.roleSet().stream().map(Enum::name).sorted().toList(),
                current.hasRole(UserRole.TALENT),
                current.hasRole(UserRole.OPERATOR)));
    }
}
