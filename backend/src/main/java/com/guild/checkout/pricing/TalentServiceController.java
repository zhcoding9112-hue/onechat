package com.guild.checkout.pricing;

import com.guild.checkout.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 粉丝端查询某主播的可购服务与价格。
 *
 * 返回内容按项目分组、按配置顺序排列。未配置价格、价格已停用、档位已停用、
 * 项目已停用的组合一律不出现——粉丝看不到就不可能下单。
 *
 * 主播资料本身（昵称、封面、标签、在线状态）见 talent-catalog 的详情接口。
 */
@RestController
@RequestMapping("/api/talents")
public class TalentServiceController {

    private final PricingService pricingService;

    public TalentServiceController(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    public record SpecOption(
            Long serviceSpecId,
            String code,
            String name,
            Integer durationMinutes,
            long unitPriceCents) {
    }

    public record ItemGroup(
            Long serviceItemId,
            String code,
            String name,
            List<SpecOption> specs) {
    }

    @GetMapping("/{talentId}/services")
    public ApiResponse<List<ItemGroup>> listServices(@PathVariable long talentId) {
        Map<Long, ItemGroupBuilder> grouped = new LinkedHashMap<>();
        for (PricingService.PurchasableSpec spec : pricingService.listPurchasable(talentId)) {
            grouped.computeIfAbsent(spec.serviceItemId(),
                            id -> new ItemGroupBuilder(id, spec.itemCode(), spec.itemName()))
                    .specs.add(new SpecOption(
                            spec.serviceSpecId(), spec.specCode(), spec.specName(),
                            spec.durationMinutes(), spec.unitPriceCents()));
        }
        return ApiResponse.ok(grouped.values().stream()
                .map(b -> new ItemGroup(b.id, b.code, b.name, List.copyOf(b.specs)))
                .toList());
    }

    private static final class ItemGroupBuilder {
        private final Long id;
        private final String code;
        private final String name;
        private final List<SpecOption> specs = new ArrayList<>();

        private ItemGroupBuilder(Long id, String code, String name) {
            this.id = id;
            this.code = code;
            this.name = name;
        }
    }
}
