package com.guild.checkout.talent;

import com.guild.checkout.auth.CurrentUserHolder;
import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.pricing.PricingService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 粉丝的收藏操作与收藏列表。 */
@RestController
@RequestMapping("/api")
public class TalentFavoriteController {

    private final TalentFavoriteService favoriteService;
    private final TalentService talentService;
    private final PricingService pricingService;

    public TalentFavoriteController(TalentFavoriteService favoriteService,
                                    TalentService talentService,
                                    PricingService pricingService) {
        this.favoriteService = favoriteService;
        this.talentService = talentService;
        this.pricingService = pricingService;
    }

    /**
     * 收藏列表项。
     *
     * 比主播卡片多一个 available：已下架的主播仍留在收藏里，
     * 但要标记为暂不可约且不给下单入口——直接从列表里抹掉会让粉丝
     * 以为自己的收藏丢了。
     */
    public record FavoriteItem(
            Long id,
            String nickname,
            String coverUrl,
            boolean online,
            String gender,
            Integer age,
            String region,
            boolean available,
            String unavailableReason) {
    }

    @PostMapping("/talents/{talentId}/favorite")
    public ApiResponse<Map<String, Boolean>> favorite(@PathVariable long talentId) {
        favoriteService.add(CurrentUserHolder.requireUserId(), talentId);
        return ApiResponse.ok(Map.of("favorited", true));
    }

    @DeleteMapping("/talents/{talentId}/favorite")
    public ApiResponse<Map<String, Boolean>> unfavorite(@PathVariable long talentId) {
        favoriteService.remove(CurrentUserHolder.requireUserId(), talentId);
        return ApiResponse.ok(Map.of("favorited", false));
    }

    @GetMapping("/me/favorites")
    public ApiResponse<List<FavoriteItem>> myFavorites() {
        long userId = CurrentUserHolder.requireUserId();
        List<Long> ids = favoriteService.listTalentIds(userId);
        if (ids.isEmpty()) {
            return ApiResponse.ok(List.of());
        }
        Map<Long, Talent> byId = talentService.findByIds(ids);
        return ApiResponse.ok(ids.stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .map(this::toItem)
                .toList());
    }

    private FavoriteItem toItem(Talent t) {
        boolean listed = Boolean.TRUE.equals(t.getListed());
        boolean hasService = listed && !pricingService.listPurchasable(t.getId()).isEmpty();
        String reason = !listed ? "该主播暂不可约" : (hasService ? null : "该主播暂无可购服务");
        return new FavoriteItem(
                t.getId(), t.getNickname(), t.getCoverUrl(),
                Boolean.TRUE.equals(t.getOnline()),
                t.getGender(), t.getAge(), t.getRegion(),
                hasService, reason);
    }
}
