package com.guild.checkout.acceptance;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guild.checkout.analytics.AnalyticsEventMapper;
import com.guild.checkout.auth.WechatAuthController;
import com.guild.checkout.credit.CreditService;
import com.guild.checkout.credit.CreditTransaction;
import com.guild.checkout.credit.CreditTransactionMapper;
import com.guild.checkout.order.ServiceOrder;
import com.guild.checkout.order.ServiceOrderMapper;
import com.guild.checkout.pricing.ServiceItem;
import com.guild.checkout.pricing.ServiceSpec;
import com.guild.checkout.pricing.admin.CommissionAdminService;
import com.guild.checkout.pricing.admin.ServiceCatalogAdminService;
import com.guild.checkout.pricing.admin.TalentPriceAdminService;
import com.guild.checkout.settlement.SettlementReportService;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.admin.TalentAdminService;
import com.guild.checkout.tipping.TipRecord;
import com.guild.checkout.tipping.TipRecordMapper;
import com.guild.checkout.user.AppUser;
import com.guild.checkout.user.AppUserService;
import com.guild.checkout.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 撮合平台改版的集成验收（add-marketplace-frontend-revamp，tasks 第 14 组）。
 *
 * 与同包的 {@link EndToEndAcceptanceTest} 区分开：那一份是上一个 change
 * （add-private-checkout-mvp）的第 14 组，两边的任务号各自对应各自的 tasks.md。
 *
 * 每条用例走通一条完整链路，全部经过真实 HTTP 接口，不走捷径直接调 service——
 * 捷径会绕过鉴权、参数校验与视图装配，而那些地方恰恰是单测覆盖不到的接缝。
 */
@SpringBootTest
@Transactional
class MarketplaceAcceptanceTest {

    @Autowired private WebApplicationContext context;
    @Autowired private AppUserService userService;
    @Autowired private TalentAdminService talentAdminService;
    @Autowired private ServiceCatalogAdminService catalogService;
    @Autowired private TalentPriceAdminService priceService;
    @Autowired private CommissionAdminService commissionService;
    @Autowired private ServiceOrderMapper orderMapper;
    @Autowired private AnalyticsEventMapper eventMapper;
    @Autowired private TipRecordMapper tipMapper;
    @Autowired private CreditService creditService;
    @Autowired private CreditTransactionMapper creditTransactionMapper;
    @Autowired private SettlementReportService reportService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private MockHttpSession fan;
    private MockHttpSession talentSession;
    private MockHttpSession operator;
    private long fanId;
    private long operatorId;
    private long talentId;
    private long talentUserId;
    private long specId;
    private long serviceItemId;
    private String categoryCode;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        commissionService.setDefault(3000);
        // 要按构造数据集手工核算，就得有确定的基线。随事务回滚，不影响开发库。
        eventMapper.delete(Wrappers.emptyWrapper());
        orderMapper.delete(Wrappers.emptyWrapper());
        tipMapper.delete(Wrappers.emptyWrapper());

        AppUser fanUser = newUser("mk_fan_");
        fanId = fanUser.getId();
        fan = sessionFor(fanUser);

        AppUser op = newUser("mk_op_");
        operatorId = op.getId();
        userService.grantRole(operatorId, UserRole.OPERATOR);
        operator = sessionFor(op);

        categoryCode = "mk_" + System.nanoTime();
        ServiceItem item = catalogService.createItem(categoryCode, "情感倾听", null, 0);
        serviceItemId = item.getId();
        ServiceSpec spec = catalogService.createSpec(item.getId(), "m30", "30分钟", 30, 30);
        specId = spec.getId();

        talentId = newTalent("验收主播");
        priceService.setPrice(talentId, specId, 10000, true);

