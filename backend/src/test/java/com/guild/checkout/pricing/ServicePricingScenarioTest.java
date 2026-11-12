package com.guild.checkout.pricing;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.pricing.admin.CommissionAdminService;
import com.guild.checkout.pricing.admin.ServiceCatalogAdminService;
import com.guild.checkout.pricing.admin.TalentPriceAdminService;
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
 * service-pricing spec 的场景测试，逐条对应规格中的 Scenario。
 */
@SpringBootTest
@Transactional
class ServicePricingScenarioTest {

    private static final long TALENT_A = 3001L;
    private static final long TALENT_B = 3002L;

    @Autowired private PricingService pricingService;
    @Autowired private ServiceCatalogAdminService catalogService;
    @Autowired private TalentPriceAdminService priceService;
    @Autowired private CommissionAdminService commissionService;
    @Autowired private ServiceItemMapper itemMapper;
    @Autowired private CommissionConfigMapper commissionMapper;

    private ServiceItem item;
    private ServiceSpec spec30;

    @BeforeEach
    void setUp() {
        item = catalogService.createItem("sc_" + System.nanoTime(), "情感倾听", "一对一倾听", 0);
        spec30 = catalogService.createSpec(item.getId(), "m30", "30分钟", 30, 30);
        commissionService.setDefault(3000);
    }

    // ===== Requirement: 服务目录与档位配置 =====

    @Test
    @DisplayName("Scenario: 新增服务项目与档位 — 可被分配给主播并在粉丝端展示")
    void 新增服务项目与档位() {
        priceService.setPrice(TALENT_A, spec30.getId(), 1500, true);

        List<PricingService.PurchasableSpec> list = pricingService.listPurchasable(TALENT_A);

        assertThat(list).singleElement().satisfies(s -> {
            assertThat(s.itemName()).isEqualTo("情感倾听");
            assertThat(s.specName()).isEqualTo("30分钟");
            assertThat(s.unitPriceCents()).isEqualTo(1500);
        });
    }

    @Test
    @DisplayName("Scenario: 停用服务项目 — 粉丝端不再展示，已存在的订单不受影响")
    void 停用服务项目() {
        priceService.setPrice(TALENT_A, spec30.getId(), 1500, true);
        // 停用前先成单，模拟「已存在的该项目订单」
        PriceQuote existingOrder = pricingService.quote(TALENT_A, spec30.getId(), 1);

        catalogService.setItemEnabled(item.getId(), false);

        // 粉丝端不再展示该项目的下单入口
        assertThat(pricingService.listPurchasable(TALENT_A)).isEmpty();
        assertThatThrownBy(() -> pricingService.quote(TALENT_A, spec30.getId(), 1))
                .isInstanceOf(PricingException.class);

        // 已存在的订单快照不受任何影响
        assertThat(existingOrder.payableCents()).isEqualTo(1500);
        assertThat(existingOrder.serviceItemName()).isEqualTo("情感倾听");
        assertThat(existingOrder.guildCents()).isEqualTo(450);
        assertThat(existingOrder.talentCents()).isEqualTo(1050);
    }

    // ===== Requirement: 按主播覆写价格 =====

    @Test
    @DisplayName("Scenario: 为主播设定差异化价格 — 各自看到专属价格")
    void 为主播设定差异化价格() {
        priceService.setPrice(TALENT_A, spec30.getId(), 1500, true);
        priceService.setPrice(TALENT_B, spec30.getId(), 8800, true);

        assertThat(pricingService.quote(TALENT_A, spec30.getId(), 1).unitPriceCents()).isEqualTo(1500);
        assertThat(pricingService.quote(TALENT_B, spec30.getId(), 1).unitPriceCents()).isEqualTo(8800);
    }

    @Test
    @DisplayName("Scenario: 未设定价格的档位 — 不出现在其详情页的可购列表中")
    void 未设定价格的档位不出现() {
        ServiceSpec spec60 = catalogService.createSpec(item.getId(), "m60", "60分钟", 60, 60);
        priceService.setPrice(TALENT_A, spec30.getId(), 1500, true);

        List<PricingService.PurchasableSpec> list = pricingService.listPurchasable(TALENT_A);

        assertThat(list).extracting(PricingService.PurchasableSpec::serviceSpecId)
                .containsExactly(spec30.getId())
                .doesNotContain(spec60.getId());
    }

    // ===== Requirement: 分成比例配置 =====

    @Test
    @DisplayName("Scenario: 配置全局默认分成 — 未单独配置的主播按默认比例")
    void 配置全局默认分成() {
        commissionService.setDefault(2500);
        priceService.setPrice(TALENT_A, spec30.getId(), 10000, true);

        PriceQuote quote = pricingService.quote(TALENT_A, spec30.getId(), 1);

        assertThat(quote.commissionRateBp()).isEqualTo(2500);
        assertThat(quote.guildCents()).isEqualTo(2500);
        assertThat(quote.talentCents()).isEqualTo(7500);
    }

    @Test
    @DisplayName("Scenario: 按主播覆写分成 — 不受全局默认值影响")
    void 按主播覆写分成() {
        commissionService.setDefault(3000);
        commissionService.setOverride(TALENT_A, 4500);
        priceService.setPrice(TALENT_A, spec30.getId(), 10000, true);
        priceService.setPrice(TALENT_B, spec30.getId(), 10000, true);

        assertThat(pricingService.quote(TALENT_A, spec30.getId(), 1).commissionRateBp()).isEqualTo(4500);
        assertThat(pricingService.quote(TALENT_B, spec30.getId(), 1).commissionRateBp()).isEqualTo(3000);

        // 改全局默认，已覆写的主播不受影响
        commissionService.setDefault(1000);
        assertThat(pricingService.quote(TALENT_A, spec30.getId(), 1).commissionRateBp()).isEqualTo(4500);
        assertThat(pricingService.quote(TALENT_B, spec30.getId(), 1).commissionRateBp()).isEqualTo(1000);
    }

