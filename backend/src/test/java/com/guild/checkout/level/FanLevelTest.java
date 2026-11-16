package com.guild.checkout.level;

import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.order.OrderCreateCommand;
import com.guild.checkout.order.OrderService;
import com.guild.checkout.order.ServiceOrder;
import com.guild.checkout.pricing.ServiceItem;
import com.guild.checkout.pricing.ServiceSpec;
import com.guild.checkout.pricing.admin.CommissionAdminService;
import com.guild.checkout.pricing.admin.ServiceCatalogAdminService;
import com.guild.checkout.pricing.admin.TalentPriceAdminService;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.admin.TalentAdminService;
import com.guild.checkout.tipping.TipRecord;
import com.guild.checkout.tipping.TipService;
import com.guild.checkout.user.AppUser;
import com.guild.checkout.user.AppUserService;
import com.guild.checkout.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 粉丝等级（tasks 8.5 - 8.6）。
 */
@SpringBootTest
@Transactional
class FanLevelTest {

    @Autowired private FanLevelService levelService;
    @Autowired private OrderService orderService;
    @Autowired private TipService tipService;
    @Autowired private AppUserService userService;
    @Autowired private TalentAdminService talentAdminService;
    @Autowired private ServiceCatalogAdminService catalogService;
    @Autowired private TalentPriceAdminService priceService;
    @Autowired private CommissionAdminService commissionService;

    private long fanId;
    private long operatorId;
    private long talentId;
    private long talentUserId;
    private long specId;

    @BeforeEach
    void setUp() {
        commissionService.setDefault(3000);
        fanId = newUser("vf_").getId();
        operatorId = newUser("vp_").getId();
        userService.grantRole(operatorId, UserRole.OPERATOR);

        ServiceItem item = catalogService.createItem("vc_" + System.nanoTime(), "情感倾听", null, 0);
        ServiceSpec spec = catalogService.createSpec(item.getId(), "m30", "30分钟", 30, 30);
        specId = spec.getId();

        Talent talent = talentAdminService.create(new TalentAdminService.ProfileForm(
                "等级测试主播", null, null, 0, null, null, null, null));
        talentAdminService.setListed(talent.getId(), true);
        talentId = talent.getId();
        priceService.setPrice(talentId, specId, 10000, true);

        AppUser talentUser = newUser("vt_");
        talentAdminService.linkUser(talentId, talentUser.getId());
        talentUserId = talentUser.getId();
    }

    private AppUser newUser(String prefix) {
        return userService.registerOrUpdate(prefix + System.nanoTime(), null, "测试用户", null);
    }

    /** 下单并确认收款：这一刻起计入累计消费。 */
    private ServiceOrder paidOrder(long fan, int quantity) {
        ServiceOrder order = orderService.create(new OrderCreateCommand(
                fan, talentId, specId, quantity, "wxid_abc", null));
        return orderService.confirmPayment(order.getId(), order.getPayableCents(), operatorId, null);
    }

    // ===== 8.5 等级计算与门槛 =====

    @Test
    @DisplayName("8.5 没有任何消费的粉丝是最低等级")
    void 零消费为最低等级() {
        FanLevelService.FanLevel level = levelService.levelOf(fanId);

        assertThat(level.level()).isEqualTo(1);
        assertThat(level.spentCents()).isZero();
        assertThat(level.nextLevel()).isEqualTo(2);
    }

    @Test
    @DisplayName("8.5 累计消费越过门槛后等级提升")
    void 越过门槛升级() {
        // 默认门槛：L2 需要 200 元
        assertThat(levelService.levelOf(fanId).level()).isEqualTo(1);

        paidOrder(fanId, 2);   // 200 元

        FanLevelService.FanLevel level = levelService.levelOf(fanId);
        assertThat(level.spentCents()).isEqualTo(20000);
        assertThat(level.level()).isEqualTo(2);
    }

    @Test
    @DisplayName("8.5 打赏也计入累计消费")
    void 打赏计入累计() {
        TipRecord tip = tipService.create(fanId, talentId, 20000, null);
        tipService.confirmPayment(tip.getId(), 20000, null);

        assertThat(levelService.levelOf(fanId).spentCents()).isEqualTo(20000);
        assertThat(levelService.levelOf(fanId).level()).isEqualTo(2);
    }

    @Test
    @DisplayName("8.5 未确认收款的订单不计入累计")
    void 未收款不计入() {
        orderService.create(new OrderCreateCommand(
                fanId, talentId, specId, 2, "wxid_abc", null));

        assertThat(levelService.levelOf(fanId).spentCents()).isZero();
    }

    @Test
    @DisplayName("8.5 已退款的订单从累计中剔除，等级随之回落")
    void 退款后等级回落() {
        ServiceOrder order = paidOrder(fanId, 2);
        assertThat(levelService.levelOf(fanId).level()).isEqualTo(2);

        orderService.accept(order.getId(), talentUserId);
        orderService.complete(order.getId(), talentUserId);
        orderService.markRefunded(order.getId(), operatorId, "纠纷退款");

        assertThat(levelService.levelOf(fanId).spentCents()).isZero();
        assertThat(levelService.levelOf(fanId).level()).isEqualTo(1);
    }

