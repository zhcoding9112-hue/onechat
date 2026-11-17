package com.guild.checkout.assets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 首屏体积预算（task 12.3）。
 *
 * 真正的测量逻辑在 frontend/scripts/first-screen.mjs——它顺着 Vite manifest 的
 * imports 闭包算「打开首页要下载哪些 chunk」。这里只负责在构建里把它跑起来，
 * 不在 Java 里再实现一遍：两套实现迟早会算出两个数，而那时没人知道该信哪个。
 */
class FirstScreenBudgetTest {

    private static final Path FRONTEND = Path.of("../frontend");

    @Test
    @DisplayName("12.3 首屏 JS 未超过改版前的 1.5 倍")
    void 首屏体积在预算内() throws IOException, InterruptedException {
        Path manifest = FRONTEND.resolve("dist/.vite/manifest.json");
        if (!Files.exists(manifest)) {
            // 前端还没构建过。后端单测不该强制先跑 npm run build，
            // 所以这里跳过而不是失败；构建流水线里 dist 一定存在。
            return;
        }

        ProcessBuilder pb = new ProcessBuilder("node", "scripts/first-screen.mjs", "--check")
                .directory(FRONTEND.toFile())
                .redirectErrorStream(true);

        Process process;
        try {
            process = pb.start();
        } catch (IOException noNode) {
            // 这台机器上没有 node。同理跳过，而不是把后端构建拖下水。
            return;
        }

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).as("测量脚本应在一分钟内跑完").isTrue();

        assertThat(process.exitValue()).as("首屏体积超预算：%n%s", output).isZero();
        assertThat(output).contains("\"ratio\"");
    }
}
