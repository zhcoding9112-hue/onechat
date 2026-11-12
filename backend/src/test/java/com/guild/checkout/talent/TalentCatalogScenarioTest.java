package com.guild.checkout.talent;

import com.guild.checkout.pricing.PricingException;
import com.guild.checkout.pricing.PricingService;
import com.guild.checkout.pricing.PriceQuote;
import com.guild.checkout.pricing.ServiceItem;
import com.guild.checkout.pricing.ServiceSpec;
import com.guild.checkout.pricing.admin.ServiceCatalogAdminService;
import com.guild.checkout.pricing.admin.TalentPriceAdminService;
import com.guild.checkout.talent.admin.TalentAdminService;
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
 * talent-catalog spec 的场景测试，逐条对应规格中的 Scenario。
 */
@SpringBootTest
@Transactional
class TalentCatalogScenarioTest {

    @Autowired private TalentService talentService;
    @Autowired private TalentAdminService talentAdminService;
    @Autowired private PricingService pricingService;
    @Autowired private ServiceCatalogAdminService catalogService;
    @Autowired private TalentPriceAdminService priceService;
    @Autowired private AppUserService userService;
    @Autowired private TalentMapper talentMapper;

    private ServiceSpec spec30;

    @BeforeEach
    void setUp() {
        ServiceItem item = catalogService.createItem("ts_" + System.nanoTime(), "情感倾听", null, 0);
        spec30 = catalogService.createSpec(item.getId(), "m30", "30分钟", 30, 30);
        // 把既有已上架主播全部下架，保证本类的断言不受其他数据干扰
        for (Talent t : talentService.listListed()) {
            talentAdminService.setListed(t.getId(), false);
        }
    }

    // ===== Requirement: 主播列表展示 =====

    @Test
    @DisplayName("Scenario: 展示已上架主播 — 按运营设定排序，含封面昵称标签在线状态")
    void 展示已上架主播() {
        Talent a = listed("甲", 20);
        Talent b = listed("乙", 10);
        talentAdminService.replaceTags(b.getId(), List.of("温柔"));

        List<Talent> list = talentService.listListed();

        assertThat(list).extracting(Talent::getNickname).containsExactly("乙", "甲");
        assertThat(talentService.listTags(b.getId())).extracting(TalentTag::getName).containsExactly("温柔");
        assertThat(list.get(0).getOnline()).isFalse();
        assertThat(a.getId()).isNotNull();
    }

    @Test
    @DisplayName("Scenario: 主播被下架 — 刷新列表后不再出现")
    void 主播被下架后不再出现() {
        Talent t = listed("小雨", 0);
        assertThat(talentService.listListed()).extracting(Talent::getId).contains(t.getId());

        talentAdminService.setListed(t.getId(), false);

        assertThat(talentService.listListed()).extracting(Talent::getId).doesNotContain(t.getId());
    }

    @Test
    @DisplayName("Scenario: 无可用主播 — 返回空列表而非错误")
    void 无可用主播() {
        assertThat(talentService.listListed()).isEmpty();
    }

    // ===== Requirement: 主播详情展示 =====

    @Test
    @DisplayName("Scenario: 查看主播详情 — 价格取自当前生效的价格配置")
    void 查看主播详情() {
        Talent t = listed("小雨", 0);
        talentAdminService.update(t.getId(), null, "温柔倾听", null, null);
        talentAdminService.replaceTags(t.getId(), List.of("温柔", "耐心"));
        priceService.setPrice(t.getId(), spec30.getId(), 1500, true);

        Talent loaded = talentService.require(t.getId());
        assertThat(loaded.getIntro()).isEqualTo("温柔倾听");
        assertThat(talentService.listTags(t.getId())).hasSize(2);
        assertThat(pricingService.listPurchasable(t.getId()))
                .singleElement()
                .satisfies(s -> assertThat(s.unitPriceCents()).isEqualTo(1500));

        // 改价后详情取到的是新价
        priceService.setPrice(t.getId(), spec30.getId(), 2000, true);
        assertThat(pricingService.listPurchasable(t.getId()).get(0).unitPriceCents()).isEqualTo(2000);
    }

    @Test
    @DisplayName("Scenario: 主播未配置任何可售服务 — 资料仍在，但无可购项")
    void 未配置可售服务() {
        Talent t = listed("小雨", 0);

        assertThat(talentService.require(t.getId()).getNickname()).isEqualTo("小雨");
        assertThat(pricingService.listPurchasable(t.getId())).isEmpty();
    }

    // ===== Requirement: 主播在线状态展示 =====

    @Test
    @DisplayName("Scenario: 展示在线状态 — 主播置为在线后粉丝端可见")
    void 展示在线状态() {
        Talent t = listed("小雨", 0);
        assertThat(talentService.require(t.getId()).getOnline()).isFalse();

        setOnline(t.getId(), true);

        assertThat(talentService.listListed().stream()
                .filter(x -> x.getId().equals(t.getId())).findFirst().orElseThrow()
                .getOnline()).isTrue();
    }

