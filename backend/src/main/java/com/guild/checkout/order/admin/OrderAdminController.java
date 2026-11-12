package com.guild.checkout.order.admin;

import com.guild.checkout.auth.CurrentUserHolder;
import com.guild.checkout.auth.RequireRole;
import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.order.OrderService;
import com.guild.checkout.order.OrderStatus;
import com.guild.checkout.order.OrderView;
import com.guild.checkout.order.OrderViewAssembler;
import com.guild.checkout.order.ServiceOrder;
import com.guild.checkout.order.ServiceOrderMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.user.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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

    public OrderAdminController(OrderService orderService,
                                OrderViewAssembler assembler,
                                ServiceOrderMapper orderMapper) {
        this.orderService = orderService;
        this.assembler = assembler;
        this.orderMapper = orderMapper;
    }

    public record ReasonRequest(
            @NotBlank(message = "原因为必填")
            @Size(max = 255, message = "原因不超过 255 字符") String reason) {
    }

    public record ConfirmPaymentRequest(
            @Min(value = 0, message = "实收金额不得为负") long paidCents,
            @Size(max = 255, message = "差异说明不超过 255 字符") String note) {
    }

    /** 订单列表，可按状态过滤。 */
    @GetMapping
    public ApiResponse<List<OrderView>> list(@RequestParam(required = false) String status) {
        var query = Wrappers.<ServiceOrder>lambdaQuery();
        if (status != null && !status.isBlank()) {
            query.eq(ServiceOrder::getStatus, OrderStatus.valueOf(status).name());
        }
        return ApiResponse.ok(orderMapper.selectList(query.orderByDesc(ServiceOrder::getId))
                .stream().map(o -> assembler.assemble(o, OrderViewAssembler.Viewer.OPERATOR)).toList());
    }

    /** 待退款队列：已超时但尚未标记退款的订单。 */
    @GetMapping("/refund-queue")
    public ApiResponse<List<OrderView>> refundQueue() {
        return ApiResponse.ok(orderMapper.selectList(Wrappers.<ServiceOrder>lambdaQuery()
                        .eq(ServiceOrder::getStatus, OrderStatus.TIMEOUT.name())
                        .orderByAsc(ServiceOrder::getClosedAt))
                .stream().map(o -> assembler.assemble(o, OrderViewAssembler.Viewer.OPERATOR)).toList());
    }

    /**
     * 确认收款。
     * 实收与应付不一致时必须填写差异说明，否则拒绝——金额对不上而无人解释，
     * 月底对账时谁也说不清。
     */
    @PutMapping("/{orderId}/confirm-payment")
    public ApiResponse<OrderView> confirmPayment(@PathVariable long orderId,
                                                 @Valid @RequestBody ConfirmPaymentRequest req) {
        Long operatorId = CurrentUserHolder.requireUserId();
        ServiceOrder existing = orderService.requireOrder(orderId);
        if (existing.getPayableCents() != null && existing.getPayableCents() != req.paidCents()
                && (req.note() == null || req.note().isBlank())) {
            throw new com.guild.checkout.common.exception.BusinessException(
                    "实收金额与应付金额不一致，请填写差异说明");
        }
        ServiceOrder order = orderService.confirmPayment(orderId, req.paidCents(), operatorId, req.note());
        return ApiResponse.ok(assembler.assemble(order, OrderViewAssembler.Viewer.OPERATOR));
    }

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
