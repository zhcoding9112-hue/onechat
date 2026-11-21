package com.guild.checkout.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 上传根目录的护栏。
 *
 * 这条测试守的是一个真实事故：storage.root-dir 默认值 {@code ./uploads} 是相对
 * **进程工作目录**的。开发期先用 {@code mvn spring-boot:run}（工作目录 backend/）
 * 传了几张封面，后来改成在仓库根目录 {@code java -jar} 启动，于是出现了两个
 * 互不相干的文件库：backend/uploads 和 ./uploads。数据库里的 URL 还在、
 * 请求也是 200（静态资源映射指向新的空目录，404 由前端的 img 吞掉），
 * 页面上只是封面裂了。没有任何一处日志会喊。
 *
 * 开发期无所谓，换个目录重传一次就行；生产环境则等于悄悄丢掉全部用户上传。
 * 所以生产环境宁可起不来：把一个会飘的路径拦在启动阶段。
 */
class LocalFileStorageRootTest {

    @TempDir
    Path tempDir;

    private static MockEnvironment profiles(String... active) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(active);
        return env;
    }

    @Test
    @DisplayName("生产环境给相对路径时直接启动失败，而不是带着会飘的路径跑起来")
    void 生产环境拒绝相对路径() {
        StorageProperties relative = new StorageProperties("./uploads", "/uploads");

        assertThatThrownBy(() -> new LocalFileStorage(relative, profiles("prod")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("必须是绝对路径")
                // 报错要能直接照着改，否则运维只会把它当噪音
                .hasMessageContaining("STORAGE_ROOT");
    }

    @Test
    @DisplayName("生产环境给绝对路径时正常启动")
    void 生产环境接受绝对路径() {
        Path root = tempDir.resolve("uploads");
        StorageProperties absolute =
                new StorageProperties(root.toAbsolutePath().toString(), "/uploads");

        LocalFileStorage storage = new LocalFileStorage(absolute, profiles("prod"));

        assertThat(storage).isNotNull();
        assertThat(Files.isDirectory(root)).as("根目录应被创建出来").isTrue();
    }

    @Test
    @DisplayName("开发环境仍允许相对路径 — 本机开发不该被迫配环境变量")
    void 开发环境允许相对路径() {
        Path cwdRelative = Path.of("target/test-uploads-" + System.nanoTime());
        StorageProperties relative = new StorageProperties(cwdRelative.toString(), "/uploads");

        LocalFileStorage storage = new LocalFileStorage(relative, profiles("dev"));

        assertThat(storage).isNotNull();
        assertThat(Files.isDirectory(cwdRelative)).isTrue();
        cwdRelative.toFile().delete();
    }

    @Test
    @DisplayName("没有任何 profile 时也按开发环境处理，只有 prod 才收紧")
    void 无profile时不拦() {
        Path cwdRelative = Path.of("target/test-uploads-" + System.nanoTime());
        StorageProperties relative = new StorageProperties(cwdRelative.toString(), "/uploads");

        assertThat(new LocalFileStorage(relative, profiles())).isNotNull();
        cwdRelative.toFile().delete();
    }
}
