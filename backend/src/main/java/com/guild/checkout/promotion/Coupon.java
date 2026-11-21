package com.guild.checkout.promotion;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 优惠券模板。面额与门槛均为整数分。 */
@TableName("coupon")
public class Coupon {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long amountCents;
    private Long thresholdCents;
    /** 为空表示不限品类。 */
    private Long serviceItemId;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private Boolean enabled;
    /** 注册时自动发放给新用户。见 V22 迁移的说明。 */
    private Boolean autoGrantOnRegister;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getAmountCents() { return amountCents; }
    public void setAmountCents(Long amountCents) { this.amountCents = amountCents; }
    public Long getThresholdCents() { return thresholdCents; }
    public void setThresholdCents(Long thresholdCents) { this.thresholdCents = thresholdCents; }
    public Long getServiceItemId() { return serviceItemId; }
    public void setServiceItemId(Long serviceItemId) { this.serviceItemId = serviceItemId; }
    public LocalDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDateTime validFrom) { this.validFrom = validFrom; }
    public LocalDateTime getValidTo() { return validTo; }
    public void setValidTo(LocalDateTime validTo) { this.validTo = validTo; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Boolean getAutoGrantOnRegister() { return autoGrantOnRegister; }
    public void setAutoGrantOnRegister(Boolean v) { this.autoGrantOnRegister = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
