package com.guild.checkout.session;

import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.common.exception.UnauthenticatedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 当前会话查询。
 *
 * 本阶段仅作为「受保护接口」的最小实现，用于打通前端请求封装与未认证处理；
 * 真正的微信授权建立会话、会话续期与角色鉴权见 wechat-auth 相关任务。
 */
@RestController
@RequestMapping("/api/session")
public class SessionController {

    static final String USER_ID_KEY = "APP_USER_ID";

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object userId = session == null ? null : session.getAttribute(USER_ID_KEY);
        if (userId == null) {
            throw new UnauthenticatedException();
        }
        return ApiResponse.ok(Map.of("userId", userId));
    }
}
