package com.guild.checkout.talent.admin;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.TalentMapper;
import com.guild.checkout.talent.TalentService;
import com.guild.checkout.talent.TalentTag;
import com.guild.checkout.talent.TalentTagMapper;
import com.guild.checkout.user.AppUser;
import com.guild.checkout.user.AppUserService;
import com.guild.checkout.user.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 运营侧的主播档案维护。
 *
 * 上下架是软开关：下架只阻止新订单，既有未完结订单仍可正常接单与完成——
 * 否则下架一位主播会让粉丝已付的钱卡死。
 */
@Service
public class TalentAdminService {

    private static final Logger log = LoggerFactory.getLogger(TalentAdminService.class);

    private static final int MAX_TAGS = 8;

    private final TalentMapper talentMapper;
    private final TalentTagMapper tagMapper;
    private final TalentService talentService;
    private final AppUserService userService;

    public TalentAdminService(TalentMapper talentMapper,
                              TalentTagMapper tagMapper,
                              TalentService talentService,
                              AppUserService userService) {
        this.talentMapper = talentMapper;
        this.tagMapper = tagMapper;
        this.talentService = talentService;
        this.userService = userService;
    }

    @Transactional
    public Talent create(String nickname, String intro, String coverUrl, Integer sortOrder) {
        requireText(nickname, "昵称");

        Talent talent = new Talent();
        talent.setNickname(nickname.trim());
        talent.setIntro(trimToNull(intro));
        talent.setCoverUrl(trimToNull(coverUrl));
        talent.setSortOrder(sortOrder == null ? 0 : sortOrder);
        talent.setOnline(false);
        // 新建默认不上架：资料与价格配齐之前不该出现在粉丝端。
        talent.setListed(false);
        talentMapper.insert(talent);
        return talent;
    }

    @Transactional
    public Talent update(long talentId, String nickname, String intro, String coverUrl, Integer sortOrder) {
        Talent talent = talentService.require(talentId);

        // MyBatis-Plus 的 updateById 会跳过 null 字段，所以「把简介清空」这类操作
        // 用实体更新是无效的。凡是可以被置空的字段一律走显式 set。
        LambdaUpdateWrapper<Talent> update = Wrappers.<Talent>lambdaUpdate()
                .eq(Talent::getId, talentId);
        boolean changed = false;

        if (nickname != null) {
            requireText(nickname, "昵称");
            talent.setNickname(nickname.trim());
            update.set(Talent::getNickname, talent.getNickname());
            changed = true;
        }
        if (intro != null) {
            talent.setIntro(trimToNull(intro));
            update.set(Talent::getIntro, talent.getIntro());
            changed = true;
        }
        if (coverUrl != null) {
            talent.setCoverUrl(trimToNull(coverUrl));
            update.set(Talent::getCoverUrl, talent.getCoverUrl());
            changed = true;
        }
        if (sortOrder != null) {
            talent.setSortOrder(sortOrder);
            update.set(Talent::getSortOrder, sortOrder);
            changed = true;
        }
        if (changed) {
            talentMapper.update(null, update);
        }
        return talent;
    }

    /** 上架或下架。下架不影响既有未完结订单。 */
    @Transactional
    public Talent setListed(long talentId, boolean listed) {
        Talent talent = talentService.require(talentId);
        if (Boolean.valueOf(listed).equals(talent.getListed())) {
            return talent;
        }
        talent.setListed(listed);
        talentMapper.updateById(talent);
        log.info("主播 {} 已{}", talentId, listed ? "上架" : "下架");
        return talent;
    }

    /** 配置试听片段。url 为空表示清除，清除后粉丝端不展示播放控件。 */
    @Transactional
    public Talent setAudio(long talentId, String audioUrl, Integer seconds) {
        Talent talent = talentService.require(talentId);
        String url = trimToNull(audioUrl);
        if (url == null) {
            talent.setAudioUrl(null);
            talent.setAudioSeconds(null);
        } else {
            if (seconds == null || seconds <= 0) {
                throw new BusinessException("试听时长必须大于零");
            }
            talent.setAudioUrl(url);
            talent.setAudioSeconds(seconds);
        }
        // 清除试听时两个字段都要置空，必须显式 set——updateById 会跳过 null。
        talentMapper.update(null, Wrappers.<Talent>lambdaUpdate()
                .eq(Talent::getId, talentId)
                .set(Talent::getAudioUrl, talent.getAudioUrl())
                .set(Talent::getAudioSeconds, talent.getAudioSeconds()));
        return talent;
    }

    /** 整体替换标签。 */
    @Transactional
    public List<TalentTag> replaceTags(long talentId, List<String> names) {
        talentService.require(talentId);
        List<String> cleaned = names == null ? List.of() : names.stream()
                .map(TalentAdminService::trimToNull)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (cleaned.size() > MAX_TAGS) {
            throw new BusinessException("标签最多 " + MAX_TAGS + " 个");
        }

        tagMapper.delete(Wrappers.<TalentTag>lambdaQuery().eq(TalentTag::getTalentId, talentId));
        int order = 0;
        for (String name : cleaned) {
            TalentTag tag = new TalentTag();
            tag.setTalentId(talentId);
            tag.setName(name);
            tag.setSortOrder(order++);
            tagMapper.insert(tag);
        }
        return talentService.listTags(talentId);
    }

    /**
     * 把一个微信用户关联到主播档案，并授予其主播角色。
     * 关联后该用户下次打开 H5 即可进入主播工作台，其粉丝身份态不受影响。
     */
    @Transactional
    public Talent linkUser(long talentId, long userId) {
        Talent talent = talentService.require(talentId);
        AppUser user = userService.requireById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在: " + userId);
        }

        Talent occupied = talentService.findByUserId(userId);
        if (occupied != null && !occupied.getId().equals(talentId)) {
            throw new BusinessException(
                    "该微信用户已关联到主播档案 " + occupied.getId() + "，请先解除关联");
        }

        talent.setUserId(userId);
        try {
            talentMapper.updateById(talent);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("该微信用户已关联到其他主播档案");
        }
        userService.grantRole(userId, UserRole.TALENT);
        log.info("主播档案 {} 已关联用户 {}，并授予主播角色", talentId, userId);
        return talent;
    }

    /** 解除关联并回收主播角色。 */
    @Transactional
    public Talent unlinkUser(long talentId) {
        Talent talent = talentService.require(talentId);
        Long userId = talent.getUserId();
        if (userId == null) {
            return talent;
        }
        talent.setUserId(null);
        // 同理：解除关联是把 user_id 置空，必须显式 set。
        talentMapper.update(null, Wrappers.<Talent>lambdaUpdate()
                .eq(Talent::getId, talentId)
                .set(Talent::getUserId, null));
        userService.revokeRole(userId, UserRole.TALENT);
        log.info("主播档案 {} 已解除与用户 {} 的关联，并回收主播角色", talentId, userId);
        return talent;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(field + "不能为空");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