    // ===== Requirement: 下单时的价格与分成快照（两条关键场景）=====

    @Test
    @DisplayName("Scenario: 调价不影响历史订单 — 历史金额不变，新订单按新价")
    void 调价不影响历史订单() {
        priceService.setPrice(TALENT_A, spec30.getId(), 1500, true);
        PriceQuote history = pricingService.quote(TALENT_A, spec30.getId(), 2);

        assertThat(history.unitPriceCents()).isEqualTo(1500);
        assertThat(history.payableCents()).isEqualTo(3000);
        assertThat(history.guildCents()).isEqualTo(900);
        assertThat(history.talentCents()).isEqualTo(2100);

        // 运营上调价格
        priceService.setPrice(TALENT_A, spec30.getId(), 9900, true);

        // 历史订单的应付金额与分账金额保持创建时的取值不变
        assertThat(history.unitPriceCents()).isEqualTo(1500);
        assertThat(history.payableCents()).isEqualTo(3000);
        assertThat(history.guildCents()).isEqualTo(900);
        assertThat(history.talentCents()).isEqualTo(2100);

        // 之后创建的新订单按新价格计算
        PriceQuote fresh = pricingService.quote(TALENT_A, spec30.getId(), 2);
        assertThat(fresh.unitPriceCents()).isEqualTo(9900);
        assertThat(fresh.payableCents()).isEqualTo(19800);
    }

    @Test
    @DisplayName("Scenario: 调整分成比例不影响历史分账 — 历史订单仍按原比例")
    void 调整分成比例不影响历史分账() {
        commissionService.setDefault(3000);
        priceService.setPrice(TALENT_A, spec30.getId(), 10000, true);
        PriceQuote history = pricingService.quote(TALENT_A, spec30.getId(), 1);

        assertThat(history.commissionRateBp()).isEqualTo(3000);
        assertThat(history.guildCents()).isEqualTo(3000);
        assertThat(history.talentCents()).isEqualTo(7000);

        // 运营修改该主播的分成比例
        commissionService.setOverride(TALENT_A, 5000);

        // 历史订单的分账结果保持创建时的比例不变
        assertThat(history.commissionRateBp()).isEqualTo(3000);
        assertThat(history.guildCents()).isEqualTo(3000);
        assertThat(history.talentCents()).isEqualTo(7000);

        // 新订单按新比例
        PriceQuote fresh = pricingService.quote(TALENT_A, spec30.getId(), 1);
        assertThat(fresh.commissionRateBp()).isEqualTo(5000);
        assertThat(fresh.guildCents()).isEqualTo(5000);
    }

    @Test
    @DisplayName("Scenario: 订单展示所用取值 — 展示创建时固定的单价与比例，而非当前配置")
    void 订单展示所用取值为快照而非当前配置() {
        priceService.setPrice(TALENT_A, spec30.getId(), 1500, true);
        commissionService.setDefault(3000);
        PriceQuote snapshot = pricingService.quote(TALENT_A, spec30.getId(), 1);

        // 配置全部改掉
        priceService.setPrice(TALENT_A, spec30.getId(), 7777, true);
        commissionService.setOverride(TALENT_A, 6000);
        item.setName("情绪疏导");
        itemMapper.updateById(item);

        // 快照里的每一项都还是创建时的值
        assertThat(snapshot.unitPriceCents()).isEqualTo(1500);
        assertThat(snapshot.commissionRateBp()).isEqualTo(3000);
        assertThat(snapshot.serviceItemName()).isEqualTo("情感倾听");

        // 当前配置确实已经变了，证明上面的不变不是因为没改成功
        assertThat(priceService.find(TALENT_A, spec30.getId()).getUnitPriceCents()).isEqualTo(7777);
        assertThat(pricingService.resolveCommissionRateBp(TALENT_A)).isEqualTo(6000);
        assertThat(itemMapper.selectById(item.getId()).getName()).isEqualTo("情绪疏导");
    }

    // ===== 不变式 =====

    @Test
    @DisplayName("任何快照的分账两项之和恒等于应付金额")
    void 快照分账之和恒等于应付金额() {
        int[] rates = {0, 1, 1234, 3333, 6667, 9999, 10000};
        long[] prices = {1, 7, 99, 1500, 3333, 19999, 123456};

        for (int rateBp : rates) {
            commissionService.setOverride(TALENT_A, rateBp);
            for (long price : prices) {
                priceService.setPrice(TALENT_A, spec30.getId(), price, true);
                for (int qty : new int[]{1, 2, 3, 7}) {
                    PriceQuote q = pricingService.quote(TALENT_A, spec30.getId(), qty);
                    assertThat(q.guildCents() + q.talentCents())
                            .as("比例 %d 单价 %d 数量 %d", rateBp, price, qty)
                            .isEqualTo(q.payableCents());
                    assertThat(q.payableCents()).isEqualTo(price * qty);
                }
            }
        }
    }

    @Test
    @DisplayName("全局默认分成缺失时取价明确失败，而不是悄悄按零分成成单")
    void 全局默认缺失时取价失败() {
        priceService.setPrice(TALENT_A, spec30.getId(), 1500, true);
        commissionMapper.delete(Wrappers.<CommissionConfig>lambdaQuery()
                .isNull(CommissionConfig::getTalentId));

        assertThatThrownBy(() -> pricingService.quote(TALENT_A, spec30.getId(), 1))
                .isInstanceOf(PricingException.class)
                .hasMessageContaining("全局默认分成比例");
    }
}