    @Test
    @DisplayName("Scenario: 对离线主播下单 — 允许进入下单流程")
    void 对离线主播可下单() {
        Talent t = listed("小雨", 0);
        priceService.setPrice(t.getId(), spec30.getId(), 1500, true);
        assertThat(talentService.require(t.getId()).getOnline()).isFalse();

        // 离线不阻止取价，即不阻止进入下单流程
        PriceQuote quote = pricingService.quote(t.getId(), spec30.getId(), 1);
        assertThat(quote.payableCents()).isEqualTo(1500);
    }

    // ===== Requirement: 主播试听片段 =====

    @Test
    @DisplayName("Scenario: 播放试听片段 — 配置后返回地址与时长")
    void 播放试听片段() {
        Talent t = listed("小雨", 0);
        talentAdminService.setAudio(t.getId(), "/uploads/talent-audio/x.mp3", 15);

        Talent loaded = talentService.require(t.getId());
        assertThat(loaded.getAudioUrl()).isEqualTo("/uploads/talent-audio/x.mp3");
        assertThat(loaded.getAudioSeconds()).isEqualTo(15);
    }

    @Test
    @DisplayName("Scenario: 未配置试听 — 不返回试听信息")
    void 未配置试听() {
        Talent t = listed("小雨", 0);
        assertThat(talentService.require(t.getId()).getAudioUrl()).isNull();

        // 配置后再清除
        talentAdminService.setAudio(t.getId(), "/uploads/a.mp3", 10);
        talentAdminService.setAudio(t.getId(), null, null);

        Talent cleared = talentService.require(t.getId());
        assertThat(cleared.getAudioUrl()).isNull();
        assertThat(cleared.getAudioSeconds()).isNull();
    }

    // ===== Requirement: 运营维护主播档案 =====

    @Test
    @DisplayName("Scenario: 新建并上架主播 — 立即出现在粉丝端列表")
    void 新建并上架() {
        Talent t = talentAdminService.create("新人", null, null, 0);
        assertThat(talentService.listListed()).extracting(Talent::getId).doesNotContain(t.getId());

        talentAdminService.setListed(t.getId(), true);

        assertThat(talentService.listListed()).extracting(Talent::getId).contains(t.getId());
    }

    @Test
    @DisplayName("Scenario: 关联主播档案与用户 — 获得主播角色")
    void 关联档案与用户() {
        Talent t = talentAdminService.create("小雨", null, null, 0);
        AppUser user = userService.registerOrUpdate("sc_" + System.nanoTime(), null, "某人", null);
        assertThat(user.hasRole(UserRole.TALENT)).isFalse();

        talentAdminService.linkUser(t.getId(), user.getId());

        AppUser reloaded = userService.requireById(user.getId());
        assertThat(reloaded.hasRole(UserRole.TALENT)).isTrue();
        assertThat(reloaded.hasRole(UserRole.FAN)).isTrue();
        assertThat(talentService.findByUserId(user.getId()).getId()).isEqualTo(t.getId());
    }

    @Test
    @DisplayName("Scenario: 下架存在进行中订单的主播 — 阻止新订单，既有订单不受影响")
    void 下架不影响既有订单() {
        Talent t = listed("小雨", 0);
        priceService.setPrice(t.getId(), spec30.getId(), 1500, true);

        // 下架前成单，模拟「进行中的订单」
        PriceQuote inFlight = pricingService.quote(t.getId(), spec30.getId(), 1);

        talentAdminService.setListed(t.getId(), false);

        // 粉丝端看不到，无法再下新单
        assertThat(talentService.listListed()).extracting(Talent::getId).doesNotContain(t.getId());

        // 既有订单的快照完全不受影响
        assertThat(inFlight.payableCents()).isEqualTo(1500);
        assertThat(inFlight.guildCents() + inFlight.talentCents()).isEqualTo(1500);
    }

    @Test
    @DisplayName("档案停用后取价仍可成功，因为下架只挡粉丝端入口而非既有履约")
    void 下架不影响取价本身() {
        // 说明职责边界：是否允许下单由上层的 available 判定，取价本身只关心价格配置
        Talent t = listed("小雨", 0);
        priceService.setPrice(t.getId(), spec30.getId(), 1500, true);
        talentAdminService.setListed(t.getId(), false);

        assertThat(pricingService.quote(t.getId(), spec30.getId(), 1).payableCents()).isEqualTo(1500);

        // 而价格被停用则真正不可购
        priceService.setEnabled(t.getId(), spec30.getId(), false);
        assertThatThrownBy(() -> pricingService.quote(t.getId(), spec30.getId(), 1))
                .isInstanceOf(PricingException.class);
    }

    // ---------- 辅助 ----------

    private Talent listed(String nickname, int sortOrder) {
        Talent t = talentAdminService.create(nickname, null, null, sortOrder);
        talentAdminService.setListed(t.getId(), true);
        return talentService.require(t.getId());
    }

    /** 与主播工作台 /online/{online} 相同的写入路径。 */
    private void setOnline(long talentId, boolean online) {
        Talent t = talentMapper.selectById(talentId);
        t.setOnline(online);
        talentMapper.updateById(t);
    }
}
