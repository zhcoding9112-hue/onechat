package com.guild.checkout.analytics;

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
import com.guild.checkout.user.AppUser;
import com.guild.checkout.user.AppUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 采集失败不影响业务（task 12.2）。
 *
 * 这条规格看着简单，实现上有个陷阱：若埋点与主事务共用一个事务，
 * 埋点抛异常会把主事务标记为 rollback-only，外层 catch 住也救不回来，
 * 下单最终仍会失败。这个测试就是为了把那种实现挡在门外。
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

    private long fanId;
    private long talentId;
    private long specId;

    @BeforeEach
    void setUp() {
        Mockito.reset(analyticsService);
        commissionService.setDefault(3000);
        fanId = userService.registerOrUpdate("ar_" + System.nanoTime(), null, "粉丝", null).getId();

        ServiceItem item = catalogService.createItem("ar_" + System.nanoTime(), "情感倾听", null, 0);
        ServiceSpec spec = catalogService.createSpec(item.getId(), "m30", "30分钟", 30, 30);
        specId = spec.getId();

        Talent talent = talentAdminService.create(new TalentAdminService.ProfileForm("小雨", null, null, 0, null, null, null, null));
        talentAdminService.setListed(talent.getId(), true);
        talentId = talent.getId();
        priceService.setPrice(talentId, specId, 1500, true);
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