    @Test
    @DisplayName("8.5 累计消费取实付而非原价：用券抵掉的部分粉丝没花")
    void 累计取实付() {
        // 这里不造券，直接核对口径：没有券时实付等于原价，累计等于实付
        ServiceOrder order = paidOrder(fanId, 1);

        assertThat(order.getPayableCents()).isEqualTo(order.getOriginalCents());
        assertThat(levelService.levelOf(fanId).spentCents()).isEqualTo(order.getPayableCents());
    }

    @Test
    @DisplayName("8.5 运营调整门槛后，等级计算立刻按新门槛执行")
    void 调整门槛立刻生效() {
        paidOrder(fanId, 1);   // 100 元，默认不够 L2
        assertThat(levelService.levelOf(fanId).level()).isEqualTo(1);

        levelService.updateThreshold(2, 10000, null);

        assertThat(levelService.levelOf(fanId).level()).isEqualTo(2);
    }

    @Test
    @DisplayName("8.5 门槛必须随等级递增")
    void 门槛必须递增() {
        assertThatThrownBy(() -> levelService.updateThreshold(2, 5_000_000, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("递增");
    }

    @Test
    @DisplayName("8.5 最低等级的门槛必须为 0")
    void 最低档门槛必须为零() {
        assertThatThrownBy(() -> levelService.updateThreshold(1, 1000, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须为 0");
    }

    @Test
    @DisplayName("8.5 门槛不得为负")
    void 门槛不得为负() {
        assertThatThrownBy(() -> levelService.updateThreshold(3, -1, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不得为负");
    }

    @Test
    @DisplayName("8.5 满级后不再有下一级")
    void 满级无下一级() {
        List<FanLevelSetting> tiers = levelService.thresholds();
        // 自低向高逐档下调，中途始终保持递增（从高往低调会在中途违反递增被拒）
        for (int i = 1; i < tiers.size(); i++) {
            levelService.updateThreshold(tiers.get(i).getLevel(), i, null);
        }
        paidOrder(fanId, 1);

        FanLevelService.FanLevel level = levelService.levelOf(fanId);
        assertThat(level.level()).isEqualTo(tiers.get(tiers.size() - 1).getLevel());
        assertThat(level.nextLevel()).isNull();
        assertThat(level.toNextCents()).isNull();
    }

    // ===== 8.6 等级不影响计价 =====

    @Test
    @DisplayName("8.6 不同等级的粉丝对同一主播同一档位下单，金额与分成快照完全一致")
    void 等级不影响计价() {
        long lowFan = fanId;
        long highFan = newUser("vh_").getId();

        // 把 L2 门槛降到一笔订单就够得着，这样两位粉丝能落在不同等级上
        levelService.updateThreshold(2, 10000, null);
        paidOrder(highFan, 1);

        int lowLevel = levelService.levelOf(lowFan).level();
        int highLevel = levelService.levelOf(highFan).level();
        assertThat(highLevel).as("两位粉丝必须处于不同等级，否则这条用例什么都没验").isGreaterThan(lowLevel);

        ServiceOrder lowOrder = orderService.create(new OrderCreateCommand(
                lowFan, talentId, specId, 1, "wxid_low", null));
        ServiceOrder highOrder = orderService.create(new OrderCreateCommand(
                highFan, talentId, specId, 1, "wxid_high", null));

        assertThat(highOrder.getUnitPriceCents()).isEqualTo(lowOrder.getUnitPriceCents());
        assertThat(highOrder.getOriginalCents()).isEqualTo(lowOrder.getOriginalCents());
        assertThat(highOrder.getPayableCents()).isEqualTo(lowOrder.getPayableCents());
        assertThat(highOrder.getCommissionRateBp()).isEqualTo(lowOrder.getCommissionRateBp());
        assertThat(highOrder.getGuildCents()).isEqualTo(lowOrder.getGuildCents());
        assertThat(highOrder.getTalentCents()).isEqualTo(lowOrder.getTalentCents());
    }

    @Test
    @DisplayName("8.6 计价链路不依赖等级：源码中 pricing 包不引用 FanLevel")
    void 计价不引用等级() throws Exception {
        java.nio.file.Path pricing = java.nio.file.Path.of("src/main/java/com/guild/checkout/pricing");
        try (var files = java.nio.file.Files.walk(pricing)) {
            List<String> offenders = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            return java.nio.file.Files.readString(p).contains("FanLevel");
                        } catch (java.io.IOException e) {
                            return false;
                        }
                    })
                    .map(java.nio.file.Path::toString).toList();
            assertThat(offenders).as("计价代码一旦读等级，就再也不是「等级不影响计价」了").isEmpty();
        }
    }
}
