package com.guild.checkout.payment;

import com.guild.checkout.analytics.AnalyticsService;
import com.guild.checkout.analytics.EventKey;
import com.guild.checkout.auth.CurrentUser;
import com.guild.checkout.auth.CurrentUserHolder;
import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.common.exception.ForbiddenException;
import com.guild.checkout.order.OrderOperatorType;
import com.guild.checkout.order.OrderService;
import com.guild.checkout.order.OrderStateService;
import com.guild.checkout.order.OrderStatus;
import com.guild.checkout.order.ServiceOrder;
import com.guild.checkout.storage.FileStorage;
import com.guild.checkout.storage.StoredFile;
import com.guild.checkout.user.UserRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * 粉丝端的支付环节：取付款指引、上传凭证。
 */
@RestController
@RequestMapping("/api/orders/{orderId}/payment")
public class PaymentController {

    /** 凭证图片上限 5 MB。手机截图远小于此。 */
    private static final long MAX_PROOF_BYTES = 5L * 1024 * 1024;
    private static final List<String> IMAGE_TYPES =
            List.of("image/jpeg", "image/png", "image/webp", "image/heic");

    private final OrderService orderService;
    private final OrderStateService stateService;
    private final PaymentService paymentService;
    private final PaymentProofMapper proofMapper;
    private final FileStorage storage;
    private final AnalyticsService analytics;

    public PaymentController(OrderService orderService,
                             OrderStateService stateService,
                             PaymentService paymentService,
                             PaymentProofMapper proofMapper,
                             FileStorage storage,
                             AnalyticsService analytics) {
        this.orderService = orderService;
        this.stateService = stateService;
        this.paymentService = paymentService;
        this.proofMapper = proofMapper;
        this.storage = storage;
        this.analytics = analytics;
    }

    public record ProviderOption(String code, String displayName) {
    }

    public record PaymentPage(
            String orderRef,
            long payableCents,
            String status,
            String statusLabel,
            List<ProviderOption> providers,
            PaymentInstruction instruction,
            List<ProofView> proofs) {
    }

    public record ProofView(Long id, String url, java.time.LocalDateTime uploadedAt) {
    }

    /**
     * 支付环节页面所需的一切：应付金额、订单号、可用方式与付款指引、已传凭证。
     *
     * 支付方式被停用不会让已创建的待支付订单失效——此处只是不再给出该方式的指引。
     */
    @GetMapping
    public ApiResponse<PaymentPage> page(@PathVariable long orderId,
                                         @RequestParam(required = false) String provider) {
        ServiceOrder order = requireOwnOrder(orderId);
        List<PaymentProvider> enabled = paymentService.enabledProviders();

        PaymentInstruction instruction = null;
        String chosen = provider != null && !provider.isBlank()
                ? provider
                : enabled.stream().findFirst().map(PaymentProvider::code).orElse(null);
        if (chosen != null) {
            instruction = paymentService.instruct(order, chosen);
        }

        analytics.track(AnalyticsService.Track.of(EventKey.VIEW_PAYMENT_INFO)
                .user(order.getFanUserId()).talent(order.getTalentId())
                .serviceItem(order.getServiceItemId()).order(order.getOrderRef()));

        return ApiResponse.ok(new PaymentPage(
                order.getOrderRef(),
                order.getPayableCents(),
                order.statusEnum().name(),
                order.statusEnum().label(),
                enabled.stream().map(p -> new ProviderOption(p.code(), p.displayName())).toList(),
                instruction,
                listProofs(orderId)));
    }

    /**
     * 上传支付凭证。
     *
     * 上传成功后订单进入待确认收款；重复上传全部保留，状态保持不变。
     */
    @PostMapping("/proofs")
    public ApiResponse<ProofView> uploadProof(@PathVariable long orderId,
                                              @RequestParam("file") MultipartFile file) {
        ServiceOrder order = requireOwnOrder(orderId);
        OrderStatus status = order.statusEnum();
        if (status != OrderStatus.PENDING_PAYMENT && status != OrderStatus.PENDING_CONFIRM) {
            throw new BusinessException("当前订单状态为%s，不能上传凭证".formatted(status.label()));
        }
        validate(file);

        StoredFile stored;
        try (var in = file.getInputStream()) {
            stored = storage.store("payment-proof", file.getOriginalFilename(),
                    file.getContentType(), file.getSize(), in);
        } catch (IOException ex) {
            throw new BusinessException("读取上传文件失败");
        }

        PaymentProof proof = new PaymentProof();
        proof.setOrderId(order.getId());
        proof.setOrderRef(order.getOrderRef());
        proof.setUrl(stored.url());
        proof.setSizeBytes(stored.sizeBytes());
        proof.setContentType(stored.contentType());
        proof.setUploadedBy(CurrentUserHolder.requireUserId());
        proofMapper.insert(proof);

        // 首次上传推进状态；再次上传保持待确认收款不变
        if (status == OrderStatus.PENDING_PAYMENT) {
            stateService.transition(order, OrderStatus.PENDING_CONFIRM,
                    OrderOperatorType.FAN, CurrentUserHolder.requireUserId(), "上传支付凭证", null);
        }
        analytics.track(AnalyticsService.Track.of(EventKey.UPLOAD_PROOF)
                .user(order.getFanUserId()).talent(order.getTalentId())
                .serviceItem(order.getServiceItemId()).order(order.getOrderRef()));
        return ApiResponse.ok(new ProofView(proof.getId(), proof.getUrl(), proof.getCreatedAt()));
    }

    /** 自己传过的凭证。 */
    @GetMapping("/proofs")
    public ApiResponse<List<ProofView>> proofs(@PathVariable long orderId) {
        requireOwnOrder(orderId);
        return ApiResponse.ok(listProofs(orderId));
    }

    private List<ProofView> listProofs(long orderId) {
        return proofMapper.selectList(
                        com.baomidou.mybatisplus.core.toolkit.Wrappers.<PaymentProof>lambdaQuery()
                                .eq(PaymentProof::getOrderId, orderId)
                                .orderByAsc(PaymentProof::getId))
                .stream()
                .map(p -> new ProofView(p.getId(), p.getUrl(), p.getCreatedAt()))
                .toList();
    }

    /** 支付环节只对下单本人开放：别人不该看到也不该替你付。 */
    private ServiceOrder requireOwnOrder(long orderId) {
        CurrentUser current = CurrentUserHolder.require();
        ServiceOrder order = orderService.requireOrder(orderId);
        if (!orderService.isFan(order, current.userId())) {
            // 运营要看凭证走后台接口，不借这条路
            throw new ForbiddenException("只有下单本人可以操作支付");
        }
        return order;
    }

    private static void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的凭证图片");
        }
        if (file.getSize() > MAX_PROOF_BYTES) {
            throw new BusinessException("凭证图片不得超过 %d MB".formatted(MAX_PROOF_BYTES / 1024 / 1024));
        }
        String type = file.getContentType();
        if (type == null || !IMAGE_TYPES.contains(type.toLowerCase(Locale.ROOT))) {
            throw new BusinessException("凭证需为图片，不支持的格式：" + type);
        }
    }
}
