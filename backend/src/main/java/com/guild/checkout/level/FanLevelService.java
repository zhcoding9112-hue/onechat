package com.guild.checkout.level;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 粉丝等级。
 *
 * 等级只影响展示，不参与计价与分成——与 talent.level 同样的定位，
 * leaderboard 规格里是明文要求，也有专门的用例盯着。
 *
 * 等级即时按门槛算出来，不落库：门槛是运营可改的，落库的等级在改门槛后会全体过期，
 * 还得想办法重算一遍。算出来的等级永远与当前门槛一致，没有「忘了刷新」这回事。
 */
@Service
public class FanLevelService {

    private final FanLevelSettingMapper settingMapper;
    private final FanSpendingMapper spendingMapper;

    public FanLevelService(FanLevelSettingMapper settingMapper, FanSpendingMapper spendingMapper) {
        this.settingMapper = settingMapper;
        this.spendingMapper = spendingMapper;
    }

    /**
     * 粉丝当前的等级视图。
     *
     * @param level        当前等级
     * @param name         等级名称
     * @param spentCents   累计已确认消费
     * @param nextLevel    下一等级，已满级时为 null
     * @param nextName     下一等级名称，已满级时为 null
     * @param toNextCents  距下一等级还差多少，已满级时为 null
     */
    public record FanLevel(int level, String name, long spentCents,
                           Integer nextLevel, String nextName, Long toNextCents) {
    }

    /** 累计已确认消费：订单实付 + 已完成打赏。 */
    public long spendingOf(long userId) {
        return spendingMapper.orderSpending(userId) + spendingMapper.tipSpending(userId);
    }

    public FanLevel levelOf(long userId) {
        long spent = spendingOf(userId);
        List<FanLevelSetting> tiers = thresholds();

        FanLevelSetting current = tiers.get(0);
        FanLevelSetting next = null;
        for (FanLevelSetting tier : tiers) {
            if (spent >= tier.getThresholdCents()) {
                current = tier;
            } else {
                next = tier;
                break;
            }
        }
        return new FanLevel(current.getLevel(), current.getName(), spent,
                next == null ? null : next.getLevel(),
                next == null ? null : next.getName(),
                next == null ? null : next.getThresholdCents() - spent);
    }

    /** 全部等级门槛，按等级升序。 */
    public List<FanLevelSetting> thresholds() {
        List<FanLevelSetting> tiers = settingMapper.selectList(
                Wrappers.<FanLevelSetting>lambdaQuery().orderByAsc(FanLevelSetting::getLevel));
        if (tiers.isEmpty()) {
            throw new IllegalStateException("粉丝等级门槛未初始化");
        }
        return tiers;
    }

    /** 运营调整某一档的门槛。此后的等级计算立刻按新门槛执行。 */
    @Transactional
    public FanLevelSetting updateThreshold(int level, long thresholdCents, String name) {
        if (thresholdCents < 0) {
            throw new BusinessException("门槛不得为负");
        }
        FanLevelSetting tier = settingMapper.selectOne(Wrappers.<FanLevelSetting>lambdaQuery()
                .eq(FanLevelSetting::getLevel, level));
        if (tier == null) {
            throw new BusinessException("等级不存在: " + level);
        }
        if (level == 1 && thresholdCents != 0) {
            // 最低一档必须是 0，否则会出现「谁都不够格」的空档
            throw new BusinessException("最低等级的门槛必须为 0");
        }
        tier.setThresholdCents(thresholdCents);
        if (name != null && !name.isBlank()) {
            tier.setName(name.trim());
        }
        settingMapper.updateById(tier);
        requireAscending();
        return tier;
    }

    /** 门槛必须随等级递增，否则「越过门槛升级」就没有确定含义。 */
    private void requireAscending() {
        List<FanLevelSetting> tiers = thresholds();
        for (int i = 1; i < tiers.size(); i++) {
            if (tiers.get(i).getThresholdCents() <= tiers.get(i - 1).getThresholdCents()) {
                throw new BusinessException("门槛必须随等级递增：L%d 的门槛不高于 L%d"
                        .formatted(tiers.get(i).getLevel(), tiers.get(i - 1).getLevel()));
            }
        }
    }
}
