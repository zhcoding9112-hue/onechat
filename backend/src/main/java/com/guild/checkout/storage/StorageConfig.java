package com.guild.checkout.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * 存储装配，并把本地存储目录以只读方式暴露给前端访问。
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig implements WebMvcConfigurer {

    private final StorageProperties properties;

    public StorageConfig(StorageProperties properties) {
        this.properties = properties;
    }

    @Bean
    public FileStorage fileStorage() {
        return new LocalFileStorage(properties);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = Path.of(properties.rootDir()).toAbsolutePath().normalize();
        registry.addResourceHandler(properties.publicPrefix() + "/**")
                .addResourceLocations(root.toUri().toString());
    }
}
