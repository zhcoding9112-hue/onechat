package com.guild.checkout.pricing;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 取价与快照。核心断言：快照产出后再改配置，快照结果不得变化。
 */
@SpringBootTest
@Transactional
class PricingServiceTest {

    @Autowired private PricingService pricingService;
    @Autowired private ServiceItemMapper itemMapper;
    @Autowired private ServiceSpecMapper specMapper;
    @Autowired private TalentPriceMapper priceMapper;
    @Autowired private CommissionConfigMapper commissionMapper;

    private static final long TALENT = 1001L;
    private static final long OTHER_TALENT = 1002L;

    private ServiceItem item;
    private ServiceSpec spec30;

    @BeforeEach
    void setUp() {
        item = newItem("listen_" + System.nanoTime(), "情感倾听", true);
        spec30 = newSpec(item.getId(), "m30", "30分钟", 30, true);
        newPrice(TALENT, spec30.getId(), 15_00L, true);
    }

    // ---------- 快照 ----------

    @Test
    void 取价返回完整快照() {
        PriceQuote quote = pricingService.quote(TALENT, spec30.getId(), 2);

        assertThat(quote.unitPriceCents()).isEqualTo(1500);
        assertThat(quote.quantity()).isEqualTo(2);
        assertThat(quote.payableCents()).isEqualTo(3000);
        assertThat(quote.commissionRateBp()).isEqualTo(3000);
        assertThat(quote.guildCents()).isEqualTo(900);
        assertThat(quote.talentCents()).isEqualTo(2100);
        assertThat(quote.guildCents() + quote.talentCents()).isEqualTo(quote.payableCents());
        // 名称也进快照：项目日后改名，历史订单仍显示当时的名称
        assertThat(quote.serviceItemName()).isEqualTo("情感倾听");
        assertThat(quote.serviceSpecName()).isEqualTo("30分钟");
    }

    @Test
    void 取价后上调价格快照结果不变() {
        PriceQuote before = pricingService.quote(TALENT, spec30.getId(), 1);

        updatePrice(TALENT, spec30.getId(), 99_00L);

        // 已产出的快照是不可变对象，不随配置变动
        assertThat(before.unitPriceCents()).isEqualTo(1500);
        assertThat(before.payableCents()).isEqualTo(1500);
        assertThat(before.guildCents()).isEqualTo(450);
        assertThat(before.talentCents()).isEqualTo(1050);

        // 之后的新订单按新价格
        PriceQuote after = pricingService.quote(TALENT, spec30.getId(), 1);
        assertThat(after.unitPriceCents()).isEqualTo(9900);
    }

    @Test
    void 取价后修改分成比例快照结果不变() {
        PriceQuote before = pricingService.quote(TALENT, spec30.getId(), 1);
        assertThat(before.commissionRateBp()).isEqualTo(3000);

        setTalentCommission(TALENT, 5000);

        assertThat(before.commissionRateBp()).isEqualTo(3000);
        assertThat(before.guildCents()).isEqualTo(450);

        PriceQuote after = pricingService.quote(TALENT, spec30.getId(), 1);
        assertThat(after.commissionRateBp()).isEqualTo(5000);
        assertThat(after.guildCents()).isEqualTo(750);
    }

    @Test
    void 项目改名不影响已产出快照中的名称() {
        PriceQuote before = pricingService.quote(TALENT, spec30.getId(), 1);

        item.setName("情绪疏导");
        itemMapper.updateById(item);

        assertThat(before.serviceItemName()).isEqualTo("情感倾听");
        assertThat(pricingService.quote(TALENT, spec30.getId(), 1).serviceItemName()).isEqualTo("情绪疏导");
    }

    // ---------- 分成比例取值 ----------

    @Test
    void 未覆写的主播用全局默认() {
        assertThat(pricingService.resolveCommissionRateBp(OTHER_TALENT)).isEqualTo(3000);
    }

    @Test
    void 按主播覆写优先于全局默认() {
        setTalentCommission(TALENT, 4500);

        assertThat(pricingService.resolveCommissionRateBp(TALENT)).isEqualTo(4500);
        // 其他主播不受影响
        assertThat(pricingService.resolveCommissionRateBp(OTHER_TALENT)).isEqualTo(3000);
    }

    @Test
    void 修改全局默认不影响已覆写的主播() {
        setTalentCommission(TALENT, 4500);
        CommissionConfig global = commissionMapper.selectOne(
                Wrappers.<CommissionConfig>lambdaQuery().isNull(CommissionConfig::getTalentId));
        global.setRateBp(1000);
        commissionMapper.updateById(global);

        assertThat(pricingService.resolveCommissionRateBp(TALENT)).isEqualTo(4500);
        assertThat(pricingService.resolveCommissionRateBp(OTHER_TALENT)).isEqualTo(1000);
    }

    // ---------- 不可购的情形 ----------

    @Test
    void 未配置价格的档位不可购() {
        ServiceSpec spec60 = newSpec(item.getId(), "m60", "60分钟", 60, true);

        assertThatThrownBy(() -> pricingService.quote(TALENT, spec60.getId(), 1))
                .isInstanceOf(PricingException.class)
                .hasMessageContaining("未开放");
    }

