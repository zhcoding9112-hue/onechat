package com.guild.checkout.auth;

import com.guild.checkout.auth.wechat.WechatOAuthClient;
import com.guild.checkout.auth.wechat.WechatOAuthException;
import com.guild.checkout.auth.wechat.WechatOAuthProperties;
import com.guild.checkout.auth.wechat.WechatUserInfo;
import com.guild.checkout.user.AppUser;
import com.guild.checkout.user.AppUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 服务号网页授权：发起跳转与接收回调。
 *
 * 流程：前端访问 /api/auth/wechat/authorize?redirect=原页面
 *   → 302 到微信授权页
 *   → 微信带 code 回跳 /api/auth/wechat/callback
 *   → 换取 openid、建号或更新资料、建立会话
 *   → 302 回原页面
 */
@RestController
@RequestMapping("/api/auth/wechat")
@PublicApi
public class WechatAuthController {

    private static final Logger log = LoggerFactory.getLogger(WechatAuthController.class);

    /** 会话中存放已登录用户 id 的键。 */
    public static final String SESSION_USER_ID = "APP_USER_ID";

    private static final String SESSION_STATE = "WECHAT_OAUTH_STATE";
    private static final String SESSION_STATE_AT = "WECHAT_OAUTH_STATE_AT";
    private static final String SESSION_REDIRECT = "WECHAT_OAUTH_REDIRECT";

    private static final String CALLBACK_PATH = "/api/auth/wechat/callback";
    private static final String DENIED_PATH = "/auth/denied";
    private static final String WECHAT_ONLY_PATH = "/auth/wechat-only";
    private static final String DEFAULT_REDIRECT = "/";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final WechatOAuthClient oauthClient;
    private final WechatOAuthProperties properties;
    private final AppUserService userService;

    public WechatAuthController(WechatOAuthClient oauthClient,
                                WechatOAuthProperties properties,
                                AppUserService userService) {
        this.oauthClient = oauthClient;
        this.properties = properties;
        this.userService = userService;
    }

    /** 发起授权。已登录则直接回到目标页，不再跳微信。 */
    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize(
            @RequestParam(name = "redirect", required = false) String redirect,
            HttpServletRequest request) {

        String target = safeRedirect(redirect);

        // 非微信内置浏览器：引导去微信打开，不发起授权流程，也不建任何会话或用户。
        if (!WechatClientDetector.isWechatBrowser(request)) {
            log.info("非微信内置浏览器访问授权入口，返回引导页");
            return redirectTo(withRedirect(WECHAT_ONLY_PATH, target));
        }

        HttpSession session = request.getSession(true);

        if (session.getAttribute(SESSION_USER_ID) != null) {
            return redirectTo(target);
        }

        String state = newState();
        session.setAttribute(SESSION_STATE, state);
        session.setAttribute(SESSION_STATE_AT, System.currentTimeMillis());
        session.setAttribute(SESSION_REDIRECT, target);

        String callbackUri = properties.callbackBaseUrl() + CALLBACK_PATH;
        return redirectTo(oauthClient.buildAuthorizeUrl(callbackUri, state));
    }

    /** 微信回调。code 为空视为用户拒绝授权。 */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state,
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        String target = session == null ? DEFAULT_REDIRECT
                : safeRedirect((String) session.getAttribute(SESSION_REDIRECT));

        // 用户点了拒绝：微信不带 code 回跳。不建用户，引导到说明页。
        if (code == null || code.isBlank()) {
            log.info("用户拒绝授权，回跳说明页");
            return redirectTo(withRedirect(DENIED_PATH, target));
        }

        if (!stateValid(session, state)) {
            log.warn("授权 state 校验未通过，拒绝本次回调");
            return redirectTo(withRedirect(DENIED_PATH, target));
        }
        clearStateAttributes(session);

        WechatUserInfo info;
        try {
            info = oauthClient.exchangeCode(code);
        } catch (WechatOAuthException ex) {
            log.warn("换取微信用户标识失败: {}", ex.getMessage());
            return redirectTo(withRedirect(DENIED_PATH, target));
        }

        AppUser user = userService.registerOrUpdate(
                info.openId(), info.unionId(), info.nickname(), info.avatarUrl());

        // 换 session id，避免固定会话攻击。
        request.changeSessionId();
        request.getSession(true).setAttribute(SESSION_USER_ID, user.getId());

        log.info("授权完成，userId={}", user.getId());
        return redirectTo(target);
    }

    private boolean stateValid(HttpSession session, String state) {
        if (session == null || state == null || state.isBlank()) {
            return false;
        }
        Object expected = session.getAttribute(SESSION_STATE);
        Object issuedAt = session.getAttribute(SESSION_STATE_AT);
        if (!(expected instanceof String expectedState) || !(issuedAt instanceof Long at)) {
            return false;
        }
        if (!java.security.MessageDigest.isEqual(
                expectedState.getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8))) {
            return false;
        }
        long ageSeconds = (System.currentTimeMillis() - at) / 1000;
        return ageSeconds <= properties.stateTtlSeconds();
    }

    private static void clearStateAttributes(HttpSession session) {
        if (session == null) {
            return;
        }
        session.removeAttribute(SESSION_STATE);
        session.removeAttribute(SESSION_STATE_AT);
    }

    /**
     * 只接受站内相对路径，杜绝被构造成开放重定向。
     * 必须以单个 / 开头，且不得以 // 或 /\ 开头（那会被浏览器当作协议相对地址跳到站外）。
     */
    static String safeRedirect(String redirect) {
        if (redirect == null || redirect.isBlank()) {
            return DEFAULT_REDIRECT;
        }
        if (!redirect.startsWith("/") || redirect.startsWith("//") || redirect.startsWith("/\\")) {
            return DEFAULT_REDIRECT;
        }
        return redirect;
    }

    private static String withRedirect(String path, String target) {
        return UriComponentsBuilder.fromPath(path)
                .queryParam("redirect", target)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();
    }

    private static String newState() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static ResponseEntity<Void> redirectTo(String location) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
    }
}
