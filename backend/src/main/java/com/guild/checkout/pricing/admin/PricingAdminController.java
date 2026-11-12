package com.guild.checkout.pricing.admin;

import com.guild.checkout.auth.RequireRole;
import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.pricing.CommissionCalculator;
import com.guild.checkout.pricing.CommissionConfig;
import com.guild.checkout.pricing.ServiceItem;
import com.guild.checkout.pricing.ServiceSpec;
import com.guild.checkout.pricing.TalentPrice;
import com.guild.checkout.user.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

/**
 * 运营后台的服务目录、价格与分成配置接口。整类要求运营角色。
 */
@RestController
@RequestMapping("/api/admin/pricing")
@RequireRole(UserRole.OPERATOR)
public class PricingAdminController {

    private final ServiceCatalogAdminService catalogService;
    private final TalentPriceAdminService priceService;
    private final CommissionAdminService commissionService;

    public PricingAdminController(ServiceCatalogAdminService catalogService,
                                  TalentPriceAdminService priceService,
                                  CommissionAdminService commissionService) {
        this.catalogService = catalogService;
        this.priceService = priceService;
        this.commissionService = commissionService;
    }

    // ---------- 视图模型 ----------

    public record ItemView(Long id, String code, String name, String description,
                           int sortOrder, boolean enabled, List<SpecView> specs) {
    }

    public record SpecView(Long id, Long serviceItemId, String code, String name,
                           Integer durationMinutes, int sortOrder, boolean enabled) {
    }

    public record PriceView(Long id, Long talentId, Long serviceSpecId,
                            long unitPriceCents, boolean enabled) {
    }

    public record CommissionView(Long id, Long talentId, int rateBp, String rateText) {
    }

    // ---------- 服务项目 ----------

    public record CreateItemRequest(
            @NotBlank(message = "项目编码不能为空") @Size(max = 32, message = "项目编码不超过 32 字符") String code,
            @NotBlank(message = "项目名称不能为空") @Size(max = 64, message = "项目名称不超过 64 字符") String name,
            @Size(max = 255, message = "项目说明不超过 255 字符") String description,
            Integer sortOrder) {
    }

    public record UpdateItemRequest(
            @Size(max = 64, message = "项目名称不超过 64 字符") String name,
            @Size(max = 255, message = "项目说明不超过 255 字符") String description,
            Integer sortOrder) {
    }

    @GetMapping("/items")
    public ApiResponse<List<ItemView>> listItems() {
        List<ItemView> views = catalogService.listItems().stream()
                .map(item -> toItemView(item, catalogService.listSpecs(item.getId())))
                .toList();
        return ApiResponse.ok(views);
    }

    @PostMapping("/items")
    public ApiResponse<ItemView> createItem(@Valid @RequestBody CreateItemRequest req) {
        ServiceItem item = catalogService.createItem(req.code(), req.name(), req.description(), req.sortOrder());
        return ApiResponse.ok(toItemView(item, List.of()));
    }

    @PutMapping("/items/{itemId}")
    public ApiResponse<ItemView> updateItem(@PathVariable long itemId,
                                            @Valid @RequestBody UpdateItemRequest req) {
        ServiceItem item = catalogService.updateItem(itemId, req.name(), req.description(), req.sortOrder());
        return ApiResponse.ok(toItemView(item, catalogService.listSpecs(itemId)));
    }

    @PutMapping("/items/{itemId}/enabled/{enabled}")
    public ApiResponse<ItemView> setItemEnabled(@PathVariable long itemId, @PathVariable boolean enabled) {
        ServiceItem item = catalogService.setItemEnabled(itemId, enabled);
        return ApiResponse.ok(toItemView(item, catalogService.listSpecs(itemId)));
    }

    // ---------- 档位 ----------

    public record CreateSpecRequest(
            @NotBlank(message = "档位编码不能为空") @Size(max = 32, message = "档位编码不超过 32 字符") String code,
            @NotBlank(message = "档位名称不能为空") @Size(max = 64, message = "档位名称不超过 64 字符") String name,
            @Positive(message = "服务时长必须大于零") Integer durationMinutes,
            Integer sortOrder) {
    }

    public record UpdateSpecRequest(
            @Size(max = 64, message = "档位名称不超过 64 字符") String name,
            @Positive(message = "服务时长必须大于零") Integer durationMinutes,
            Integer sortOrder) {
    }

    @GetMapping("/items/{itemId}/specs")
    public ApiResponse<List<SpecView>> listSpecs(@PathVariable long itemId) {
        return ApiResponse.ok(catalogService.listSpecs(itemId).stream().map(PricingAdminController::toSpecView).toList());
    }

    @PostMapping("/items/{itemId}/specs")
    public ApiResponse<SpecView> createSpec(@PathVariable long itemId,
                                            @Valid @RequestBody CreateSpecRequest req) {
        ServiceSpec spec = catalogService.createSpec(
                itemId, req.code(), req.name(), req.durationMinutes(), req.sortOrder());
        return ApiResponse.ok(toSpecView(spec));
    }

