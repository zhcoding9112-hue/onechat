package com.guild.checkout.assets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 美术资产来源（task 12.2）。
 *
 * 验收要求「产物中不包含任何来源于竞品的图片文件」。这种事没法靠一次人工检查守住——
 * 下次有人把一张图拖进 public/ 就破了。所以让构建来盯：
 * 凡是图片文件，都必须记在 docs/assets.md 的清单里，并写明来源。
 */
class AssetProvenanceTest {

    private static final Path FRONTEND = Path.of("../frontend");
    private static final Path DOC = Path.of("../docs/assets.md");

    /** 认得出的图片后缀。字体不在其中——字体的来源在文档里单列。 */
    private static final Set<String> IMAGE_SUFFIXES = Set.of(
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".bmp", ".ico", ".avif");

    /**
     * 允许存在的图片文件，路径相对 frontend/。
     *
     * 每加一项，都要同时在 docs/assets.md 里写明它从哪来。
     */
    private static final List<String> ALLOWED = List.of(
            "public/images/avatar-placeholder.svg");

    @Test
    @DisplayName("12.2 前端源码里只有清单上的图片文件")
    void 源码里没有计划外的图片() throws IOException {
        List<String> found = imageFilesUnder(FRONTEND.resolve("public"));
        found.addAll(imageFilesUnder(FRONTEND.resolve("src")));

        assertThat(found)
                .as("出现了不在 docs/assets.md 清单里的图片文件。"
                        + "若是自制或已采购授权的资产，请先把它记进清单再提交")
                .containsExactlyInAnyOrderElementsOf(ALLOWED);
    }

    @Test
    @DisplayName("12.2 清单文档为每个资产写明了来源")
    void 清单写明来源() throws IOException {
        assertThat(Files.exists(DOC)).as("docs/assets.md 应存在").isTrue();
        String text = Files.readString(DOC, StandardCharsets.UTF_8);

        for (String asset : ALLOWED) {
            assertThat(text).as("清单缺少 %s", asset).contains(asset);
        }
        assertThat(text).contains("不使用任何竞品素材");
    }

    /**
     * 构建产物里也不该有计划外的图片。
     *
     * dist 要先跑过一次 npm run build 才有；没有就跳过，而不是失败——
     * 后端的单测不该强制要求先构建前端。源码那条断言才是常态防线。
     */
    @Test
    @DisplayName("12.2 构建产物里也只有清单上的图片文件")
    void 产物里没有计划外的图片() throws IOException {
        Path dist = FRONTEND.resolve("dist");
        if (!Files.isDirectory(dist)) {
            return;
        }
        List<String> found = imageFilesUnder(dist).stream()
                .map(p -> p.substring("dist/".length()))
                .toList();
        List<String> expected = ALLOWED.stream()
                .map(p -> p.substring("public/".length()))
                .toList();
        assertThat(found).containsExactlyInAnyOrderElementsOf(expected);
    }

    /** 产物里不该引用我们不控制的外部域名上的资源。 */
    @Test
    @DisplayName("12.2 产物的样式表不向第三方 CDN 取资源")
    void 产物不引用外部CDN() throws IOException {
        Path assets = FRONTEND.resolve("dist/assets");
        if (!Files.isDirectory(assets)) {
            return;
        }
        try (Stream<Path> files = Files.walk(assets)) {
            for (Path css : files.filter(p -> p.toString().endsWith(".css")).toList()) {
                String text = Files.readString(css, StandardCharsets.UTF_8);
                assertThat(text)
                        .as("%s 仍在引用外部 CDN；见 vite.config.ts 的 stripRemoteIconFont",
                                css.getFileName())
                        .doesNotContain("at.alicdn.com");
            }
        }
    }

    private static List<String> imageFilesUnder(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return new java.util.ArrayList<>();
        }
        Path base = FRONTEND.toAbsolutePath().normalize();
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(AssetProvenanceTest::isImage)
                    .map(p -> base.relativize(p.toAbsolutePath().normalize()).toString())
                    .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        }
    }

    private static boolean isImage(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return IMAGE_SUFFIXES.stream().anyMatch(name::endsWith);
    }
}
