package com.guild.checkout.match;

import com.guild.checkout.credit.CreditService;
import com.guild.checkout.credit.CreditType;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.admin.TalentAdminService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 随机匹配（tasks 4.1 - 4.5）。
 *
 * 券包要到第 7 组才存在，4.5 的「券包不变」在那一组的用例里覆盖。
 */
@SpringBootTest
@Transactional
class RandomMatchTest {

    private static final long FAN_ID = 999_000_002L;

    @Autowired private RandomMatchService matchService;
    @Autowired private TalentAdminService talentAdminService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CreditService creditService;

    private static RandomMatchService.Criteria any() {
        return new RandomMatchService.Criteria(null, null, false, false, List.of());
    }

    private Talent newListed(String nickname, String gender, boolean online) {
        Talent t = talentAdminService.create(new TalentAdminService.ProfileForm(
                nickname, null, null, 0, gender, null, null, null));
        talentAdminService.setListed(t.getId(), true);
        if (online) {
            // 在线状态由主播自己在工作台切换，没有运营侧入口，这里直接改库
            jdbc.update("UPDATE talent SET online = 1 WHERE id = ?", t.getId());
        }
        return t;
    }

    @Test
    @DisplayName("4.1 连续 100 次匹配的结果既不固定也不是轮询")
    void 连续匹配不是固定顺序或轮询() {
        List<Long> draws = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            matchService.match(FAN_ID, any()).ifPresent(t -> draws.add(t.getId()));
        }

        assertThat(draws).hasSize(100);
        Set<Long> distinct = new HashSet<>(draws);
        assertThat(distinct.size())
                .as("结果固定或候选过少，随机性无从体现")
                .isGreaterThanOrEqualTo(3);

        // 轮询的特征是存在一个周期 p，使得序列每隔 p 项完全重复。
        // 逐一排除小周期，把「看起来随机其实是轮询」挡掉。
        for (int period = 1; period <= 10; period++) {
            boolean periodic = true;
            for (int i = period; i < draws.size(); i++) {
                if (!draws.get(i).equals(draws.get(i - period))) {
                    periodic = false;
                    break;
                }
            }
            assertThat(periodic).as("结果呈周期 %d 的轮询", period).isFalse();
        }
    }

    @Test
    @DisplayName("4.2 排除已下过单后，结果不含该粉丝下过单的主播")
    void 排除已下过单的主播() {
        Talent ordered = newListed("下过单的", null, false);
        jdbc.update("""
                INSERT INTO service_order
                  (order_ref, fan_user_id, talent_id, service_item_id, service_spec_id,
                   service_item_name, service_spec_name, quantity, unit_price_cents,
                   payable_cents, paid_cents, commission_rate_bp, guild_cents, talent_cents,
                   status, contact, version)
                VALUES (?, ?, ?, 1, 1, '项目', '档位', 1, 100, 100, 100, 3000, 30, 70,
                        'COMPLETED', 'wx', 0)
                """,
                "M" + Long.toString(System.nanoTime() % 100000000L, 36), FAN_ID, ordered.getId());

        var criteria = new RandomMatchService.Criteria(null, null, false, true, List.of());
        for (int i = 0; i < 60; i++) {
            Optional<Talent> hit = matchService.match(FAN_ID, criteria);
            assertThat(hit).isPresent();
            assertThat(hit.get().getId()).isNotEqualTo(ordered.getId());
        }
    }

    @Test
    @DisplayName("4.2 按性别匹配时结果只会是该性别")
    void 按性别匹配() {
        newListed("匹配用男主播", "MALE", false);
        var criteria = new RandomMatchService.Criteria(null, "MALE", false, false, List.of());

        for (int i = 0; i < 30; i++) {
            Optional<Talent> hit = matchService.match(FAN_ID, criteria);
            assertThat(hit).isPresent();
            assertThat(hit.get().getGender()).isEqualTo("MALE");
        }
    }

    @Test
    @DisplayName("4.3 无符合条件的主播时返回空，不退化成随便给一个")
    void 无符合条件时返回空() {
        // 不存在的品类，候选集必然为空
        var criteria = new RandomMatchService.Criteria(
                "不存在的品类代码-" + System.nanoTime(), null, false, false, List.of());

        assertThat(matchService.match(FAN_ID, criteria)).isEmpty();
    }

    @Test
    @DisplayName("4.4 连续换五位，候选充足时互不相同")
    void 连续换五位互不相同() {
        List<Long> seen = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Optional<Talent> hit = matchService.match(FAN_ID,
                    new RandomMatchService.Criteria(null, null, false, false, List.copyOf(seen)));
            assertThat(hit).as("第 %d 次换人应当有结果", i + 1).isPresent();
            seen.add(hit.get().getId());
        }
        assertThat(seen).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("4.4 候选只剩一位时允许重复，而不是报告无结果")
    void 候选见底时放宽而非报空() {
        Talent only = newListed("唯一候选", "MALE", true);
        // 只匹配在线男主播，并把除它以外的同类全部排除掉
        List<Long> others = jdbc.queryForList(
                "SELECT id FROM talent WHERE listed=1 AND gender='MALE' AND online=1 AND id<>?",
                Long.class, only.getId());

        var criteria = new RandomMatchService.Criteria(
                null, "MALE", true, false, new ArrayList<>(others) {{ add(only.getId()); }});

        Optional<Talent> hit = matchService.match(FAN_ID, criteria);
        assertThat(hit).isPresent();
        assertThat(hit.get().getId()).isEqualTo(only.getId());
    }

    @Test
    @DisplayName("4.5 连续 50 次匹配不产生任何订单或打赏记录")
    void 匹配不产生任何账务副作用() {
        long ordersBefore = jdbc.queryForObject("SELECT COUNT(*) FROM service_order", Long.class);
        long tipsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM tip_record", Long.class);

        for (int i = 0; i < 50; i++) {
            matchService.match(FAN_ID, any());
        }

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM service_order", Long.class))
                .isEqualTo(ordersBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tip_record", Long.class))
                .isEqualTo(tipsBefore);
    }

    @Test
    @DisplayName("4.5 连续 50 次匹配后元子余额与流水完全不变")
    void 匹配不动元子余额() {
        // 先给这位粉丝垫一个非零余额：余额恒为 0 的账户看不出「有没有被扣」
        creditService.apply(FAN_ID, 100, CreditType.GRANT, "匹配不扣费用例的期初", FAN_ID);
        long balanceBefore = creditService.balanceOf(FAN_ID);
        long txBefore = creditService.countTransactions(FAN_ID);

        for (int i = 0; i < 50; i++) {
            matchService.match(FAN_ID, any());
        }

        assertThat(creditService.balanceOf(FAN_ID)).isEqualTo(balanceBefore);
        assertThat(creditService.countTransactions(FAN_ID)).isEqualTo(txBefore);
    }
}
