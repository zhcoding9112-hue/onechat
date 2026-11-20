package com.guild.checkout.storage.admin;

import com.guild.checkout.auth.RequireRole;
import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.storage.FileStorage;
import com.guild.checkout.storage.StoredFile;
import com.guild.checkout.user.UserRole;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 运营侧的通用图片上传。
 *
 * 为什么需要它：首页运营位、以后的活动页这类配置只存一个 imageUrl，
 * 没有自己的上传入口。在只有本地磁盘、没有图床的情况下，运营拿不到任何
 * 可用的图片地址，运营位就只能是纯文字。
 *
 * 与主播素材分开：那条路径传完会写进主播档案并删掉旧文件，这里只负责
 * 存文件、返回地址，调用方自己决定把地址写到哪。旧文件也不删——
 * 一个地址可能被多处引用，这里无从判断。
 */
@RestController
@RequestMapping("/api/admin/uploads")
@RequireRole(UserRole.OPERATOR)
public class AdminImageUploadController {

    /** 与主播封面同一上限。首页上的图不该比封面还大。 */
    private static final long MAX_BYTES = 2L * 1024 * 1024;

    private static final List<String> IMAGE_TYPES =
            List.of("image/jpeg", "image/png", "image/webp");

    /**
     * 允许的业务分类。
     *
     * 目录穿越本身已经由 LocalFileStorage 挡住了（它把分类里非 [a-z0-9-] 的字符
     * 全部剔掉，"../../etc" 会变成 "etc"），所以这里不是在补那个洞。
     * 白名单管的是别的事：category 直接来自请求参数，不限制的话存储目录会被
     * 写出一堆谁也不认识的分类，日后清理和迁移都无从下手。
     */
    private static final Set<String> CATEGORIES = Set.of("home-entry");

    private final FileStorage storage;

    public AdminImageUploadController(FileStorage storage) {
        this.storage = storage;
    }

    public record UploadResult(String url, long sizeBytes, String contentType) {
    }

    @PostMapping("/image")
    public ApiResponse<UploadResult> upload(@RequestParam("file") MultipartFile file,
                                            @RequestParam(defaultValue = "home-entry") String category) {
        if (!CATEGORIES.contains(category)) {
            throw new BusinessException("未知的图片分类: " + category);
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的文件");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException("图片大小不得超过 %d MB".formatted(MAX_BYTES / 1024 / 1024));
        }
        String type = file.getContentType();
        if (type == null || !IMAGE_TYPES.contains(type.toLowerCase(Locale.ROOT))) {
            throw new BusinessException("不支持的图片格式：" + type);
        }

        try (var in = file.getInputStream()) {
            StoredFile stored = storage.store(category, file.getOriginalFilename(),
                    file.getContentType(), file.getSize(), in);
            return ApiResponse.ok(new UploadResult(
                    stored.url(), stored.sizeBytes(), stored.contentType()));
        } catch (IOException ex) {
            throw new BusinessException("读取上传文件失败");
        }
    }
}
