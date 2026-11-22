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
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
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
    private final TalentImportService importService;

    public TalentAdminController(TalentAdminService adminService, TalentService talentService,
                                 TalentImportService importService) {
        this.importService = importService;
        this.adminService = adminService;
        this.talentService = talentService;
    }

    public record TalentView(
            Long id, Long userId, String nickname, String coverUrl, String intro,
            String audioUrl, Integer audioSeconds, boolean online, boolean listed,
            int sortOrder, List<String> tags,
            String gender, Integer age, String region, List<String> promoTags) {
    }

    public record CreateRequest(
            @NotBlank(message = "昵称不能为空") @Size(max = 64, message = "昵称不超过 64 字符") String nickname,
            @Size(max = 500, message = "简介不超过 500 字符") String intro,
            @Size(max = 512) String coverUrl,
            Integer sortOrder,
            @Size(max = 8) String gender,
            Integer age,
            @Size(max = 32, message = "地区不超过 32 字符") String region,
            List<String> promoTags) {
    }

    public record UpdateRequest(
            @Size(max = 64, message = "昵称不超过 64 字符") String nickname,
            @Size(max = 500, message = "简介不超过 500 字符") String intro,
            @Size(max = 512) String coverUrl,
            Integer sortOrder,
            @Size(max = 8) String gender,
            Integer age,
            @Size(max = 32, message = "地区不超过 32 字符") String region,
            // 数量上限不用 @Size：超限要的是「最多 2 个」这句业务提示，
            // 统一在 TalentAdminService 里校验，和长度校验留在同一处。
            List<String> promoTags) {
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
        Talent talent = adminService.create(new TalentAdminService.ProfileForm(
                req.nickname(), req.intro(), req.coverUrl(), req.sortOrder(),
                req.gender(), req.age(), req.region(), req.promoTags()));
        return ApiResponse.ok(toView(talent, List.of()));
    }

    @PutMapping("/{talentId}")
    public ApiResponse<TalentView> update(@PathVariable long talentId,
                                          @Valid @RequestBody UpdateRequest req) {
        Talent talent = adminService.update(talentId, new TalentAdminService.ProfileForm(
                req.nickname(), req.intro(), req.coverUrl(), req.sortOrder(),
                req.gender(), req.age(), req.region(), req.promoTags()));
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
                tags == null ? List.of() : tags.stream().map(TalentTag::getName).toList(),
                t.getGender(), t.getAge(), t.getRegion(),
                java.util.stream.Stream.of(t.getPromoTag1(), t.getPromoTag2())
                        .filter(java.util.Objects::nonNull).toList());
    }

    // ---------- 批量导入 ----------

    public record ImportRequest(String csv, Boolean dryRun) {
    }

    /**
     * 批量导入花名册。
     *
     * dryRun=true 只校验不写库——几十行的表格里有一个错字，
     * 先试算一遍比导进去再收拾划算得多。
     */
    @PostMapping("/import")
    public ApiResponse<TalentImportService.ImportResult> importCsv(
            @RequestBody ImportRequest req) {
        boolean dryRun = Boolean.TRUE.equals(req.dryRun());
        try {
            return ApiResponse.ok(importService.importCsv(req.csv(), dryRun));
        } catch (TalentImportService.DryRunComplete done) {
            return ApiResponse.ok(done.result());
        } catch (TalentImportService.ImportFailed failed) {
            // 带着逐行结果回去，运营照着行号改表
            throw new com.guild.checkout.common.exception.BusinessException(failed.getMessage());
        }
    }

    /**
     * CSV 模板。带表头与一行示例，直接另存为就能填。
     *
     * 示例行刻意写成多档多价：「服务档位」「价格元」两列都按竖线分隔、按位置对应。
     * 运营看模板比看文档多，示例只给一档的话，多数人不会知道能填多档。
     */
    @GetMapping("/import/template")
    public ResponseEntity<Resource> template() {
        String csv = "\uFEFF"
                + String.join(",", TalentImportService.HEADERS) + "\n"
                + "小雨,女,23,杭州,\"温柔，耐心，喜欢听人说话\",温柔|耐心|夜猫子,深夜电台|声音很治愈,10,"
                + "listen:m15|listen:m30|listen:m60,9|15|25\n";
        byte[] body = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"talents-template.csv\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(new org.springframework.core.io.ByteArrayResource(body));
    }
}
