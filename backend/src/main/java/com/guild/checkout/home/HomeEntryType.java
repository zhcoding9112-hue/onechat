package com.guild.checkout.home;

/** 首页配置位的位置类型。 */
public enum HomeEntryType {
    /** 九宫格服务入口。 */
    GRID,
    /** 运营横幅轮播。 */
    BANNER;

    public static HomeEntryType of(String value) {
        for (HomeEntryType t : values()) {
            if (t.name().equalsIgnoreCase(value)) {
                return t;
            }
        }
        throw new IllegalArgumentException("未知的首页位类型: " + value);
    }
}
