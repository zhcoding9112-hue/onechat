package com.guild.checkout.auth.wechat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;

/**
 * 本机开发用的授权桩。不访问微信，直接把授权码当作 openid 回跳。
 *
 * 只在未配置 AppID 且运行于 local/test 环境时启用；生产环境缺配置会在启动时直接失败，
 * 不会退化成这个实现——否则任何人都能以任意 openid 登录。
 */
public class StubWechatOAuthClient implements WechatOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(StubWechatOAuthClient.class);

    public static final String STUB_OPENID_PREFIX = "stub_";

    public StubWechatOAuthClient() {
        log.warn("微信网页授权使用本机桩实现：任何 code 都会被当作 openid 直接登录。仅限本机开发。");
    }

    @Override
    public String buildAuthorizeUrl(String redirectUri, String state) {
        // 模拟微信授权页：直接回跳，code 取固定值，便于重复登录同一个测试用户。
        return UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("code", STUB_OPENID_PREFIX + "local_dev")
                .queryParam("state", state)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();
    }

    @Override
    public WechatUserInfo exchangeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new WechatOAuthException("桩实现要求 code 非空");
        }
        String openId = code.startsWith(STUB_OPENID_PREFIX) ? code : STUB_OPENID_PREFIX + code;
        return new WechatUserInfo(openId, null, "本机测试用户", null);
    }
}
