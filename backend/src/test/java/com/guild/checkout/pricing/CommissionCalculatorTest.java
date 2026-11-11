package com.guild.checkout.pricing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 分账取整。这是全系统最不能错的一处：错一分钱会在账上长期漂移。
 */
class CommissionCalculatorTest {

    // ---------- 能整除的基准 ----------

    @ParameterizedTest
    @CsvSource({
            // 金额(分), 比例(基点), 期望公会, 期望主播
            "10000, 3000, 3000, 7000",   // 100 元，30%
            "10000, 2000, 2000, 8000",   // 100 元，20%
            "10000, 5000, 5000, 5000",   // 100 元，50%
            "2500,  4000, 1000, 1500",   // 25 元，40%
            "0,     3000,    0,    0",   // 零元订单
            "10000,    0,    0, 10000",  // 不抽成
            "10000, 10000, 10000,   0",  // 全额抽成
    })
    void 能整除时按比例精确拆分(long amount, int rateBp, long guild, long talent) {
        CommissionSplit split = CommissionCalculator.split(amount, rateBp);
        assertThat(split.guildCents()).isEqualTo(guild);
        assertThat(split.talentCents()).isEqualTo(talent);
    }

    // ---------- 无法整除：余数必须归主播 ----------

    @ParameterizedTest
    @CsvSource({
            // 金额(分), 比例(基点), 期望公会(向下取整), 期望主播(含余数)
            "9999,  3000, 2999, 7000",   // 99.99 元 × 30% = 2999.7 → 公会 2999
            "3333,  3333, 1110, 2223",   // 33.33 元 × 33.33% = 1110.89 → 公会 1110
            "1,     5000,    0,    1",   // 1 分 × 50% = 0.5 → 公会 0，那一分给主播
            "1,     9999,    0,    1",   // 1 分 × 99.99% → 公会仍为 0
            "7,     3333,    2,    5",   // 7 分 × 33.33% = 2.33 → 公会 2
            "12345, 1234, 1523, 10822",  // 123.45 元 × 12.34% = 1523.37 → 公会 1523
            "19999, 2500, 4999, 15000",  // 199.99 元 × 25% = 4999.75 → 公会 4999
            "99,    101,     0,   99",   // 0.99 元 × 1.01% = 0.99 → 公会 0
    })
    void 无法整除时公会向下取整且余数归主播(long amount, int rateBp, long guild, long talent) {
        CommissionSplit split = CommissionCalculator.split(amount, rateBp);
        assertThat(split.guildCents()).as("公会分成向下取整").isEqualTo(guild);
        assertThat(split.talentCents()).as("余数归主播").isEqualTo(talent);
        assertThat(split.guildCents() + split.talentCents())
                .as("两项之和恒等于订单金额").isEqualTo(amount);
    }

    // ---------- 不变式：穷举与随机 ----------

    @Test
    void 穷举小额与常用比例两项之和恒等于订单金额() {
        int[] rates = {0, 1, 101, 500, 1234, 2000, 2500, 3000, 3333, 4000, 5000, 6667, 9999, 10000};
        for (long amount = 0; amount <= 2000; amount++) {
            for (int rateBp : rates) {
                CommissionSplit split = CommissionCalculator.split(amount, rateBp);
                assertThat(split.guildCents() + split.talentCents())
                        .as("金额 %d 比例 %d", amount, rateBp)
                        .isEqualTo(amount);
                assertThat(split.guildCents()).isBetween(0L, amount);
                assertThat(split.talentCents()).isBetween(0L, amount);
            }
        }
    }

    @Test
    void 随机金额与比例下不变式依然成立() {
        Random random = new Random(20261111L);
        for (int i = 0; i < 20000; i++) {
            long amount = random.nextLong(0, 100_000_000L); // 0 到 100 万元
            int rateBp = random.nextInt(0, 10001);
            CommissionSplit split = CommissionCalculator.split(amount, rateBp);
            assertThat(split.guildCents() + split.talentCents()).isEqualTo(amount);
        }
    }

    @Test
    void 公会分成绝不超过按比例应得() {
        // 向下取整意味着公会永远不会多拿
        Random random = new Random(42L);
        for (int i = 0; i < 5000; i++) {
            long amount = random.nextLong(0, 10_000_000L);
            int rateBp = random.nextInt(0, 10001);
            CommissionSplit split = CommissionCalculator.split(amount, rateBp);
            java.math.BigInteger exact = java.math.BigInteger.valueOf(amount)
                    .multiply(java.math.BigInteger.valueOf(rateBp));
            java.math.BigInteger guildTimes = java.math.BigInteger.valueOf(split.guildCents())
                    .multiply(java.math.BigInteger.valueOf(10000));
            assertThat(guildTimes).isLessThanOrEqualTo(exact);
        }
    }

    @Test
    void 极大金额不溢出() {
        long huge = 9_000_000_000_000_000L; // 90 万亿分 = 9000 亿元
        CommissionSplit split = CommissionCalculator.split(huge, 3333);
        assertThat(split.guildCents() + split.talentCents()).isEqualTo(huge);
        assertThat(split.guildCents()).isEqualTo(2_999_700_000_000_000L);
    }

    // ---------- 非法输入 ----------

    @Test
    void 负数金额被拒绝() {
        assertThatThrownBy(() -> CommissionCalculator.split(-1, 3000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不得为负");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 10001, Integer.MAX_VALUE, Integer.MIN_VALUE})
    void 越界比例被拒绝(int rateBp) {
        assertThatThrownBy(() -> CommissionCalculator.split(10000, rateBp))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0 到 10000");
    }

    @Test
    void 拆分结果自身校验不变式() {
        assertThatThrownBy(() -> new CommissionSplit(100, 3000, 30, 60))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不符");
    }

    // ---------- 展示 ----------

    @ParameterizedTest
    @CsvSource({
            "3000, 30%",
            "2000, 20%",
            "10000, 100%",
            "0, 0%",
            "2550, 25.5%",
            "1234, 12.34%",
            "101, 1.01%",
    })
    void 基点转百分比文案(int rateBp, String expected) {
        assertThat(CommissionCalculator.formatRate(rateBp)).isEqualTo(expected);
    }
}
