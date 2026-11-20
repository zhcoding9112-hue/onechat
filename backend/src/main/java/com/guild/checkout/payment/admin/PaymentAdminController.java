package com.guild.checkout.payment.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.auth.CurrentUserHolder;
import com.guild.checkout.auth.RequireRole;
import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.order.OrderOperatorType;
import com.guild.checkout.order.OrderService;
import com.guild.checkout.order.OrderStateService;
import com.guild.checkout.order.OrderStatus;
import com.guild.checkout.order.OrderView;
import com.guild.checkout.order.OrderViewAssembler;
import com.guild.checkout.order.ServiceOrder;
import com.guild.checkout.order.ServiceOrderMapper;
import com.guild.checkout.payment.ManualPaymentProvider;
import com.guild.checkout.payment.PaymentConfirmed;
import com.guild.checkout.payment.PaymentProof;
import com.guild.checkout.payment.PaymentProofMapper;
import com.guild.checkout.payment.PaymentService;
import com.guild.checkout.payment.PaymentSetting;
import com.guild.checkout.payment.PaymentSettingMapper;
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
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 运营侧的对账与支付配置。整类要求运营角色。
 */
@RestController
@RequestMapping("/api/admin/payment")
@RequireRole(UserRole.OPERATOR)
public class PaymentAdminController {

    private final PaymentService paymentService;
    private final PaymentSettingMapper settingMapper;
    private final PaymentProofMapper proofMapper;
    private final ServiceOrderMapper orderMapper;
    private final OrderService orderService;
    private final OrderStateService stateService;
    private final OrderViewAssembler assembler;

    public PaymentAdminController(PaymentService paymentService,
                                  PaymentSettingMapper settingMapper,
                                  PaymentProofMapper proofMapper,
                                  ServiceOrderMapper orderMapper,
                                  OrderService orderService,
                                  OrderStateService stateService,
                                  OrderViewAssembler assembler) {
        this.paymentService = paymentService;
        this.settingMapper = settingMapper;
        this.proofMapper = proofMapper;
        this.orderMapper = orderMapper;
        this.orderService = orderService;
        this.stateService = stateService;
        this.assembler = assembler;
    }

    // ---------- 待对账 ----------

    /**
     * 待对账的一行。
     *
     * 除了金额与凭证，还带上「这笔钱是买什么的」：运营要判断该确认还是该驳回，
     * 光有订单号和金额不够——驳回时得在原因里说清是哪位主播的哪个服务。
     */
    public record ReconcileItem(
            Long orderId,
            String orderRef,
            long payableCents,
            String status,
            String statusLabel,
            LocalDateTime createdAt,
            List<String> proofUrls,
            Long talentId,
            String talentNickname,
            String serviceItemName,
            String serviceSpecName,
            int quantity,
            long originalCents,
            long couponDiscountCents,
            String remark,
            LocalDateTime payDeadline) {
    }

    /** 待对账列表：已上传凭证等待核对的订单，最早的排在前面。 */
    @GetMapping("/reconcile")
    public ApiResponse<List<ReconcileItem>> reconcileQueue() {
        List<ServiceOrder> orders = orderMapper.selectList(Wrappers.<ServiceOrder>lambdaQuery()
                .eq(ServiceOrder::getStatus, OrderStatus.PENDING_CONFIRM.name())
                .orderByAsc(ServiceOrder::getId));
        if (orders.isEmpty()) {
            return ApiResponse.ok(List.of());
        }

        Map<Long, List<PaymentProof>> proofs = proofMapper.selectList(
                        Wrappers.<PaymentProof>lambdaQuery()
                                .in(PaymentProof::getOrderId, orders.stream().map(ServiceOrder::getId).toList())
                                .orderByAsc(PaymentProof::getId))
                .stream().collect(Collectors.groupingBy(PaymentProof::getOrderId));

        return ApiResponse.ok(orders.stream().map(o -> {
            // 主播昵称等上下文沿用订单视图的装配逻辑，避免这里重复一套取名规则
            OrderView view = assembler.assemble(o, OrderViewAssembler.Viewer.OPERATOR);
            return new ReconcileItem(
                    o.getId(), o.getOrderRef(), o.getPayableCents(),
                    o.statusEnum().name(), o.statusEnum().label(), o.getCreatedAt(),
                    proofs.getOrDefault(o.getId(), List.of()).stream().map(PaymentProof::getUrl).toList(),
                    view.talentId(), view.talentNickname(),
                    view.serviceItemName(), view.serviceSpecName(), view.quantity(),
                    view.originalCents(), view.couponDiscountCents(),
                    view.remark(), view.payDeadline());
        }).toList());
    }

    // ---------- 确认与驳回 ----------

    public record ConfirmRequest(
            @Min(value = 0, message = "实收金额不得为负") long paidCents,
            @Size(max = 64, message = "流水号不超过 64 字符") String externalRef,
            @Size(max = 255, message = "差异说明不超过 255 字符") String note) {
    }

