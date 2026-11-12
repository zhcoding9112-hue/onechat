package com.guild.checkout.pricing.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.pricing.ServiceSpec;
import com.guild.checkout.pricing.TalentPrice;
import com.guild.checkout.pricing.TalentPriceMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 运营侧的主播价格配置。
 *
 * 价格按「主播 × 档位」逐条配置，没有这条记录就代表该主播不开放这个档位。
 * 这里不做"删除价格"，只做启停：删除会让历史配置无迹可循，而启停可追溯。
 */
@Service
public class TalentPriceAdminService {

    /** 单价上限：100 万元。防止运营少打一个小数点配出天价。 */
    private static final long MAX_UNIT_PRICE_CENTS = 100_000_000L;

    private final TalentPriceMapper priceMapper;
    private final ServiceCatalogAdminService catalogService;

    public TalentPriceAdminService(TalentPriceMapper priceMapper, ServiceCatalogAdminService catalogService) {
        this.priceMapper = priceMapper;
        this.catalogService = catalogService;
    }

    public List<TalentPrice> listByTalent(long talentId) {
        return priceMapper.selectList(Wrappers.<TalentPrice>lambdaQuery()
                .eq(TalentPrice::getTalentId, talentId)
                .orderByAsc(TalentPrice::getServiceSpecId));
    }

    public TalentPrice find(long talentId, long serviceSpecId) {
        return priceMapper.selectOne(Wrappers.<TalentPrice>lambdaQuery()
                .eq(TalentPrice::getTalentId, talentId)
                .eq(TalentPrice::getServiceSpecId, serviceSpecId));
    }

    /**
     * 设定某主播某档位的价格。已有则更新，没有则新建。
     *
     * @param unitPriceCents 单价，单位分
     */
    @Transactional
    public TalentPrice setPrice(long talentId, long serviceSpecId, long unitPriceCents, boolean enabled) {
        if (unitPriceCents <= 0) {
            throw new BusinessException("单价必须大于零");
        }
        if (unitPriceCents > MAX_UNIT_PRICE_CENTS) {
            throw new BusinessException("单价超出上限，请确认是否少打了小数点");
        }
        ServiceSpec spec = catalogService.requireSpec(serviceSpecId);
        catalogService.requireItem(spec.getServiceItemId());

        TalentPrice existing = find(talentId, serviceSpecId);
        if (existing != null) {
            existing.setUnitPriceCents(unitPriceCents);
            existing.setEnabled(enabled);
            priceMapper.updateById(existing);
            return existing;
        }

        TalentPrice price = new TalentPrice();
        price.setTalentId(talentId);
        price.setServiceSpecId(serviceSpecId);
        price.setUnitPriceCents(unitPriceCents);
        price.setEnabled(enabled);
        try {
            priceMapper.insert(price);
        } catch (DuplicateKeyException ex) {
            // 并发配置同一组合：唯一索引挡住第二条，回读后改为更新。
            TalentPrice raced = find(talentId, serviceSpecId);
            if (raced == null) {
                throw ex;
            }
            raced.setUnitPriceCents(unitPriceCents);
            raced.setEnabled(enabled);
            priceMapper.updateById(raced);
            return raced;
        }
        return price;
    }

    /** 启停某主播某档位。停用后该档位不再出现在其详情页。 */
    @Transactional
    public TalentPrice setEnabled(long talentId, long serviceSpecId, boolean enabled) {
        TalentPrice price = find(talentId, serviceSpecId);
        if (price == null) {
            throw new BusinessException("该主播尚未配置此档位的价格");
        }
        if (Boolean.valueOf(enabled).equals(price.getEnabled())) {
            return price;
        }
        price.setEnabled(enabled);
        priceMapper.updateById(price);
        return price;
    }
}