    @PutMapping("/specs/{specId}")
    public ApiResponse<SpecView> updateSpec(@PathVariable long specId,
                                            @Valid @RequestBody UpdateSpecRequest req) {
        ServiceSpec spec = catalogService.updateSpec(specId, req.name(), req.durationMinutes(), req.sortOrder());
        return ApiResponse.ok(toSpecView(spec));
    }

    @PutMapping("/specs/{specId}/enabled/{enabled}")
    public ApiResponse<SpecView> setSpecEnabled(@PathVariable long specId, @PathVariable boolean enabled) {
        return ApiResponse.ok(toSpecView(catalogService.setSpecEnabled(specId, enabled)));
    }

    // ---------- 主播价格 ----------

    public record SetPriceRequest(
            @Min(value = 1, message = "单价必须大于零") long unitPriceCents,
            Boolean enabled) {
    }

    @GetMapping("/talents/{talentId}/prices")
    public ApiResponse<List<PriceView>> listPrices(@PathVariable long talentId) {
        return ApiResponse.ok(priceService.listByTalent(talentId).stream()
                .map(PricingAdminController::toPriceView).toList());
    }

    @PutMapping("/talents/{talentId}/prices/{specId}")
    public ApiResponse<PriceView> setPrice(@PathVariable long talentId,
                                           @PathVariable long specId,
                                           @Valid @RequestBody SetPriceRequest req) {
        TalentPrice price = priceService.setPrice(
                talentId, specId, req.unitPriceCents(), req.enabled() == null || req.enabled());
        return ApiResponse.ok(toPriceView(price));
    }

    @PutMapping("/talents/{talentId}/prices/{specId}/enabled/{enabled}")
    public ApiResponse<PriceView> setPriceEnabled(@PathVariable long talentId,
                                                  @PathVariable long specId,
                                                  @PathVariable boolean enabled) {
        return ApiResponse.ok(toPriceView(priceService.setEnabled(talentId, specId, enabled)));
    }

    // ---------- 分成比例 ----------

    public record SetRateRequest(
            @Min(value = 0, message = "分成比例不得小于 0 基点")
            @Max(value = 10000, message = "分成比例不得大于 10000 基点") int rateBp) {
    }

    @GetMapping("/commission")
    public ApiResponse<CommissionSettings> getCommission() {
        return ApiResponse.ok(new CommissionSettings(
                toCommissionView(commissionService.getDefault()),
                commissionService.listOverrides().stream()
                        .map(PricingAdminController::toCommissionView).toList()));
    }

    public record CommissionSettings(CommissionView defaultConfig, List<CommissionView> overrides) {
    }

    @PutMapping("/commission/default")
    public ApiResponse<CommissionView> setDefaultRate(@Valid @RequestBody SetRateRequest req) {
        return ApiResponse.ok(toCommissionView(commissionService.setDefault(req.rateBp())));
    }

    @PutMapping("/commission/talents/{talentId}")
    public ApiResponse<CommissionView> setTalentRate(@PathVariable long talentId,
                                                     @Valid @RequestBody SetRateRequest req) {
        return ApiResponse.ok(toCommissionView(commissionService.setOverride(talentId, req.rateBp())));
    }

    @DeleteMapping("/commission/talents/{talentId}")
    public ApiResponse<Void> removeTalentRate(@PathVariable long talentId) {
        commissionService.removeOverride(talentId);
        return ApiResponse.ok();
    }

    // ---------- 转换 ----------

    private static ItemView toItemView(ServiceItem item, List<ServiceSpec> specs) {
        return new ItemView(
                item.getId(), item.getCode(), item.getName(), item.getDescription(),
                item.getSortOrder() == null ? 0 : item.getSortOrder(),
                Boolean.TRUE.equals(item.getEnabled()),
                specs.stream().map(PricingAdminController::toSpecView).toList());
    }

    private static SpecView toSpecView(ServiceSpec spec) {
        return new SpecView(
                spec.getId(), spec.getServiceItemId(), spec.getCode(), spec.getName(),
                spec.getDurationMinutes(), spec.getSortOrder() == null ? 0 : spec.getSortOrder(),
                Boolean.TRUE.equals(spec.getEnabled()));
    }

    private static PriceView toPriceView(TalentPrice price) {
        return new PriceView(price.getId(), price.getTalentId(), price.getServiceSpecId(),
                price.getUnitPriceCents(), Boolean.TRUE.equals(price.getEnabled()));
    }

    private static CommissionView toCommissionView(CommissionConfig config) {
        return new CommissionView(config.getId(), config.getTalentId(), config.getRateBp(),
                CommissionCalculator.formatRate(config.getRateBp()));
    }
}
