package com.guild.checkout.pricing.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.pricing.CommissionCalculator;
import com.guild.checkout.pricing.CommissionConfig;
import com.guild.checkout.pricing.CommissionConfigMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 运营侧的分成比例配置。
 *
 * 全局默认必有且仅有一条，不可删除——删掉它会让没有覆写的主播下单时取不到比例。
 * 按主播的覆写可以删除，删除后该主播回落到全局默认。
 *
 * 修改只对新订单生效：已创建的订单在创建瞬间就把比例快照进了订单自身。
 */
@Service
public class CommissionAdminService {

    private final CommissionConfigMapper mapper;

    public CommissionAdminService(CommissionConfigMapper mapper) {
        this.mapper = mapper;
    }

    /** 全局默认。迁移保证它存在。 */
    public CommissionConfig getDefault() {
        CommissionConfig global = mapper.selectOne(
                Wrappers.<CommissionConfig>lambdaQuery().isNull(CommissionConfig::getTalentId));
        if (global == null) {
            throw new BusinessException("全局默认分成比例缺失，请联系技术排查");
        }
        return global;
    }

    /** 全部按主播的覆写。 */
    public List<CommissionConfig> listOverrides() {
        return mapper.selectList(Wrappers.<CommissionConfig>lambdaQuery()
                .isNotNull(CommissionConfig::getTalentId)
                .orderByAsc(CommissionConfig::getTalentId));
    }

    @Transactional
    public CommissionConfig setDefault(int rateBp) {
        requireRate(rateBp);
        CommissionConfig global = getDefault();
        global.setRateBp(rateBp);
        mapper.updateById(global);
        return global;
    }

    @Transactional
    public CommissionConfig setOverride(long talentId, int rateBp) {
        requireRate(rateBp);
        CommissionConfig existing = findOverride(talentId);
        if (existing != null) {
            existing.setRateBp(rateBp);
            mapper.updateById(existing);
            return existing;
        }
        CommissionConfig config = new CommissionConfig();
        config.setTalentId(talentId);
        config.setRateBp(rateBp);
        try {
            mapper.insert(config);
        } catch (DuplicateKeyException ex) {
            CommissionConfig raced = findOverride(talentId);
            if (raced == null) {
                throw ex;
            }
            raced.setRateBp(rateBp);
            mapper.updateById(raced);
            return raced;
        }
        return config;
    }

    /** 删除覆写，该主播回落到全局默认。 */
    @Transactional
    public void removeOverride(long talentId) {
        CommissionConfig existing = findOverride(talentId);
        if (existing != null) {
            mapper.deleteById(existing.getId());
        }
    }

    public CommissionConfig findOverride(long talentId) {
        return mapper.selectOne(
                Wrappers.<CommissionConfig>lambdaQuery().eq(CommissionConfig::getTalentId, talentId));
    }

    private static void requireRate(int rateBp) {
        try {
            CommissionCalculator.requireValidRate(rateBp);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ex.getMessage());
        }
    }
}
