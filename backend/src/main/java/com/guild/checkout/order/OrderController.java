package com.guild.checkout.order;

import com.guild.checkout.auth.CurrentUser;
import com.guild.checkout.auth.CurrentUserHolder;
import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.common.exception.NotFoundException;
import com.guild.checkout.user.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 粉丝端订单接口。
 *
 * 读取一律先过归属校验：非本人、非该单主播、非运营，一律无权限。
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderViewAssembler assembler;
    private final OrderStateService stateService;

    public OrderController(OrderService orderService,
                           OrderViewAssembler assembler,
                           OrderStateService stateService) {
        this.orderService = orderService;
        this.assembler = assembler;
        this.stateService = stateService;
    }

    public record CreateRequest(
            @NotNull(message = "请选择主播") Long talentId,
            @NotNull(message = "请选择服务档位") Long serviceSpecId,
            @Min(value = 1, message = "数量必须大于零") int quantity,
            @NotBlank(message = "联系方式为必填")
            @Size(max = 64, message = "联系方式不超过 64 字符") String contact,
            @Size(max = 255, message = "备注不超过 255 字符") String remark) {
    }

    /** 指定主播下单。 */
    @PostMapping
    public ApiResponse<OrderView> create(@Valid @RequestBody CreateRequest req) {
        long userId = CurrentUserHolder.requireUserId();
        ServiceOrder order = orderService.create(new OrderCreateCommand(
                userId, req.talentId(), req.serviceSpecId(), req.quantity(),
                req.contact(), req.remark()));
        return ApiResponse.ok(assembler.assemble(order, OrderViewAssembler.Viewer.FAN));
    }

    /** 我的订单。 */
    @GetMapping
    public ApiResponse<List<OrderView>> myOrders() {
        long userId = CurrentUserHolder.requireUserId();
        return ApiResponse.ok(orderService.listByFan(userId).stream()
                .map(o -> assembler.assemble(o, OrderViewAssembler.Viewer.FAN))
                .toList());
    }

    /** 订单详情。本人、该单主播或运营可看，其余无权限。 */
    @GetMapping("/{orderId}")
    public ApiResponse<OrderView> detail(@PathVariable long orderId) {
        CurrentUser current = CurrentUserHolder.require();
        ServiceOrder order = orderService.requireOrder(orderId);
        orderService.requireReadable(order, current.userId(), current.hasRole(UserRole.OPERATOR));
        return ApiResponse.ok(assembler.assemble(order, viewerOf(order, current)));
    }

    /** 按订单号查，便于粉丝凭单号找单、运营凭流水备注找单。 */
    @GetMapping("/by-ref/{orderRef}")
    public ApiResponse<OrderView> byRef(@PathVariable String orderRef) {
        CurrentUser current = CurrentUserHolder.require();
        ServiceOrder order = orderService.findByRef(orderRef);
        if (order == null) {
            throw new NotFoundException("订单不存在");
        }
        orderService.requireReadable(order, current.userId(), current.hasRole(UserRole.OPERATOR));
        return ApiResponse.ok(assembler.assemble(order, viewerOf(order, current)));
    }

    /** 订单的状态变更轨迹。与详情同样的归属校验。 */
    @GetMapping("/{orderId}/history")
    public ApiResponse<List<HistoryEntry>> history(@PathVariable long orderId) {
        CurrentUser current = CurrentUserHolder.require();
        ServiceOrder order = orderService.requireOrder(orderId);
        orderService.requireReadable(order, current.userId(), current.hasRole(UserRole.OPERATOR));

        return ApiResponse.ok(stateService.history(orderId).stream()
                .map(e -> new HistoryEntry(
                        e.getFromStatus(),
                        e.getFromStatus() == null ? null : OrderStatus.valueOf(e.getFromStatus()).label(),
                        e.getToStatus(),
                        OrderStatus.valueOf(e.getToStatus()).label(),
                        e.getOperatorType(),
                        e.getReason(),
                        e.getOccurredAt()))
                .toList());
    }

    public record HistoryEntry(
            String fromStatus, String fromLabel,
            String toStatus, String toLabel,
            String operatorType, String reason,
            java.time.LocalDateTime occurredAt) {
    }

    /**
     * 判定查看者身份：先看与这笔订单的关系，都不是才按运营看。
     *
     * 顺序不能反过来。公会很小，运营本人也会下单，若运营优先，他看自己的订单时
     * 拿到的是运营操作，「上传凭证」反而消失，自己的单反而付不了钱；
     * 而且运营也不该对自己的订单执行确认收款。
     */
    private OrderViewAssembler.Viewer viewerOf(ServiceOrder order, CurrentUser current) {
        if (orderService.isFan(order, current.userId())) {
            return OrderViewAssembler.Viewer.FAN;
        }
        if (orderService.isOwningTalent(order, current.userId())) {
            return OrderViewAssembler.Viewer.TALENT;
        }
        if (current.hasRole(UserRole.OPERATOR)) {
            return OrderViewAssembler.Viewer.OPERATOR;
        }
        // 走到这里说明归属校验有漏，保守按粉丝视图（字段最少）
        return OrderViewAssembler.Viewer.FAN;
    }
}
