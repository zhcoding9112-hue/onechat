package com.guild.checkout.tipping.timeout;

import com.guild.checkout.pricing.ServiceItem;
import com.guild.checkout.pricing.ServiceSpec;
import com.guild.checkout.pricing.admin.CommissionAdminService;
import com.guild.checkout.pricing.admin.ServiceCatalogAdminService;
import com.guild.checkout.pricing.admin.TalentPriceAdminService;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.admin.TalentAdminService;
import com.guild.checkout.tipping.TipRecord;
import com.guild.checkout.tipping.TipRecordMapper;
import com.guild.checkout.tipping.TipService;
import com.guild.checkout.tipping.TipStatus;
import com.guild.checkout.user.AppUser;
import com.guild.checkout.user.AppUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 打赏的支付超时回收。
 *
 * 这段此前完全不存在：payDeadline 写了，但没有任何代码去关它。
 */
@SpringBootTest
@Transactional
class TipTimeoutTest {

    @Autowired private TipService tipService;
    @Autowired private TipRecordMapper tipMapper;
    @Autowired private TipTimeoutService timeoutService;
    @Autowired private AppUserService userService;
    @Autowired private TalentAdminService talentAdminService;
    @Autowired private ServiceCatalogAdminService catalogService;
    @Autowired private TalentPriceAdminService priceService;
    @Autowired private CommissionAdminService commissionService;

    private long fanId;
    private long talentId;

    @BeforeEach
    void setUp() {
        commissionService.setDefault(3000);
        tipService.updateSetting(100L, 200000L);

        fanId = newUser("tto_").getId();

        ServiceItem item = catalogService.createItem("tto_" + System.nanoTime(), "情感倾听", null, 0);
        ServiceSpec spec = catalogService.createSpec(item.getId(), "m30", "30分钟", 30, 30);
        Talent talent = talentAdminService.create(new TalentAdminService.ProfileForm(
                "超时打赏主播", null, null, 0, null, null, null, null));
        talentAdminService.setListed(talent.getId(), true);
        talentId = talent.getId();
        priceService.setPrice(talentId, spec.getId(), 1500, true);
    }

    @Test
    @DisplayName("到期未付款的打赏被自动关闭，原因写明是系统关的")
    void 到期自动关闭() {
        TipRecord tip = overdueTip();

        assertThat(timeoutService.expire(tip.getId()))
                .isEqualTo(TipTimeoutService.Result.HANDLED);

        TipRecord stored = tipMapper.selectById(tip.getId());
        assertThat(stored.statusEnum()).isEqualTo(TipStatus.CANCELLED);
        assertThat(stored.getCloseReason()).isEqualTo(TipTimeoutService.TIMEOUT_REASON);
        assertThat(stored.getPaidCents()).as("没收过款就不该有实收金额").isNull();
    }

    @Test
    @DisplayName("没到期的不动")
    void 未到期不动() {
        TipRecord tip = tipService.create(fanId, talentId, 5000, null);

        assertThat(timeoutService.expire(tip.getId()))
                .isEqualTo(TipTimeoutService.Result.NOT_DUE);
        assertThat(tipMapper.selectById(tip.getId()).statusEnum())
                .isEqualTo(TipStatus.PENDING_PAYMENT);
    }

    /**
     * 与订单侧同一条规则：钱已经付了、只是还没对上账的，不能因为时限到了就关掉。
     * 打赏目前没有凭证上传入口，所以这一条守的是以后接上凭证时不出事。
     */
    @Test
    @DisplayName("已到待确认收款的打赏不被超时关闭")
    void 待确认收款不被关闭() {
        TipRecord tip = overdueTip();
        tipService.markPendingConfirm(tip.getId());

        assertThat(timeoutService.expire(tip.getId()))
                .isEqualTo(TipTimeoutService.Result.SKIPPED);
        assertThat(tipMapper.selectById(tip.getId()).statusEnum())
                .isEqualTo(TipStatus.PENDING_CONFIRM);
    }

    @Test
    @DisplayName("刚好被确认收款的，超时处置不生效也不报错")
    void 与确认收款并发时不生效() {
        TipRecord tip = overdueTip();
        tipService.confirmPayment(tip.getId(), 5000, null);

        assertThat(timeoutService.expire(tip.getId()))
                .isEqualTo(TipTimeoutService.Result.SKIPPED);
        assertThat(tipMapper.selectById(tip.getId()).statusEnum())
                .isEqualTo(TipStatus.COMPLETED);
    }

    @Test
    @DisplayName("扫表能捞到到期的那一笔")
    void 扫表捞到期() {
        TipRecord tip = overdueTip();

        assertThat(timeoutService.overdue(200).stream().map(TipRecord::getId))
                .contains(tip.getId());
    }

    @Test
    @DisplayName("重复处置是幂等的，不会把已取消的再关一次")
    void 重复处置幂等() {
        TipRecord tip = overdueTip();

        assertThat(timeoutService.expire(tip.getId())).isEqualTo(TipTimeoutService.Result.HANDLED);
        assertThat(timeoutService.expire(tip.getId())).isEqualTo(TipTimeoutService.Result.SKIPPED);
        assertThat(tipMapper.selectById(tip.getId()).getCloseReason())
                .isEqualTo(TipTimeoutService.TIMEOUT_REASON);
    }

    /** 建一笔并把支付截止时间推到过去，省得真等几十分钟。 */
    private TipRecord overdueTip() {
        TipRecord tip = tipService.create(fanId, talentId, 5000, null);
        tip.setPayDeadline(LocalDateTime.now().minusMinutes(1));
        tipMapper.updateById(tip);
        return tipMapper.selectById(tip.getId());
    }

    private AppUser newUser(String prefix) {
        return userService.registerOrUpdate(prefix + System.nanoTime(), null, prefix, null);
    }
}
