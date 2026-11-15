package com.guild.checkout.payment;

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
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.admin.TalentAdminService;
import com.guild.checkout.user.AppUser;
import com.guild.checkout.user.AppUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 支付抽象的边界（tasks 6.1 / 6.10）。
 *
 * 这些断言把 docs/payment-provider.md 的承诺钉在构建里：
 * 文档说"接入新支付方式不需要改订单模块"，就由测试来保证这句话不会悄悄失效。
 */
@SpringBootTest
@Transactional
class PaymentAbstractionTest {

    @Autowired private PaymentService paymentService;
    @Autowired private OrderService orderService;
    @Autowired private ServiceOrderMapper orderMapper;
    @Autowired private AppUserService userService;
    @Autowired private TalentAdminService talentAdminService;
    @Autowired private ServiceCatalogAdminService catalogService;
    @Autowired private TalentPriceAdminService priceService;
    @Autowired private CommissionAdminService commissionService;

    private long fanId;
    private long talentId;
    private long specId;

    @BeforeEach
    void setUp() {
        commissionService.setDefault(3000);
        AppUser fanUser = userService.registerOrUpdate("ab_" + System.nanoTime(), null, "粉丝", null);
        fanId = fanUser.getId();

        ServiceItem item = catalogService.createItem("abs_" + System.nanoTime(), "情感倾听", null, 0);
        ServiceSpec spec = catalogService.createSpec(item.getId(), "m30", "30分钟", 30, 30);
        specId = spec.getId();

        Talent talent = talentAdminService.create(new TalentAdminService.ProfileForm("小雨", null, null, 0, null, null, null, null));
        talentAdminService.setListed(talent.getId(), true);
        talentId = talent.getId();
        priceService.setPrice(talentId, specId, 1500, true);
    }

    /** 一个完全虚构的支付方式，代表"将来要接的任何一种"。 */
    private static final class StubProvider implements PaymentProvider {
        @Override public String code() { return "stub-pay"; }
        @Override public String displayName() { return "桩支付"; }
        @Override public PaymentInstruction instruct(String orderRef, long payableCents) {
            return new PaymentInstruction(code(), PaymentInstruction.Kind.INVOKE_SDK,
                    Map.of("orderRef", orderRef, "amount", payableCents));
        }
    }

    @Test
    @DisplayName("换一种支付方式，订单流转行为完全一致")
    void 换支付方式订单行为不变() {
        ServiceOrder order = newOrder();

        // 用一个与人工收款毫无关系的 provider 发事件
        PaymentService.ConfirmResult result = paymentService.confirm(new PaymentConfirmed(
                order.getOrderRef(), 1500, new StubProvider().code(),
                "stub-txn-0001", null, LocalDateTime.now(), null));

        assertThat(result.outcome()).isEqualTo(PaymentService.Outcome.ACCEPTED);
        ServiceOrder paid = orderMapper.selectById(order.getId());
        assertThat(paid.statusEnum()).isEqualTo(OrderStatus.PENDING_ACCEPT);
        assertThat(paid.getPaidAt()).isNotNull();
        assertThat(paid.getAcceptDeadline()).isNotNull();

        // 分账快照不受支付方式影响
        assertThat(paid.getGuildCents() + paid.getTalentCents()).isEqualTo(paid.getPayableCents());
    }

    @Test
    @DisplayName("异步回调式的收款事件没有操作者，同样能入账")
    void 无操作者的收款事件可入账() {
        ServiceOrder order = newOrder();

        PaymentService.ConfirmResult result = paymentService.confirm(new PaymentConfirmed(
                order.getOrderRef(), 1500, "stub-pay", "txn-async",
                null, LocalDateTime.now(), null));

        assertThat(result.outcome()).isEqualTo(PaymentService.Outcome.ACCEPTED);
        assertThat(paymentService.recordsOf(order.getId()).get(0).getOperatorId()).isNull();
    }

    @Test
    @DisplayName("订单模块不依赖 payment 包：接新支付方式无需改订单代码")
    void 订单模块不依赖支付包() throws IOException {
        Path orderDir = Path.of("src/main/java/com/guild/checkout/order");
        assertThat(Files.isDirectory(orderDir)).as("订单模块目录应存在").isTrue();

        try (Stream<Path> files = Files.walk(orderDir)) {
            List<String> offenders = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            return Files.readString(p, StandardCharsets.UTF_8)
                                    .contains("com.guild.checkout.payment");
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .map(Path::toString)
                    .toList();

            assertThat(offenders)
                    .as("订单模块出现了对 payment 包的依赖，抽象已被绕过，请看 docs/payment-provider.md")
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("支付端口与事件不含任何具体支付方式的词汇")
    void 端口保持中立() throws IOException {
        for (String file : List.of("PaymentProvider.java", "PaymentConfirmed.java",
                "PaymentInstruction.java")) {
            Path path = Path.of("src/main/java/com/guild/checkout/payment", file);
            // 只看代码本身，注释里举例说明是允许的
            String code = Files.readString(path, StandardCharsets.UTF_8)
                    .replaceAll("(?s)/\\*.*?\\*/", "")
                    .replaceAll("//[^\n]*", "");

            assertThat(code.toLowerCase())
                    .as("%s 的代码中不应出现具体支付方式的概念", file)
                    .doesNotContain("wechat")
                    .doesNotContain("weixin")
                    .doesNotContain("alipay")
                    .doesNotContain("qrcode")
                    .doesNotContain("jsapi");
        }
    }

    @Test
    @DisplayName("接入文档存在并覆盖关键改动点")
    void 接入文档完整() throws IOException {
        Path doc = Path.of("../docs/payment-provider.md");
        assertThat(Files.exists(doc)).as("docs/payment-provider.md 应存在").isTrue();

        String text = Files.readString(doc, StandardCharsets.UTF_8);
        assertThat(text)
                .contains("PaymentProvider")
                .contains("PaymentConfirmed")
                .contains("externalRef")
                .contains("payment_setting")
                .contains("订单模块不在改动范围内");
    }

    private ServiceOrder newOrder() {
        return orderService.create(new OrderCreateCommand(
                fanId, talentId, specId, 1, "wxid_test", null));
    }
}
