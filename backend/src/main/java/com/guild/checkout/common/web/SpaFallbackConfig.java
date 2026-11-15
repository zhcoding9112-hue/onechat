package com.guild.checkout.common.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 单页应用的路径回退。
 *
 * 前端用 history 模式路由，直接访问 /orders/123 这类深层路径时，
 * 服务端没有对应的静态文件，必须回退到 index.html 交给前端路由处理。
 *
 * /api 与 /actuator 不在回退范围内：那两个前缀下找不到就应当是 404，
 * 回退成 index.html 会让接口调用拿到一坨 HTML，排查起来格外费劲。
 */
@Configuration
public class SpaFallbackConfig implements WebMvcConfigurer {

    /**
     * 可以回退的路径段。
     *
     * 两条约束缺一不可：
     *   - 排除 api / actuator / uploads / __ 前缀：那些下面找不到就该是 404，
     *     回退成 index.html 会让接口调用拿到一坨 HTML，排查起来格外费劲。
     *   - 排除含点的段：index.html 本身若能匹配上，转发就会指回自己，
     *     结果是 StackOverflowError 而不是首页。
     */
    private static final String NOT_SPA = "^(?!api$|actuator$|uploads$|__)[^.]*$";

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 一级到三级路径各一条；排除含点的路径（那是静态资源）
        registry.addViewController("/{p1:" + NOT_SPA + "}")
                .setViewName("forward:/index.html");
        registry.addViewController("/{p1:" + NOT_SPA + "}/{p2:[^.]*}")
                .setViewName("forward:/index.html");
        registry.addViewController("/{p1:" + NOT_SPA + "}/{p2}/{p3:[^.]*}")
                .setViewName("forward:/index.html");
    }
}
