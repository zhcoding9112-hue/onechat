package com.guild.checkout.talent;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 收藏主播。 */
@Service
public class TalentFavoriteService {

    private final TalentFavoriteMapper mapper;
    private final TalentService talentService;

    public TalentFavoriteService(TalentFavoriteMapper mapper, TalentService talentService) {
        this.mapper = mapper;
        this.talentService = talentService;
    }

    /**
     * 收藏。重复收藏幂等。
     *
     * 幂等靠唯一索引 + 捕获 DuplicateKeyException，而不是「先查有没有再插」：
     * 后者在并发下有双写窗口，同一粉丝连点两次就可能插出两行。
     */
    @Transactional
    public void add(long userId, long talentId) {
        // 主播必须存在；已下架的仍允许收藏，粉丝可能是从旧链接进来的
        talentService.require(talentId);
        TalentFavorite f = new TalentFavorite();
        f.setUserId(userId);
        f.setTalentId(talentId);
        try {
            mapper.insert(f);
        } catch (DuplicateKeyException ignored) {
            // 已收藏，按成功处理
        }
    }

    /** 取消收藏。未收藏时同样按成功处理。 */
    @Transactional
    public void remove(long userId, long talentId) {
        mapper.delete(Wrappers.<TalentFavorite>lambdaQuery()
                .eq(TalentFavorite::getUserId, userId)
                .eq(TalentFavorite::getTalentId, talentId));
    }

    public boolean isFavorited(long userId, long talentId) {
        return mapper.selectCount(Wrappers.<TalentFavorite>lambdaQuery()
                .eq(TalentFavorite::getUserId, userId)
                .eq(TalentFavorite::getTalentId, talentId)) > 0;
    }

    /** 某粉丝收藏的主播 id，按收藏时间倒序。 */
    public List<Long> listTalentIds(long userId) {
        return mapper.selectList(Wrappers.<TalentFavorite>lambdaQuery()
                .eq(TalentFavorite::getUserId, userId)
                .orderByDesc(TalentFavorite::getCreatedAt)
                .orderByDesc(TalentFavorite::getId))
                .stream().map(TalentFavorite::getTalentId).toList();
    }

    /** 批量判断收藏状态，供列表页一次查出，避免 N 次查询。 */
    public Set<Long> filterFavorited(long userId, List<Long> talentIds) {
        if (talentIds.isEmpty()) {
            return Set.of();
        }
        return mapper.selectList(Wrappers.<TalentFavorite>lambdaQuery()
                .eq(TalentFavorite::getUserId, userId)
                .in(TalentFavorite::getTalentId, talentIds))
                .stream().map(TalentFavorite::getTalentId).collect(Collectors.toSet());
    }
}
