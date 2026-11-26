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
    /** 宣传语标签上限。库里是固定两列，这里的校验是为了让超限请求得到明确拒绝而不是被静默截断。 */
    public static final int MAX_PROMO_TAGS = 2;
    private static final int MAX_PROMO_TAG_LENGTH = 16;
    private static final List<String> GENDERS = List.of("FEMALE", "MALE");
    private static final int MIN_AGE = 1;
    private static final int MAX_AGE = 120;

    /**
     * 主播档案的可编辑字段。
     *
     * 做成参数对象而不是继续加形参：扩展资料上来就是 5 个字段，再平铺下去
     * create/update 会变成 9 个同类型形参并排，调用处极易错位。
     *
     * 每个字段为 null 表示「本次不修改」；promoTags 传空列表表示「清空」。
     */
    public record ProfileForm(String nickname,
                              String intro,
                              String coverUrl,
                              Integer sortOrder,
                              String gender,
                              Integer age,
                              String region,
                              List<String> promoTags, Integer level) {
        public ProfileForm(String nickname, String intro, String coverUrl, Integer sortOrder, String gender, Integer age, String region, List<String> promoTags) {
            this(nickname, intro, coverUrl, sortOrder, gender, age, region, promoTags, null);
        }
    }

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

    private static void validateLevel(Integer level) {
        if (level != null && (level < 1 || level > 6)) throw new BusinessException("主播等级需在 1 到 6 之间");
    }

    @Transactional
    public Talent create(ProfileForm form) {
        requireText(form.nickname(), "昵称");
        String gender = normalizeGender(form.gender());
        validateAge(form.age());
        List<String> promoTags = normalizePromoTags(form.promoTags());

        Talent talent = new Talent();
        validateLevel(form.level());
        talent.setLevel(form.level() == null ? 1 : form.level());
        talent.setNickname(form.nickname().trim());
        talent.setIntro(trimToNull(form.intro()));
        talent.setCoverUrl(trimToNull(form.coverUrl()));
        talent.setGender(gender);
        talent.setAge(form.age());
        talent.setRegion(trimToNull(form.region()));
        talent.setPromoTag1(promoTagAt(promoTags, 0));
        talent.setPromoTag2(promoTagAt(promoTags, 1));
        talent.setSortOrder(form.sortOrder() == null ? 0 : form.sortOrder());
        talent.setOnline(false);
        // 新建默认不上架：资料与价格配齐之前不该出现在粉丝端。
        talent.setListed(false);
        talentMapper.insert(talent);
        return talent;
    }

    @Transactional
    public Talent update(long talentId, ProfileForm form) {
        Talent talent = talentService.require(talentId);
        String nickname = form.nickname();
        String intro = form.intro();
        String coverUrl = form.coverUrl();
        Integer sortOrder = form.sortOrder();

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
        if (form.level() != null) {
            validateLevel(form.level());
            talent.setLevel(form.level());
            update.set(Talent::getLevel, form.level());
            changed = true;
        }
        if (form.gender() != null) {
            talent.setGender(normalizeGender(form.gender()));
            update.set(Talent::getGender, talent.getGender());
            changed = true;
        }
        if (form.age() != null) {
            validateAge(form.age());
            talent.setAge(form.age());
            update.set(Talent::getAge, form.age());
            changed = true;
        }
        if (form.region() != null) {
            talent.setRegion(trimToNull(form.region()));
            update.set(Talent::getRegion, talent.getRegion());
            changed = true;
        }
        if (form.promoTags() != null) {
            List<String> promoTags = normalizePromoTags(form.promoTags());
            talent.setPromoTag1(promoTagAt(promoTags, 0));
            talent.setPromoTag2(promoTagAt(promoTags, 1));
            update.set(Talent::getPromoTag1, talent.getPromoTag1());
            update.set(Talent::getPromoTag2, talent.getPromoTag2());
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

    /** 空串与纯空白按「未填写」处理，统一落成 NULL，避免粉丝端出现空占位。 */
    private static String normalizeGender(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        String upper = trimmed.toUpperCase(java.util.Locale.ROOT);
        if (!GENDERS.contains(upper)) {
            throw new BusinessException("性别只能是 FEMALE 或 MALE");
        }
        return upper;
    }

    private static void validateAge(Integer age) {
        if (age != null && (age < MIN_AGE || age > MAX_AGE)) {
            throw new BusinessException("年龄需在 %d 到 %d 之间".formatted(MIN_AGE, MAX_AGE));
        }
    }

    /**
     * 宣传语标签：先丢掉空白项再判数量，否则运营多留一个空输入框就会被误判超限。
     * 系统只校验数量与长度，不校验内容，也不提供任何文案模板。
     */
    private static List<String> normalizePromoTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        List<String> cleaned = tags.stream()
                .map(TalentAdminService::trimToNull)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (cleaned.size() > MAX_PROMO_TAGS) {
            throw new BusinessException("宣传语标签最多 %d 个".formatted(MAX_PROMO_TAGS));
        }
        for (String tag : cleaned) {
            if (tag.length() > MAX_PROMO_TAG_LENGTH) {
                throw new BusinessException("单个宣传语标签不超过 %d 字符".formatted(MAX_PROMO_TAG_LENGTH));
            }
        }
        return cleaned;
    }

    private static String promoTagAt(List<String> tags, int index) {
        return index < tags.size() ? tags.get(index) : null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
