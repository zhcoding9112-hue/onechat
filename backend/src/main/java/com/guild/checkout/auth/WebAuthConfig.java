package com.guild.checkout.auth;

import com.guild.checkout.user.AppUserService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 把鉴权拦截器挂到全部 /api 路径上。
 * actuator 与前端静态资源不在 /api 之下，自然不受影响。
 */
@Configuration
public class WebAuthConfig implements WebMvcConfigurer {

    private final AppUserService userService;

    public WebAuthConfig(AppUserService userService) {
        this.userService = userService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthInterceptor(userService))
                .addPathPatterns("/api/**");
    }
}
