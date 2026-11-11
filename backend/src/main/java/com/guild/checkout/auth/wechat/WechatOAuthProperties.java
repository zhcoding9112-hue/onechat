package com.guild.checkout.auth.wechat;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 服务号网页授权配置。
 *
 * @param appId       服务号 AppID，留空表示本机未配置，将改用桩实现
 * @param appSecret   服务号 AppSecret
 * @param scope       授权作用域。snsapi_userinfo 可取昵称头像，snsapi_base 只能拿 openid
 * @param callbackBaseUrl 回调所用的对外基地址，必须与公众平台配置的网页授权域名一致
 * @param stateTtlSeconds state 的有效期，防重放
 */
@ConfigurationProperties(prefix = "wechat.oauth")
public record WechatOAuthProperties(
        String appId,
        String appSecret,
        String scope,
        String callbackBaseUrl,
        Integer stateTtlSeconds) {

    public WechatOAuthProperties {
        if (scope == null || scope.isBlank()) {
            scope = "snsapi_userinfo";
        }
        if (callbackBaseUrl == null || callbackBaseUrl.isBlank()) {
            callbackBaseUrl = "http://127.0.0.1:5173";
        }
        if (stateTtlSeconds == null || stateTtlSeconds <= 0) {
            stateTtlSeconds = 300;
        }
    }

    public boolean configured() {
        return appId != null && !appId.isBlank() && appSecret != null && !appSecret.isBlank();
    }
}
