package com.guild.checkout.storage;

import com.guild.checkout.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * 本地磁盘存储。
 *
 * 文件名一律重新生成，绝不沿用上传者给的名字——原始文件名可能带 ../ 或路径分隔符，
 * 直接用会被穿越出存储目录。扩展名从原名推断，但只接受白名单内的字符。
 */
public class LocalFileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);

    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final Path root;
    private final String publicPrefix;

    public LocalFileStorage(StorageProperties properties,
                            org.springframework.core.env.Environment environment) {
        requireAbsoluteInProd(properties.rootDir(), environment);
        this.root = Path.of(properties.rootDir()).toAbsolutePath().normalize();
        this.publicPrefix = stripTrailingSlash(properties.publicPrefix());
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new IllegalStateException("无法创建存储目录: " + root, ex);
        }
        log.info("本地文件存储根目录: {}，对外前缀: {}", root, publicPrefix);
    }

    /**
     * 生产环境必须显式给绝对路径。
     *
     * 默认值 ./uploads 是相对**进程工作目录**的。同一个服务从不同目录启动，
     * 就是两个互不相干的文件库：之前传的封面、试听、支付凭证全部 404，
     * 而数据库里的地址还在、页面也不报错，只是图裂了——
     * 这种故障没有任何一处会喊，只能靠人眼发现。开发期踩过一次。
     *
     * 所以生产环境宁可起不来，也不让它带着一个会飘的路径跑起来。
     */
    private static void requireAbsoluteInProd(String rootDir,
                                              org.springframework.core.env.Environment environment) {
        boolean prod = java.util.Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (prod && !Path.of(rootDir).isAbsolute()) {
            throw new IllegalStateException(
                    "storage.root-dir 必须是绝对路径，当前为「%s」。".formatted(rootDir)
                            + "相对路径随启动目录变化，换个目录启动就等于丢掉全部已上传文件。"
                            + "请设置环境变量 STORAGE_ROOT，例如 /var/lib/private-checkout/uploads");
        }
    }

    @Override
    public StoredFile store(String category, String originalName, String contentType,
                            long sizeBytes, InputStream content) {
        String safeCategory = sanitizeSegment(category, "misc");
        String extension = extensionOf(originalName);
        String dateDir = LocalDate.now().format(DATE_DIR);
        String fileName = UUID.randomUUID().toString().replace("-", "") + extension;
        String key = "%s/%s/%s".formatted(safeCategory, dateDir, fileName);

        Path target = root.resolve(key).normalize();
        // 双保险：即便上面的清洗被绕过，也不允许写到根目录之外。
        if (!target.startsWith(root)) {
            throw new BusinessException("非法的存储路径");
        }

        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("写入文件失败: " + key, ex);
        }
        return new StoredFile(key, publicPrefix + "/" + key, sizeBytes, contentType);
    }

    @Override
    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            log.warn("忽略越界的删除请求: {}", key);
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            log.warn("删除文件失败: {}", key, ex);
        }
    }

    /** 只保留字母数字与短横线，其余一律丢弃。 */
    private static String sanitizeSegment(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String cleaned = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "");
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    /** 从原始文件名推断扩展名，最长 8 字符，只接受字母数字。 */
    static String extensionOf(String originalName) {
        if (originalName == null) {
            return "";
        }
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) {
            return "";
        }
        String ext = originalName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (ext.length() > 8 || !ext.matches("[a-z0-9]+")) {
            return "";
        }
        return "." + ext;
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
