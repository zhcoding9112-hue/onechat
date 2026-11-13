package com.guild.checkout.talent;

import com.guild.checkout.auth.CurrentUserHolder;
import com.guild.checkout.auth.RequireRole;
import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.common.exception.ForbiddenException;
import com.guild.checkout.order.OrderService;
import com.guild.checkout.order.OrderStatus;
import com.guild.checkout.order.OrderView;
import com.guild.checkout.order.OrderViewAssembler;
import com.guild.checkout.order.ServiceOrder;
import com.guild.checkout.tipping.TipRecord;
import com.guild.checkout.tipping.TipService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;
import com.guild.checkout.user.UserRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 主播工作台。整类接口要求主播角色，粉丝访问一律无权限。
 *
 * 当前只落了路由与角色守卫；待接单列表、接单与标记完成、在线状态、收入与评分
 * 由 talent-workspace 相关任务填充。
 */
@RestController
@RequestMapping("/api/talent/workspace")
@RequireRole(UserRole.TALENT)
public class TalentWorkspaceController {

    private final TalentService talentService;
    private final TalentMapper talentMapper;
    private final OrderService orderService;
    private final OrderViewAssembler assembler;
    private final TipService tipService;

    public TalentWorkspaceController(TalentService talentService,
                                     TalentMapper talentMapper,
                                     OrderService orderService,
                                     OrderViewAssembler assembler,
                                     TipService tipService) {
        this.talentService = talentService;
        this.talentMapper = talentMapper;
        this.orderService = orderService;
        this.assembler = assembler;
        this.tipService = tipService;
    }

    /** 当前登录者对应的主播档案。没有档案说明角色与档案不一致，按无权限处理。 */
    private Talent requireMyTalent() {
        Long userId = CurrentUserHolder.requireUserId();
        Talent talent = talentService.findByUserId(userId);
        if (talent == null) {
            throw new ForbiddenException("当前账号未关联任何主播档案");
        }
        return talent;
    }

    /**
     * 主播自己看到的档案。刻意不复用运营端的视图：
     * userId、排序这类运营才关心的字段不该出现在主播端。
     */
    public record MyProfile(
            Long talentId, String nickname, String coverUrl, String intro,
            java.util.List<String> tags, boolean online, boolean listed,
            String audioUrl, Integer audioSeconds) {
    }

    @GetMapping("/me")
    public ApiResponse<MyProfile> me() {
        return ApiResponse.ok(toProfile(requireMyTalent()));
    }

    /** 切换在线状态。结果即时反映到粉丝端列表与详情。 */
    @PutMapping("/online/{online}")
    public ApiResponse<MyProfile> setOnline(@PathVariable boolean online) {
        Talent talent = requireMyTalent();
        if (!Boolean.valueOf(online).equals(talent.getOnline())) {
            talent.setOnline(online);
            talentMapper.updateById(talent);
        }
        return ApiResponse.ok(toProfile(talent));
    }

    private MyProfile toProfile(Talent talent) {
        return new MyProfile(
                talent.getId(), talent.getNickname(), talent.getCoverUrl(), talent.getIntro(),
                talentService.listTags(talent.getId()).stream().map(TalentTag::getName).toList(),
                Boolean.TRUE.equals(talent.getOnline()),
                Boolean.TRUE.equals(talent.getListed()),
                talent.getAudioUrl(), talent.getAudioSeconds());
    }

    // ---------- 订单 ----------

    /** 待接单列表。剩余接单时间由前端按 acceptDeadline 倒计时展示。 */
    @GetMapping("/pending-orders")
    public ApiResponse<java.util.List<OrderView>> pendingOrders() {
        Talent talent = requireMyTalent();
        return ApiResponse.ok(orderService.listByTalent(talent.getId(), OrderStatus.PENDING_ACCEPT)
                .stream().map(o -> assembler.assemble(o, OrderViewAssembler.Viewer.TALENT)).toList());
    }

    /** 进行中的订单（已接单未完成）。 */
    @GetMapping("/active-orders")
    public ApiResponse<java.util.List<OrderView>> activeOrders() {
        Talent talent = requireMyTalent();
        return ApiResponse.ok(orderService.listByTalent(talent.getId(), OrderStatus.ACCEPTED)
                .stream().map(o -> assembler.assemble(o, OrderViewAssembler.Viewer.TALENT)).toList());
    }

    /** 历史订单：已完成、已超时、已退款。 */
    @GetMapping("/history-orders")
    public ApiResponse<java.util.List<OrderView>> historyOrders() {
        Talent talent = requireMyTalent();
        return ApiResponse.ok(orderService.listByTalent(talent.getId(),
                        OrderStatus.COMPLETED, OrderStatus.TIMEOUT, OrderStatus.REFUNDED)
                .stream().map(o -> assembler.assemble(o, OrderViewAssembler.Viewer.TALENT)).toList());
    }

