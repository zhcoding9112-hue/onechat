package com.guild.checkout.home;

import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.pricing.ServiceItem;
import com.guild.checkout.pricing.admin.ServiceCatalogAdminService;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.TalentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 首页各区块的数据接口。
 *
 * 刻意按区块拆成多个端点，而不是一个聚合接口：首页要求任一区块取数失败时
 * 其余部分仍可正常浏览。聚合接口做不到这一点——一处异常整页都拿不到数据。
 */
@RestController
@RequestMapping("/api/home")
public class HomeController {

    private static final int POPULAR_LIMIT = 10;
    private static final int MARQUEE_LIMIT = 20;

    private final HomeService homeService;
    private final TalentService talentService;
    private final ServiceCatalogAdminService catalogService;
    private final SupportProperties supportProperties;

    public HomeController(HomeService homeService,
                          TalentService talentService,
                          ServiceCatalogAdminService catalogService,
                          SupportProperties supportProperties) {
        this.homeService = homeService;
        this.talentService = talentService;
        this.catalogService = catalogService;
        this.supportProperties = supportProperties;
    }

    public record CategoryView(String code, String name) {
    }

    public record PopularTalentView(Long id, String nickname, String coverUrl, boolean online) {
    }

    public record MarqueeItem(String nickname, long amountCents) {
    }

    public record EntryView(Long id, String title, String subtitle, String imageUrl, String targetUrl) {
    }

    public record SupportView(String title, String description, String hours, String wechatId,
                              String qrCodeUrl, String serviceUrl, boolean configured) {
    }

    /** 与首页其余接口一样要求已登录；只返回客服展示字段，不暴露其他部署配置。 */
    @GetMapping("/support")
    public ApiResponse<SupportView> support() {
        var p = supportProperties;
        return ApiResponse.ok(new SupportView(p.title(), p.description(), p.hours(), p.wechatId(),
                p.qrCodeUrl(), p.serviceUrl(), p.configured()));
    }

    /** 品类 Tab。取自服务项目配置，不硬编码。 */
    @GetMapping("/categories")
    public ApiResponse<List<CategoryView>> categories() {
        return ApiResponse.ok(catalogService.listItems().stream()
                .filter(i -> Boolean.TRUE.equals(i.getEnabled()))
                .map(i -> new CategoryView(i.getCode(), i.getName()))
                .toList());
    }

    /** 人气店员。 */
    @GetMapping("/popular-talents")
    public ApiResponse<List<PopularTalentView>> popularTalents() {
        List<Long> ids = homeService.popularTalentIds(POPULAR_LIMIT);
        Map<Long, Talent> byId = ids.isEmpty() ? Map.of()
                : talentService.listListed().stream()
                        .filter(t -> ids.contains(t.getId()))
                        .collect(java.util.stream.Collectors.toMap(Talent::getId, t -> t));
        return ApiResponse.ok(ids.stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .map(t -> new PopularTalentView(
                        t.getId(), t.getNickname(), t.getCoverUrl(), Boolean.TRUE.equals(t.getOnline())))
                .toList());
    }

    /** 打赏跑马灯。返回项不含任何打赏人信息。 */
    @GetMapping("/tip-marquee")
    public ApiResponse<List<MarqueeItem>> tipMarquee() {
        return ApiResponse.ok(homeService.recentTips(MARQUEE_LIMIT).stream()
                .map(row -> new MarqueeItem(
                        String.valueOf(row.get("nickname")),
                        ((Number) row.get("amountCents")).longValue()))
                .toList());
    }

    /** 九宫格入口与运营横幅。type 取 GRID 或 BANNER。 */
    @GetMapping("/entries")
    public ApiResponse<List<EntryView>> entries(@RequestParam String type) {
        return ApiResponse.ok(homeService.listActiveEntries(HomeEntryType.of(type)).stream()
                .map(e -> new EntryView(
                        e.getId(), e.getTitle(), e.getSubtitle(), e.getImageUrl(), e.getTargetUrl()))
                .toList());
    }
}
