package com.guild.checkout.analytics;

import java.util.List;

/**
 * 发现入口。
 *
 * 每个入口有自己的事件键（{@link #eventKey()}，形如 {@code ENTRY_POPULAR}），
 * 入口被点击时记一条；粉丝由此进入主播详情时，详情事件带上同一个来源。
 *
 * 两条设计约束：
 *
 * 1. **没有「未知」这一项**。来源为空的一律记 DIRECT（直接访问）。允许为空会让
 *    拆分表各行之和小于总数，而少掉的那部分没人知道去了哪——
 *    拆分表最要紧的性质就是加起来等于总数。
 *
 * 2. **TALENT_LIST 是我加的，不在 tasks 列举的 11 个之内**。首页品类 Tab 未选中时
 *    的默认列表也是一个真实入口，把它归进 DIRECT 会让「直接访问」这一行
 *    混进大量并非来自分享链接的人，那一行就不能用来衡量分享效果了。
 */
public enum EntrySource {

    /** 首页品类 Tab（选中了某个品类后点进的主播）。 */
    CATEGORY_TAB("品类 Tab"),
    /** 首页主列表（未选中品类时）。 */
    TALENT_LIST("主播列表"),
    /** 首页人气店员横滑。 */
    POPULAR("人气店员"),
    /** 首页九宫格运营位。 */
    GRID("九宫格"),
    /** 首页运营横幅。 */
    BANNER("运营横幅"),
    /** 试音选人。 */
    AUDITION("试音选人"),
    /** 随机匹配。 */
    RANDOM_MATCH("随机匹配"),
    /** 心动墙。 */
    CRUSH_WALL("心动墙"),
    /** 动态广场。 */
    FEED("动态广场"),
    /** 打赏榜。 */
    LEADERBOARD("榜单"),
    /** 我的收藏。 */
    FAVORITES("收藏列表"),
    /** 站内聊天。 */
    CHAT("站内聊天"),
    /**
     * 直接访问：分享链接、历史记录、扫码等没有站内来源的进入。
     *
     * 它没有可点击的入口，{@link #eventKey()} 对它永远是零——保留这个方法的一致性
     * 比为它开一个特例更简单。
     */
    DIRECT("直接访问");

    private final String label;

    EntrySource(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 该入口被点击时上报的事件键。 */
    public String eventKey() {
        return "ENTRY_" + name();
    }

    public static List<EntrySource> all() {
        return List.of(values());
    }

    /** 宽松解析：认不出来的一律当直接访问，绝不返回 null。 */
    public static EntrySource of(String value) {
        if (value != null) {
            for (EntrySource s : values()) {
                if (s.name().equalsIgnoreCase(value.trim())) {
                    return s;
                }
            }
        }
        return DIRECT;
    }

    /** 由事件键反查入口；不是入口事件则返回 null。 */
    public static EntrySource byEventKey(String eventKey) {
        if (eventKey != null) {
            for (EntrySource s : values()) {
                if (s.eventKey().equals(eventKey)) {
                    return s;
                }
            }
        }
        return null;
    }
}