    public record RejectRequest(
            @NotBlank(message = "驳回原因为必填")
            @Size(max = 255, message = "驳回原因不超过 255 字符") String reason) {
    }

    /**
     * 确认收款。走与微信支付回调同一条路径：构造 PaymentConfirmed 事件交给 PaymentService。
     * 实收与应付不一致时必须填差异说明。
     */
    @PutMapping("/{orderId}/confirm")
    public ApiResponse<Map<String, Object>> confirm(@PathVariable long orderId,
                                                    @Valid @RequestBody ConfirmRequest req) {
        Long operatorId = CurrentUserHolder.requireUserId();
        ServiceOrder order = orderService.requireOrder(orderId);

        if (order.getPayableCents() != null && order.getPayableCents() != req.paidCents()
                && (req.note() == null || req.note().isBlank())) {
            throw new BusinessException("实收金额与应付金额不一致，请填写差异说明");
        }

        // 运营没填流水号时用订单号加时间兜底，保证幂等键存在
        String externalRef = req.externalRef() != null && !req.externalRef().isBlank()
                ? req.externalRef().trim()
                : "manual-" + order.getOrderRef();

        PaymentService.ConfirmResult result = paymentService.confirm(new PaymentConfirmed(
                order.getOrderRef(), req.paidCents(), ManualPaymentProvider.CODE,
                externalRef, operatorId, LocalDateTime.now(), req.note()));

        return ApiResponse.ok(Map.of(
                "outcome", result.outcome().name(),
                "status", result.order().statusEnum().name(),
                "statusLabel", result.order().statusEnum().label(),
                "detail", result.detail() == null ? "" : result.detail()));
    }

    /** 驳回凭证：订单退回待支付，粉丝可重新上传。 */
    @PutMapping("/{orderId}/reject")
    public ApiResponse<Map<String, Object>> reject(@PathVariable long orderId,
                                                   @Valid @RequestBody RejectRequest req) {
        Long operatorId = CurrentUserHolder.requireUserId();
        ServiceOrder order = orderService.requireOrder(orderId);
        if (order.statusEnum() != OrderStatus.PENDING_CONFIRM) {
            throw new BusinessException("只有待确认收款的订单可以驳回");
        }
        ServiceOrder back = stateService.transition(order, OrderStatus.PENDING_PAYMENT,
                OrderOperatorType.OPERATOR, operatorId, req.reason().trim(), null);
        return ApiResponse.ok(Map.of(
                "status", back.statusEnum().name(),
                "statusLabel", back.statusEnum().label(),
                "reason", req.reason().trim()));
    }

    // ---------- 支付方式配置 ----------

    public record SettingRequest(
            @Size(max = 64) String accountName,
            @Size(max = 64) String accountNo,
            @Size(max = 64) String bankName,
            @Size(max = 512) String qrUrl,
            @Size(max = 500) String tips) {
    }

    @GetMapping("/settings")
    public ApiResponse<List<PaymentSetting>> settings() {
        return ApiResponse.ok(paymentService.listSettings());
    }

    /** 更换收款信息。此后进入支付环节的订单展示新信息。 */
    @PutMapping("/settings/{provider}")
    public ApiResponse<PaymentSetting> updateSetting(@PathVariable String provider,
                                                     @Valid @RequestBody SettingRequest req) {
        PaymentSetting setting = requireSetting(provider);
        if (req.accountName() != null) setting.setAccountName(trimToNull(req.accountName()));
        if (req.accountNo() != null) setting.setAccountNo(trimToNull(req.accountNo()));
        if (req.bankName() != null) setting.setBankName(trimToNull(req.bankName()));
        if (req.qrUrl() != null) setting.setQrUrl(trimToNull(req.qrUrl()));
        if (req.tips() != null) setting.setTips(trimToNull(req.tips()));
        // 可置空的字段用显式 set，updateById 会跳过 null
        settingMapper.update(null, Wrappers.<PaymentSetting>lambdaUpdate()
                .eq(PaymentSetting::getId, setting.getId())
                .set(PaymentSetting::getAccountName, setting.getAccountName())
                .set(PaymentSetting::getAccountNo, setting.getAccountNo())
                .set(PaymentSetting::getBankName, setting.getBankName())
                .set(PaymentSetting::getQrUrl, setting.getQrUrl())
                .set(PaymentSetting::getTips, setting.getTips()));
        return ApiResponse.ok(requireSetting(provider));
    }

    /** 启停支付方式。停用不影响已创建的待支付订单。 */
    @PutMapping("/settings/{provider}/enabled/{enabled}")
    public ApiResponse<PaymentSetting> setEnabled(@PathVariable String provider,
                                                  @PathVariable boolean enabled) {
        PaymentSetting setting = requireSetting(provider);
        setting.setEnabled(enabled);
        settingMapper.updateById(setting);
        return ApiResponse.ok(setting);
    }

    private PaymentSetting requireSetting(String provider) {
        PaymentSetting setting = paymentService.findSetting(provider);
        if (setting == null) {
            throw new BusinessException("未知的支付方式: " + provider);
        }
        return setting;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
