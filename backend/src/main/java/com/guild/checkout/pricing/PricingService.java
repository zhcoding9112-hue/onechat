package com.guild.checkout.pricing;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 取价与取分成比例。
 *
 * 唯一的对外契约是 {@link #quote}：它在下单瞬间把当时的单价与分成比例算定并固化，
 * 返回的 {@link PriceQuote} 不可变。订单侧只保存这个结果，结算时绝不回头读配置。
 */
@Service
public class PricingService {

    private final ServiceItemMapper itemMapper;
    private final ServiceSpecMapper specMapper;
    private final TalentPriceMapper priceMapper;
    private final CommissionConfigMapper commissionMapper;

    public PricingService(ServiceItemMapper itemMapper,
                          ServiceSpecMapper specMapper,
                          TalentPriceMapper priceMapper,
                          CommissionConfigMapper commissionMapper) {
        this.itemMapper = itemMapper;
        this.specMapper = specMapper;
        this.priceMapper = priceMapper;
        this.commissionMapper = commissionMapper;
    }

    /**
     * 为一次下单产出计价快照。
     *
     * @throws PricingException 档位不存在、已停用、所属项目已停用，或该主播未配置该档位的价格
     */
    public PriceQuote quote(long talentId, long serviceSpecId, int quantity) {
        if (quantity <= 0) {
            throw new PricingException("数量必须大于零");
        }

        ServiceSpec spec = specMapper.selectById(serviceSpecId);
        if (spec == null) {
            throw new PricingException("档位不存在");
        }
        if (!Boolean.TRUE.equals(spec.getEnabled())) {
            throw new PricingException("该档位已停用");
        }

        ServiceItem item = itemMapper.selectById(spec.getServiceItemId());
        if (item == null) {
            throw new PricingException("服务项目不存在");
        }
        if (!Boolean.TRUE.equals(item.getEnabled())) {
            throw new PricingException("该服务项目已停用");
        }

        TalentPrice price = findPrice(talentId, serviceSpecId);
        if (price == null || !Boolean.TRUE.equals(price.getEnabled())) {
            throw new PricingException("该主播未开放此档位");
        }

        long unitPriceCents = price.getUnitPriceCents();
        long payableCents = Math.multiplyExact(unitPriceCents, (long) quantity);
        int rateBp = resolveCommissionRateBp(talentId);
        CommissionSplit split = CommissionCalculator.split(payableCents, rateBp);

        return new PriceQuote(
                talentId,
                item.getId(),
                spec.getId(),
                item.getName(),
                spec.getName(),
                unitPriceCents,
                quantity,
                payableCents,
                rateBp,
                split.guildCents(),
                split.talentCents());
    }

    /**
     * 该主播当前生效的分成比例：按主播覆写优先于全局默认。
     *
     * @throws PricingException 全局默认缺失（迁移保证它存在，缺失说明配置被误删）
     */
    public int resolveCommissionRateBp(long talentId) {
        CommissionConfig override = commissionMapper.selectOne(
                Wrappers.<CommissionConfig>lambdaQuery().eq(CommissionConfig::getTalentId, talentId));
        if (override != null && override.getRateBp() != null) {
            return override.getRateBp();
        }
        CommissionConfig global = commissionMapper.selectOne(
                Wrappers.<CommissionConfig>lambdaQuery().isNull(CommissionConfig::getTalentId));
        if (global == null || global.getRateBp() == null) {
            throw new PricingException("未配置全局默认分成比例");
        }
        return global.getRateBp();
    }

    /** 某主播当前可购的档位。未配置价格或已停用的档位不出现。 */
    public List<PurchasableSpec> listPurchasable(long talentId) {
        List<TalentPrice> prices = priceMapper.selectList(
                Wrappers.<TalentPrice>lambdaQuery()
                        .eq(TalentPrice::getTalentId, talentId)
                        .eq(TalentPrice::getEnabled, true));
        if (prices.isEmpty()) {
            return List.of();
        }

        List<Long> specIds = prices.stream().map(TalentPrice::getServiceSpecId).toList();
        Map<Long, ServiceSpec> specs = specMapper.selectList(
                        Wrappers.<ServiceSpec>lambdaQuery()
                                .in(ServiceSpec::getId, specIds)
                                .eq(ServiceSpec::getEnabled, true))
                .stream().collect(Collectors.toMap(ServiceSpec::getId, Function.identity()));
        if (specs.isEmpty()) {
            return List.of();
        }

        List<Long> itemIds = specs.values().stream().map(ServiceSpec::getServiceItemId).distinct().toList();
        Map<Long, ServiceItem> items = itemMapper.selectList(
                        Wrappers.<ServiceItem>lambdaQuery()
                                .in(ServiceItem::getId, itemIds)
                                .eq(ServiceItem::getEnabled, true))
                .stream().collect(Collectors.toMap(ServiceItem::getId, Function.identity()));

        return prices.stream()
                .map(price -> {
                    ServiceSpec spec = specs.get(price.getServiceSpecId());
                    if (spec == null) {
                        return null;
                    }
                    ServiceItem item = items.get(spec.getServiceItemId());
                    if (item == null) {
                        return null;
                    }
                    return new PurchasableSpec(
                            item.getId(), item.getCode(), item.getName(), item.getSortOrder(),
                            spec.getId(), spec.getCode(), spec.getName(), spec.getDurationMinutes(),
                            spec.getSortOrder(), price.getUnitPriceCents());
                })
                .filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator
                        .comparingInt(PurchasableSpec::itemSortOrder)
                        .thenComparingLong(PurchasableSpec::serviceItemId)
                        .thenComparingInt(PurchasableSpec::specSortOrder)
                        .thenComparingLong(PurchasableSpec::serviceSpecId))
                .toList();
    }

    private TalentPrice findPrice(long talentId, long serviceSpecId) {
        return priceMapper.selectOne(Wrappers.<TalentPrice>lambdaQuery()
                .eq(TalentPrice::getTalentId, talentId)
                .eq(TalentPrice::getServiceSpecId, serviceSpecId));
    }

    /** 主播详情页上的一个可购档位。 */
    public record PurchasableSpec(
            Long serviceItemId,
            String itemCode,
            String itemName,
            int itemSortOrder,
            Long serviceSpecId,
            String specCode,
            String specName,
            Integer durationMinutes,
            int specSortOrder,
            long unitPriceCents) {
    }
}
