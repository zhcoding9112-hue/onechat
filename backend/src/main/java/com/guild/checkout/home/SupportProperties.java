package com.guild.checkout.home;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

/** 客服公开展示配置；只包含可向粉丝展示的联系方式，不承载账号密钥。 */
@ConfigurationProperties(prefix = "support")
public record SupportProperties(String title, String description, String hours,
                                String wechatId, String qrCodeUrl, String serviceUrl) {
    public SupportProperties {
        title = text(title, "在线客服");
        description = text(description, "订单咨询 · 服务协助");
        hours = text(hours, "");
        wechatId = text(wechatId, "");
        qrCodeUrl = safeUrl(qrCodeUrl);
        serviceUrl = safeUrl(serviceUrl);
    }

    public boolean configured() {
        return !wechatId.isEmpty() || !qrCodeUrl.isEmpty() || !serviceUrl.isEmpty();
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String safeUrl(String value) {
        String url = text(value, "");
        if (url.isEmpty()) return url;
        try {
            URI uri = URI.create(url);
            boolean local = url.startsWith("/") && !url.startsWith("//")
                    && !url.contains("\\") && uri.getAuthority() == null;
            boolean https = "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null && uri.getUserInfo() == null;
            if (local || https) return url;
        } catch (IllegalArgumentException ignored) {
            // 统一拒绝格式错误和危险协议，避免错误配置变成可点击脚本。
        }
        throw new IllegalArgumentException("客服图片与链接必须使用 HTTPS 或站内绝对路径");
    }
}
