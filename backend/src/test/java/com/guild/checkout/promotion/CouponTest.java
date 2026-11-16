package com.guild.checkout.promotion;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.order.OrderCreateCommand;
import com.guild.checkout.order.OrderService;
import com.guild.checkout.order.OrderStatus;
import com.guild.checkout.order.ServiceOrder;
import com.guild.checkout.order.ServiceOrderMapper;
import com.guild.checkout.pricing.ServiceItem;
import com.guild.checkout.pricing.ServiceSpec;
import com.guild.checkout.pricing.admin.CommissionAdminService;
import com.guild.checkout.pricing.admin.ServiceCatalogAdminService;
import com.guild.checkout.pricing.admin.TalentPriceAdminService;
import com.guild.checkout.settlement.SettlementEntry;
import com.guild.checkout.settlement.SettlementService;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.admin.TalentAdminService;
import com.guild.checkout.user.AppUser;
import com.guild.checkout.user.AppUserService;
import com.guild.checkout.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 优惠券与券包（tasks 7.1 - 7.9）。
 *
 * 并发重复领取单独放在 {@link CouponConcurrencyTest}：它必须在真实提交下跑。
 */
@SpringBootTest
@Transactional
class CouponTest {

    @Autowired private CouponService couponService;
    @Autowired private UserCouponMapper userCouponMapper;
    @Autowired private OrderService orderService;
    @Autowired private ServiceOrderMapper orderMapper;
    @Autowired private SettlementService settlementService;
    @Autowired private com.guild.checkout.settlement.SettlementReportService reportService;
    @Autowired private AppUserService userService;
    @Autowired private TalentAdminService talentAdminService;
    @Autowired private ServiceCatalogAdminService catalogService;
    @Autowired private TalentPriceAdminService priceService;
    @Autowired private CommissionAdminService commissionService;
    @Autowired private JdbcTemplate jdbc;

    private long fanId;
    private long operatorId;
    private long talentId;
    private long talentUserId;
    private long specId;
    private long itemId;

    @BeforeEach
    void setUp() {
        commissionService.setDefault(3000);
        fanId = newUser("cf_").getId();
        operatorId = newUser("cp_").getId();
        userService.grantRole(operatorId, UserRole.OPERATOR);

        ServiceItem item = catalogService.createItem("cc_" + System.nanoTime(), "情感倾听", null, 0);
        itemId = item.getId();
        ServiceSpec spec = catalogService.createSpec(itemId, "m30", "30分钟", 30, 30);
        specId = spec.getId();

        Talent talent = talentAdminService.create(new TalentAdminService.ProfileForm(
                "用券测试主播", null, null, 0, null, null, null, null));
        talentAdminService.setListed(talent.getId(), true);
        talentId = talent.getId();
        // 单价 128 元，正好是规格里举的例子
        priceService.setPrice(talentId, specId, 12800, true);

        AppUser talentUser = newUser("ct_");
        talentAdminService.linkUser(talentId, talentUser.getId());
        talentUserId = talentUser.getId();
    }

    private AppUser newUser(String prefix) {
        return userService.registerOrUpdate(prefix + System.nanoTime(), null, "测试用户", null);
    }

