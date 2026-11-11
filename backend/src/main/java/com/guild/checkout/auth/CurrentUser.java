package com.guild.checkout.auth;

import com.guild.checkout.user.Roles;
import com.guild.checkout.user.UserRole;

import java.util.Set;

/**
 * 当前请求的登录用户。由拦截器放入 {@link CurrentUserHolder}，控制器按需取用。
 */
public record CurrentUser(Long userId, int roles) {

    public boolean hasRole(UserRole role) {
        return Roles.has(roles, role);
    }

    public Set<UserRole> roleSet() {
        return Roles.toSet(roles);
    }
}
