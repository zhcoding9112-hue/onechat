package com.guild.checkout.feed;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.TalentService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 心动墙。
 *
 * 本阶段只做运营手工挑选（tasks 9.5 明确「先做运营手工挑选」）。
 * 自动筛选规则还没定下来——design.md 把「心动墙筛选规则」列为待定项，
 * 在规则定下来之前写一套猜出来的算法，只会让人以为它有依据。
 *
 * 挑选结果即读即生效，没有缓存：运营改完，粉丝刷新就能看到。
 */
@Service
public class CrushWallService {

    private final CrushWallPickMapper pickMapper;
    private final TalentService talentService;

    public CrushWallService(CrushWallPickMapper pickMapper, TalentService talentService) {
        this.pickMapper = pickMapper;
        this.talentService = talentService;
    }

    /**
     * 粉丝看到的心动墙。
     *
     * 已下架的主播不展示：点进去是「暂不可约」，把人放在墙上只是浪费一次点击。
     */
    public List<Talent> wall(int limit) {
        List<CrushWallPick> picks = pickMapper.selectList(Wrappers.<CrushWallPick>lambdaQuery()
                .eq(CrushWallPick::getEnabled, true)
                .orderByAsc(CrushWallPick::getSortOrder)
                .orderByAsc(CrushWallPick::getId)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 100)));
        if (picks.isEmpty()) {
            return List.of();
        }
        Map<Long, Talent> talents = talentService.findByIds(
                picks.stream().map(CrushWallPick::getTalentId).toList());

        return picks.stream()
                .map(p -> talents.get(p.getTalentId()))
                .filter(Objects::nonNull)
                .filter(t -> Boolean.TRUE.equals(t.getListed()))
                .toList();
    }

    /** 运营侧：全部挑选项，含已停用的。 */
    public List<CrushWallPick> listPicks() {
        return pickMapper.selectList(Wrappers.<CrushWallPick>lambdaQuery()
                .orderByAsc(CrushWallPick::getSortOrder)
                .orderByAsc(CrushWallPick::getId));
    }

    /** 加入心动墙。已在墙上则只更新顺序并启用。 */
    @Transactional
    public CrushWallPick pick(long talentId, int sortOrder) {
        talentService.require(talentId);
        CrushWallPick existing = findByTalent(talentId);
        if (existing != null) {
            existing.setSortOrder(sortOrder);
            existing.setEnabled(true);
            pickMapper.updateById(existing);
            return existing;
        }
        CrushWallPick pick = new CrushWallPick();
        pick.setTalentId(talentId);
        pick.setSortOrder(sortOrder);
        pick.setEnabled(true);
        try {
            pickMapper.insert(pick);
            return pick;
        } catch (DuplicateKeyException e) {
            // 另一个请求刚加好，返回既有那条
            return findByTalent(talentId);
        }
    }

    /** 移出心动墙。 */
    @Transactional
    public void unpick(long talentId) {
        CrushWallPick existing = findByTalent(talentId);
        if (existing == null) {
            throw new BusinessException("该主播不在心动墙上");
        }
        pickMapper.deleteById(existing.getId());
    }

    private CrushWallPick findByTalent(long talentId) {
        return pickMapper.selectOne(Wrappers.<CrushWallPick>lambdaQuery()
                .eq(CrushWallPick::getTalentId, talentId));
    }
}
