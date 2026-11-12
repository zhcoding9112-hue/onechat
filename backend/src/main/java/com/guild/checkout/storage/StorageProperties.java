package com.guild.checkout.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 存储配置。
 *
 * @param rootDir   本地存储根目录
 * @param publicPrefix 对外访问的 URL 前缀
 */
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(String rootDir, String publicPrefix) {

    public StorageProperties {
        if (rootDir == null || rootDir.isBlank()) {
            rootDir = "./uploads";
        }
        if (publicPrefix == null || publicPrefix.isBlank()) {
            publicPrefix = "/uploads";
        }
    }
}
