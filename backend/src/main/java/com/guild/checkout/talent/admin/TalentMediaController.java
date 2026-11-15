package com.guild.checkout.talent.admin;

import com.guild.checkout.auth.RequireRole;
import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.storage.FileStorage;
import com.guild.checkout.storage.StoredFile;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.TalentService;
import com.guild.checkout.user.UserRole;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * 主播媒体上传：封面图与试听片段。
 */
@RestController
@RequestMapping("/api/admin/talents/{talentId}/media")
@RequireRole(UserRole.OPERATOR)
public class TalentMediaController {

    /** 试听片段上限 5 MB。微信里加载更大的音频体验已经不可接受。 */
    private static final long MAX_AUDIO_BYTES = 5L * 1024 * 1024;
    /** 封面图上限 2 MB。 */
    private static final long MAX_IMAGE_BYTES = 2L * 1024 * 1024;

    private static final List<String> AUDIO_TYPES = List.of("audio/mpeg", "audio/mp3", "audio/mp4",
            "audio/aac", "audio/x-m4a", "audio/m4a", "audio/wav", "audio/x-wav");
    private static final List<String> IMAGE_TYPES = List.of("image/jpeg", "image/png", "image/webp");

    private final FileStorage storage;
    private final TalentService talentService;
    private final TalentAdminService adminService;

    public TalentMediaController(FileStorage storage,
                                 TalentService talentService,
                                 TalentAdminService adminService) {
        this.storage = storage;
        this.talentService = talentService;
        this.adminService = adminService;
    }

    public record UploadResult(String url, long sizeBytes, String contentType) {
    }

    /** 上传试听片段并写入档案。时长由前端读出后一并提交。 */
    @PostMapping("/audio")
    public ApiResponse<UploadResult> uploadAudio(@PathVariable long talentId,
                                                 @RequestParam("file") MultipartFile file,
                                                 @RequestParam("seconds") int seconds) {
        Talent talent = talentService.require(talentId);
        validate(file, AUDIO_TYPES, MAX_AUDIO_BYTES, "音频");
        if (seconds <= 0) {
            throw new BusinessException("试听时长必须大于零");
        }

        StoredFile stored = store(file, "talent-audio");
        // 换新片段时把旧文件删掉，避免存储里堆积无人引用的文件。
        String previous = talent.getAudioUrl();
        adminService.setAudio(talentId, stored.url(), seconds);
        deleteByUrl(previous);

        return ApiResponse.ok(new UploadResult(stored.url(), stored.sizeBytes(), stored.contentType()));
    }

    /** 上传封面图并写入档案。 */
    @PostMapping("/cover")
    public ApiResponse<UploadResult> uploadCover(@PathVariable long talentId,
                                                 @RequestParam("file") MultipartFile file) {
        Talent talent = talentService.require(talentId);
        validate(file, IMAGE_TYPES, MAX_IMAGE_BYTES, "图片");

        StoredFile stored = store(file, "talent-cover");
        String previous = talent.getCoverUrl();
        adminService.update(talentId, new TalentAdminService.ProfileForm(
                null, null, stored.url(), null, null, null, null, null));
        deleteByUrl(previous);

        return ApiResponse.ok(new UploadResult(stored.url(), stored.sizeBytes(), stored.contentType()));
    }

    private StoredFile store(MultipartFile file, String category) {
        try (var in = file.getInputStream()) {
            return storage.store(category, file.getOriginalFilename(),
                    file.getContentType(), file.getSize(), in);
        } catch (IOException ex) {
            throw new BusinessException("读取上传文件失败");
        }
    }

    private void deleteByUrl(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        int slash = url.indexOf("/uploads/");
        if (slash >= 0) {
            storage.delete(url.substring(slash + "/uploads/".length()));
        }
    }

    private static void validate(MultipartFile file, List<String> allowedTypes,
                                 long maxBytes, String label) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的文件");
        }
        if (file.getSize() > maxBytes) {
            throw new BusinessException("%s大小不得超过 %d MB".formatted(label, maxBytes / 1024 / 1024));
        }
        String type = file.getContentType();
        if (type == null || !allowedTypes.contains(type.toLowerCase(Locale.ROOT))) {
            throw new BusinessException("不支持的%s格式：%s".formatted(label, type));
        }
    }
}
