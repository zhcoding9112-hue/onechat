package com.guild.checkout.order.admin;

import com.guild.checkout.auth.CurrentUserHolder;
import com.guild.checkout.auth.RequireRole;
import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.common.api.PageResponse;
import com.guild.checkout.common.api.Pageables;
import com.guild.checkout.order.OrderDeadlineProvider;
import com.guild.checkout.order.OrderDeadlineSetting;
import com.guild.checkout.order.OrderService;
import com.guild.checkout.order.OrderStatus;
import com.guild.checkout.order.OrderView;
import com.guild.checkout.order.OrderViewAssembler;
import com.guild.checkout.order.ServiceOrder;
import com.guild.checkout.order.ServiceOrderMapper;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.TalentService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.user.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 运营侧的订单处置。整类要求运营角色。
 */
@RestController
@RequestMapping("/api/admin/orders")
@RequireRole(UserRole.OPERATOR)
public class OrderAdminController {

    private final OrderService orderService;
    private final OrderViewAssembler assembler;
    private final ServiceOrderMapper orderMapper;
    private final OrderDeadlineProvider deadlines;
    private final TalentService talentService;

    public OrderAdminController(OrderService orderService,
                                OrderViewAssembler assembler,
                                ServiceOrderMapper orderMapper,
                                OrderDeadlineProvider deadlines,
                                TalentService talentService) {
        this.orderService = orderService;
        this.assembler = assembler;
        this.orderMapper = orderMapper;
        this.deadlines = deadlines;
        this.talentService = talentService;
    }

    // ---------- 时限配置 ----------

    public record DeadlineRequest(Integer payMinutes, Integer acceptMinutes) {
    }

    @GetMapping("/deadlines")
    public ApiResponse<OrderDeadlineSetting> deadlines() {
        return ApiResponse.ok(deadlines.current());
    }

    /**
     * 调整时限。只影响此后创建的订单——已在计时中的订单在创建时就把时限
     * 折算成绝对时间戳写进了自身。
     */
    @PutMapping("/deadlines")
    public ApiResponse<OrderDeadlineSetting> updateDeadlines(@RequestBody DeadlineRequest req) {
        return ApiResponse.ok(deadlines.update(req.payMinutes(), req.acceptMinutes()));
    }

    public record ReasonRequest(
            @NotBlank(message = "原因为必填")
            @Size(max = 255, message = "原因不超过 255 字符") String reason) {
    }

    /**
     * 订单列表：按状态过滤、按关键字搜索、分页。
     *
     * keyword 同时匹配订单号、粉丝微信号与主播昵称。昵称不在 service_order 上，
     * 所以先用昵称去 talent 表捞出 id 再 IN——主播表很小，比给订单表做联表
     * 划算，也不用为搜索引入新的 SQL 映射文件。
     */
    @GetMapping
    public ApiResponse<PageResponse<OrderView>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        int p = Pageables.page(page);
        int s = Pageables.size(size);

        var query = Wrappers.<ServiceOrder>lambdaQuery();
        if (status != null && !status.isBlank()) {
            query.eq(ServiceOrder::getStatus, OrderStatus.valueOf(status).name());
        }
        if (!Pageables.blank(keyword)) {
            String like = Pageables.like(keyword);
            List<Long> talentIds = talentService.listAll().stream()
                    .filter(t -> matches(t.getNickname(), keyword))
                    .map(Talent::getId)
                    .toList();
            query.and(w -> {
                w.apply("LOWER(order_ref) LIKE {0}" + Pageables.ESCAPE_CLAUSE, like)
                        .or().apply("LOWER(contact) LIKE {0}" + Pageables.ESCAPE_CLAUSE, like);
                if (!talentIds.isEmpty()) {
                    w.or().in(ServiceOrder::getTalentId, talentIds);
                }
            });
        }

        long total = orderMapper.selectCount(query);
        List<OrderView> items = orderMapper.selectList(query
                        .orderByDesc(ServiceOrder::getId)
                        .last("LIMIT " + s + " OFFSET " + Pageables.offset(p, s)))
                .stream().map(o -> assembler.assemble(o, OrderViewAssembler.Viewer.OPERATOR)).toList();
        return ApiResponse.ok(PageResponse.of(items, total, p, s));
    }

    private static boolean matches(String value, String keyword) {
        return value != null
                && value.toLowerCase(java.util.Locale.ROOT)
                        .contains(keyword.trim().toLowerCase(java.util.Locale.ROOT));
    }

    /** 待退款队列：已超时但尚未标记退款的订单。 */
    @GetMapping("/refund-queue")
    public ApiResponse<List<OrderView>> refundQueue() {
        return ApiResponse.ok(orderMapper.selectList(Wrappers.<ServiceOrder>lambdaQuery()
                        .eq(ServiceOrder::getStatus, OrderStatus.TIMEOUT.name())
                        .orderByAsc(ServiceOrder::getClosedAt))
                .stream().map(o -> assembler.assemble(o, OrderViewAssembler.Viewer.OPERATOR)).toList());
    }

    // 确认收款不在这里：它必须经过 PaymentService 走收款事件，才能产生收款记录并保证幂等。
    // 入口见 PUT /api/admin/payment/{orderId}/confirm。

    /** 人工取消。原因必填。 */
    @PutMapping("/{orderId}/cancel")
    public ApiResponse<OrderView> cancel(@PathVariable long orderId,
                                         @Valid @RequestBody ReasonRequest req) {
        Long operatorId = CurrentUserHolder.requireUserId();
        ServiceOrder order = orderService.cancel(orderId, operatorId, req.reason());
        return ApiResponse.ok(assembler.assemble(order, OrderViewAssembler.Viewer.OPERATOR));
    }

    /** 标记已退款。重复标记幂等。 */
    @PutMapping("/{orderId}/refund")
    public ApiResponse<OrderView> markRefunded(@PathVariable long orderId,
                                               @Valid @RequestBody ReasonRequest req) {
        Long operatorId = CurrentUserHolder.requireUserId();
        ServiceOrder order = orderService.markRefunded(orderId, operatorId, req.reason());
        return ApiResponse.ok(assembler.assemble(order, OrderViewAssembler.Viewer.OPERATOR));
    }
}
