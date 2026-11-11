package com.guild.checkout.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class WechatClientDetectorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 "
                    + "(KHTML, like Gecko) Mobile/15E148 MicroMessenger/8.0.44(0x18002c2b) NetType/WIFI",
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/116 "
                    + "MQQBrowser/6.2 Mobile Safari/537.36 MicroMessenger/8.0.42.2460 NetType/WIFI",
            "Mozilla/5.0 micromessenger/8.0",
            "Mozilla/5.0 wechatdevtools/1.06 MicroMessenger/8.0",
    })
    void 微信内置浏览器被识别(String ua) {
        assertThat(WechatClientDetector.isWechatBrowser(ua)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/120 Safari/537.36",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) Safari/604.1",
            "curl/8.4.0",
            "Mozilla/5.0 (Linux; Android 13) AlipayClient/10.3.0",
    })
    void 非微信浏览器不被识别(String ua) {
        assertThat(WechatClientDetector.isWechatBrowser(ua)).isFalse();
    }

    @Test
    void 缺失或空UA不被识别() {
        assertThat(WechatClientDetector.isWechatBrowser((String) null)).isFalse();
        assertThat(WechatClientDetector.isWechatBrowser("")).isFalse();
        assertThat(WechatClientDetector.isWechatBrowser("   ")).isFalse();
    }
}
