package com.guild.checkout.auth.wechat;

import com.guild.checkout.user.UserDisplay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 资料接口异常时的降级：登录必须照常成功，展示用占位。
 * spec 明确「获取失败不得阻断登录」。
 */
class ProfileFallbackTest {

    private static final WechatOAuthProperties PROPS = new WechatOAuthProperties(
            "wxTESTAPPID", "TESTSECRET", "snsapi_userinfo", "https://example.com", 300);

    private MockRestServiceServer server;
    private RealWechatOAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RealWechatOAuthClient(PROPS, builder.build());
    }

    private void tokenOk() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/sns/oauth2/access_token")))
                .andRespond(withSuccess("""
                        {"access_token":"AT","openid":"oFALLBACK","scope":"snsapi_userinfo"}
                        """, MediaType.APPLICATION_JSON));
    }

    @Test
    void 资料接口返回5xx时登录仍成功并用占位昵称头像() {
        tokenOk();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/sns/userinfo")))
                .andRespond(withServerError());

        WechatUserInfo info = client.exchangeCode("CODE");

        assertThat(info.openId()).isEqualTo("oFALLBACK");
        assertThat(info.nickname()).isNull();

        // 展示层给出占位，而不是把空值透给前端
        assertThat(UserDisplay.nickname(info.nickname(), 42L)).isEqualTo("微信用户0042");
        assertThat(UserDisplay.avatarUrl(info.avatarUrl())).isEqualTo("/images/avatar-placeholder.svg");
        assertThat(UserDisplay.isPlaceholderNickname(info.nickname())).isTrue();
    }

    @Test
    void 资料接口返回errcode时登录仍成功() {
        tokenOk();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/sns/userinfo")))
                .andRespond(withSuccess("""
                        {"errcode":40003,"errmsg":"invalid openid"}
                        """, MediaType.APPLICATION_JSON));

        WechatUserInfo info = client.exchangeCode("CODE");

        assertThat(info.openId()).isEqualTo("oFALLBACK");
        assertThat(info.nickname()).isNull();
    }

    @Test
    void 资料接口返回空昵称时同样走占位() {
        tokenOk();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/sns/userinfo")))
                .andRespond(withSuccess("""
                        {"openid":"oFALLBACK","nickname":"","headimgurl":""}
                        """, MediaType.APPLICATION_JSON));

        WechatUserInfo info = client.exchangeCode("CODE");

        assertThat(UserDisplay.nickname(info.nickname(), 42L)).isEqualTo("微信用户0042");
        assertThat(UserDisplay.avatarUrl(info.avatarUrl())).isEqualTo("/images/avatar-placeholder.svg");
    }

    @Test
    void 资料接口返回畸形json时登录仍成功() {
        tokenOk();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/sns/userinfo")))
                .andRespond(withSuccess("not json at all", MediaType.APPLICATION_JSON));

        WechatUserInfo info = client.exchangeCode("CODE");

        assertThat(info.openId()).isEqualTo("oFALLBACK");
        assertThat(info.nickname()).isNull();
    }
}
