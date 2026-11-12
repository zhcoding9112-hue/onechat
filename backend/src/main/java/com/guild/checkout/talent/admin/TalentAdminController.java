package com.guild.checkout.talent.admin;

import com.guild.checkout.auth.RequireRole;
import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.TalentService;
import com.guild.checkout.talent.TalentTag;
import com.guild.checkout.user.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 运营侧的主播档案维护。整类要求运营角色。 */
@RestController
@RequestMapping("/api/admin/talents")
@RequireRole(UserRole.OPERATOR)
public class TalentAdminController {

    private final TalentAdminService adminService;
    private final TalentService talentService;

    public TalentAdminController(TalentAdminService adminService, TalentService talentService) {
        this.adminService = adminService;
        this.talentService = talentService;
    }

    public record TalentView(
            Long id, Long userId, String nickname, String coverUrl, String intro,
            String audioUrl, Integer audioSeconds, boolean online, boolean listed,
            int sortOrder, List<String> tags) {
    }

    public record CreateRequest(
            @NotBlank(message = "昵称不能为空") @Size(max = 64, message = "昵称不超过 64 字符") String nickname,
            @Size(max = 500, message = "简介不超过 500 字符") String intro,
            @Size(max = 512) String coverUrl,
            Integer sortOrder) {
    }

    public record UpdateRequest(
            @Size(max = 64, message = "昵称不超过 64 字符") String nickname,
            @Size(max = 500, message = "简介不超过 500 字符") String intro,
            @Size(max = 512) String coverUrl,
            Integer sortOrder) {
    }

    public record TagsRequest(List<@Size(max = 32, message = "单个标签不超过 32 字符") String> tags) {
    }

    public record AudioRequest(@Size(max = 512) String audioUrl,
                               @Positive(message = "试听时长必须大于零") Integer seconds) {
    }

    public record LinkRequest(@jakarta.validation.constraints.NotNull(message = "用户 ID 不能为空") Long userId) {
    }

    @GetMapping
    public ApiResponse<List<TalentView>> list() {
        List<Talent> talents = talentService.listAll();
        Map<Long, List<TalentTag>> tags = talentService.listTagsByTalents(
                talents.stream().map(Talent::getId).toList());
        return ApiResponse.ok(talents.stream().map(t -> toView(t, tags.get(t.getId()))).toList());
    }

    @GetMapping("/{talentId}")
    public ApiResponse<TalentView> detail(@PathVariable long talentId) {
        Talent talent = talentService.require(talentId);
        return ApiResponse.ok(toView(talent, talentService.listTags(talentId)));
    }

    @PostMapping
    public ApiResponse<TalentView> create(@Valid @RequestBody CreateRequest req) {
        Talent talent = adminService.create(req.nickname(), req.intro(), req.coverUrl(), req.sortOrder());
        return ApiResponse.ok(toView(talent, List.of()));
    }

    @PutMapping("/{talentId}")
    public ApiResponse<TalentView> update(@PathVariable long talentId,
                                          @Valid @RequestBody UpdateRequest req) {
        Talent talent = adminService.update(
                talentId, req.nickname(), req.intro(), req.coverUrl(), req.sortOrder());
        return ApiResponse.ok(toView(talent, talentService.listTags(talentId)));
    }

    @PutMapping("/{talentId}/listed/{listed}")
    public ApiResponse<TalentView> setListed(@PathVariable long talentId, @PathVariable boolean listed) {
        Talent talent = adminService.setListed(talentId, listed);
        return ApiResponse.ok(toView(talent, talentService.listTags(talentId)));
    }

    @PutMapping("/{talentId}/tags")
    public ApiResponse<TalentView> setTags(@PathVariable long talentId,
                                           @Valid @RequestBody TagsRequest req) {
        List<TalentTag> tags = adminService.replaceTags(talentId, req.tags());
        return ApiResponse.ok(toView(talentService.require(talentId), tags));
    }

    @PutMapping("/{talentId}/audio")
    public ApiResponse<TalentView> setAudio(@PathVariable long talentId,
                                            @Valid @RequestBody AudioRequest req) {
        Talent talent = adminService.setAudio(talentId, req.audioUrl(), req.seconds());
        return ApiResponse.ok(toView(talent, talentService.listTags(talentId)));
    }

    @PutMapping("/{talentId}/user")
    public ApiResponse<TalentView> linkUser(@PathVariable long talentId,
                                            @Valid @RequestBody LinkRequest req) {
        Talent talent = adminService.linkUser(talentId, req.userId());
        return ApiResponse.ok(toView(talent, talentService.listTags(talentId)));
    }

    @DeleteMapping("/{talentId}/user")
    public ApiResponse<TalentView> unlinkUser(@PathVariable long talentId) {
        Talent talent = adminService.unlinkUser(talentId);
        return ApiResponse.ok(toView(talent, talentService.listTags(talentId)));
    }

    static TalentView toView(Talent t, List<TalentTag> tags) {
        return new TalentView(
                t.getId(), t.getUserId(), t.getNickname(), t.getCoverUrl(), t.getIntro(),
                t.getAudioUrl(), t.getAudioSeconds(),
                Boolean.TRUE.equals(t.getOnline()), Boolean.TRUE.equals(t.getListed()),
                t.getSortOrder() == null ? 0 : t.getSortOrder(),
                tags == null ? List.of() : tags.stream().map(TalentTag::getName).toList());
    }
}
