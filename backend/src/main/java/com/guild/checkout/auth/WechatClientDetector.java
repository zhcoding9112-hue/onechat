package com.guild.checkout.auth;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 判断请求是否来自微信内置浏览器。
 *
 * 判据是 User-Agent 中的 MicroMessenger 标记。UA 可被伪造，所以这里只作为
 * 「引导用户去微信打开」的体验判断，绝不作为安全边界——真正的身份由微信授权建立，
 * 伪造 UA 的请求走到授权环节同样拿不到 openid。
 */
public final class WechatClientDetector {

    private static final String WECHAT_MARK = "micromessenger";
    /** 微信开发者工具，联调时需要放行。 */
    private static final String DEVTOOLS_MARK = "wechatdevtools";

    private WechatClientDetector() {
    }

    public static boolean isWechatBrowser(HttpServletRequest request) {
        return isWechatBrowser(request == null ? null : request.getHeader("User-Agent"));
    }

    public static boolean isWechatBrowser(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return false;
        }
        String ua = userAgent.toLowerCase(java.util.Locale.ROOT);
        return ua.contains(WECHAT_MARK) || ua.contains(DEVTOOLS_MARK);
    }
}
