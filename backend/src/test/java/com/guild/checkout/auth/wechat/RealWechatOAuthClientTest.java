package com.guild.checkout.auth.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;

/**
 * 以假的微信服务端覆盖真实客户端。真测试号环境的联调见 README。
 */
class RealWechatOAuthClientTest {

    private static final WechatOAuthProperties PROPS = new WechatOAuthProperties(
            "wxTESTAPPID", "TESTSECRET", "snsapi_userinfo", "https://example.com", 300);

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private RealWechatOAuthClient client;

    @BeforeEach
    void setUp() {
        // 必须与生产共用同一个 builder 配置。此前这里用的是裸 RestClient.builder()，
        // 加上下面全部以 application/json 应答，恰好绕开了微信真实返回 text/plain 的情况。
        builder = WechatOAuthConfig.restClientBuilder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RealWechatOAuthClient(PROPS, builder.build());
    }

    @AfterEach
    void tearDown() {
        server.reset();
    }

    @Test
    void 授权地址包含必需参数且以wechat_redirect结尾() {
        String url = client.buildAuthorizeUrl("https://example.com/api/auth/wechat/callback", "st/ate+1");

        assertThat(url)
                .startsWith("https://open.weixin.qq.com/connect/oauth2/authorize")
                .contains("appid=wxTESTAPPID")
                .contains("response_type=code")
                .contains("scope=snsapi_userinfo")
                .endsWith("#wechat_redirect");
        // redirect_uri 与 state 必须转义，否则微信侧会截断
        assertThat(url).contains("redirect_uri=https%3A%2F%2Fexample.com%2Fapi%2Fauth%2Fwechat%2Fcallback");
        // state 里的 / 和 + 必须转义，否则回调拿到的 state 与下发的不一致，校验恒失败
        assertThat(url).contains("state=st%2Fate%2B1").doesNotContain("state=st/ate+1");
    }

    @Test
    void 换取成功时返回openid与资料() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/sns/oauth2/access_token")))
                .andRespond(withSuccess("""
                        {"access_token":"AT","openid":"oABC123","scope":"snsapi_userinfo","unionid":"uXYZ"}
                        """, MediaType.TEXT_PLAIN));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/sns/userinfo")))
                .andRespond(withSuccess("""
                        {"openid":"oABC123","nickname":"小张","headimgurl":"https://img/1.png"}
                        """, MediaType.TEXT_PLAIN));

        WechatUserInfo info = client.exchangeCode("CODE");

        assertThat(info.openId()).isEqualTo("oABC123");
        assertThat(info.unionId()).isEqualTo("uXYZ");
        assertThat(info.nickname()).isEqualTo("小张");
        assertThat(info.avatarUrl()).isEqualTo("https://img/1.png");
        server.verify();
    }

    @Test
    void 微信以applicationjson应答时同样可解析() {
        // 微信文档未承诺 Content-Type，实测返回 text/plain，但不排除后续改回 application/json。
        // 转换器必须同时吃下两种，修一种不能破另一种。
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/sns/oauth2/access_token")))
                .andRespond(withSuccess("""
                        {"access_token":"AT","openid":"oJSON1","scope":"snsapi_userinfo"}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/sns/userinfo")))
                .andRespond(withSuccess("""
                        {"openid":"oJSON1","nickname":"小李"}
                        """, MediaType.APPLICATION_JSON));

        WechatUserInfo info = client.exchangeCode("CODE");

        assertThat(info.openId()).isEqualTo("oJSON1");
        assertThat(info.nickname()).isEqualTo("小李");
        server.verify();
    }

    @Test
    void 资料接口失败不阻断登录且openid仍可用() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/sns/oauth2/access_token")))
                .andRespond(withSuccess("""
                        {"access_token":"AT","openid":"oABC123","scope":"snsapi_userinfo"}
                        """, MediaType.TEXT_PLAIN));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/sns/userinfo")))
                .andRespond(withServerError());

        WechatUserInfo info = client.exchangeCode("CODE");

        assertThat(info.openId()).isEqualTo("oABC123");
        assertThat(info.nickname()).isNull();
        assertThat(info.avatarUrl()).isNull();
    }

    @Test
    void snsapi_base作用域下不请求资料接口() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/sns/oauth2/access_token")))
                .andRespond(withSuccess("""
                        {"access_token":"AT","openid":"oBASE","scope":"snsapi_base"}
                        """, MediaType.TEXT_PLAIN));

        WechatUserInfo info = client.exchangeCode("CODE");

        assertThat(info.openId()).isEqualTo("oBASE");
        assertThat(info.nickname()).isNull();
        // 只发了一次请求：没有去打资料接口
        server.verify();
    }

    @Test
    void 微信返回errcode时抛出异常() {
        server.expect(anything())
                .andRespond(withSuccess("""
                        {"errcode":40029,"errmsg":"invalid code"}
                        """, MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> client.exchangeCode("BAD"))
                .isInstanceOf(WechatOAuthException.class)
                .hasMessageContaining("40029")
                .hasMessageContaining("invalid code");
    }

    @Test
    void 微信未返回openid时抛出异常() {
        server.expect(anything())
                .andRespond(withSuccess("{\"access_token\":\"AT\"}", MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> client.exchangeCode("CODE"))
                .isInstanceOf(WechatOAuthException.class)
                .hasMessageContaining("openid");
    }

    @Test
    void 网络异常被包装为授权异常() {
        server.expect(anything()).andRespond(withServerError());

        assertThatThrownBy(() -> client.exchangeCode("CODE"))
                .isInstanceOf(WechatOAuthException.class);
    }

    @Test
    void 桩实现把code当作openid且带占位昵称() {
        StubWechatOAuthClient stub = new StubWechatOAuthClient();

        WechatUserInfo info = stub.exchangeCode("local_dev");
        assertThat(info.openId()).isEqualTo("stub_local_dev");
        assertThat(info.nickname()).isNotBlank();

        String url = stub.buildAuthorizeUrl("http://127.0.0.1:5173/api/auth/wechat/callback", "S1");
        assertThat(url).contains("code=stub_local_dev").contains("state=S1");

        assertThatThrownBy(() -> stub.exchangeCode(""))
                .isInstanceOf(WechatOAuthException.class);
    }

    @SuppressWarnings("unused")
    private static JsonNode unused() {
        return null;
    }
}
