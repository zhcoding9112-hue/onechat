package com.guild.checkout.audition;

import com.guild.checkout.auth.CurrentUserHolder;
import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.TalentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 试音选人：粉丝侧与主播侧的接口。 */
@RestController
@RequestMapping("/api/auditions")
public class AuditionController {

    private final AuditionService auditionService;
    private final TalentService talentService;

    public AuditionController(AuditionService auditionService, TalentService talentService) {
        this.auditionService = auditionService;
        this.talentService = talentService;
    }

    public record PublishRequest(
            Long serviceItemId,
            @NotBlank(message = "声音描述不能为空")
            @Size(max = 200, message = "声音描述不超过 200 字符") String voiceDesc) {
    }

    public record ReplyRequest(@Size(max = 200, message = "留言不超过 200 字符") String message) {
    }

    public record RequestView(Long id, Long serviceItemId, String voiceDesc,
                              String status, LocalDateTime expiresAt, int replyCount) {
    }

    /** 应征项。带上主播的试听片段，粉丝据此挑人。 */
    public record ReplyView(Long replyId, Long talentId, String nickname, String coverUrl,
                            String audioUrl, Integer audioSeconds, String message,
                            boolean online, LocalDateTime repliedAt) {
    }

    // ---------- 粉丝侧 ----------

    /** 我当前进行中的需求；没有则 data 为 null。 */
    @GetMapping("/mine")
    public ApiResponse<RequestView> mine() {
        long fanUserId = CurrentUserHolder.requireUserId();
        return ApiResponse.ok(auditionService.findActive(fanUserId)
                .map(this::toView)
                .orElse(null));
    }

    @PostMapping
    public ApiResponse<RequestView> publish(@Valid @RequestBody PublishRequest req) {
        long fanUserId = CurrentUserHolder.requireUserId();
        return ApiResponse.ok(toView(
                auditionService.publish(fanUserId, req.serviceItemId(), req.voiceDesc())));
    }

    @DeleteMapping("/{requestId}")
    public ApiResponse<Void> close(@PathVariable long requestId) {
        auditionService.closeOwn(CurrentUserHolder.requireUserId(), requestId);
        return ApiResponse.ok(null);
    }

    /** 某条需求的应征列表。只有需求发布者本人可见。 */
    @GetMapping("/{requestId}/replies")
    public ApiResponse<List<ReplyView>> replies(@PathVariable long requestId) {
        long fanUserId = CurrentUserHolder.requireUserId();
        AuditionRequest req = auditionService.require(requestId);
        if (!java.util.Objects.equals(req.getFanUserId(), fanUserId)) {
            throw new BusinessException("只能查看自己发布的试音需求");
        }
        List<AuditionReply> replies = auditionService.listReplies(requestId);
        Map<Long, Talent> talents = talentService.findByIds(
                replies.stream().map(AuditionReply::getTalentId).toList());

        return ApiResponse.ok(replies.stream()
                .map(r -> {
                    Talent t = talents.get(r.getTalentId());
                    if (t == null) {
                        return null;
                    }
                    return new ReplyView(
                            r.getId(), t.getId(), t.getNickname(), t.getCoverUrl(),
                            t.getAudioUrl(), t.getAudioSeconds(), r.getMessage(),
                            Boolean.TRUE.equals(t.getOnline()), r.getCreatedAt());
                })
                .filter(java.util.Objects::nonNull)
                .toList());
    }

    // ---------- 主播侧 ----------

    /** 可应征的开放需求。 */
    @GetMapping("/open")
    public ApiResponse<List<RequestView>> open() {
        return ApiResponse.ok(auditionService.listOpen(50).stream().map(this::toView).toList());
    }

    @PostMapping("/{requestId}/replies")
    public ApiResponse<Map<String, Long>> reply(@PathVariable long requestId,
                                                @Valid @RequestBody ReplyRequest req) {
        long userId = CurrentUserHolder.requireUserId();
        Talent me = talentService.findByUserId(userId);
        if (me == null) {
            throw new BusinessException("只有主播可以应征试音需求");
        }
        AuditionReply reply = auditionService.reply(me.getId(), requestId, req.message());
        return ApiResponse.ok(Map.of("replyId", reply.getId()));
    }

    private RequestView toView(AuditionRequest req) {
        return new RequestView(
                req.getId(), req.getServiceItemId(), req.getVoiceDesc(),
                req.getStatus(), req.getExpiresAt(),
                auditionService.listReplies(req.getId()).size());
    }
}
