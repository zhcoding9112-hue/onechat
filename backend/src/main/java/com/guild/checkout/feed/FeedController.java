package com.guild.checkout.feed;

import com.guild.checkout.auth.CurrentUser;
import com.guild.checkout.auth.CurrentUserHolder;
import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.talent.Talent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** 粉丝侧的动态广场、心动墙与举报。 */
@RestController
@RequestMapping("/api/feed")
public class FeedController {

    private final FeedService feedService;
    private final ReportService reportService;
    private final CrushWallService crushWallService;

    public FeedController(FeedService feedService,
                          ReportService reportService,
                          CrushWallService crushWallService) {
        this.feedService = feedService;
        this.reportService = reportService;
        this.crushWallService = crushWallService;
    }

    public record PostView(Long id, Long talentId, String nickname, String coverUrl,
                           String content, List<String> images,
                           long likeCount, long commentCount, boolean liked,
                           LocalDateTime createdAt) {
    }

    public record PostPage(List<PostView> items, long total, boolean hasMore) {
    }

    public record CommentView(Long id, Long userId, String content, LocalDateTime createdAt) {
    }

    public record CommentRequest(
            @NotBlank(message = "评论内容不能为空")
            @Size(max = 500, message = "评论不超过 500 字符") String content) {
    }

    public record ReportRequest(
            @NotBlank(message = "举报理由为必填")
            @Size(max = 255, message = "举报理由不超过 255 字符") String reason) {
    }

    public record WallItem(Long talentId, String nickname, String coverUrl,
                           boolean online, List<String> promoTags) {
    }

    // ---------- 广场 ----------

    @GetMapping("/posts")
    public ApiResponse<PostPage> square(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "10") int size) {
        Long viewer = viewerId();
        int limit = Math.min(Math.max(size, 1), 50);
        List<PostView> items = feedService.square(viewer, page, limit).stream()
                .map(FeedController::toView).toList();
        long total = feedService.countVisible();
        boolean hasMore = (long) Math.max(page, 0) * limit + items.size() < total;
        return ApiResponse.ok(new PostPage(items, total, hasMore));
    }

    @GetMapping("/posts/{postId}")
    public ApiResponse<PostView> detail(@PathVariable long postId) {
        return ApiResponse.ok(toView(feedService.detail(postId, viewerId())));
    }

    /** 某位主播的动态，供详情页内嵌展示。 */
    @GetMapping("/talents/{talentId}/posts")
    public ApiResponse<List<PostView>> byTalent(@PathVariable long talentId,
                                                @RequestParam(defaultValue = "5") int limit) {
        return ApiResponse.ok(feedService.byTalent(talentId, viewerId(), limit).stream()
                .map(FeedController::toView).toList());
    }

    // ---------- 互动 ----------

    @PostMapping("/posts/{postId}/like")
    public ApiResponse<Long> like(@PathVariable long postId) {
        feedService.like(CurrentUserHolder.requireUserId(), postId);
        return ApiResponse.ok(feedService.likeCount(postId));
    }

    @DeleteMapping("/posts/{postId}/like")
    public ApiResponse<Long> unlike(@PathVariable long postId) {
        feedService.unlike(CurrentUserHolder.requireUserId(), postId);
        return ApiResponse.ok(feedService.likeCount(postId));
    }

    @GetMapping("/posts/{postId}/comments")
    public ApiResponse<List<CommentView>> comments(@PathVariable long postId) {
        feedService.requireVisible(postId);
        return ApiResponse.ok(feedService.comments(postId).stream()
                .map(c -> new CommentView(c.getId(), c.getUserId(), c.getContent(), c.getCreatedAt()))
                .toList());
    }

    @PostMapping("/posts/{postId}/comments")
    public ApiResponse<CommentView> comment(@PathVariable long postId,
                                            @Valid @RequestBody CommentRequest req) {
        FeedComment c = feedService.comment(
                CurrentUserHolder.requireUserId(), postId, req.content());
        return ApiResponse.ok(new CommentView(c.getId(), c.getUserId(), c.getContent(), c.getCreatedAt()));
    }

    // ---------- 举报 ----------

    @PostMapping("/posts/{postId}/report")
    public ApiResponse<Void> reportPost(@PathVariable long postId,
                                        @Valid @RequestBody ReportRequest req) {
        reportService.report(CurrentUserHolder.requireUserId(),
                ContentReport.TARGET_POST, postId, req.reason());
        return ApiResponse.ok(null);
    }

    @PostMapping("/comments/{commentId}/report")
    public ApiResponse<Void> reportComment(@PathVariable long commentId,
                                           @Valid @RequestBody ReportRequest req) {
        reportService.report(CurrentUserHolder.requireUserId(),
                ContentReport.TARGET_COMMENT, commentId, req.reason());
        return ApiResponse.ok(null);
    }

    // ---------- 心动墙 ----------

    @GetMapping("/crush-wall")
    public ApiResponse<List<WallItem>> crushWall(@RequestParam(defaultValue = "12") int limit) {
        return ApiResponse.ok(crushWallService.wall(limit).stream()
                .map(FeedController::toWallItem).toList());
    }

    // ---------- 内部 ----------

    /**
     * 当前查看者。
     *
     * 本类的接口默认要求已登录（见 PublicApi 的说明），所以实际上不会为 null；
     * 这里仍做空判断，是为了日后真把广场开成免登录浏览时，这条路径不会 NPE。
     */
    private static Long viewerId() {
        CurrentUser user = CurrentUserHolder.get();
        return user == null ? null : user.userId();
    }

    private static PostView toView(FeedService.PostDetail d) {
        Talent t = d.talent();
        return new PostView(
                d.post().getId(), d.post().getTalentId(),
                t == null ? null : t.getNickname(),
                t == null ? null : t.getCoverUrl(),
                d.post().getContent(), d.images(),
                d.likeCount(), d.commentCount(), d.likedByMe(),
                d.post().getCreatedAt());
    }

    private static WallItem toWallItem(Talent t) {
        List<String> tags = new java.util.ArrayList<>();
        if (t.getPromoTag1() != null && !t.getPromoTag1().isBlank()) {
            tags.add(t.getPromoTag1());
        }
        if (t.getPromoTag2() != null && !t.getPromoTag2().isBlank()) {
            tags.add(t.getPromoTag2());
        }
        return new WallItem(t.getId(), t.getNickname(), t.getCoverUrl(),
                Boolean.TRUE.equals(t.getOnline()), tags);
    }
}
