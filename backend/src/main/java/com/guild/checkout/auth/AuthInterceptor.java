package com.guild.checkout.auth;

import com.guild.checkout.common.exception.ForbiddenException;
import com.guild.checkout.common.exception.UnauthenticatedException;
import com.guild.checkout.user.AppUser;
import com.guild.checkout.user.AppUserService;
import com.guild.checkout.user.Roles;
import com.guild.checkout.user.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.lang.Nullable;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 会话与角色校验。
 *
 * 判定顺序：
 *   1. 标了 {@link PublicApi} 的接口直接放行。
 *   2. 其余接口一律要求已登录，否则 401（而不是 403）——前端据此触发重新授权。
 *   3. 标了 {@link RequireRole} 的接口再校验角色，不满足则 403。
 *
 * 默认要求登录而非默认放行：新增接口时漏标注解只会多一次授权，不会泄露数据。
 */
public class AuthInterceptor implements HandlerInterceptor {

    private final AppUserService userService;

    public AuthInterceptor(AppUserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        if (findAnnotation(method, PublicApi.class) != null) {
            return true;
        }

        CurrentUser current = resolveCurrentUser(request);
        if (current == null) {
            throw new UnauthenticatedException();
        }
        CurrentUserHolder.set(current);

        RequireRole requireRole = findAnnotation(method, RequireRole.class);
        if (requireRole != null && !satisfied(current.roles(), requireRole)) {
            throw new ForbiddenException();
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, @Nullable Exception ex) {
        CurrentUserHolder.clear();
    }

    private CurrentUser resolveCurrentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object userId = session.getAttribute(WechatAuthController.SESSION_USER_ID);
        if (!(userId instanceof Number id)) {
            return null;
        }
        // 角色每次从库里读：运营刚授予的主播角色应当立即生效，不能等会话过期。
        AppUser user = userService.requireById(id.longValue());
        if (user == null) {
            // 用户被删除但会话还在：视为未登录，让其重新授权。
            session.invalidate();
            return null;
        }
        return new CurrentUser(user.getId(), Roles.sanitize(user.getRoles() == null ? 0 : user.getRoles()));
    }

    private static boolean satisfied(int roles, RequireRole requireRole) {
        UserRole[] required = requireRole.value();
        if (required.length == 0) {
            return true;
        }
        return requireRole.requireAll() ? Roles.hasAll(roles, required) : Roles.hasAny(roles, required);
    }

    /** 方法上的声明覆盖类上的声明。 */
    private static <A extends java.lang.annotation.Annotation> A findAnnotation(
            HandlerMethod method, Class<A> type) {
        A onMethod = method.getMethodAnnotation(type);
        return onMethod != null ? onMethod : method.getBeanType().getAnnotation(type);
    }
}
