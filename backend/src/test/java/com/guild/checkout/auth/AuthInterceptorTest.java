package com.guild.checkout.auth;

import com.guild.checkout.common.exception.ForbiddenException;
import com.guild.checkout.common.exception.UnauthenticatedException;
import com.guild.checkout.user.AppUser;
import com.guild.checkout.user.AppUserService;
import com.guild.checkout.user.Roles;
import com.guild.checkout.user.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 鉴权拦截器的判定规则。重点是「未标注解默认要求登录」与「角色不足返回无权限」。
 */
class AuthInterceptorTest {

    private AppUserService userService;
    private AuthInterceptor interceptor;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        userService = mock(AppUserService.class);
        interceptor = new AuthInterceptor(userService);
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        interceptor.afterCompletion(new MockHttpServletRequest(), response, null, null);
    }

    // ---------- 未登录 ----------

    @Test
    void 未登录访问需登录接口抛未认证() {
        assertThatThrownBy(() -> preHandle(loggedOut(), "needsLogin"))
                .isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void 未登录访问公开接口放行() {
        assertThat(preHandle(loggedOut(), "publicEndpoint")).isTrue();
        assertThat(CurrentUserHolder.get()).isNull();
    }

    @Test
    void 未登录访问主播工作台接口抛未认证而非无权限() {
        // 先给 401 让前端去重新授权，而不是 403 让用户看到"没有权限"的死胡同
        assertThatThrownBy(() -> preHandle(loggedOut(), "talentOnly"))
                .isInstanceOf(UnauthenticatedException.class);
    }

    // ---------- 粉丝 ----------

    @Test
    void 粉丝访问主播工作台接口返回无权限() {
        assertThatThrownBy(() -> preHandle(loggedInAs(UserRole.FAN), "talentOnly"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void 粉丝访问看板接口返回无权限() {
        assertThatThrownBy(() -> preHandle(loggedInAs(UserRole.FAN), "operatorOnly"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void 粉丝访问仅需登录的接口放行且上下文可用() {
        assertThat(preHandle(loggedInAs(UserRole.FAN), "needsLogin")).isTrue();
        CurrentUser current = CurrentUserHolder.require();
        assertThat(current.userId()).isEqualTo(7L);
        assertThat(current.hasRole(UserRole.FAN)).isTrue();
        assertThat(current.hasRole(UserRole.TALENT)).isFalse();
    }

    // ---------- 主播与运营 ----------

    @Test
    void 主播访问主播工作台接口放行() {
        assertThat(preHandle(loggedInAs(UserRole.FAN, UserRole.TALENT), "talentOnly")).isTrue();
    }

    @Test
    void 主播访问看板接口返回无权限() {
        assertThatThrownBy(() -> preHandle(loggedInAs(UserRole.FAN, UserRole.TALENT), "operatorOnly"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void 运营访问看板接口放行() {
        assertThat(preHandle(loggedInAs(UserRole.FAN, UserRole.OPERATOR), "operatorOnly")).isTrue();
    }

    @Test
    void 同时持有主播与运营角色者两类接口都可访问() {
        assertThat(preHandle(loggedInAs(UserRole.TALENT, UserRole.OPERATOR), "talentOnly")).isTrue();
        assertThat(preHandle(loggedInAs(UserRole.TALENT, UserRole.OPERATOR), "operatorOnly")).isTrue();
    }

    @Test
    void requireAll要求同时具备全部角色() {
        assertThatThrownBy(() -> preHandle(loggedInAs(UserRole.TALENT), "talentAndOperator"))
                .isInstanceOf(ForbiddenException.class);
        assertThat(preHandle(loggedInAs(UserRole.TALENT, UserRole.OPERATOR), "talentAndOperator")).isTrue();
    }

    @Test
    void 方法上的注解覆盖类上的注解() {
        // 类上要求运营，方法上改为仅需登录
        assertThat(preHandle(loggedInAs(UserRole.FAN), ClassLevelProbe.class, "overridden")).isTrue();
        // 类上的声明对未覆盖的方法仍然生效
        assertThatThrownBy(() -> preHandle(loggedInAs(UserRole.FAN), ClassLevelProbe.class, "inherited"))
                .isInstanceOf(ForbiddenException.class);
    }

    // ---------- 会话与库不一致 ----------

    @Test
    void 会话中的用户已被删除时视为未登录() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(WechatAuthController.SESSION_USER_ID, 999L);
        request.setSession(session);
        when(userService.requireById(anyLong())).thenReturn(null);

        assertThatThrownBy(() -> preHandle(request, "needsLogin"))
                .isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void 角色每次从库读取以便运营授权立即生效() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(WechatAuthController.SESSION_USER_ID, 7L);
        request.setSession(session);

        // 同一会话，库里角色从粉丝变为粉丝+主播
        when(userService.requireById(7L)).thenReturn(user(Roles.of(UserRole.FAN)));
        assertThatThrownBy(() -> preHandle(request, "talentOnly")).isInstanceOf(ForbiddenException.class);

        when(userService.requireById(7L)).thenReturn(user(Roles.of(UserRole.FAN, UserRole.TALENT)));
        assertThat(preHandle(request, "talentOnly")).isTrue();
    }

    @Test
    void 库中脏角色位不会被误判为已知角色() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(WechatAuthController.SESSION_USER_ID, 7L);
        request.setSession(session);
        when(userService.requireById(7L)).thenReturn(user(Roles.of(UserRole.FAN) | 0b1000_0000));

        assertThatThrownBy(() -> preHandle(request, "operatorOnly")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void 请求结束后上下文被清理() {
        preHandle(loggedInAs(UserRole.FAN), "needsLogin");
        assertThat(CurrentUserHolder.get()).isNotNull();

        interceptor.afterCompletion(new MockHttpServletRequest(), response, null, null);
        assertThat(CurrentUserHolder.get()).isNull();
    }

    @Test
    void 非控制器方法的handler直接放行() {
        assertThat(interceptor.preHandle(loggedOut(), response, new Object())).isTrue();
    }

    // ---------- 辅助 ----------

    private boolean preHandle(MockHttpServletRequest request, String methodName) {
        return preHandle(request, Probe.class, methodName);
    }

    private boolean preHandle(MockHttpServletRequest request, Class<?> type, String methodName) {
        try {
            Method method = type.getDeclaredMethod(methodName);
            return interceptor.preHandle(request, response, new HandlerMethod(type.getDeclaredConstructor().newInstance(), method));
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private MockHttpServletRequest loggedOut() {
        return new MockHttpServletRequest();
    }

    private MockHttpServletRequest loggedInAs(UserRole... roles) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(WechatAuthController.SESSION_USER_ID, 7L);
        request.setSession(session);
        when(userService.requireById(7L)).thenReturn(user(Roles.of(roles)));
        return request;
    }

    private static AppUser user(int roles) {
        AppUser user = new AppUser();
        user.setId(7L);
        user.setRoles(roles);
        return user;
    }

    /** 探针控制器：覆盖各种注解组合。 */
    public static class Probe {
        @GetMapping public void needsLogin() { }
        @GetMapping @PublicApi public void publicEndpoint() { }
        @GetMapping @RequireRole(UserRole.TALENT) public void talentOnly() { }
        @GetMapping @RequireRole(UserRole.OPERATOR) public void operatorOnly() { }
        @GetMapping @RequireRole(value = {UserRole.TALENT, UserRole.OPERATOR}, requireAll = true)
        public void talentAndOperator() { }
    }

    /** 探针控制器：类级注解与方法级覆盖。 */
    @RequireRole(UserRole.OPERATOR)
    public static class ClassLevelProbe {
        @GetMapping @RequireRole({}) public void overridden() { }
        @GetMapping public void inherited() { }
    }
}