    /** 满 100 减 20，当前有效。 */
    private Coupon coupon100minus20() {
        return couponService.create(new CouponService.CouponForm(
                "满 100 减 20", 2000L, 10000L, null,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30)));
    }

    private long claimed(Coupon coupon) {
        return couponService.claim(fanId, coupon.getId()).getId();
    }

    private ServiceOrder createOrder(Long userCouponId) {
        return orderService.create(new OrderCreateCommand(
                fanId, talentId, specId, 1, "wxid_abc", null, userCouponId));
    }

    // ===== 7.1 / 7.2 定义与发放 =====

    @Test
    @DisplayName("7.1 创建面额高于门槛的券被拒绝")
    void 面额高于门槛被拒绝() {
        assertThatThrownBy(() -> couponService.create(new CouponService.CouponForm(
                "面额离谱", 12000L, 10000L, null,
                LocalDateTime.now(), LocalDateTime.now().plusDays(1))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("面额不得超过门槛");
    }

    @Test
    @DisplayName("7.1 面额等于门槛是允许的（满 20 减 20）")
    void 面额等于门槛允许() {
        Coupon c = couponService.create(new CouponService.CouponForm(
                "满 20 减 20", 2000L, 2000L, null,
                LocalDateTime.now(), LocalDateTime.now().plusDays(1)));
        assertThat(c.getId()).isNotNull();
    }

    @Test
    @DisplayName("7.1 数据库约束兜底：绕过服务层直插面额高于门槛的券被拒绝")
    void 面额约束在数据库层也生效() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO coupon (name, amount_cents, threshold_cents, valid_from, valid_to) "
                        + "VALUES ('绕过', 5000, 1000, NOW(3), DATE_ADD(NOW(3), INTERVAL 1 DAY))"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class)
                .hasMessageContaining("ck_coupon_amount");
    }

    @Test
    @DisplayName("7.2 向全体发放后粉丝券包中可见该券及门槛与有效期")
    void 全体发放后券包可见() {
        Coupon c = coupon100minus20();
        couponService.grantToAll(c.getId());

        List<CouponService.OwnedCoupon> wallet = couponService.wallet(fanId);
        CouponService.OwnedCoupon mine = wallet.stream()
                .filter(o -> o.coupon().getId().equals(c.getId())).findFirst().orElseThrow();

        assertThat(mine.coupon().getThresholdCents()).isEqualTo(10000);
        assertThat(mine.coupon().getAmountCents()).isEqualTo(2000);
        // 与库里的模板比，而不是与内存里那个对象比：
        // DATETIME(3) 只存到毫秒，LocalDateTime.now() 是微秒，直接比会差在小数位上
        assertThat(mine.owned().getExpiresAt()).isEqualTo(couponService.require(c.getId()).getValidTo());
        assertThat(mine.owned().getStatus()).isEqualTo(UserCoupon.AVAILABLE);
    }

    @Test
    @DisplayName("7.2 重复向全体发放不会发出第二张")
    void 重复发放不产生第二张() {
        Coupon c = coupon100minus20();
        couponService.grantToAll(c.getId());
        couponService.grantToAll(c.getId());

        assertThat(userCouponMapper.selectCount(Wrappers.<UserCoupon>lambdaQuery()
                .eq(UserCoupon::getCouponId, c.getId())
                .eq(UserCoupon::getUserId, fanId))).isEqualTo(1);
    }

    @Test
    @DisplayName("7.2 发放时快照有效期：事后改模板不影响已发出的券")
    void 有效期发放时快照() {
        Coupon c = coupon100minus20();
        claimed(c);
        LocalDateTime original = couponService.require(c.getId()).getValidTo();

        jdbc.update("UPDATE coupon SET valid_to = ? WHERE id = ?",
                original.plusDays(365), c.getId());

        UserCoupon owned = userCouponMapper.selectOne(Wrappers.<UserCoupon>lambdaQuery()
                .eq(UserCoupon::getCouponId, c.getId()).eq(UserCoupon::getUserId, fanId));
        assertThat(owned.getExpiresAt()).isEqualTo(original);
    }

    // ===== 7.3 券包三态与领取幂等 =====

    @Test
    @DisplayName("7.3 重复领取同一张券被拒绝且券包中只有一张")
    void 重复领取被拒绝() {
        Coupon c = coupon100minus20();
        couponService.claim(fanId, c.getId());

        assertThatThrownBy(() -> couponService.claim(fanId, c.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已领取过");

        assertThat(userCouponMapper.selectCount(Wrappers.<UserCoupon>lambdaQuery()
                .eq(UserCoupon::getCouponId, c.getId())
                .eq(UserCoupon::getUserId, fanId))).isEqualTo(1);
    }

    @Test
    @DisplayName("7.3 过期的券在读券包时落成已过期")
    void 过期券在读时落态() {
        Coupon c = coupon100minus20();
        long ucId = claimed(c);
        jdbc.update("UPDATE user_coupon SET expires_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(1), ucId);

        couponService.wallet(fanId);

        assertThat(userCouponMapper.selectById(ucId).getStatus()).isEqualTo(UserCoupon.EXPIRED);
    }

    @Test
    @DisplayName("7.3 三态齐全：可用、已使用、已过期各归各栏")
    void 三态分栏() {
        Coupon available = coupon100minus20();
        claimed(available);

        Coupon expired = couponService.create(new CouponService.CouponForm(
                "快过期的", 1000L, 5000L, null,
                LocalDateTime.now().minusDays(2), LocalDateTime.now().plusSeconds(5)));
        long expiredId = claimed(expired);
        jdbc.update("UPDATE user_coupon SET expires_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(1), expiredId);

        Coupon used = couponService.create(new CouponService.CouponForm(
                "会被用掉的", 2000L, 10000L, null,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30)));
        long usedId = claimed(used);
        ServiceOrder order = createOrder(usedId);
        orderService.confirmPayment(order.getId(), order.getPayableCents(), operatorId, null);

        var buckets = couponService.wallet(fanId).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        o -> CouponController.bucketOf(o.owned().getStatus()),
                        java.util.stream.Collectors.counting()));

        assertThat(buckets).containsEntry("AVAILABLE", 1L)
                .containsEntry("USED", 1L)
                .containsEntry("EXPIRED", 1L);
    }

    @Test
    @DisplayName("7.3 被未支付订单占用的券归入「已使用」而不是「可用」")
    void 占用中的券不算可用() {
        Coupon c = coupon100minus20();
        long ucId = claimed(c);
        createOrder(ucId);

        UserCoupon owned = userCouponMapper.selectById(ucId);
        assertThat(owned.getStatus()).isEqualTo(UserCoupon.LOCKED);
        assertThat(CouponController.bucketOf(owned.getStatus())).isEqualTo("USED");
        assertThat(couponService.usableFor(fanId, 99999, itemId)).isEmpty();
    }

    // ===== 7.4 订单原价与抵扣字段 =====

    @Test
    @DisplayName("7.4 不用券的订单：原价等于应付、抵扣为 0")
    void 不用券时原价等于应付() {
        ServiceOrder order = createOrder(null);

        assertThat(order.getOriginalCents()).isEqualTo(12800);
        assertThat(order.getCouponDiscountCents()).isZero();
        assertThat(order.getPayableCents()).isEqualTo(12800);
        assertThat(order.getUserCouponId()).isNull();
    }

    @Test
    @DisplayName("7.4 迁移回填：库中不存在原价为 0 的历史订单")
    void 历史订单已回填原价() {
        Long broken = jdbc.queryForObject(
                "SELECT COUNT(*) FROM service_order WHERE original_cents = 0 AND payable_cents > 0",
                Long.class);
        assertThat(broken).isZero();

        // 回填口径：历史订单没有券，原价必须等于应付
        Long mismatched = jdbc.queryForObject(
                "SELECT COUNT(*) FROM service_order "
                        + "WHERE coupon_discount_cents = 0 AND original_cents <> payable_cents",
                Long.class);
        assertThat(mismatched).isZero();
    }

    // ===== 7.5 下单用券 =====

    @Test
    @DisplayName("7.5 原价 128 用满 100 减 20 的券，实付 108，订单同时记录 128 与 20")
    void 用券下单金额正确() {
        Coupon c = coupon100minus20();
        ServiceOrder order = createOrder(claimed(c));

        assertThat(order.getOriginalCents()).isEqualTo(12800);
        assertThat(order.getCouponDiscountCents()).isEqualTo(2000);
        assertThat(order.getPayableCents()).isEqualTo(10800);
    }

    @Test
    @DisplayName("7.5 单笔限一张：同一张券不能同时用于第二笔订单")
    void 单笔限一张() {
        Coupon c = coupon100minus20();
        long ucId = claimed(c);
        createOrder(ucId);

        assertThatThrownBy(() -> createOrder(ucId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("占用");
    }

    @Test
    @DisplayName("7.5 唯一索引兜底：一笔订单上挂不了第二张券")
    void 一单一券在数据库层兜底() {
        Coupon first = coupon100minus20();
        Coupon second = couponService.create(new CouponService.CouponForm(
                "另一张", 1000L, 5000L, null,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30)));
        long secondId = claimed(second);
        ServiceOrder order = createOrder(claimed(first));

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE user_coupon SET order_id = ? WHERE id = ?", order.getId(), secondId))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test
    @DisplayName("7.5 别人的券用不了")
    void 不能用别人的券() {
        Coupon c = coupon100minus20();
        long otherFan = newUser("co_").getId();
        couponService.grantTo(c.getId(), List.of(otherFan));
        UserCoupon theirs = userCouponMapper.selectOne(Wrappers.<UserCoupon>lambdaQuery()
                .eq(UserCoupon::getCouponId, c.getId()).eq(UserCoupon::getUserId, otherFan));

        assertThatThrownBy(() -> createOrder(theirs.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于你");
    }

    // ===== 7.6 分成基于原价 =====

    @Test
    @DisplayName("7.6 用券与不用券的两笔订单，主播分成完全一致")
    void 分成不受券影响() {
        ServiceOrder noCoupon = createOrder(null);

        Coupon c = coupon100minus20();
        ServiceOrder withCoupon = createOrder(claimed(c));

        assertThat(withCoupon.getTalentCents()).isEqualTo(noCoupon.getTalentCents());
        assertThat(withCoupon.getGuildCents()).isEqualTo(noCoupon.getGuildCents());
        assertThat(withCoupon.getCommissionRateBp()).isEqualTo(noCoupon.getCommissionRateBp());
        // 分成之和仍等于原价，而不是实付
        assertThat(withCoupon.getGuildCents() + withCoupon.getTalentCents())
                .isEqualTo(withCoupon.getOriginalCents());
    }

    @Test
    @DisplayName("7.6 台账按原价记账，促销成本单列")
    void 台账按原价记账() {
        Coupon c = coupon100minus20();
        ServiceOrder order = completed(createOrder(claimed(c)));

        SettlementEntry entry = settlementService.findBySource(
                SettlementEntry.SOURCE_ORDER, order.getId());
        assertThat(entry.getAmountCents()).isEqualTo(12800);
        assertThat(entry.getPromoCostCents()).isEqualTo(2000);
        assertThat(entry.getGuildCents() + entry.getTalentCents()).isEqualTo(12800);
        // 70% 归主播
        assertThat(entry.getTalentCents()).isEqualTo(8960);
    }

    // ===== 7.7 门槛与有效期 =====

    @Test
    @DisplayName("7.7 对 68 元订单使用满 100 减 20 的券被拒绝")
    void 未达门槛被拒绝() {
        priceService.setPrice(talentId, specId, 6800, true);
        Coupon c = coupon100minus20();
        long ucId = claimed(c);

        assertThatThrownBy(() -> createOrder(ucId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未达使用门槛");
    }

    @Test
    @DisplayName("7.7 使用已过期的券被拒绝")
    void 过期券被拒绝() {
        Coupon c = coupon100minus20();
        long ucId = claimed(c);
        jdbc.update("UPDATE user_coupon SET expires_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(1), ucId);

        assertThatThrownBy(() -> createOrder(ucId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已过期");
    }

    @Test
    @DisplayName("7.7 使用尚未生效的券被拒绝")
    void 未生效券被拒绝() {
        Coupon c = couponService.create(new CouponService.CouponForm(
                "下周才开始", 2000L, 10000L, null,
                LocalDateTime.now().plusDays(7), LocalDateTime.now().plusDays(14)));
        long ucId = claimed(c);

        assertThatThrownBy(() -> createOrder(ucId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未生效");
    }

    @Test
    @DisplayName("7.7 限定品类的券用于其他品类被拒绝")
    void 品类不符被拒绝() {
        ServiceItem other = catalogService.createItem("co_" + System.nanoTime(), "哄睡", null, 0);
        Coupon c = couponService.create(new CouponService.CouponForm(
                "仅限哄睡", 2000L, 10000L, other.getId(),
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30)));
        long ucId = claimed(c);

        assertThatThrownBy(() -> createOrder(ucId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不适用");
    }

    @Test
    @DisplayName("7.7 门槛按原价判定，与是否用过别的券无关")
    void 门槛按原价判定() {
        Coupon c = coupon100minus20();
        // 128 >= 100，可用
        assertThat(couponService.usableFor(fanId, 12800, itemId)).isEmpty();
        claimed(c);
        assertThat(couponService.usableFor(fanId, 12800, itemId)).hasSize(1);
        assertThat(couponService.usableFor(fanId, 9999, itemId)).isEmpty();
    }

    // ===== 7.8 核销与退回 =====

    @Test
    @DisplayName("7.8 支付确认后券变为已使用")
    void 支付确认后核销() {
        Coupon c = coupon100minus20();
        long ucId = claimed(c);
        ServiceOrder order = createOrder(ucId);

        orderService.confirmPayment(order.getId(), order.getPayableCents(), operatorId, null);

        UserCoupon owned = userCouponMapper.selectById(ucId);
        assertThat(owned.getStatus()).isEqualTo(UserCoupon.USED);
        assertThat(owned.getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("7.8 退款后未过期的券退回券包且恢复为可用")
    void 退款后券退回() {
        Coupon c = coupon100minus20();
        long ucId = claimed(c);
        ServiceOrder order = completed(createOrder(ucId));

        orderService.markRefunded(order.getId(), operatorId, "粉丝投诉");

        UserCoupon owned = userCouponMapper.selectById(ucId);
        assertThat(owned.getStatus()).isEqualTo(UserCoupon.AVAILABLE);
        assertThat(owned.getOrderId()).isNull();
        assertThat(owned.getUsedAt()).isNull();
        assertThat(owned.getRemark()).contains("退回");
    }

    @Test
    @DisplayName("7.8 退款时券已过期则不退回，并记录未退回的原因")
    void 过期券退款时不退回() {
        Coupon c = coupon100minus20();
        long ucId = claimed(c);
        ServiceOrder order = completed(createOrder(ucId));

        // 服务期间券过了有效期
        jdbc.update("UPDATE user_coupon SET expires_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(1), ucId);

        orderService.markRefunded(order.getId(), operatorId, "粉丝投诉");

        UserCoupon owned = userCouponMapper.selectById(ucId);
        assertThat(owned.getStatus()).isEqualTo(UserCoupon.EXPIRED);
        assertThat(owned.getRemark()).contains("已过有效期").contains("未退回");
    }

    @Test
    @DisplayName("7.8 运营取消订单后券退回")
    void 取消后券退回() {
        Coupon c = coupon100minus20();
        long ucId = claimed(c);
        ServiceOrder order = createOrder(ucId);

        orderService.cancel(order.getId(), operatorId, "粉丝要求取消");

        assertThat(userCouponMapper.selectById(ucId).getStatus()).isEqualTo(UserCoupon.AVAILABLE);
    }

    @Test
    @DisplayName("7.8 退回的券可以再次用于新订单")
    void 退回的券可再用() {
        Coupon c = coupon100minus20();
        long ucId = claimed(c);
        ServiceOrder first = createOrder(ucId);
        orderService.cancel(first.getId(), operatorId, "粉丝要求取消");

        ServiceOrder second = createOrder(ucId);
        assertThat(second.getPayableCents()).isEqualTo(10800);
    }

    @Test
    @DisplayName("7.8 订单完成后券保持已使用，不退回")
    void 完成后券不退回() {
        Coupon c = coupon100minus20();
        long ucId = claimed(c);
        completed(createOrder(ucId));

        assertThat(userCouponMapper.selectById(ucId).getStatus()).isEqualTo(UserCoupon.USED);
    }

    // ===== 7.9 报表促销成本 =====

    @Test
    @DisplayName("7.9 冲销后促销成本不再计入有效台账")
    void 冲销后不计促销成本() {
        Coupon c = coupon100minus20();
        ServiceOrder order = completed(createOrder(claimed(c)));
        orderService.markRefunded(order.getId(), operatorId, "粉丝投诉");

        SettlementEntry entry = settlementService.findBySource(
                SettlementEntry.SOURCE_ORDER, order.getId());
        assertThat(entry.getStatus()).isEqualTo(SettlementEntry.REVERSED);
    }

    // ---------- 辅助 ----------

    /** 走完支付 → 接单 → 完成。 */
    private ServiceOrder completed(ServiceOrder order) {
        orderService.confirmPayment(order.getId(), order.getPayableCents(), operatorId, null);
        orderService.accept(order.getId(), talentUserId);
        return orderService.complete(order.getId(), talentUserId);
    }

    @Test
    @DisplayName("7.9 已完成订单的台账促销成本合计等于订单抵扣合计")
    void 报表促销成本与订单抵扣一致() {
        Coupon c = coupon100minus20();
        ServiceOrder withCoupon = completed(createOrder(claimed(c)));
        ServiceOrder without = completed(createOrder(null));

        long entrySum = List.of(withCoupon, without).stream()
                .map(o -> settlementService.findBySource(SettlementEntry.SOURCE_ORDER, o.getId()))
                .mapToLong(e -> e.getPromoCostCents() == null ? 0 : e.getPromoCostCents())
                .sum();
        long orderSum = List.of(withCoupon, without).stream()
                .map(o -> orderMapper.selectById(o.getId()))
                .mapToLong(ServiceOrder::getCouponDiscountCents)
                .sum();

        assertThat(entrySum).isEqualTo(orderSum).isEqualTo(2000);
    }

    @Test
    @DisplayName("7.9 报表 CSV 单列促销成本，且该列数值与订单抵扣一致")
    void 报表CSV单列促销成本() {
        Coupon c = coupon100minus20();
        ServiceOrder order = completed(createOrder(claimed(c)));

        String period = order.getCompletedAt().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        String csv = new String(reportService.exportCsv(period), java.nio.charset.StandardCharsets.UTF_8);

        String[] lines = csv.split("\n");
        List<String> header = List.of(lines[0].replace("\uFEFF", "").trim().split(","));
        assertThat(header).contains("促销成本");
        int col = header.indexOf("促销成本");

        String row = java.util.Arrays.stream(lines)
                .filter(l -> l.startsWith(talentId + ","))
                .findFirst().orElseThrow();
        assertThat(row.split(",")[col]).isEqualTo("20.00");
    }

    @Test
    @DisplayName("7.9 逐笔明细 CSV 也带促销成本，便于与汇总核对")
    void 明细CSV带促销成本() {
        Coupon c = coupon100minus20();
        ServiceOrder order = completed(createOrder(claimed(c)));

        String period = order.getCompletedAt().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        String csv = new String(reportService.exportDetailCsv(period, talentId),
                java.nio.charset.StandardCharsets.UTF_8);

        List<String> header = List.of(csv.split("\n")[0].replace("\uFEFF", "").trim().split(","));
        assertThat(header).contains("促销成本");
        int col = header.indexOf("促销成本");

        String row = java.util.Arrays.stream(csv.split("\n"))
                .filter(l -> l.contains(order.getOrderRef()))
                .findFirst().orElseThrow();
        assertThat(row.split(",")[col]).isEqualTo("20.00");
    }

    @Test
    @DisplayName("7.9 没用券的订单促销成本为 0 而不是 null")
    void 无券订单促销成本为零() {
        ServiceOrder order = completed(createOrder(null));

        SettlementEntry entry = settlementService.findBySource(
                SettlementEntry.SOURCE_ORDER, order.getId());
        assertThat(entry.getPromoCostCents()).isZero();
    }

    @Test
    @DisplayName("7.5 订单视图同时透出原价、抵扣与实付")
    void 订单状态流转不受券影响() {
        Coupon c = coupon100minus20();
        ServiceOrder order = completed(createOrder(claimed(c)));

        assertThat(order.statusEnum()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(orderMapper.selectById(order.getId()).getPaidCents()).isEqualTo(10800);
    }
}
