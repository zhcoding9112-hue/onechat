package com.guild.checkout.order;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.common.exception.ForbiddenException;
import com.guild.checkout.common.exception.NotFoundException;
import com.guild.checkout.pricing.PriceQuote;
import com.guild.checkout.settlement.SettlementEntry;
import com.guild.checkout.settlement.SettlementService;
import com.guild.checkout.pricing.PricingService;
import com.guild.checkout.promotion.CouponService;
import com.guild.checkout.analytics.AnalyticsService;
import com.guild.checkout.analytics.EventKey;
import com.guild.checkout.order.timeout.DelayQueue;
import com.guild.checkout.order.timeout.OrderNotifier;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.TalentService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 订单的创建与履约推进。
 *
 * 所有状态变更一律委托给 {@link OrderStateService}，本类只负责业务前置校验、
 * 快照取值与时限计算。
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final int MAX_QUANTITY = 99;
    private static final int MAX_CONTACT_LENGTH = 64;
    private static final int MAX_REMARK_LENGTH = 255;
    /** 订单号碰撞时的重试次数。跨进程才可能撞上，撞一次的概率已极低。 */
    private static final int REF_RETRY = 5;

    private final ServiceOrderMapper orderMapper;
    private final OrderStateService stateService;
    private final OrderRefGenerator refGenerator;
    private final PricingService pricingService;
    private final TalentService talentService;
    private final OrderDeadlineProvider deadlines;
    private final DelayQueue payQueue;
    private final DelayQueue acceptQueue;
    private final OrderNotifier notifier;
    private final SettlementService settlementService;
    private final AnalyticsService analytics;
    private final CouponService couponService;

    public OrderService(ServiceOrderMapper orderMapper,
                        OrderStateService stateService,
                        OrderRefGenerator refGenerator,
                        PricingService pricingService,
                        TalentService talentService,
                        OrderDeadlineProvider deadlines,
                        @Qualifier("payTimeoutQueue") DelayQueue payQueue,
                        @Qualifier("acceptTimeoutQueue") DelayQueue acceptQueue,
                        OrderNotifier notifier,
                        SettlementService settlementService,
                        AnalyticsService analytics,
                        CouponService couponService) {
        this.orderMapper = orderMapper;
        this.stateService = stateService;
        this.refGenerator = refGenerator;
        this.pricingService = pricingService;
        this.talentService = talentService;
        this.deadlines = deadlines;
        this.payQueue = payQueue;
        this.acceptQueue = acceptQueue;
        this.notifier = notifier;
        this.settlementService = settlementService;
        this.analytics = analytics;
        this.couponService = couponService;
    }

    // ---------- 创建 ----------

    /**
     * 指定主播下单。
     *
     * 价格与分成比例在此刻快照写入订单，此后配置怎么改都不影响这一单。
     */
    @Transactional
    public ServiceOrder create(OrderCreateCommand command) {
        String contact = requireContact(command.contact());
        String remark = normalizeRemark(command.remark());
        int quantity = requireQuantity(command.quantity());

        Talent talent = talentService.find(command.talentId());
        if (talent == null) {
            throw new NotFoundException("主播不存在");
        }
        if (!Boolean.TRUE.equals(talent.getListed())) {
            throw new BusinessException("该主播暂不可约");
        }

        // 取价失败会抛 PricingException（档位停用、未配价等），直接透出给粉丝
        PriceQuote quote = pricingService.quote(command.talentId(), command.serviceSpecId(), quantity);

        // 券只减少实付，不动分成：quote.payableCents() 就是原价，也是分成的基数。
        // 校验与占用分两步——占用要带订单 id，而订单号要等插入后才有。
        CouponService.Discount discount = command.userCouponId() == null
                ? CouponService.Discount.NONE
                : couponService.validate(command.fanUserId(), command.userCouponId(),
                        quote.payableCents(), quote.serviceItemId());

        LocalDateTime now = LocalDateTime.now();
        ServiceOrder order = new ServiceOrder();
        order.setFanUserId(command.fanUserId());
        order.setTalentId(quote.talentId());
        order.setServiceItemId(quote.serviceItemId());
        order.setServiceSpecId(quote.serviceSpecId());
        order.setServiceItemName(quote.serviceItemName());
        order.setServiceSpecName(quote.serviceSpecName());
        order.setUnitPriceCents(quote.unitPriceCents());
        order.setQuantity(quote.quantity());
        order.setOriginalCents(quote.payableCents());
        order.setCouponDiscountCents(discount.discountCents());
        order.setUserCouponId(discount.userCouponId());
        order.setPayableCents(quote.payableCents() - discount.discountCents());
        order.setCommissionRateBp(quote.commissionRateBp());
        order.setGuildCents(quote.guildCents());
        order.setTalentCents(quote.talentCents());
        order.setStatusEnum(OrderStatus.PENDING_PAYMENT);
        order.setContact(contact);
        order.setRemark(remark);
        // 时限折算成绝对时间戳：之后运营改配置，这一单也沿用创建时的时限
        order.setPayDeadline(now.plusMinutes(deadlines.payMinutes()));

        insertWithUniqueRef(order);
        // 占用失败就抛，本事务回滚把订单一起撤掉——
        // 绝不能留下「订单记了抵扣、券却没被占住」的半成品，那张券就能被二次使用
        if (discount.userCouponId() != null) {
            couponService.lock(command.fanUserId(), discount.userCouponId(), order.getId());
        }
        stateService.recordCreation(order, command.fanUserId());
        payQueue.schedule(order.getId(), order.getPayDeadline());
        analytics.track(AnalyticsService.Track.of(EventKey.SUBMIT_ORDER)
                .user(command.fanUserId())
                .talent(quote.talentId())
                .serviceItem(quote.serviceItemId())
                .order(order.getOrderRef()));
        log.info("订单 {} 已创建，粉丝 {} 指定主播 {}，原价 {} 分、抵扣 {} 分、应付 {} 分",
                order.getOrderRef(), command.fanUserId(), quote.talentId(),
                order.getOriginalCents(), order.getCouponDiscountCents(), order.getPayableCents());
        return order;
    }

    /** 订单号由生成器保证同进程不重复，跨进程的极小概率由唯一索引兜底，这里重试。 */
    private void insertWithUniqueRef(ServiceOrder order) {
        for (int attempt = 1; ; attempt++) {
            order.setOrderRef(refGenerator.next());
            try {
                orderMapper.insert(order);
                return;
            } catch (DuplicateKeyException ex) {
                if (attempt >= REF_RETRY) {
                    throw ex;
                }
                log.warn("订单号 {} 冲突，第 {} 次重试", order.getOrderRef(), attempt);
            }
        }
    }

    // ---------- 履约推进 ----------

    /** 确认收款：由支付侧的收款事件驱动，订单只认这一个入口。 */
    @Transactional
    public ServiceOrder confirmPayment(long orderId, long paidCents, Long operatorId, String note) {
        ServiceOrder order = requireOrder(orderId);
        ServiceOrder paid = stateService.transition(order, OrderStatus.PENDING_ACCEPT,
                OrderOperatorType.OPERATOR, operatorId, note, o -> {
                    o.setPaidCents(paidCents);
                    o.setPaidAt(LocalDateTime.now());
                    // 接单时限从确认收款这一刻起算
                    o.setAcceptDeadline(LocalDateTime.now().plusMinutes(deadlines.acceptMinutes()));
                });
        // 钱已到账，不再需要待支付超时；改为盯接单超时
        payQueue.cancel(orderId);
        acceptQueue.schedule(orderId, paid.getAcceptDeadline());
        notifier.notifyPendingAccept(paid);
        analytics.track(AnalyticsService.Track.of(EventKey.CONFIRM_PAYMENT)
                .user(paid.getFanUserId()).talent(paid.getTalentId())
                .serviceItem(paid.getServiceItemId()).order(paid.getOrderRef()));
        return paid;
    }

    /** 主播接单。只有该订单指定的主播本人可以接。 */
    @Transactional
    public ServiceOrder accept(long orderId, long talentUserId) {
        ServiceOrder order = requireOrder(orderId);
        requireOwningTalent(order, talentUserId);

        LocalDateTime deadline = order.getAcceptDeadline();
        if (deadline != null && LocalDateTime.now().isAfter(deadline)) {
            throw new BusinessException("该订单已超过接单时限");
        }
        ServiceOrder accepted = stateService.transition(order, OrderStatus.ACCEPTED,
                OrderOperatorType.TALENT, talentUserId, null,
                o -> o.setAcceptedAt(LocalDateTime.now()));
        acceptQueue.cancel(orderId);
        // 接单率与平均接单时长都从这条事件算，props 里带上等待了多久
        long waitedSeconds = accepted.getPaidAt() == null ? 0
                : java.time.Duration.between(accepted.getPaidAt(), accepted.getAcceptedAt()).getSeconds();
        analytics.track(AnalyticsService.Track.of(EventKey.ACCEPT_ORDER)
                .user(accepted.getFanUserId()).talent(accepted.getTalentId())
                .serviceItem(accepted.getServiceItemId()).order(accepted.getOrderRef())
                .props(java.util.Map.of("waitedSeconds", waitedSeconds)));
        return accepted;
    }

    /** 主播标记服务完成。 */
    @Transactional
    public ServiceOrder complete(long orderId, long talentUserId) {
        ServiceOrder order = requireOrder(orderId);
        requireOwningTalent(order, talentUserId);

        ServiceOrder done = stateService.transition(order, OrderStatus.COMPLETED,
                OrderOperatorType.TALENT, talentUserId, null,
                o -> o.setCompletedAt(LocalDateTime.now()));
        // 订单完成即入账。幂等由台账的唯一索引保证。
        settlementService.recordOrder(done);
        analytics.track(AnalyticsService.Track.of(EventKey.COMPLETE_ORDER)
                .user(done.getFanUserId()).talent(done.getTalentId())
                .serviceItem(done.getServiceItemId()).order(done.getOrderRef()));
        recordRepurchaseIfAny(done);
        return done;
    }

    /** 运营人工取消。原因必填。 */
    @Transactional
    public ServiceOrder cancel(long orderId, long operatorId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("取消原因为必填");
        }
        ServiceOrder order = requireOrder(orderId);
        ServiceOrder cancelled = stateService.transition(order, OrderStatus.CANCELLED,
                OrderOperatorType.OPERATOR, operatorId, reason.trim(), null);
        dequeue(orderId);
        return cancelled;
    }

    /** 运营标记已退款。重复标记不产生第二条记录。 */
    @Transactional
    public ServiceOrder markRefunded(long orderId, long operatorId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("退款原因为必填");
        }
        ServiceOrder order = requireOrder(orderId);
        // 幂等：已是已退款则原样返回，不重复变更也不重复留痕
        if (order.statusEnum() == OrderStatus.REFUNDED) {
            return order;
        }
        ServiceOrder refunded = stateService.transition(order, OrderStatus.REFUNDED,
                OrderOperatorType.OPERATOR, operatorId, reason.trim(), null);
        // 已退款的订单不计入结算；若已记过账则冲销，不删除以便追溯
        settlementService.reverse(SettlementEntry.SOURCE_ORDER, orderId, reason.trim());
        dequeue(orderId);
        return refunded;
    }

    /**
     * 7 日复购判定。
     *
     * 口径：这位粉丝在本单之前 7 天内，是否还有另一笔已完成的订单。
     * 判定放在完成时而不是下单时——没走到完成的单不算真的复购。
     */
    private void recordRepurchaseIfAny(ServiceOrder done) {
        LocalDateTime since = done.getCompletedAt().minusDays(7);
        Long earlier = orderMapper.selectCount(Wrappers.<ServiceOrder>lambdaQuery()
                .eq(ServiceOrder::getFanUserId, done.getFanUserId())
                .eq(ServiceOrder::getStatus, OrderStatus.COMPLETED.name())
                .ne(ServiceOrder::getId, done.getId())
                .ge(ServiceOrder::getCompletedAt, since)
                .lt(ServiceOrder::getCompletedAt, done.getCompletedAt()));
        if (earlier != null && earlier > 0) {
            analytics.track(AnalyticsService.Track.of(EventKey.REPURCHASE_7D)
                    .user(done.getFanUserId()).talent(done.getTalentId())
                    .serviceItem(done.getServiceItemId()).order(done.getOrderRef()));
        }
    }

    /** 订单离开等待态时把它从两条队列里摘掉，避免堆积无效成员。 */
    private void dequeue(long orderId) {
        payQueue.cancel(orderId);
        acceptQueue.cancel(orderId);
    }

    // ---------- 查询 ----------

    public ServiceOrder requireOrder(long orderId) {
        ServiceOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new NotFoundException("订单不存在");
        }
        return order;
    }

    public ServiceOrder findByRef(String orderRef) {
        if (!OrderRefGenerator.isWellFormed(orderRef)) {
            return null;
        }
        return orderMapper.selectOne(
                Wrappers.<ServiceOrder>lambdaQuery().eq(ServiceOrder::getOrderRef, orderRef));
    }

    /** 粉丝自己的订单。 */
    public List<ServiceOrder> listByFan(long fanUserId) {
        return orderMapper.selectList(Wrappers.<ServiceOrder>lambdaQuery()
                .eq(ServiceOrder::getFanUserId, fanUserId)
                .orderByDesc(ServiceOrder::getId));
    }

    /** 某主播的订单，可按状态过滤。 */
    public List<ServiceOrder> listByTalent(long talentId, OrderStatus... statuses) {
        var query = Wrappers.<ServiceOrder>lambdaQuery().eq(ServiceOrder::getTalentId, talentId);
        if (statuses != null && statuses.length > 0) {
            query.in(ServiceOrder::getStatus,
                    java.util.Arrays.stream(statuses).map(Enum::name).toList());
        }
        return orderMapper.selectList(query.orderByDesc(ServiceOrder::getId));
    }

    // ---------- 校验 ----------

    /**
     * 订单资源的归属校验：仅下单粉丝本人、该单指定主播本人，或运营可读。
     * 这是 wechat-auth 规格的「越权访问他人订单」要求。
     */
    public void requireReadable(ServiceOrder order, Long userId, boolean operator) {
        if (operator) {
            return;
        }
        if (isFan(order, userId) || isOwningTalent(order, userId)) {
            return;
        }
        throw new ForbiddenException("无权查看该订单");
    }

    /** 是否为该单的下单粉丝。 */
    public boolean isFan(ServiceOrder order, Long userId) {
        return Objects.equals(order.getFanUserId(), userId) && userId != null;
    }

    /**
     * 是否为该单指定的主播本人。
     *
     * 一律用 Objects.equals 而非 ==：两侧都是 Long 时 == 比的是引用，
     * 对超出 Integer 缓存范围的 id 会恒为 false，把主播误判成无关者。
     */
    public boolean isOwningTalent(ServiceOrder order, Long userId) {
        if (userId == null) {
            return false;
        }
        Talent talent = talentService.find(order.getTalentId());
        return talent != null && Objects.equals(talent.getUserId(), userId);
    }

    /** 写操作：只有该订单指定的主播本人可以接单与标记完成。 */
    private void requireOwningTalent(ServiceOrder order, Long talentUserId) {
        if (!isOwningTalent(order, talentUserId)) {
            throw new ForbiddenException("只有该订单指定的主播可以执行此操作");
        }
    }

    private static String requireContact(String contact) {
        if (contact == null || contact.isBlank()) {
            throw new BusinessException("联系方式为必填");
        }
        String trimmed = contact.trim();
        if (trimmed.length() > MAX_CONTACT_LENGTH) {
            throw new BusinessException("联系方式不超过 " + MAX_CONTACT_LENGTH + " 字符");
        }
        return trimmed;
    }

    private static String normalizeRemark(String remark) {
        if (remark == null) {
            return null;
        }
        String trimmed = remark.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_REMARK_LENGTH) {
            throw new BusinessException("备注不超过 " + MAX_REMARK_LENGTH + " 字符");
        }
        return trimmed;
    }

    private static int requireQuantity(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException("数量必须大于零");
        }
        if (quantity > MAX_QUANTITY) {
            throw new BusinessException("单笔订单数量不超过 " + MAX_QUANTITY);
        }
        return quantity;
    }
}
