package com.guild.checkout.talent;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.common.exception.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 主播档案的查询。写操作见 {@code admin.TalentAdminService}。
 */
@Service
public class TalentService {

    private final TalentMapper talentMapper;
    private final TalentTagMapper tagMapper;

    public TalentService(TalentMapper talentMapper, TalentTagMapper tagMapper) {
        this.talentMapper = talentMapper;
        this.tagMapper = tagMapper;
    }

    /** 粉丝端列表：只含已上架，按运营设定的顺序。不提供搜索与筛选。 */
    public List<Talent> listListed() {
        return talentMapper.selectList(Wrappers.<Talent>lambdaQuery()
                .eq(Talent::getListed, true)
                .orderByAsc(Talent::getSortOrder)
                .orderByAsc(Talent::getId));
    }

    /** 运营端列表：含未上架与已下架。 */
    public List<Talent> listAll() {
        return talentMapper.selectList(Wrappers.<Talent>lambdaQuery()
                .orderByAsc(Talent::getSortOrder)
                .orderByAsc(Talent::getId));
    }

    public Talent find(long talentId) {
        return talentMapper.selectById(talentId);
    }

    public Talent require(long talentId) {
        Talent talent = talentMapper.selectById(talentId);
        if (talent == null) {
            throw new NotFoundException("主播不存在: " + talentId);
        }
        return talent;
    }

    /** 按关联的微信账号找档案。主播工作台据此确定“我是哪位主播”。 */
    public Talent findByUserId(long userId) {
        return talentMapper.selectOne(Wrappers.<Talent>lambdaQuery().eq(Talent::getUserId, userId));
    }

    public List<TalentTag> listTags(long talentId) {
        return tagMapper.selectList(Wrappers.<TalentTag>lambdaQuery()
                .eq(TalentTag::getTalentId, talentId)
                .orderByAsc(TalentTag::getSortOrder)
                .orderByAsc(TalentTag::getId));
    }

    /** 批量取标签，避免列表页逐个查询。 */
    public Map<Long, List<TalentTag>> listTagsByTalents(List<Long> talentIds) {
        if (talentIds.isEmpty()) {
            return Map.of();
        }
        return tagMapper.selectList(Wrappers.<TalentTag>lambdaQuery()
                        .in(TalentTag::getTalentId, talentIds)
                        .orderByAsc(TalentTag::getSortOrder)
                        .orderByAsc(TalentTag::getId))
                .stream()
                .collect(Collectors.groupingBy(TalentTag::getTalentId));
    }
}
