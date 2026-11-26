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
 * 最新规则要求所有图片登记来源；通过文件清单检查避免漏登记——
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
            "public/images/avatar-placeholder.svg",
            "public/images/portraits.webp",
            // 以下只服务开发预览，放在 demo-assets/ 而不是 public/：
            // public/ 下的文件会被原样拷进 dist，演示代码摇掉了素材照样进生产包。
            "demo-assets/images/peiyi-magic-frame-color.png",
            "demo-assets/images/peiyi-magic-frame-mask.png",
            "demo-assets/images/lucky-bow-frame.png",
            "demo-assets/images/feed-lifestyle-scenes.png");

    @Test
    @DisplayName("12.2 前端源码里只有清单上的图片文件")
    void 源码里没有计划外的图片() throws IOException {
        List<String> found = imageFilesUnder(FRONTEND.resolve("public"));
        found.addAll(imageFilesUnder(FRONTEND.resolve("src")));
        // demo-assets 也要扫：素材挪出 public/ 之后，不扫这里等于把登记制度留了个后门
        found.addAll(imageFilesUnder(FRONTEND.resolve("demo-assets")));

        assertThat(found)
                .as("出现了不在 docs/assets.md 清单里的图片文件。"
                        + "若是本轮确认使用的资产，请先把它记进清单再提交")
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
        assertThat(text).contains("所有资产必须登记来源");
    }

    /**
     * SVG 必须是合法的 XML。
     *
     * 这条是补上一个真出过的事故：默认头像的说明注释里写了 CSS 变量名
     * （--g-avatar），而 XML 注释里不允许出现连续两个减号，整个文件解析失败。
     * 后果是全站的默认头像一直是一个裂图标——文件在、路径对、Content-Type 对、
     * HTTP 200，只有浏览器解析时才报错，所以谁也没发现。
     *
     * 让构建去解析一遍，比指望有人盯着每个头像看可靠。
     */
    @Test
    @DisplayName("12.2 每个 SVG 都能被解析 — 裂图不该靠肉眼发现")
    void SVG必须是合法XML() throws Exception {
        List<Path> svgs = new java.util.ArrayList<>();
        for (Path root : List.of(FRONTEND.resolve("public"), FRONTEND.resolve("src"))) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                        .filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".svg"))
                        .forEach(svgs::add);
            }
        }

        javax.xml.parsers.DocumentBuilderFactory factory =
                javax.xml.parsers.DocumentBuilderFactory.newInstance();
        // 不去网上取 DTD，离线构建也要能跑
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        for (Path svg : svgs) {
            try {
                javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
                builder.setErrorHandler(null);
                builder.parse(svg.toFile());
            } catch (Exception ex) {
                throw new AssertionError(
                        "SVG 解析失败，浏览器会把它显示成裂图：" + svg + " —— " + ex.getMessage(), ex);
            }
        }
    }

    /**
     * 构建产物里也不该有计划外的图片。
     *
     * dist 要先跑过一次 npm run build 才有；没有就跳过，而不是失败——
     * 后端的单测不该强制要求先构建前端。源码那条断言才是常态防线。
     */
    @Test
    @DisplayName("12.2 构建产物里只有 public/ 的图片，演示素材一张都不能进")
    void 产物里没有计划外的图片() throws IOException {
        Path dist = FRONTEND.resolve("dist");
        if (!Files.isDirectory(dist)) {
            return;
        }
        List<String> found = imageFilesUnder(dist).stream()
                .map(p -> p.substring("dist/".length()))
                .toList();
        // 只有 public/ 下的会被拷进 dist。demo-assets/ 下的由开发期插件提供，
        // 进了产物就说明有人把它挪回 public/ 了——那 8.5 MB 会跟着发到线上。
        List<String> expected = ALLOWED.stream()
                .filter(p -> p.startsWith("public/"))
                .map(p -> p.substring("public/".length()))
                .toList();
        assertThat(found)
                .as("产物里出现了不该有的图片；演示素材属于 demo-assets/，不能放回 public/")
                .containsExactlyInAnyOrderElementsOf(expected);
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
