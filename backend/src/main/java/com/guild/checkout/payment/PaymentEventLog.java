package com.guild.checkout.payment;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 收款事件的处理结果。被忽略的重复事件也会留一条。 */
@TableName("payment_event_log")
public class PaymentEventLog {

    public static final String ACCEPTED = "ACCEPTED";
    public static final String DUPLICATE = "DUPLICATE";
    public static final String REJECTED = "REJECTED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderRef;
    private String provider;
    private String externalRef;
    private Long paidCents;
    private String outcome;
    private String detail;
    private LocalDateTime occurredAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderRef() { return orderRef; }
    public void setOrderRef(String orderRef) { this.orderRef = orderRef; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getExternalRef() { return externalRef; }
    public void setExternalRef(String externalRef) { this.externalRef = externalRef; }
    public Long getPaidCents() { return paidCents; }
    public void setPaidCents(Long paidCents) { this.paidCents = paidCents; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
}
