package com.guild.checkout.feed;

import com.guild.checkout.auth.CurrentUserHolder;
import com.guild.checkout.auth.RequireRole;
import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.TalentService;
import com.guild.checkout.user.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 运营侧：内容审核、下架与心动墙。整类要求运营角色。 */
@RestController
@RequestMapping("/api/admin/feed")
@RequireRole(UserRole.OPERATOR)
public class FeedAdminController {

    private final FeedService feedService;
    private final ReportService reportService;
    private final CrushWallService crushWallService;
    private final TalentService talentService;

    public FeedAdminController(FeedService feedService,
                               ReportService reportService,
                               CrushWallService crushWallService,
                               TalentService talentService) {
        this.feedService = feedService;
        this.reportService = reportService;
        this.crushWallService = crushWallService;
        this.talentService = talentService;
    }

    public record ReasonRequest(
            @NotBlank(message = "原因为必填")
            @Size(max = 255, message = "原因不超过 255 字符") String reason) {
    }

    public record NoteRequest(@Size(max = 255, message = "说明不超过 255 字符") String note) {
    }

    /**
     * 运营看到的一条动态：含原始内容与完整处理记录。
     *
     * 已下架的动态在这里仍然原样可见——软下架的全部意义就在于此。
     */
    public record AuditPostView(Long id, Long talentId, String nickname,
                                String content, List<String> images, String status,
                                Long removedBy, LocalDateTime removedAt, String removeReason,
                                long likeCount, long commentCount,
                                LocalDateTime createdAt,
                                List<ContentReport> reports) {
    }

    @GetMapping("/posts")
    public ApiResponse<List<AuditPostView>> posts(@RequestParam(required = false) String status,
                                                  @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(feedService.listAllForOperator(status, limit).stream()
                .map(p -> view(feedService.detailForOperator(p.getId())))
                .toList());
    }

    @GetMapping("/posts/{postId}")
    public ApiResponse<AuditPostView> post(@PathVariable long postId) {
        return ApiResponse.ok(view(feedService.detailForOperator(postId)));
    }

    /** 一条动态的全部评论，含已下架的。 */
    @GetMapping("/posts/{postId}/comments")
    public ApiResponse<List<FeedComment>> comments(@PathVariable long postId) {
        return ApiResponse.ok(feedService.allCommentsForOperator(postId));
    }

    @PutMapping("/posts/{postId}/remove")
    public ApiResponse<Void> removePost(@PathVariable long postId,
                                        @Valid @RequestBody ReasonRequest req) {
        feedService.removePost(postId, CurrentUserHolder.requireUserId(), req.reason());
        return ApiResponse.ok(null);
    }

    @PutMapping("/comments/{commentId}/remove")
    public ApiResponse<Void> removeComment(@PathVariable long commentId,
                                           @Valid @RequestBody ReasonRequest req) {
        feedService.removeComment(commentId, CurrentUserHolder.requireUserId(), req.reason());
        return ApiResponse.ok(null);
    }

    // ---------- 举报队列 ----------

    @GetMapping("/reports")
    public ApiResponse<List<ContentReport>> reports(@RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(reportService.pending(limit));
    }

    @PutMapping("/reports/{reportId}/remove")
    public ApiResponse<Map<String, Integer>> handle(@PathVariable long reportId,
                                                    @Valid @RequestBody ReasonRequest req) {
        int closed = reportService.handleByRemoving(
                reportId, CurrentUserHolder.requireUserId(), req.reason());
        return ApiResponse.ok(Map.of("closedReports", closed));
    }

    @PutMapping("/reports/{reportId}/dismiss")
    public ApiResponse<Map<String, Integer>> dismiss(@PathVariable long reportId,
                                                     @RequestBody(required = false) NoteRequest req) {
        int closed = reportService.dismiss(reportId, CurrentUserHolder.requireUserId(),
                req == null ? null : req.note());
        return ApiResponse.ok(Map.of("closedReports", closed));
    }

    // ---------- 心动墙 ----------

    public record PickRequest(Long talentId, Integer sortOrder) {
    }

    public record PickView(Long talentId, String nickname, String coverUrl,
                           int sortOrder, boolean enabled, boolean listed) {
    }

    @GetMapping("/crush-wall")
    public ApiResponse<List<PickView>> picks() {
        List<CrushWallPick> picks = crushWallService.listPicks();
        Map<Long, Talent> talents = talentService.findByIds(
                picks.stream().map(CrushWallPick::getTalentId).toList());
        return ApiResponse.ok(picks.stream().map(p -> {
            Talent t = talents.get(p.getTalentId());
            return new PickView(p.getTalentId(),
                    t == null ? null : t.getNickname(),
                    t == null ? null : t.getCoverUrl(),
                    p.getSortOrder(), Boolean.TRUE.equals(p.getEnabled()),
                    t != null && Boolean.TRUE.equals(t.getListed()));
        }).toList());
    }

    @PostMapping("/crush-wall")
    public ApiResponse<Void> pick(@RequestBody PickRequest req) {
        crushWallService.pick(req.talentId(), req.sortOrder() == null ? 0 : req.sortOrder());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/crush-wall/{talentId}")
    public ApiResponse<Void> unpick(@PathVariable long talentId) {
        crushWallService.unpick(talentId);
        return ApiResponse.ok(null);
    }

    // ---------- 配置 ----------

    public record MaxImagesRequest(Integer maxImages) {
    }

    @GetMapping("/settings")
    public ApiResponse<FeedSetting> settings() {
        return ApiResponse.ok(feedService.setting());
    }

    @PutMapping("/settings")
    public ApiResponse<FeedSetting> updateSettings(@RequestBody MaxImagesRequest req) {
        return ApiResponse.ok(feedService.updateMaxImages(
                req.maxImages() == null ? 9 : req.maxImages()));
    }

    private AuditPostView view(FeedService.PostDetail d) {
        return new AuditPostView(
                d.post().getId(), d.post().getTalentId(),
                d.talent() == null ? null : d.talent().getNickname(),
                d.post().getContent(), d.images(), d.post().getStatus(),
                d.post().getRemovedBy(), d.post().getRemovedAt(), d.post().getRemoveReason(),
                d.likeCount(), d.commentCount(), d.post().getCreatedAt(),
                reportService.onTarget(ContentReport.TARGET_POST, d.post().getId()));
    }
}
