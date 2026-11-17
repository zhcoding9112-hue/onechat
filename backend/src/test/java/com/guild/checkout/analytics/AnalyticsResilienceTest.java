package com.guild.checkout.analytics;

import com.guild.checkout.order.OrderCreateCommand;
import com.guild.checkout.order.OrderService;
import com.guild.checkout.order.ServiceOrder;
import com.guild.checkout.pricing.ServiceItem;
import com.guild.checkout.pricing.ServiceSpec;
import com.guild.checkout.pricing.admin.CommissionAdminService;
import com.guild.checkout.pricing.admin.ServiceCatalogAdminService;
import com.guild.checkout.pricing.ServiceItemMapper;
import com.guild.checkout.pricing.ServiceSpecMapper;
import com.guild.checkout.pricing.TalentPrice;
import com.guild.checkout.pricing.TalentPriceMapper;
import com.guild.checkout.pricing.admin.TalentPriceAdminService;
import com.guild.checkout.order.ServiceOrderMapper;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.TalentMapper;
import com.guild.checkout.talent.admin.TalentAdminService;
import com.guild.checkout.user.AppUserMapper;
import com.guild.checkout.user.AppUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 采集失败不影响业务（task 12.2）。
 *
 * 这条规格看着简单，实现上有个陷阱：若埋点与主事务共用一个事务，
 * 埋点抛异常会把主事务标记为 rollback-only，外层 catch 住也救不回来，
 * 下单最终仍会失败。这个测试就是为了把那种实现挡在门外。
 *
 * **本类刻意不加 @Transactional**：要验的就是真实提交下的事务传播行为，
 * 套一层测试事务就什么也验不到了。代价是造出来的数据不会回滚，必须自己清——
 * 这一点此前漏了，于是每跑一次测试就往开发库里漏四个**已上架**的主播和四个服务项目，
 * 日积月累到了一百多个，粉丝端首页和品类 Tab 全被它们占满。
 * 见 docs/launch-checklist.md。
 */
@SpringBootTest
class AnalyticsResilienceTest {

    @MockitoSpyBean
    private AnalyticsService analyticsService;

    @Autowired private OrderService orderService;
    @Autowired private AppUserService userService;
    @Autowired private TalentAdminService talentAdminService;
    @Autowired private ServiceCatalogAdminService catalogService;
    @Autowired private TalentPriceAdminService priceService;
    @Autowired private CommissionAdminService commissionService;
    @Autowired private AnalyticsEventMapper eventMapper;
    @Autowired private TalentMapper talentMapper;
    @Autowired private TalentPriceMapper priceMapper;
    @Autowired private ServiceItemMapper itemMapper;
    @Autowired private ServiceSpecMapper specMapper;
    @Autowired private ServiceOrderMapper orderMapper;
    @Autowired private AppUserMapper userMapper;

    private long fanId;
    private long talentId;
    private long specId;
    private long itemId;

    @BeforeEach
    void setUp() {
        Mockito.reset(analyticsService);
        commissionService.setDefault(3000);
        fanId = userService.registerOrUpdate("ar_" + System.nanoTime(), null, "粉丝", null).getId();

        ServiceItem item = catalogService.createItem("ar_" + System.nanoTime(), "情感倾听", null, 0);
        itemId = item.getId();
        ServiceSpec spec = catalogService.createSpec(item.getId(), "m30", "30分钟", 30, 30);
        specId = spec.getId();

        Talent talent = talentAdminService.create(new TalentAdminService.ProfileForm("小雨", null, null, 0, null, null, null, null));
        talentAdminService.setListed(talent.getId(), true);
        talentId = talent.getId();
        priceService.setPrice(talentId, specId, 1500, true);
    }

    /**
     * 自行清理。没有测试事务兜底，不清就是往开发库里漏数据。
     *
     * 顺序按外键方向来：订单 → 价格 → 主播 → 档位 → 项目 → 用户。
     */
    @AfterEach
    void tearDown() {
        orderMapper.delete(Wrappers.<ServiceOrder>lambdaQuery()
                .eq(ServiceOrder::getFanUserId, fanId));
        priceMapper.delete(Wrappers.<TalentPrice>lambdaQuery()
                .eq(TalentPrice::getTalentId, talentId));
        talentMapper.deleteById(talentId);
        specMapper.deleteById(specId);
        itemMapper.deleteById(itemId);
        userMapper.deleteById(fanId);
    }

    @Test
    @DisplayName("12.2 埋点写入抛异常时，下单请求仍成功返回")
    void 埋点异常不影响下单() {
        // 人为制造写入异常
        Mockito.doThrow(new RuntimeException("模拟埋点库挂了"))
                .when(analyticsService).writeInNewTransaction(Mockito.any());

        ServiceOrder order = orderService.create(new OrderCreateCommand(
                fanId, talentId, specId, 1, "wxid_test", null));

        assertThat(order.getId()).isNotNull();
        assertThat(order.getOrderRef()).hasSize(18);
        assertThat(order.getPayableCents()).isEqualTo(1500);
    }

    @Test
    @DisplayName("埋点异常时订单确实落库，不是只在内存里")
    void 埋点异常时订单真的落库() {
        Mockito.doThrow(new RuntimeException("模拟埋点库挂了"))
                .when(analyticsService).writeInNewTransaction(Mockito.any());

        ServiceOrder order = orderService.create(new OrderCreateCommand(
                fanId, talentId, specId, 1, "wxid_test", null));

        assertThat(orderService.findByRef(order.getOrderRef())).isNotNull();
    }

    @Test
    @DisplayName("埋点正常时事件确实写入")
    void 埋点正常时事件写入() {
        long before = countEvents();

        orderService.create(new OrderCreateCommand(
                fanId, talentId, specId, 1, "wxid_test", null));

        assertThat(countEvents()).isGreaterThan(before);
    }

    @Test
    @DisplayName("属性序列化失败不丢整条事件")
    void 属性异常不丢事件() {
        long before = countEvents();

        // 自引用的 Map 无法序列化
        java.util.Map<String, Object> bad = new java.util.HashMap<>();
        bad.put("self", bad);
        analyticsService.track(AnalyticsService.Track.of(EventKey.OPEN_H5).user(fanId).props(bad));

        assertThat(countEvents()).isEqualTo(before + 1);
    }

    private long countEvents() {
        Long n = eventMapper.selectCount(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.emptyWrapper());
        return n == null ? 0 : n;
    }
}
