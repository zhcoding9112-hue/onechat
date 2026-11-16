package com.guild.checkout.promotion;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.common.exception.NotFoundException;
import com.guild.checkout.user.AppUser;
import com.guild.checkout.user.AppUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 优惠券与券包。
 *
 * 券的抵扣由公会承担：它只减少粉丝实付，不减少主播分成。因此订单上「原价」与
 * 「实付」是两个字段，分成算原价、收款算实付，见 {@code V17__coupon.sql}。
 *
 * 过期关闭是惰性的，与试音需求同样的理由：所有在意「这张券还能不能用」的路径
 * 都会先读一次，不存在「已过期却没人发现」的窗口，多一个定时任务只会多一处会漏跑的地方。
 */
@Service
public class CouponService {

    private static final Logger log = LoggerFactory.getLogger(CouponService.class);

    private static final int MAX_NAME_LENGTH = 64;

    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;
    private final AppUserMapper userMapper;

    public CouponService(CouponMapper couponMapper,
                         UserCouponMapper userCouponMapper,
                         AppUserMapper userMapper) {
        this.couponMapper = couponMapper;
        this.userCouponMapper = userCouponMapper;
        this.userMapper = userMapper;
    }

    // ---------- 运营：定义与发放 ----------

    /** 券模板的创建入参。 */
    public record CouponForm(String name, long amountCents, long thresholdCents,
                             Long serviceItemId, LocalDateTime validFrom, LocalDateTime validTo) {
    }

    @Transactional
    public Coupon create(CouponForm form) {
        String name = form.name() == null ? "" : form.name().trim();
        if (name.isEmpty()) {
            throw new BusinessException("券名不能为空");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new BusinessException("券名不超过 %d 字符".formatted(MAX_NAME_LENGTH));
        }
        if (form.amountCents() <= 0) {
            throw new BusinessException("面额必须大于 0");
        }
        // 面额高于门槛意味着一张券能把订单抵成负数，或让门槛形同虚设
        if (form.amountCents() > form.thresholdCents()) {
            throw new BusinessException("面额不得超过门槛");
        }
        if (form.validFrom() == null || form.validTo() == null
                || !form.validTo().isAfter(form.validFrom())) {
            throw new BusinessException("失效时间必须晚于生效时间");
        }

        Coupon coupon = new Coupon();
        coupon.setName(name);
        coupon.setAmountCents(form.amountCents());
        coupon.setThresholdCents(form.thresholdCents());
        coupon.setServiceItemId(form.serviceItemId());
        coupon.setValidFrom(form.validFrom());
        coupon.setValidTo(form.validTo());
        coupon.setEnabled(true);
        couponMapper.insert(coupon);
        log.info("已创建优惠券 {}：满 {} 减 {} 分", coupon.getId(),
                coupon.getThresholdCents(), coupon.getAmountCents());
        return coupon;
    }

    public List<Coupon> listCoupons() {
        return couponMapper.selectList(Wrappers.<Coupon>lambdaQuery()
                .orderByDesc(Coupon::getId));
    }