    /** 接单。接单后返回的视图带粉丝联系方式与备注。 */
    @PutMapping("/orders/{orderId}/accept")
    public ApiResponse<OrderView> accept(@PathVariable long orderId) {
        Long userId = CurrentUserHolder.requireUserId();
        ServiceOrder order = orderService.accept(orderId, userId);
        return ApiResponse.ok(assembler.assemble(order, OrderViewAssembler.Viewer.TALENT));
    }

    /** 标记服务完成。 */
    @PutMapping("/orders/{orderId}/complete")
    public ApiResponse<OrderView> complete(@PathVariable long orderId) {
        Long userId = CurrentUserHolder.requireUserId();
        ServiceOrder order = orderService.complete(orderId, userId);
        return ApiResponse.ok(assembler.assemble(order, OrderViewAssembler.Viewer.TALENT));
    }

    // ---------- 收入 ----------

    /**
     * 主播收入。
     *
     * 刻意只有查询，没有任何写操作：分成是站内记账，实际支付走既有的线下月结，
     * 系统不提供提现、打款或结算状态变更（settlement 规格明确要求）。
     *
     * @param entries 逐笔明细，按时间倒序
     */
    public record IncomeView(
            long totalCents,
            long orderCents,
            long tipCents,
            long completedOrderCount,
            Map<String, Long> monthlyCents,
            java.util.List<IncomeEntry> entries) {
    }

    /** 一笔收入明细。金额是订单上的主播应得快照，不随分成配置变动。 */
    public record IncomeEntry(
            Long orderId,
            String orderRef,
            String serviceItemName,
            String serviceSpecName,
            long payableCents,
            int commissionRateBp,
            long talentCents,
            java.time.LocalDateTime completedAt) {
    }

    // ---------- 打赏 ----------

    /**
     * 我收到的打赏。留言只在这里出现——其他粉丝浏览主播详情页看不到任何留言。
     */
    public record TipEntry(
            Long id,
            String tipRef,
            long amountCents,
            long talentCents,
            String message,
            java.time.LocalDateTime paidAt) {
    }

    @GetMapping("/tips")
    public ApiResponse<java.util.List<TipEntry>> tips() {
        Talent talent = requireMyTalent();
        return ApiResponse.ok(tipService.listCompletedByTalent(talent.getId()).stream()
                .map(t -> new TipEntry(
                        t.getId(), t.getTipRef(), t.getAmountCents(), t.getTalentCents(),
                        t.getMessage(), t.getPaidAt()))
                .toList());
    }

    @GetMapping("/income")
    public ApiResponse<IncomeView> income() {
        Talent talent = requireMyTalent();

        // 只有已完成的订单计入收入；已超时与已退款的不算
        java.util.List<ServiceOrder> completed =
                orderService.listByTalent(talent.getId(), OrderStatus.COMPLETED);

        long total = 0;
        long tipTotal = 0;
        Map<String, Long> monthly = new LinkedHashMap<>();
        java.util.List<IncomeEntry> entries = new java.util.ArrayList<>();

        // 打赏与服务订单分开计量，但都计入应得合计（settlement 规格要求分别展示并给出合计）
        for (TipRecord t : tipService.listCompletedByTalent(talent.getId())) {
            long amount = t.getTalentCents() == null ? 0 : t.getTalentCents();
            tipTotal += amount;
            LocalDate day = (t.getPaidAt() == null ? t.getCreatedAt() : t.getPaidAt()).toLocalDate();
            monthly.merge(YearMonth.from(day).toString(), amount, Long::sum);
        }
        total += tipTotal;

        for (ServiceOrder o : completed) {
            long amount = o.getTalentCents() == null ? 0 : o.getTalentCents();
            total += amount;

            LocalDate day = (o.getCompletedAt() == null ? o.getCreatedAt() : o.getCompletedAt())
                    .toLocalDate();
            String month = YearMonth.from(day).toString();
            monthly.merge(month, amount, Long::sum);

            entries.add(new IncomeEntry(
                    o.getId(), o.getOrderRef(), o.getServiceItemName(), o.getServiceSpecName(),
                    o.getPayableCents() == null ? 0 : o.getPayableCents(),
                    o.getCommissionRateBp() == null ? 0 : o.getCommissionRateBp(),
                    amount, o.getCompletedAt()));
        }

        return ApiResponse.ok(new IncomeView(
                total, total - tipTotal, tipTotal, completed.size(), monthly, entries));
    }
}
