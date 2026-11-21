package com.guild.checkout.common.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分页与 LIKE 入参的归一化。
 *
 * 放在纯单测里而不是接口测试里：上限是「传 100000 也只给 100 条」，
 * 要在 HTTP 层验出来得先造一百多条数据，成本远大于收益，而这里一行就验到了。
 */
class PageablesTest {

    @Test
    @DisplayName("页大小被钳在 1 到上限之间")
    void 页大小有上下限() {
        assertThat(Pageables.size(null)).isEqualTo(20);
        assertThat(Pageables.size(0)).isEqualTo(1);
        assertThat(Pageables.size(-5)).isEqualTo(1);
        assertThat(Pageables.size(50)).isEqualTo(50);
        assertThat(Pageables.size(100_000))
                .as("再怎么传也不该把整张表拉下来")
                .isEqualTo(Pageables.MAX_SIZE);
    }

    @Test
    @DisplayName("页码不接受负数")
    void 页码不为负() {
        assertThat(Pageables.page(null)).isZero();
        assertThat(Pageables.page(-1)).isZero();
        assertThat(Pageables.page(3)).isEqualTo(3);
    }

    @Test
    @DisplayName("偏移量按钳过的值算")
    void 偏移量() {
        assertThat(Pageables.offset(0, 20)).isZero();
        assertThat(Pageables.offset(2, 20)).isEqualTo(40);
    }

    @Test
    @DisplayName("LIKE 片段转义通配符，并统一成小写")
    void LIKE转义() {
        assertThat(Pageables.like("abc")).isEqualTo("%abc%");
        assertThat(Pageables.like("ABC")).as("统一小写，配合 SQL 里的 LOWER()").isEqualTo("%abc%");
        assertThat(Pageables.like("  abc  ")).as("两头空格不该参与匹配").isEqualTo("%abc%");
        assertThat(Pageables.like("a%b")).isEqualTo("%a!%b%");
        assertThat(Pageables.like("wx_id")).isEqualTo("%wx!_id%");
        assertThat(Pageables.like("a!b")).as("转义字符自己也要转").isEqualTo("%a!!b%");
    }

    @Test
    @DisplayName("空白关键字视为没填")
    void 空白关键字() {
        assertThat(Pageables.blank(null)).isTrue();
        assertThat(Pageables.blank("")).isTrue();
        assertThat(Pageables.blank("   ")).isTrue();
        assertThat(Pageables.blank("a")).isFalse();
    }

    @Test
    @DisplayName("hasMore 按已消费条数与总数比较")
    void hasMore判定() {
        assertThat(PageResponse.of(java.util.List.of(1, 2), 5, 0, 2).hasMore()).isTrue();
        assertThat(PageResponse.of(java.util.List.of(1), 5, 2, 2).hasMore())
                .as("第 2 页取到第 5 条，正好到底").isFalse();
        assertThat(PageResponse.of(java.util.List.of(), 5, 99, 2).hasMore())
                .as("翻过头了，没有下一页").isFalse();
    }
}