        AppUser talentUser = newUser("mk_tl_");
        talentAdminService.linkUser(talentId, talentUser.getId());
        talentUserId = talentUser.getId();
        talentSession = sessionFor(talentUser);
    }

    // ===== 14.1 发现 → 筛选 → 详情 → 收藏 → 下单 → 用券 → 支付 → 接单 → 完成 =====

    @Test
    @DisplayName("14.1 全链路：原价、实付、抵扣、分成逐环节核对")
    void 发现到完成全链路() throws Exception {
        // ---------- 发现：首页品类 ----------
        JsonNode categories = dataOf(mockMvc.perform(
                get("/api/home/categories").session(fan)).andExpect(status().isOk()).andReturn());
        assertThat(textsOf(categories, "code")).contains(categoryCode);

        // ---------- 筛选：按品类取列表 ----------
        JsonNode feed = dataOf(mockMvc.perform(
                get("/api/talents?category=" + categoryCode).session(fan))
                .andExpect(status().isOk()).andReturn());
        assertThat(idsOf(feed.get("items"), "id")).contains(talentId);

        // ---------- 详情 ----------
        JsonNode detail = dataOf(mockMvc.perform(
                get("/api/talents/" + talentId + "?from=CATEGORY_TAB").session(fan))
                .andExpect(status().isOk()).andReturn());
        assertThat(detail.get("available").asBoolean()).isTrue();
        assertThat(detail.get("favorited").asBoolean()).isFalse();
        long unitPrice = detail.get("services").get(0).get("specs").get(0)
                .get("unitPriceCents").asLong();
        assertThat(unitPrice).isEqualTo(10000);

        // ---------- 收藏 ----------
        mockMvc.perform(post("/api/talents/" + talentId + "/favorite").session(fan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorited").value(true));
        assertThat(idsOf(dataOf(mockMvc.perform(
                get("/api/me/favorites").session(fan)).andReturn()), "id")).contains(talentId);
        // 详情页再读一次，收藏态要跟着变——两处读的是同一个事实
        assertThat(dataOf(mockMvc.perform(get("/api/talents/" + talentId).session(fan)).andReturn())
                .get("favorited").asBoolean()).isTrue();

        // ---------- 领券 ----------
        long couponId = dataOf(mockMvc.perform(post("/api/admin/coupons").session(operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"验收满减券","amountCents":2000,"thresholdCents":5000,
                                 "validFrom":"%s","validTo":"%s"}
                                """.formatted(
                                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7))))
                .andExpect(status().isOk()).andReturn()).get("id").asLong();

        mockMvc.perform(post("/api/coupons/" + couponId + "/claim").session(fan))
                .andExpect(status().isOk());

        JsonNode usable = dataOf(mockMvc.perform(
                get("/api/coupons/usable?originalCents=20000&serviceItemId=" + serviceItemId)
                        .session(fan)).andExpect(status().isOk()).andReturn());
        assertThat(usable).hasSize(1);
        long userCouponId = usable.get(0).get("userCouponId").asLong();

        // ---------- 下单：买两份，原价 20000，券减 2000，实付 18000 ----------
        JsonNode order = dataOf(mockMvc.perform(post("/api/orders").session(fan)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"talentId":%d,"serviceSpecId":%d,"quantity":2,
                                 "contact":"wxid_mk","remark":"晚上九点后",
                                 "userCouponId":%d,"entrySource":"CATEGORY_TAB"}
                                """.formatted(talentId, specId, userCouponId)))
                .andExpect(status().isOk()).andReturn());
        long orderId = order.get("id").asLong();

        assertThat(order.get("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(order.get("originalCents").asLong()).isEqualTo(20000);
        assertThat(order.get("couponDiscountCents").asLong()).isEqualTo(2000);
        assertThat(order.get("payableCents").asLong()).isEqualTo(18000);

        // 支付页认的是实付，不是原价
        assertThat(dataOf(mockMvc.perform(
                get("/api/orders/" + orderId + "/payment").session(fan)).andReturn())
                .get("payableCents").asLong()).isEqualTo(18000);

        // 券已被这一单占住，不能再用到第二单上
        assertThat(dataOf(mockMvc.perform(
                get("/api/coupons/usable?originalCents=20000&serviceItemId=" + serviceItemId)
                        .session(fan)).andReturn())).isEmpty();

        // ---------- 支付：运营按实付确认 ----------
        mockMvc.perform(put("/api/admin/payment/" + orderId + "/confirm").session(operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paidCents\":18000,\"externalRef\":\"bank-mk-001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_ACCEPT"));

        // ---------- 接单 → 完成 ----------
        mockMvc.perform(put("/api/talent/workspace/orders/" + orderId + "/accept")
                        .session(talentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));
        mockMvc.perform(put("/api/talent/workspace/orders/" + orderId + "/complete")
                        .session(talentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        // ---------- 分成：以原价为基数，券的钱由公会承担 ----------
        ServiceOrder stored = orderMapper.selectById(orderId);
        assertThat(stored.getPaidCents()).isEqualTo(18000);
        assertThat(stored.getCommissionRateBp()).isEqualTo(3000);
        // 20000 × 30% = 6000 给公会，14000 给主播；两者相加等于原价而不是实付
        assertThat(stored.getGuildCents()).isEqualTo(6000);
        assertThat(stored.getTalentCents()).isEqualTo(14000);
        assertThat(stored.getGuildCents() + stored.getTalentCents())
                .as("分成基数是原价，不是实付")
                .isEqualTo(stored.getOriginalCents());

        var row = reportService.monthly(YearMonth.now().toString()).stream()
                .filter(r -> r.talentId().equals(talentId)).findFirst().orElseThrow();
        assertThat(row.orderAmountCents()).isEqualTo(20000);
        assertThat(row.talentCents()).isEqualTo(14000);
        // 主播到手的钱一分不因为粉丝用了券而减少
        assertThat(row.talentCents()).isEqualTo(stored.getTalentCents());

        // ---------- 券已核销，不会回到可用列表 ----------
        JsonNode mine = dataOf(mockMvc.perform(get("/api/coupons/mine").session(fan)).andReturn());
        JsonNode used = firstWhere(mine, "userCouponId", userCouponId);
        // 券包返回的是给前端分栏的三态 bucket，不是库里的 status
        assertThat(used.get("bucket").asText()).isEqualTo("USED");
    }

    @Test
    @DisplayName("14.1 取消订单会把券退回，粉丝不会白损失一张")
    void 取消订单退券() throws Exception {
        long couponId = createCoupon("可退回的券", 1000, 5000);
        mockMvc.perform(post("/api/coupons/" + couponId + "/claim").session(fan))
                .andExpect(status().isOk());
        long userCouponId = dataOf(mockMvc.perform(
                get("/api/coupons/usable?originalCents=10000&serviceItemId=" + serviceItemId)
                        .session(fan)).andReturn()).get(0).get("userCouponId").asLong();

        long orderId = dataOf(mockMvc.perform(post("/api/orders").session(fan)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"talentId":%d,"serviceSpecId":%d,"quantity":1,
                                 "contact":"wxid_mk","userCouponId":%d}
                                """.formatted(talentId, specId, userCouponId)))
                .andReturn()).get("id").asLong();

        mockMvc.perform(put("/api/admin/orders/" + orderId + "/cancel").session(operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"粉丝改主意了\"}"))
                .andExpect(status().isOk());

        assertThat(dataOf(mockMvc.perform(
                get("/api/coupons/usable?originalCents=10000&serviceItemId=" + serviceItemId)
                        .session(fan)).andReturn()))
                .as("订单取消后券要回到可用列表")
                .hasSize(1);
    }

    // ===== 14.2 随机匹配 =====

    @Test
    @DisplayName("14.2 随机匹配不扣任何费用，由此下单的金额与直接下单完全一致")
    void 随机匹配链路() throws Exception {
        // 匹配前的元子余额与流水条数
        long balanceBefore = creditService.balanceOf(fanId);
        long ledgerBefore = creditTransactionMapper.selectCount(
                Wrappers.<CreditTransaction>lambdaQuery().eq(CreditTransaction::getUserId, fanId));

        // 连续匹配若干次：即使匹配本身要收费，也该在这里露出来
        List<Long> matched = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            JsonNode result = dataOf(mockMvc.perform(
                    get("/api/match/random?category=" + categoryCode).session(fan))
                    .andExpect(status().isOk()).andReturn());
            assertThat(result.get("matched").asBoolean()).isTrue();
            matched.add(result.get("talent").get("id").asLong());
        }
        assertThat(matched).containsOnly(talentId);

        // 没扣钱：余额没动、没产生任何流水、也没凭空多出订单
        assertThat(creditService.balanceOf(fanId))
                .as("匹配过程不得扣费")
                .isEqualTo(balanceBefore);
        assertThat(creditTransactionMapper.selectCount(
                Wrappers.<CreditTransaction>lambdaQuery().eq(CreditTransaction::getUserId, fanId)))
                .as("匹配过程不得产生元子流水")
                .isEqualTo(ledgerBefore);
        assertThat(orderMapper.selectCount(Wrappers.<ServiceOrder>lambdaQuery()
                .eq(ServiceOrder::getFanUserId, fanId)))
                .as("匹配过程不得生成订单")
                .isZero();

        // 由匹配结果下单，金额与走列表下单的那一单逐项一致
        JsonNode order = dataOf(mockMvc.perform(post("/api/orders").session(fan)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"talentId":%d,"serviceSpecId":%d,"quantity":1,
                                 "contact":"wxid_mk","entrySource":"RANDOM_MATCH"}
                                """.formatted(matched.get(0), specId)))
                .andExpect(status().isOk()).andReturn());

        assertThat(order.get("originalCents").asLong()).isEqualTo(10000);
        assertThat(order.get("couponDiscountCents").asLong()).isZero();
        assertThat(order.get("payableCents").asLong()).isEqualTo(10000);

        ServiceOrder stored = orderMapper.selectById(order.get("id").asLong());
        assertThat(stored.getGuildCents()).isEqualTo(3000);
        assertThat(stored.getTalentCents()).isEqualTo(7000);
        assertThat(stored.getEntrySource()).isEqualTo("RANDOM_MATCH");
    }

    @Test
    @DisplayName("14.2 匹配不到人时给空结果与说明，而不是报错")
    void 匹配不到人() throws Exception {
        talentAdminService.setListed(talentId, false);
        JsonNode result = dataOf(mockMvc.perform(
                get("/api/match/random?category=" + categoryCode).session(fan))
                .andExpect(status().isOk()).andReturn());
        assertThat(result.get("matched").asBoolean()).isFalse();
        assertThat(result.get("message").asText()).isNotBlank();
    }

    // ===== 14.3 打赏 → 榜单 → 跑马灯 → 退款 =====

    @Test
    @DisplayName("14.3 退款后榜单金额相应减少，且不再出现在跑马灯")
    void 打赏榜单退款链路() throws Exception {
        long keepId = createTip(6600);
        long refundId = createTip(13400);

        // 两笔都确认收款
        confirmTip(keepId, 6600);
        confirmTip(refundId, 13400);

        // 榜单：两笔合计
        assertThat(boardAmount("DAY", talentId)).isEqualTo(20000);
        assertThat(boardAmount("MONTH", talentId)).isEqualTo(20000);

        // 跑马灯：两笔都在，且不含任何打赏人信息
        JsonNode marquee = dataOf(mockMvc.perform(
                get("/api/home/tip-marquee").session(fan)).andExpect(status().isOk()).andReturn());
        assertThat(amountsOf(marquee)).contains(6600L, 13400L);
        marquee.forEach(item -> {
            assertThat(item.has("fanUserId")).isFalse();
            assertThat(item.has("fanNickname")).isFalse();
        });

        // ---------- 退款 ----------
        mockMvc.perform(put("/api/admin/tips/" + refundId + "/refund").session(operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"重复支付\"}"))
                .andExpect(status().isOk());

        // 榜单按原周期扣回，只剩未退的那笔
        assertThat(boardAmount("DAY", talentId))
                .as("退款后榜单应减去该笔金额")
                .isEqualTo(6600);
        assertThat(boardAmount("MONTH", talentId)).isEqualTo(6600);

        // 跑马灯不再出现这笔
        JsonNode after = dataOf(mockMvc.perform(
                get("/api/home/tip-marquee").session(fan)).andReturn());
        assertThat(amountsOf(after)).contains(6600L).doesNotContain(13400L);
    }

    @Test
    @DisplayName("14.3 未确认收款的打赏不计入榜单，也不上跑马灯")
    void 未确认的打赏不计入() throws Exception {
        createTip(9900);

        assertThat(boardAmount("DAY", talentId)).isZero();
        assertThat(amountsOf(dataOf(mockMvc.perform(
                get("/api/home/tip-marquee").session(fan)).andReturn())))
                .doesNotContain(9900L);
    }

    // ===== 14.4 动态发布 → 举报 → 下架 =====

    @Test
    @DisplayName("14.4 下架后粉丝不可见，运营仍能看到原文与处置记录")
    void 动态举报下架链路() throws Exception {
        // 主播发布
        long postId = dataOf(mockMvc.perform(post("/api/talent/feed/posts").session(talentSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"这条动态稍后会被下架","images":["/uploads/a.jpg"]}
                                """))
                .andExpect(status().isOk()).andReturn()).get("postId").asLong();

        // 粉丝在广场看得到，并评论一条
        assertThat(idsOf(dataOf(mockMvc.perform(
                get("/api/feed/posts").session(fan)).andReturn()).get("items"), "id"))
                .contains(postId);
        mockMvc.perform(post("/api/feed/posts/" + postId + "/comments").session(fan)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"这条评论也会跟着看不见\"}"))
                .andExpect(status().isOk());

        // ---------- 举报 ----------
        mockMvc.perform(post("/api/feed/posts/" + postId + "/report").session(fan)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"内容不适\"}"))
                .andExpect(status().isOk());

        JsonNode reports = dataOf(mockMvc.perform(
                get("/api/admin/feed/reports").session(operator)).andReturn());
        JsonNode report = firstWhere(reports, "targetId", postId);
        assertThat(report.get("status").asText()).isEqualTo("PENDING");

        // ---------- 运营处理：下架 ----------
        mockMvc.perform(put("/api/admin/feed/reports/" + report.get("id").asLong() + "/remove")
                        .session(operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"违反社区规范\"}"))
                .andExpect(status().isOk());

        // ---------- 粉丝侧：广场、详情、互动全部不可见 ----------
        assertThat(idsOf(dataOf(mockMvc.perform(
                get("/api/feed/posts").session(fan)).andReturn()).get("items"), "id"))
                .doesNotContain(postId);
        mockMvc.perform(get("/api/feed/posts/" + postId).session(fan))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/feed/posts/" + postId + "/like").session(fan))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/feed/posts/" + postId + "/comments").session(fan)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"还能评论吗\"}"))
                .andExpect(status().isNotFound());

        // ---------- 运营侧：原文、图片、处置记录都在 ----------
        JsonNode audit = dataOf(mockMvc.perform(
                get("/api/admin/feed/posts/" + postId).session(operator))
                .andExpect(status().isOk()).andReturn());
        assertThat(audit.get("status").asText()).isEqualTo("REMOVED");
        assertThat(audit.get("content").asText()).isEqualTo("这条动态稍后会被下架");
        assertThat(audit.get("images").get(0).asText()).isEqualTo("/uploads/a.jpg");
        assertThat(audit.get("removedBy").asLong()).isEqualTo(operatorId);
        assertThat(audit.get("removedAt").isNull()).isFalse();
        assertThat(audit.get("removeReason").asText()).isEqualTo("违反社区规范");

        // 评论原文同样留着，供追溯
        assertThat(dataOf(mockMvc.perform(
                get("/api/admin/feed/posts/" + postId + "/comments").session(operator)).andReturn())
                .get(0).get("content").asText()).isEqualTo("这条评论也会跟着看不见");

        // 举报已闭环，不再挂在待处理队列里
        assertThat(idsOf(dataOf(mockMvc.perform(
                get("/api/admin/feed/reports").session(operator)).andReturn()), "targetId"))
                .doesNotContain(postId);
    }

    // ===== 14.5 入口归因与手工核算 =====

    @Test
    @DisplayName("14.5 看板各入口数值与手工核算一致，且各行之和等于总漏斗")
    void 入口归因核对() throws Exception {
        long talentB = newTalent("验收主播乙");
        priceService.setPrice(talentB, specId, 10000, true);
        AppUser second = newUser("mk_f2_");
        AppUser third = newUser("mk_f3_");
        MockHttpSession fan2 = sessionFor(second);
        MockHttpSession fan3 = sessionFor(third);

        // ---------- 手工构造的数据集 ----------
        // 甲：人气店员进详情 → 下单 → 收款
        viewDetail(fan, talentId, "POPULAR");
        long a = createOrder(fan, talentId, "POPULAR");
        confirmOrder(a, 10000);

        // 乙：榜单进详情 → 下单，未收款
        viewDetail(fan2, talentId, "LEADERBOARD");
        createOrder(fan2, talentId, "LEADERBOARD");

        // 丙：分享链接直接进详情（无来源），只看不下单
        viewDetail(fan3, talentB, null);

        // 甲又从榜单进了一次详情：同一个人，不该在拆分里被算两次
        viewDetail(fan, talentB, "LEADERBOARD");

        // ---------- 手工核算 ----------
        // 进详情：甲、乙、丙 = 3 人；甲归最早的那次 → 人气店员
        // 提交订单：甲、乙 = 2 人
        // 确认收款：甲 = 1 人
        Map<String, long[]> expected = new HashMap<>();
        expected.put("POPULAR", new long[]{1, 1, 1});
        expected.put("LEADERBOARD", new long[]{1, 1, 0});
        expected.put("DIRECT", new long[]{1, 0, 0});

        JsonNode view = dataOf(mockMvc.perform(
                get("/api/admin/analytics/by-entry?from=" + LocalDate.now()
                        + "&to=" + LocalDate.now()).session(operator))
                .andExpect(status().isOk()).andReturn());

        List<String> steps = textsOf(view.get("steps"), "eventKey");
        assertThat(steps).containsExactly("VIEW_TALENT_DETAIL", "SUBMIT_ORDER", "CONFIRM_PAYMENT");

        // 逐入口与手工核算比对；没列进 expected 的入口一律应为全零
        JsonNode rows = view.get("rows");
        for (JsonNode row : rows) {
            String source = row.get("source").asText();
            long[] want = expected.getOrDefault(source, new long[]{0, 0, 0});
            for (int i = 0; i < steps.size(); i++) {
                assertThat(row.get("uvByKey").get(steps.get(i)).asLong())
                        .as("入口 %s 的 %s", source, steps.get(i))
                        .isEqualTo(want[i]);
            }
        }

        // 无数据的入口展示为零而不是不出现
        assertThat(textsOf(rows, "source")).contains("CRUSH_WALL", "FEED", "CHAT", "DIRECT");

        // ---------- 各行之和等于总漏斗 ----------
        JsonNode totals = view.get("totals");
        assertThat(totals.get("VIEW_TALENT_DETAIL").asLong()).isEqualTo(3);
        assertThat(totals.get("SUBMIT_ORDER").asLong()).isEqualTo(2);
        assertThat(totals.get("CONFIRM_PAYMENT").asLong()).isEqualTo(1);

        JsonNode funnel = dataOf(mockMvc.perform(
                get("/api/admin/analytics/funnel?from=" + LocalDate.now()
                        + "&to=" + LocalDate.now()).session(operator)).andReturn()).get("funnel");
        for (String key : steps) {
            long overall = firstWhere(funnel, "eventKey", key).get("uv").asLong();
            assertThat(totals.get(key).asLong())
                    .as("拆分各行之和应等于总漏斗的 %s", key)
                    .isEqualTo(overall);
        }
    }

    // ===== 辅助 =====

    private void viewDetail(MockHttpSession session, long talent, String entry) throws Exception {
        String body = entry == null
                ? "{\"eventKey\":\"VIEW_TALENT_DETAIL\",\"talentId\":%d}".formatted(talent)
                : "{\"eventKey\":\"VIEW_TALENT_DETAIL\",\"entrySource\":\"%s\",\"talentId\":%d}"
                        .formatted(entry, talent);
        mockMvc.perform(post("/api/analytics/track").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private long createOrder(MockHttpSession session, long talent, String entry) throws Exception {
        return dataOf(mockMvc.perform(post("/api/orders").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"talentId":%d,"serviceSpecId":%d,"quantity":1,
                                 "contact":"wxid_mk","entrySource":"%s"}
                                """.formatted(talent, specId, entry)))
                .andExpect(status().isOk()).andReturn()).get("id").asLong();
    }

    private void confirmOrder(long orderId, long paidCents) throws Exception {
        mockMvc.perform(put("/api/admin/payment/" + orderId + "/confirm").session(operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paidCents\":%d,\"externalRef\":\"bank-mk\"}".formatted(paidCents)))
                .andExpect(status().isOk());
    }

    private long createCoupon(String name, long amount, long threshold) throws Exception {
        return dataOf(mockMvc.perform(post("/api/admin/coupons").session(operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","amountCents":%d,"thresholdCents":%d,
                                 "validFrom":"%s","validTo":"%s"}
                                """.formatted(name, amount, threshold,
                                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7))))
                .andExpect(status().isOk()).andReturn()).get("id").asLong();
    }

    private long createTip(long amountCents) throws Exception {
        return dataOf(mockMvc.perform(post("/api/tips").session(fan)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"talentId\":%d,\"amountCents\":%d,\"message\":\"辛苦啦\"}"
                                .formatted(talentId, amountCents)))
                .andExpect(status().isOk()).andReturn()).get("id").asLong();
    }

    private void confirmTip(long tipId, long paidCents) throws Exception {
        mockMvc.perform(put("/api/admin/tips/" + tipId + "/confirm").session(operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paidCents\":%d,\"note\":\"已到账\"}".formatted(paidCents)))
                .andExpect(status().isOk());
    }

    /** 榜单上某主播的金额；不在榜上按 0 算。 */
    private long boardAmount(String type, long talent) throws Exception {
        JsonNode rows = dataOf(mockMvc.perform(
                get("/api/leaderboard?type=" + type).session(fan))
                .andExpect(status().isOk()).andReturn()).get("rows");
        for (JsonNode row : rows) {
            if (row.get("talentId").asLong() == talent) {
                return row.get("amountCents").asLong();
            }
        }
        return 0;
    }

    private long newTalent(String nickname) {
        Talent t = talentAdminService.create(new TalentAdminService.ProfileForm(
                nickname + System.nanoTime(), null, null, 0, null, null, null, null));
        talentAdminService.setListed(t.getId(), true);
        return t.getId();
    }

    private static List<Long> idsOf(JsonNode array, String field) {
        List<Long> ids = new ArrayList<>();
        array.forEach(n -> ids.add(n.get(field).asLong()));
        return ids;
    }

    private static List<String> textsOf(JsonNode array, String field) {
        List<String> values = new ArrayList<>();
        array.forEach(n -> values.add(n.get(field).asText()));
        return values;
    }

    private static List<Long> amountsOf(JsonNode array) {
        List<Long> values = new ArrayList<>();
        array.forEach(n -> values.add(n.get("amountCents").asLong()));
        return values;
    }

    private static JsonNode firstWhere(JsonNode array, String field, long value) {
        for (JsonNode n : array) {
            if (n.get(field).asLong() == value) {
                return n;
            }
        }
        throw new AssertionError("没有找到 " + field + "=" + value + " 的记录：" + array);
    }

    private static JsonNode firstWhere(JsonNode array, String field, String value) {
        for (JsonNode n : array) {
            if (value.equals(n.get(field).asText())) {
                return n;
            }
        }
        throw new AssertionError("没有找到 " + field + "=" + value + " 的记录：" + array);
    }

    private JsonNode dataOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private AppUser newUser(String prefix) {
        return userService.registerOrUpdate(prefix + System.nanoTime(), null, prefix, null);
    }

    private MockHttpSession sessionFor(AppUser user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(WechatAuthController.SESSION_USER_ID, user.getId());
        return session;
    }
}
