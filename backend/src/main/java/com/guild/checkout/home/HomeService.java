package com.guild.checkout.home;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 首页各区块的数据来源。 */
@Service
public class HomeService {

    /** 人气统计的窗口期。 */
    private static final Duration POPULAR_WINDOW = Duration.ofDays(7);

    private final HomeEntryMapper entryMapper;
    private final HomeStatsMapper statsMapper;

    public HomeService(HomeEntryMapper entryMapper, HomeStatsMapper statsMapper) {
        this.entryMapper = entryMapper;
        this.statsMapper = statsMapper;
    }

    /**
     * 取某一位置当前生效的配置项。
     *
     * 生效判断放在查询条件里而不是查出来再过滤：横幅过期应当「刷新即消失」，
     * 由数据库按当前时间判定，不依赖任何定时任务去改 enabled。
     */
    public List<HomeEntry> listActiveEntries(HomeEntryType type) {
        LocalDateTime now = LocalDateTime.now();
        return entryMapper.selectList(Wrappers.<HomeEntry>lambdaQuery()
                .eq(HomeEntry::getEntryType, type.name())
                .eq(HomeEntry::getEnabled, true)
                .and(w -> w.isNull(HomeEntry::getStartAt).or().le(HomeEntry::getStartAt, now))
                .and(w -> w.isNull(HomeEntry::getEndAt).or().ge(HomeEntry::getEndAt, now))
                .orderByAsc(HomeEntry::getSortOrder)
                .orderByAsc(HomeEntry::getId));
    }

    /** 人气主播 id，按近 7 日成交单量倒序。 */
    public List<Long> popularTalentIds(int limit) {
        return statsMapper.findPopularTalentIds(LocalDateTime.now().minus(POPULAR_WINDOW), limit);
    }

    /** 跑马灯数据。返回项只含主播昵称与金额，不含任何打赏人信息。 */
    public List<Map<String, Object>> recentTips(int limit) {
        return statsMapper.findRecentTips(limit);
    }
}
