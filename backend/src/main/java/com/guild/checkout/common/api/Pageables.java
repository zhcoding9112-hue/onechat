package com.guild.checkout.common.api;

import java.util.Locale;

/** 分页与模糊查询的入参归一化。 */
public final class Pageables {

    /** 单页上限。再大就不是「翻页」而是「把库拉下来」了。 */
    public static final int MAX_SIZE = 100;

    private Pageables() {
    }

    public static int page(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }

    public static int size(Integer size) {
        if (size == null) {
            return 20;
        }
        return Math.min(Math.max(size, 1), MAX_SIZE);
    }

    public static int offset(int page, int size) {
        return page * size;
    }

    /**
     * LIKE 的转义字符。
     *
     * 不用默认的反斜杠：它在 Java 字符串、MySQL 字符串字面量里各要转一次，
     * 写成 ESCAPE '\\\\' 才等于一个反斜杠，中间少写一层就是语法错误——
     * 我第一版就栽在这儿。换成感叹号，两边都不需要转义。
     */
    public static final char ESCAPE_CHAR = '!';

    /** 拼进 SQL 的 ESCAPE 子句，与 {@link #like} 配套使用。 */
    public static final String ESCAPE_CLAUSE = " ESCAPE '" + ESCAPE_CHAR + "'";

    /**
     * 把用户输入变成可以安全拼进 LIKE 的片段。
     *
     * % 和 _ 在 LIKE 里是通配符：运营搜一个带下划线的微信号（wx_abc）时，
     * 不转义的话 _ 会匹配任意字符，搜出一堆不相干的单。转义字符自己也要先转。
     */
    public static String like(String keyword) {
        String escaped = keyword.trim()
                .replace(String.valueOf(ESCAPE_CHAR), ESCAPE_CHAR + "" + ESCAPE_CHAR)
                .replace("%", ESCAPE_CHAR + "%")
                .replace("_", ESCAPE_CHAR + "_");
        return "%" + escaped.toLowerCase(Locale.ROOT) + "%";
    }

    public static boolean blank(String keyword) {
        return keyword == null || keyword.isBlank();
    }
}