    public Coupon require(long couponId) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw new NotFoundException("优惠券不存在: " + couponId);
        }
        return coupon;
    }

    /**
     * 向全体粉丝发放。已持有的跳过。
     *
     * @return 本次新发出的张数
     */
    @Transactional
    public int grantToAll(long couponId) {
        List<Long> userIds = userMapper.selectList(Wrappers.<AppUser>lambdaQuery()
                        .select(AppUser::getId))
                .stream().map(AppUser::getId).toList();
        return grantTo(couponId, userIds);
    }

    /** 向指定粉丝发放。重复发放靠唯一索引挡住，不会发出第二张。 */
    @Transactional
    public int grantTo(long couponId, List<Long> userIds) {
        Coupon coupon = require(couponId);
        int granted = 0;
        for (Long userId : userIds) {
            if (issue(coupon, userId)) {
                granted++;
            }
        }
        log.info("优惠券 {} 向 {} 人发放，新发出 {} 张", couponId, userIds.size(), granted);
        return granted;
    }

    /**
     * 粉丝主动领取。重复领取被拒绝，而不是静默成功——
     * 静默成功会让粉丝以为自己领到了两张。
     */
    @Transactional
    public UserCoupon claim(long userId, long couponId) {
        Coupon coupon = require(couponId);
        if (!Boolean.TRUE.equals(coupon.getEnabled())) {
            throw new BusinessException("该券已停用");
        }
        if (coupon.getValidTo().isBefore(LocalDateTime.now())) {
            throw new BusinessException("该券已过期");
        }
        if (!issue(coupon, userId)) {
            throw new BusinessException("你已领取过这张券");
        }
        return findOwned(userId, couponId);
    }

    /** 发一张给某人。已有则返回 false。幂等的最终保证在唯一索引上。 */
    private boolean issue(Coupon coupon, Long userId) {
        UserCoupon uc = new UserCoupon();
        uc.setCouponId(coupon.getId());
        uc.setUserId(userId);
        uc.setStatus(UserCoupon.AVAILABLE);
        // 有效期在发放时快照：运营事后改模板，已发出的券不跟着变
        uc.setExpiresAt(coupon.getValidTo());
        try {
            userCouponMapper.insert(uc);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    private UserCoupon findOwned(long userId, long couponId) {
        return userCouponMapper.selectOne(Wrappers.<UserCoupon>lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, couponId));
    }

    // ---------- 粉丝：券包 ----------

    /** 券包里的一张券：持有记录加上它的模板。 */
    public record OwnedCoupon(UserCoupon owned, Coupon coupon) {
    }

    /**
     * 某粉丝的全部券，读取时顺手把过期的落成 EXPIRED。
     *
     * 只把 AVAILABLE 的改过去：LOCKED 的券挂在一笔未结束的订单上，
     * 它的归宿要等那笔订单结清或退款时再定，这里动它会把订单和券的状态割裂开。
     */
    @Transactional
    public List<OwnedCoupon> wallet(long userId) {
        LocalDateTime now = LocalDateTime.now();
        userCouponMapper.update(null, Wrappers.<UserCoupon>lambdaUpdate()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getStatus, UserCoupon.AVAILABLE)
                .le(UserCoupon::getExpiresAt, now)
                .set(UserCoupon::getStatus, UserCoupon.EXPIRED));

        List<UserCoupon> owned = userCouponMapper.selectList(Wrappers.<UserCoupon>lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .orderByDesc(UserCoupon::getId));
        return attach(owned);
    }

    /**
     * 这笔订单可用的券：满足门槛、未过期、品类匹配。
     *
     * 不满足门槛的券不在这里过滤掉——券包页要让粉丝看到「再买 32 元就能用」，
     * 所以筛选交给调用方，这里只返回仍然可用的券。
     */
    @Transactional
    public List<OwnedCoupon> usableFor(long userId, long originalCents, Long serviceItemId) {
        return wallet(userId).stream()
                .filter(o -> UserCoupon.AVAILABLE.equals(o.owned().getStatus()))
                .filter(o -> originalCents >= o.coupon().getThresholdCents())
                .filter(o -> matchesScope(o.coupon(), serviceItemId))
                .toList();
    }

    private List<OwnedCoupon> attach(List<UserCoupon> owned) {
        if (owned.isEmpty()) {
            return List.of();
        }
        List<Long> couponIds = owned.stream().map(UserCoupon::getCouponId).distinct().toList();
        Map<Long, Coupon> templates = couponMapper.selectBatchIds(couponIds).stream()
                .collect(Collectors.toMap(Coupon::getId, Function.identity()));
        return owned.stream()
                .map(uc -> {
                    Coupon c = templates.get(uc.getCouponId());
                    return c == null ? null : new OwnedCoupon(uc, c);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private boolean matchesScope(Coupon coupon, Long serviceItemId) {
        return coupon.getServiceItemId() == null
                || Objects.equals(coupon.getServiceItemId(), serviceItemId);
    }

    // ---------- 下单链路 ----------

    /** 一次抵扣的结果。 */
    public record Discount(Long userCouponId, long discountCents) {

        public static final Discount NONE = new Discount(null, 0);
    }

    /**
     * 校验这张券能否用于该笔订单。只做判断，不占用。
     *
     * 订单创建时要先确定金额、再插订单、最后拿订单 id 去占券，所以校验与占用分成两步。
     */
    @Transactional
    public Discount validate(long userId, long userCouponId, long originalCents, Long serviceItemId) {
        UserCoupon owned = userCouponMapper.selectById(userCouponId);
        if (owned == null || !Objects.equals(owned.getUserId(), userId)) {
            throw new BusinessException("券不存在或不属于你");
        }
        Coupon coupon = require(owned.getCouponId());

        if (UserCoupon.USED.equals(owned.getStatus())) {
            throw new BusinessException("该券已使用");
        }
        if (UserCoupon.LOCKED.equals(owned.getStatus())) {
            throw new BusinessException("该券已被另一笔未支付的订单占用");
        }
        if (UserCoupon.EXPIRED.equals(owned.getStatus())
                || owned.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("该券已过期");
        }
        if (coupon.getValidFrom().isAfter(LocalDateTime.now())) {
            throw new BusinessException("该券尚未生效");
        }
        if (originalCents < coupon.getThresholdCents()) {
            throw new BusinessException("未达使用门槛，满 %s 元可用"
                    .formatted(yuan(coupon.getThresholdCents())));
        }
        if (!matchesScope(coupon, serviceItemId)) {
            throw new BusinessException("该券不适用于此服务");
        }
        // 面额不得超过门槛是建券时就保证的，所以抵扣不可能大于原价
        return new Discount(owned.getId(), coupon.getAmountCents());
    }

    /**
     * 占用这张券。失败即抛，由调用方的事务回滚把订单一起撤掉——
     * 不能留下「订单记了抵扣、券却没被占」的半成品。
     */
    @Transactional
    public void lock(long userId, long userCouponId, long orderId) {
        int affected = userCouponMapper.lockForOrder(
                userCouponId, userId, orderId, LocalDateTime.now());
        if (affected == 0) {
            throw new BusinessException("该券已被占用或已失效，请重新选择");
        }
    }

    /** 订单确认收款时核销。没用券的订单调用它是空操作。 */
    @Transactional
    public void redeem(long orderId) {
        int affected = userCouponMapper.redeemByOrder(orderId, LocalDateTime.now());
        if (affected > 0) {
            log.info("订单 {} 使用的券已核销", orderId);
        }
    }

    /**
     * 订单取消、超时或退款时退回券。
     *
     * 已过期的券不退回——退回去也用不了，粉丝在券包里看到一张「可用」的过期券只会更困惑。
     * 未退回的原因写在券上，运营来问时有据可查。
     */
    @Transactional
    public void release(long orderId, String reason) {
        UserCoupon owned = userCouponMapper.selectOne(Wrappers.<UserCoupon>lambdaQuery()
                .eq(UserCoupon::getOrderId, orderId));
        if (owned == null || UserCoupon.AVAILABLE.equals(owned.getStatus())) {
            return;
        }

        if (owned.getExpiresAt().isBefore(LocalDateTime.now())) {
            userCouponMapper.update(null, Wrappers.<UserCoupon>lambdaUpdate()
                    .eq(UserCoupon::getId, owned.getId())
                    .set(UserCoupon::getStatus, UserCoupon.EXPIRED)
                    .set(UserCoupon::getRemark,
                            "订单关闭（%s）时该券已过有效期，未退回".formatted(reason)));
            log.info("订单 {} 的券已过期，不退回", orderId);
            return;
        }

        // 显式 set：updateById 会跳过 null 字段，而这里要把 order_id 与 used_at 清回 null
        userCouponMapper.update(null, Wrappers.<UserCoupon>lambdaUpdate()
                .eq(UserCoupon::getId, owned.getId())
                .set(UserCoupon::getStatus, UserCoupon.AVAILABLE)
                .set(UserCoupon::getOrderId, null)
                .set(UserCoupon::getUsedAt, null)
                .set(UserCoupon::getRemark, "订单关闭（%s）后退回".formatted(reason)));
        log.info("订单 {} 的券已退回券包", orderId);
    }

    public UserCoupon findByOrder(long orderId) {
        return userCouponMapper.selectOne(Wrappers.<UserCoupon>lambdaQuery()
                .eq(UserCoupon::getOrderId, orderId));
    }

    private static String yuan(long cents) {
        return "%d.%02d".formatted(cents / 100, Math.abs(cents % 100));
    }
}
