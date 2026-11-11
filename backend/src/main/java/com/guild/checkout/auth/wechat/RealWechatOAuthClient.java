package com.guild.checkout.auth.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 真实的服务号网页授权实现。
 */
public class RealWechatOAuthClient implements WechatOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(RealWechatOAuthClient.class);

    private static final String AUTHORIZE_URL = "https://open.weixin.qq.com/connect/oauth2/authorize";
    private static final String TOKEN_URL = "https://api.weixin.qq.com/sns/oauth2/access_token";
    private static final String USERINFO_URL = "https://api.weixin.qq.com/sns/userinfo";

    private final WechatOAuthProperties properties;
    private final RestClient restClient;

    public RealWechatOAuthClient(WechatOAuthProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public String buildAuthorizeUrl(String redirectUri, String state) {
        // 这里不能用 UriComponentsBuilder.encode()：按 RFC 3986，":" 与 "/" 在 query 中合法，
        // 它不会转义，而微信要求 redirect_uri 必须整体 URL 编码，否则授权直接失败。
        return "%s?appid=%s&redirect_uri=%s&response_type=code&scope=%s&state=%s#wechat_redirect"
                .formatted(
                        AUTHORIZE_URL,
                        urlEncode(properties.appId()),
                        urlEncode(redirectUri),
                        urlEncode(properties.scope()),
                        urlEncode(state));
    }

    private static String urlEncode(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Override
    public WechatUserInfo exchangeCode(String code) {
        JsonNode token = getJson(UriComponentsBuilder.fromUriString(TOKEN_URL)
                .queryParam("appid", properties.appId())
                .queryParam("secret", properties.appSecret())
                .queryParam("code", code)
                .queryParam("grant_type", "authorization_code")
                .toUriString(), "换取 access_token");

        requireNoError(token, "换取 access_token");

        String openId = text(token, "openid");
        if (openId == null || openId.isBlank()) {
            throw new WechatOAuthException("微信未返回 openid");
        }
        String unionId = text(token, "unionid");
        String accessToken = text(token, "access_token");
        String scope = text(token, "scope");

        // snsapi_base 拿不到资料；资料接口失败同样不得阻断登录。
        if (accessToken != null && scope != null && scope.contains("snsapi_userinfo")) {
            try {
                JsonNode profile = getJson(UriComponentsBuilder.fromUriString(USERINFO_URL)
                        .queryParam("access_token", accessToken)
                        .queryParam("openid", openId)
                        .queryParam("lang", "zh_CN")
                        .toUriString(), "获取用户资料");
                requireNoError(profile, "获取用户资料");
                return new WechatUserInfo(
                        openId,
                        unionId != null ? unionId : text(profile, "unionid"),
                        text(profile, "nickname"),
                        text(profile, "headimgurl"));
            } catch (RuntimeException ex) {
                log.warn("获取微信用户资料失败，以空资料继续登录: {}", ex.getMessage());
            }
        }
        return new WechatUserInfo(openId, unionId, null, null);
    }

    private JsonNode getJson(String url, String action) {
        try {
            JsonNode body = restClient.get().uri(url).retrieve().body(JsonNode.class);
            if (body == null) {
                throw new WechatOAuthException(action + "失败：微信返回空响应");
            }
            return body;
        } catch (WechatOAuthException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new WechatOAuthException(action + "失败：" + ex.getMessage(), ex);
        }
    }

    private static void requireNoError(JsonNode node, String action) {
        if (node.hasNonNull("errcode") && node.get("errcode").asInt() != 0) {
            throw new WechatOAuthException(
                    "%s失败：errcode=%s errmsg=%s".formatted(
                            action, node.get("errcode").asText(), text(node, "errmsg")));
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
