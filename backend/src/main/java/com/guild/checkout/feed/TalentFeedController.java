package com.guild.checkout.feed;

import com.guild.checkout.auth.CurrentUserHolder;
import com.guild.checkout.auth.RequireRole;
import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.storage.FileStorage;
import com.guild.checkout.storage.StoredFile;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.TalentService;
import com.guild.checkout.user.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 主播侧：发布动态与上传配图。 */
@RestController
@RequestMapping("/api/talent/feed")
@RequireRole(UserRole.TALENT)
public class TalentFeedController {

    /** 配图上限 2 MB，与封面图一致。 */
    private static final long MAX_IMAGE_BYTES = 2L * 1024 * 1024;
    private static final List<String> IMAGE_TYPES = List.of("image/jpeg", "image/png", "image/webp");

    private final FeedService feedService;
    private final TalentService talentService;
    private final FileStorage storage;

    public TalentFeedController(FeedService feedService,
                                TalentService talentService,
                                FileStorage storage) {
        this.feedService = feedService;
        this.talentService = talentService;
        this.storage = storage;
    }

    public record PublishRequest(
            @Size(max = 1000, message = "正文不超过 1000 字符") String content,
            List<String> images) {
    }

    /** 发布前先问上限，前端据此限制选图数量；真正的拦截仍在服务端。 */
    @GetMapping("/limits")
    public ApiResponse<Map<String, Integer>> limits() {
        return ApiResponse.ok(Map.of("maxImages", feedService.setting().getMaxImages()));
    }

    @PostMapping("/images")
    public ApiResponse<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        requireMe();
        validateImage(file);
        try (var in = file.getInputStream()) {
            StoredFile stored = storage.store("feed-image", file.getOriginalFilename(),
                    file.getContentType(), file.getSize(), in);
            return ApiResponse.ok(Map.of("url", stored.url()));
        } catch (IOException e) {
            throw new BusinessException("图片上传失败，请重试");
        }
    }

    @PostMapping("/posts")
    public ApiResponse<Map<String, Long>> publish(@Valid @RequestBody PublishRequest req) {
        Talent me = requireMe();
        FeedPost post = feedService.publish(me.getId(), req.content(), req.images());
        return ApiResponse.ok(Map.of("postId", post.getId()));
    }

    /** 我发过的动态（只含可见的；被下架的不在主播侧展示）。 */
    @GetMapping("/posts")
    public ApiResponse<List<FeedController.PostView>> mine(
            @RequestParam(defaultValue = "20") int limit) {
        Talent me = requireMe();
        return ApiResponse.ok(feedService.byTalent(me.getId(), null, limit).stream()
                .map(d -> new FeedController.PostView(
                        d.post().getId(), d.post().getTalentId(),
                        d.talent() == null ? null : d.talent().getNickname(),
                        d.talent() == null ? null : d.talent().getCoverUrl(),
                        d.post().getContent(), d.images(),
                        d.likeCount(), d.commentCount(), false,
                        d.post().getCreatedAt()))
                .toList());
    }

    private Talent requireMe() {
        Talent me = talentService.findByUserId(CurrentUserHolder.requireUserId());
        if (me == null) {
            throw new BusinessException("只有主播可以发布动态");
        }
        return me;
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择图片");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException("图片不超过 2 MB");
        }
        String type = file.getContentType() == null ? ""
                : file.getContentType().toLowerCase(Locale.ROOT);
        if (!IMAGE_TYPES.contains(type)) {
            throw new BusinessException("仅支持 JPG、PNG 与 WebP");
        }
    }
}
