package com.guild.checkout.auth.wechat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * 按配置选择真实实现还是本机桩。
 *
 * 判定顺序刻意写死，不给"配置缺失就退化"的空间：
 *   1. 配了 AppID/AppSecret → 真实实现。
 *   2. 没配，但运行在 local / test 环境 → 桩实现，并在日志里大声告警。
 *   3. 没配，又不是 local / test → 启动直接失败。
 * 第三条是防线：生产少配一个环境变量时宁可起不来，也不能变成谁都能登录。
 */
@Configuration
@EnableConfigurationProperties(WechatOAuthProperties.class)
public class WechatOAuthConfig {

    private static final Logger log = LoggerFactory.getLogger(WechatOAuthConfig.class);

    /** 允许使用桩的环境。 */
    private static final List<String> STUB_ALLOWED_PROFILES = List.of("local", "test", "dev");

    @Bean
    public WechatOAuthClient wechatOAuthClient(WechatOAuthProperties properties, Environment environment) {
        if (properties.configured()) {
            log.info("微信网页授权使用真实实现，appId={}", mask(properties.appId()));
            RestClient restClient = restClientBuilder()
                    .requestFactory(clientHttpRequestFactory())
                    .build();
            return new RealWechatOAuthClient(properties, restClient);
        }

        // 刻意不给"无 profile"开豁免：生产若同时漏配 AppID 与 profile，
        // 宁可起不来，也不能退化成任何人都能以任意 openid 登录。
        boolean stubAllowed = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(STUB_ALLOWED_PROFILES::contains);

        if (!stubAllowed) {
            throw new IllegalStateException(
                    "未配置 wechat.oauth.app-id / app-secret，且当前 profile %s 不在 %s 之列，不允许使用授权桩。"
                            .formatted(Arrays.toString(environment.getActiveProfiles()), STUB_ALLOWED_PROFILES));
        }
        return new StubWechatOAuthClient();
    }

    /**
     * 微信接口专用的 RestClient 构造器。
     *
     * 微信 /sns/oauth2/* 的响应体是 JSON，但 Content-Type 标的是 text/plain。
     * 默认的 Jackson 转换器只认 application/json，于是解析阶段直接抛
     * "no suitable HttpMessageConverter"，真机授权必然失败。
     * 这里插一个只服务于本 client 的转换器，把 text/* 也交给 Jackson。
     *
     * 该转换器放在列表首位，意味着本 client 读 String 类型的 text/plain 响应也会走
     * Jackson 并失败——可以接受，因为本 client 只读 JsonNode。不要把它复用到别处。
     *
     * 生产与单测必须共用此方法：之前单测全部用 application/json 应答，
     * 恰好绕过了真实世界唯一的差异，才让这个 bug 一路活到真机。
     */
    static RestClient.Builder restClientBuilder() {
        var converter = new MappingJackson2HttpMessageConverter();
        converter.setSupportedMediaTypes(List.of(
                MediaType.APPLICATION_JSON,
                new MediaType("application", "*+json"),
                MediaType.TEXT_PLAIN,
                MediaType.TEXT_HTML));
        return RestClient.builder()
                .messageConverters(converters -> converters.add(0, converter));
    }

    private static org.springframework.http.client.ClientHttpRequestFactory clientHttpRequestFactory() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());
        return factory;
    }

    private static String mask(String value) {
        if (value == null || value.length() <= 6) {
            return "***";
        }
        return value.substring(0, 3) + "***" + value.substring(value.length() - 3);
    }
}
