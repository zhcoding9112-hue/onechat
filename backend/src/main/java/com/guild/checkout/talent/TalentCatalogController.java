package com.guild.checkout.talent;

import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.pricing.PricingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 粉丝端的主播目录。
 *
 * 列表只含已上架主播，固定顺序，不提供搜索与筛选——粉丝是指名来找特定主播的，
 * 不存在匹配问题。详情页对已下架主播只展示提示，不给下单入口。
 */
@RestController
@RequestMapping("/api/talents")
public class TalentCatalogController {

    private final TalentService talentService;
    private final PricingService pricingService;

    public TalentCatalogController(TalentService talentService, PricingService pricingService) {
        this.talentService = talentService;
        this.pricingService = pricingService;
    }

    // ---------- 视图模型 ----------

    public record TalentCard(
            Long id,
            String nickname,
            String coverUrl,
            List<String> tags,
            boolean online,
            /** 有试听片段时才给地址，前端据此决定是否展示播放控件。 */
            String audioUrl,
            Integer audioSeconds,
            /** 以下为扩展资料，未填写时为 null，前端整项不渲染，不得展示空占位。 */
            String gender,
            Integer age,
            String region,
            /** 宣传语标签，运营自填，0 到 2 条。 */
            List<String> promoTags) {
    }

    /**
     * 一页主播。
     *
     * 带上 total 而不是只给 hasMore：前端既要判断「没有更多」，
     * 也要在筛选后告诉粉丝共多少位，两个信息用一个字段表达不了。
     */
    public record TalentFeed(List<TalentCard> items, long total, boolean hasMore) {
    }

    public record SpecOption(
            Long serviceSpecId, String code, String name,
            Integer durationMinutes, long unitPriceCents) {
    }

    public record ItemGroup(Long serviceItemId, String code, String name, List<SpecOption> specs) {
    }

    public record TalentDetail(
            Long id,
            String nickname,
            String coverUrl,
            String intro,
            List<String> tags,
            boolean online,
            String audioUrl,
            Integer audioSeconds,
            /** 是否可约。已下架为 false，此时前端不展示下单入口。 */
            boolean available,
            /** 不可约时的提示文案，可约时为 null。 */
            String unavailableReason,
            List<ItemGroup> services,
            String gender,
            Integer age,
            String region,
            List<String> promoTags) {
    }

    // ---------- 列表 ----------

    @GetMapping
    public ApiResponse<TalentFeed> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) Integer level,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        TalentService.TalentPage result = talentService.listListed(category, gender, level, page, size);
        List<Talent> talents = result.items();
        Map<Long, List<TalentTag>> tagMap = talentService.listTagsByTalents(
                talents.stream().map(Talent::getId).toList());

        List<TalentCard> cards = talents.stream()
                .map(t -> new TalentCard(
                        t.getId(),
                        t.getNickname(),
                        t.getCoverUrl(),
                        tagNames(tagMap.get(t.getId())),
                        Boolean.TRUE.equals(t.getOnline()),
                        t.getAudioUrl(),
                        t.getAudioSeconds(),
                        t.getGender(),
                        t.getAge(),
                        t.getRegion(),
                        promoTags(t)))
                .toList();

        int consumed = Math.max(page, 0) * Math.min(Math.max(size, 1), 50) + cards.size();
        return ApiResponse.ok(new TalentFeed(cards, result.total(), consumed < result.total()));
    }

    // ---------- 详情 ----------

    @GetMapping("/{talentId}")
    public ApiResponse<TalentDetail> detail(@PathVariable long talentId) {
        Talent talent = talentService.require(talentId);
        boolean listed = Boolean.TRUE.equals(talent.getListed());

        // 已下架：展示资料但不给下单入口，也不暴露价格。
        if (!listed) {
            return ApiResponse.ok(new TalentDetail(
                    talent.getId(), talent.getNickname(), talent.getCoverUrl(), talent.getIntro(),
                    tagNames(talentService.listTags(talentId)),
                    Boolean.TRUE.equals(talent.getOnline()),
                    talent.getAudioUrl(), talent.getAudioSeconds(),
                    false, "该主播暂不可约", List.of(),
                    talent.getGender(), talent.getAge(), talent.getRegion(), promoTags(talent)));
        }

        List<ItemGroup> services = groupServices(talentId);
        // 上架但一个档位都没配价：展示资料，提示暂无可购服务。
        String reason = services.isEmpty() ? "该主播暂无可购服务" : null;

        return ApiResponse.ok(new TalentDetail(
                talent.getId(), talent.getNickname(), talent.getCoverUrl(), talent.getIntro(),
                tagNames(talentService.listTags(talentId)),
                Boolean.TRUE.equals(talent.getOnline()),
                talent.getAudioUrl(), talent.getAudioSeconds(),
                !services.isEmpty(), reason, services,
                talent.getGender(), talent.getAge(), talent.getRegion(), promoTags(talent)));
    }

    /** 两列宣传语压成列表，null 的那条直接不出现——前端只管按序渲染，不必判空。 */
    private static List<String> promoTags(Talent t) {
        return java.util.stream.Stream.of(t.getPromoTag1(), t.getPromoTag2())
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    // ---------- 可购服务 ----------

    @GetMapping("/{talentId}/services")
    public ApiResponse<List<ItemGroup>> listServices(@PathVariable long talentId) {
        return ApiResponse.ok(groupServices(talentId));
    }

    // ---------- 辅助 ----------

    private List<ItemGroup> groupServices(long talentId) {
        Map<Long, Builder> grouped = new LinkedHashMap<>();
        for (PricingService.PurchasableSpec spec : pricingService.listPurchasable(talentId)) {
            grouped.computeIfAbsent(spec.serviceItemId(),
                            id -> new Builder(id, spec.itemCode(), spec.itemName()))
                    .specs.add(new SpecOption(
                            spec.serviceSpecId(), spec.specCode(), spec.specName(),
                            spec.durationMinutes(), spec.unitPriceCents()));
        }
        return grouped.values().stream()
                .map(b -> new ItemGroup(b.id, b.code, b.name, List.copyOf(b.specs)))
                .toList();
    }

    private static List<String> tagNames(List<TalentTag> tags) {
        return tags == null ? List.of() : tags.stream().map(TalentTag::getName).toList();
    }

    private static final class Builder {
        private final Long id;
        private final String code;
        private final String name;
        private final List<SpecOption> specs = new ArrayList<>();

        private Builder(Long id, String code, String name) {
            this.id = id;
            this.code = code;
            this.name = name;
        }
    }
}
