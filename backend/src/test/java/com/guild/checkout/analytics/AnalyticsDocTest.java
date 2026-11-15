package com.guild.checkout.analytics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * docs/analytics.md 与代码的一致性（task 12.13）。
 *
 * 验收要求「照文档可逐条在系统中找到对应埋点」。文档会过时，
 * 所以让构建来保证它不过时。
 */
class AnalyticsDocTest {

    private static final Path DOC = Path.of("../docs/analytics.md");

    @Test
    @DisplayName("文档存在且列出了全部事件标识")
    void 文档覆盖全部事件() throws IOException {
        assertThat(Files.exists(DOC)).as("docs/analytics.md 应存在").isTrue();
        String text = Files.readString(DOC, StandardCharsets.UTF_8);

        for (EventKey key : EventKey.values()) {
            assertThat(text)
                    .as("文档缺少事件 %s（%s）", key.name(), key.label())
                    .contains("`" + key.name() + "`")
                    .contains(key.label());
        }
    }

    @Test
    @DisplayName("文档里没有已被删除的事件标识")
    void 文档没有多余事件() throws IOException {
        String text = Files.readString(DOC, StandardCharsets.UTF_8);

        // 表格里形如 `XXX_YYY` 的全大写标识都应当是真实存在的事件
        var pattern = java.util.regex.Pattern.compile("`([A-Z][A-Z0-9_]{3,})`");
        var matcher = pattern.matcher(text);
        while (matcher.find()) {
            String token = matcher.group(1);
            // 跳过文档里提到的非事件标识
            if (token.equals("REQUIRES_NEW") || token.equals("COALESCE")) {
                continue;
            }
            assertThat(EventKey.of(token))
                    .as("文档提到的 %s 不是一个真实事件，可能已被删除或改名", token)
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("文档说明了四项核心指标与去重口径")
    void 文档覆盖关键口径() throws IOException {
        String text = Files.readString(DOC, StandardCharsets.UTF_8);

        assertThat(text)
                .contains("访问到付费总转化率")
                .contains("主播接单率")
                .contains("平均接单时长")
                .contains("7 日复购率")
                .contains("COALESCE")
                .contains("分母为零")
                .contains("REQUIRES_NEW");
    }
}