    @Test
    void 价格被停用后不可购() {
        TalentPrice price = priceMapper.selectOne(Wrappers.<TalentPrice>lambdaQuery()
                .eq(TalentPrice::getTalentId, TALENT).eq(TalentPrice::getServiceSpecId, spec30.getId()));
        price.setEnabled(false);
        priceMapper.updateById(price);

        assertThatThrownBy(() -> pricingService.quote(TALENT, spec30.getId(), 1))
                .isInstanceOf(PricingException.class);
    }

    @Test
    void 档位停用后不可购() {
        spec30.setEnabled(false);
        specMapper.updateById(spec30);

        assertThatThrownBy(() -> pricingService.quote(TALENT, spec30.getId(), 1))
                .isInstanceOf(PricingException.class)
                .hasMessageContaining("档位已停用");
    }

    @Test
    void 项目停用后其下档位不可购() {
        item.setEnabled(false);
        itemMapper.updateById(item);

        assertThatThrownBy(() -> pricingService.quote(TALENT, spec30.getId(), 1))
                .isInstanceOf(PricingException.class)
                .hasMessageContaining("项目已停用");
    }

    @Test
    void 不存在的档位被拒绝() {
        assertThatThrownBy(() -> pricingService.quote(TALENT, 99_999_999L, 1))
                .isInstanceOf(PricingException.class)
                .hasMessageContaining("档位不存在");
    }

    @Test
    void 数量必须大于零() {
        assertThatThrownBy(() -> pricingService.quote(TALENT, spec30.getId(), 0))
                .isInstanceOf(PricingException.class);
        assertThatThrownBy(() -> pricingService.quote(TALENT, spec30.getId(), -1))
                .isInstanceOf(PricingException.class);
    }

    // ---------- 可购列表 ----------

    @Test
    void 可购列表只含已配价且启用的档位() {
        ServiceSpec spec60 = newSpec(item.getId(), "m60", "60分钟", 60, true);
        ServiceSpec spec90 = newSpec(item.getId(), "m90", "90分钟", 90, true);
        newPrice(TALENT, spec60.getId(), 25_00L, true);
        newPrice(TALENT, spec90.getId(), 35_00L, false); // 已停用

        List<PricingService.PurchasableSpec> list = pricingService.listPurchasable(TALENT);

        assertThat(list).extracting(PricingService.PurchasableSpec::specName)
                .containsExactly("30分钟", "60分钟");
        assertThat(list).extracting(PricingService.PurchasableSpec::unitPriceCents)
                .containsExactly(1500L, 2500L);
    }

    @Test
    void 项目停用后其档位不出现在可购列表() {
        item.setEnabled(false);
        itemMapper.updateById(item);

        assertThat(pricingService.listPurchasable(TALENT)).isEmpty();
    }

    @Test
    void 未配置任何价格的主播可购列表为空而非报错() {
        assertThat(pricingService.listPurchasable(OTHER_TALENT)).isEmpty();
    }

    @Test
    void 不同主播看到各自的专属价格() {
        newPrice(OTHER_TALENT, spec30.getId(), 88_00L, true);

        assertThat(pricingService.quote(TALENT, spec30.getId(), 1).unitPriceCents()).isEqualTo(1500);
        assertThat(pricingService.quote(OTHER_TALENT, spec30.getId(), 1).unitPriceCents()).isEqualTo(8800);
    }

    // ---------- 辅助 ----------

    private ServiceItem newItem(String code, String name, boolean enabled) {
        ServiceItem it = new ServiceItem();
        it.setCode(code);
        it.setName(name);
        it.setSortOrder(0);
        it.setEnabled(enabled);
        itemMapper.insert(it);
        return it;
    }

    private ServiceSpec newSpec(Long itemId, String code, String name, Integer minutes, boolean enabled) {
        ServiceSpec sp = new ServiceSpec();
        sp.setServiceItemId(itemId);
        sp.setCode(code);
        sp.setName(name);
        sp.setDurationMinutes(minutes);
        sp.setSortOrder(minutes == null ? 0 : minutes);
        sp.setEnabled(enabled);
        specMapper.insert(sp);
        return sp;
    }

    private void newPrice(long talentId, Long specId, long cents, boolean enabled) {
        TalentPrice p = new TalentPrice();
        p.setTalentId(talentId);
        p.setServiceSpecId(specId);
        p.setUnitPriceCents(cents);
        p.setEnabled(enabled);
        priceMapper.insert(p);
    }

    private void updatePrice(long talentId, Long specId, long cents) {
        TalentPrice p = priceMapper.selectOne(Wrappers.<TalentPrice>lambdaQuery()
                .eq(TalentPrice::getTalentId, talentId).eq(TalentPrice::getServiceSpecId, specId));
        p.setUnitPriceCents(cents);
        priceMapper.updateById(p);
    }

    private void setTalentCommission(long talentId, int rateBp) {
        CommissionConfig existing = commissionMapper.selectOne(
                Wrappers.<CommissionConfig>lambdaQuery().eq(CommissionConfig::getTalentId, talentId));
        if (existing == null) {
            CommissionConfig cfg = new CommissionConfig();
            cfg.setTalentId(talentId);
            cfg.setRateBp(rateBp);
            commissionMapper.insert(cfg);
        } else {
            existing.setRateBp(rateBp);
            commissionMapper.updateById(existing);
        }
    }
}
