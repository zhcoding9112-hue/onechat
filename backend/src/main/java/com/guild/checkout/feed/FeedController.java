package com.guild.checkout.feed;

import com.guild.checkout.auth.CurrentUser;
import com.guild.checkout.auth.CurrentUserHolder;
import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.level.FanLevelService;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.TalentService;
import com.guild.checkout.user.AppUser;
import com.guild.checkout.user.AppUserMapper;
import com.guild.checkout.user.UserDisplay;
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
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 粉丝侧的动态广场、心动墙与举报。 */
@RestController
@RequestMapping("/api/feed")
public class FeedController {

    private final FeedService feedService;
    private final ReportService reportService;
    private final CrushWallService crushWallService;
    private final TalentService talentService;
    private final AppUserMapper userMapper;
    private final FanLevelService fanLevelService;

    public FeedController(FeedService feedService,
                          ReportService reportService,
                          CrushWallService crushWallService,
                          TalentService talentService,
                          AppUserMapper userMapper,
                          FanLevelService fanLevelService) {
        this.feedService = feedService;
        this.reportService = reportService;
        this.crushWallService = crushWallService;
        this.talentService = talentService;
        this.userMapper = userMapper;
        this.fanLevelService = fanLevelService;
    }

    public record PostView(Long id, Long talentId, String nickname, String coverUrl,
                           String content, List<String> images,
                           long likeCount, long commentCount, boolean liked,
                           LocalDateTime createdAt) {
    }

    public record PostPage(List<PostView> items, long total, boolean hasMore) {
    }

    /**
     * 一条评论。
     *
     * 原本只有 (id, userId, content, createdAt)——前端连"这句话是谁说的"都显示不了，
     * 评论区实际是匿名的。补上昵称与头像之后才算个评论区。
     *
     * @param author    是不是这条动态的主播本人
     * @param level     粉丝等级。作者本人为 null：主播没有"粉丝等级"这回事，
     *                  给他挂一个"新客"反而荒唐
     */
    public record CommentView(Long id, Long userId, String nickname, String avatarUrl,
                              boolean author, Integer level, String levelName,
                              String content, LocalDateTime createdAt) {
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
        FeedPost post = feedService.requireVisible(postId);
        return ApiResponse.ok(toCommentViews(feedService.comments(postId), post));
    }

    @PostMapping("/posts/{postId}/comments")
    public ApiResponse<CommentView> comment(@PathVariable long postId,
                                            @Valid @RequestBody CommentRequest req) {
        FeedPost post = feedService.requireVisible(postId);
        FeedComment c = feedService.comment(
                CurrentUserHolder.requireUserId(), postId, req.content());
        return ApiResponse.ok(toCommentViews(List.of(c), post).get(0));
    }

    /**
     * 把评论装配成带身份的视图。
     *
     * 昵称与等级都批量查：一篇动态下几十条评论，逐条查用户再逐条算等级，
     * 就是几十次往返加上百条聚合查询。
     */
    private List<CommentView> toCommentViews(List<FeedComment> comments, FeedPost post) {
        if (comments.isEmpty()) {
            return List.of();
        }

        // 作者本人的 userId：动态属于主播，主播可能还没绑用户
        Talent talent = post.getTalentId() == null ? null : talentService.find(post.getTalentId());
        Long authorUserId = talent == null ? null : talent.getUserId();

        List<Long> userIds = comments.stream().map(FeedComment::getUserId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, AppUser> users = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(AppUser::getId, Function.identity()));

        // 作者不参与等级计算：主播没有「粉丝等级」这回事。
        // 这里是唯一一处决定「谁有等级」——下面直接按 id 取，不再重复判一次作者，
        // 两道一模一样的闸门里坏掉任何一道都测不出来。
        List<Long> fanIds = userIds.stream()
                .filter(id -> !Objects.equals(id, authorUserId)).toList();
        Map<Long, FanLevelService.FanLevel> levels = fanLevelService.levelsOf(fanIds);

        return comments.stream().map(c -> {
            AppUser u = users.get(c.getUserId());
            boolean isAuthor = authorUserId != null && Objects.equals(c.getUserId(), authorUserId);
            FanLevelService.FanLevel lv = levels.get(c.getUserId());
            return new CommentView(
                    c.getId(), c.getUserId(),
                    UserDisplay.nickname(u == null ? null : u.getNickname(), c.getUserId()),
                    UserDisplay.avatarUrl(u == null ? null : u.getAvatarUrl()),
                    isAuthor,
                    lv == null ? null : lv.level(),
                    lv == null ? null : lv.name(),
                    c.getContent(), c.getCreatedAt());
        }).toList();
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
