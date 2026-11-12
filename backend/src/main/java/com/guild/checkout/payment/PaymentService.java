package com.guild.checkout.payment;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.common.exception.NotFoundException;
import com.guild.checkout.order.OrderService;
import com.guild.checkout.order.OrderStatus;
import com.guild.checkout.order.ServiceOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 收款事件的消费入口。
 *
 * 这是订单与各支付方式之间唯一的接缝：无论人工对账还是微信支付回调，
 * 都收敛成一个 {@link PaymentConfirmed} 事件送进 {@link #confirm}。
 *
 * 幂等由 payment_record 的 (order_ref, provider, external_ref) 唯一索引保证，
 * 而不是靠先查后写——先查后写在并发回调下挡不住。
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRecordMapper recordMapper;
    private final PaymentEventLogMapper eventLogMapper;
    private final PaymentSettingMapper settingMapper;
    private final OrderService orderService;
    private final List<PaymentProvider> providers;

    public PaymentService(PaymentRecordMapper recordMapper,
                          PaymentEventLogMapper eventLogMapper,
                          PaymentSettingMapper settingMapper,
                          OrderService orderService,
                          List<PaymentProvider> providers) {
        this.recordMapper = recordMapper;
        this.eventLogMapper = eventLogMapper;
        this.settingMapper = settingMapper;
        this.orderService = orderService;
        this.providers = providers;
    }

    /** 收款事件的处理结果。 */
    public enum Outcome {
        /** 已入账并推进订单。 */
        ACCEPTED,
        /** 重复事件，已忽略。 */
        DUPLICATE,
        /** 订单状态不允许收款，已拒绝。 */
        REJECTED
    }

    public record ConfirmResult(Outcome outcome, ServiceOrder order, String detail) {
    }

    /**
     * 消费一次收款成功事件。
     *
     * 同一订单先后收到两次收款结果时，第二次被忽略且留痕，订单状态不二次推进。
     */
    @Transactional
    public ConfirmResult confirm(PaymentConfirmed event) {
        ServiceOrder order = orderService.findByRef(event.orderRef());
        if (order == null) {
            writeEventLog(event, PaymentEventLog.REJECTED, "订单不存在");
            throw new NotFoundException("订单不存在: " + event.orderRef());
        }

        OrderStatus status = order.statusEnum();
        // 已经收过款的订单再来事件：一律视为重复，不动状态
        if (status != OrderStatus.PENDING_PAYMENT && status != OrderStatus.PENDING_CONFIRM) {
            String detail = "订单当前为%s，不再接受收款".formatted(status.label());
            if (status.isPaid()) {
                writeEventLog(event, PaymentEventLog.DUPLICATE, detail);
                log.info("订单 {} 已收款，忽略重复收款事件 {}/{}",
                        event.orderRef(), event.provider(), event.externalRef());
                return new ConfirmResult(Outcome.DUPLICATE, order, detail);
            }
            writeEventLog(event, PaymentEventLog.REJECTED, detail);
            return new ConfirmResult(Outcome.REJECTED, order, detail);
        }

        // 幂等靠唯一索引：并发回调下先查后写挡不住，插入冲突才是可靠信号
        PaymentRecord record = new PaymentRecord();
        record.setOrderRef(event.orderRef());
        record.setOrderId(order.getId());
        record.setProvider(event.provider());
        record.setExternalRef(event.externalRef());
        record.setPaidCents(event.paidCents());
        record.setOperatorId(event.operatorId());
        record.setNote(event.note());
        record.setOccurredAt(event.occurredAt());
        try {
            recordMapper.insert(record);
        } catch (DuplicateKeyException ex) {
            String detail = "该流水已入账，忽略重复事件";
            writeEventLog(event, PaymentEventLog.DUPLICATE, detail);
            log.info("流水 {}/{} 重复，订单 {} 状态不变",
                    event.provider(), event.externalRef(), event.orderRef());
            return new ConfirmResult(Outcome.DUPLICATE, order, detail);
        }

        ServiceOrder paid = orderService.confirmPayment(
                order.getId(), event.paidCents(), event.operatorId(), event.note());
        writeEventLog(event, PaymentEventLog.ACCEPTED, null);
        log.info("订单 {} 已收款 {} 分，来自 {}", event.orderRef(), event.paidCents(), event.provider());
        return new ConfirmResult(Outcome.ACCEPTED, paid, null);
    }

    // ---------- 支付方式 ----------

    /** 当前启用的支付方式。粉丝端只展示这些。 */
    public List<PaymentProvider> enabledProviders() {
        Map<String, PaymentSetting> settings = settingMapper
                .selectList(Wrappers.<PaymentSetting>lambdaQuery().eq(PaymentSetting::getEnabled, true))
                .stream()
                .collect(Collectors.toMap(PaymentSetting::getProvider, Function.identity()));
        return providers.stream().filter(p -> settings.containsKey(p.code())).toList();
    }

    public PaymentProvider requireProvider(String code) {
        return providers.stream()
                .filter(p -> p.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException("不支持的支付方式: " + code));
    }

    /** 产出付款指引。方式被停用时拒绝，但不影响已创建的待支付订单本身。 */
    public PaymentInstruction instruct(ServiceOrder order, String providerCode) {
        PaymentSetting setting = findSetting(providerCode);
        if (setting == null || !Boolean.TRUE.equals(setting.getEnabled())) {
            throw new BusinessException("该支付方式当前不可用，请选择其他方式");
        }
        return requireProvider(providerCode).instruct(order.getOrderRef(), order.getPayableCents());
    }

    public PaymentSetting findSetting(String provider) {
        return settingMapper.selectOne(
                Wrappers.<PaymentSetting>lambdaQuery().eq(PaymentSetting::getProvider, provider));
    }

    public List<PaymentSetting> listSettings() {
        return settingMapper.selectList(
                Wrappers.<PaymentSetting>lambdaQuery().orderByAsc(PaymentSetting::getProvider));
    }

    // ---------- 查询 ----------

    public List<PaymentRecord> recordsOf(long orderId) {
        return recordMapper.selectList(Wrappers.<PaymentRecord>lambdaQuery()
                .eq(PaymentRecord::getOrderId, orderId)
                .orderByAsc(PaymentRecord::getId));
    }

    public List<PaymentEventLog> eventLogsOf(String orderRef) {
        return eventLogMapper.selectList(Wrappers.<PaymentEventLog>lambdaQuery()
                .eq(PaymentEventLog::getOrderRef, orderRef)
                .orderByAsc(PaymentEventLog::getId));
    }

    private void writeEventLog(PaymentConfirmed event, String outcome, String detail) {
        PaymentEventLog entry = new PaymentEventLog();
        entry.setOrderRef(event.orderRef());
        entry.setProvider(event.provider());
        entry.setExternalRef(event.externalRef());
        entry.setPaidCents(event.paidCents());
        entry.setOutcome(outcome);
        entry.setDetail(detail);
        entry.setOccurredAt(LocalDateTime.now());
        eventLogMapper.insert(entry);
    }
}
