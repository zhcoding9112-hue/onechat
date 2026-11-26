package com.guild.checkout.match;

import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.TalentQueryMapper;
import com.guild.checkout.talent.TalentService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 随机匹配一位主播。
 *
 * 这是随机派单，不是抽奖：匹配不收费、不消耗任何次数额度、结果也不是奖品。
 * 本类因此刻意不依赖任何账务组件——没有可注入的扣费入口，就不会有人
 * 在后续迭代里「顺手」加一句扣费。要加也得先改构造函数，改动会被看见。
 */
@Service
public class RandomMatchService {

    private final TalentQueryMapper queryMapper;
    private final TalentService talentService;

    public RandomMatchService(TalentQueryMapper queryMapper, TalentService talentService) {
        this.queryMapper = queryMapper;
        this.talentService = talentService;
    }

    /** 匹配条件。各项为空表示不限制。 */
    public record Criteria(String categoryCode,
                           String gender,
                           boolean onlineOnly,
                           boolean excludeOrdered,
                           List<Long> excludeIds, Integer level) {
        public Criteria(String categoryCode, String gender, boolean onlineOnly, boolean excludeOrdered, List<Long> excludeIds) {
            this(categoryCode, gender, onlineOnly, excludeOrdered, excludeIds, null);
        }
    }

    /**
     * 按条件随机取一位主播。
     *
     * @param fanUserId 当前粉丝，仅在 excludeOrdered 为真时用于排除已下过单的主播
     * @return 匹配到的主播；无符合条件者返回空
     */
    public Optional<Talent> match(long fanUserId, Criteria criteria) {
        List<Long> exclude = criteria.excludeIds() == null ? List.of() : criteria.excludeIds();
        Long id = queryMapper.findRandomId(
                trimToNull(criteria.categoryCode()),
                trimToNull(criteria.gender()), criteria.level(),
                criteria.onlineOnly(),
                criteria.excludeOrdered() ? fanUserId : null,
                exclude);

        if (id == null && !exclude.isEmpty()) {
            // 「换一位」排到候选集见底时，放宽到允许重复，而不是告诉粉丝没有人。
            // 候选只剩一位时，一直换不出新人是正常的，不该表现为「无结果」。
            id = queryMapper.findRandomId(
                    trimToNull(criteria.categoryCode()),
                    trimToNull(criteria.gender()), criteria.level(),
                    criteria.onlineOnly(),
                    criteria.excludeOrdered() ? fanUserId : null,
                    List.of());
        }
        return id == null ? Optional.empty() : Optional.ofNullable(talentService.find(id));
    }

    private static String trimToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}
