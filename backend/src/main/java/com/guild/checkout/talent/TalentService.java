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

    private TalentQueryMapper queryMapper;
    @org.springframework.beans.factory.annotation.Autowired
    private TalentMediaMapper mediaMapper;

    public List<String> photos(long talentId) {
        if (mediaMapper == null) return List.of();
        return mediaMapper.selectList(Wrappers.<TalentMedia>lambdaQuery()
                .eq(TalentMedia::getTalentId, talentId).eq(TalentMedia::getMediaType, TalentMedia.TYPE_PHOTO)
                .orderByAsc(TalentMedia::getSortOrder).orderByAsc(TalentMedia::getId))
                .stream().map(TalentMedia::getUrl).toList();
    }

    /** 由 Spring 注入；用 setter 而非构造参数，避免改动既有构造函数的全部调用点。 */
    @org.springframework.beans.factory.annotation.Autowired
    public void setQueryMapper(TalentQueryMapper queryMapper) {
        this.queryMapper = queryMapper;
    }

    /** 一页主播及其总数。总数用于前端判断「没有更多」。 */
    public record TalentPage(List<Talent> items, long total) {
    }

    /**
     * 按条件分页取已上架主播。
     *
     * 先查 id 再按 id 取实体，而不是一次 join 查全字段：筛选条件里的 EXISTS 子查询
     * 会让结果行重复，去重后再取实体比在 SQL 里 DISTINCT 全字段更简单可靠。
     */
    public TalentPage listListed(String categoryCode, String gender, Integer level, int page, int size) {
        return listListed(categoryCode, gender, level, null, page, size);
    }

    public TalentPage listListed(String categoryCode, String gender, Integer level, String keyword, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 0);
        List<Long> ids = queryMapper.findIds(
                trimToNull(categoryCode), trimToNull(gender), level, trimToNull(keyword), safeSize, safePage * safeSize);
        long total = queryMapper.countMatching(trimToNull(categoryCode), trimToNull(gender), level, trimToNull(keyword));
        if (ids.isEmpty()) {
            return new TalentPage(List.of(), total);
        }
        Map<Long, Talent> byId = talentMapper.selectBatchIds(ids).stream()
                .collect(java.util.stream.Collectors.toMap(Talent::getId, t -> t));
        // 保持 SQL 给出的顺序：selectBatchIds 不保证顺序
        List<Talent> ordered = ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
        return new TalentPage(ordered, total);
    }

    private static String trimToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

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

    /** 按 id 批量取主播，返回 id → 实体的映射。调用方自行决定顺序。 */
    public Map<Long, Talent> findByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return talentMapper.selectBatchIds(ids).stream()
                .collect(java.util.stream.Collectors.toMap(Talent::getId, t -> t));
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
