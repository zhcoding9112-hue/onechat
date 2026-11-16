package com.guild.checkout.order;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * 服务订单。
 *
 * 带 @Version 的乐观锁：主播接单与超时处置可能同时发生，必须只有一方生效。
 */
@TableName("service_order")
public class ServiceOrder {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderRef;

    private Long fanUserId;
    private Long talentId;

    private Long serviceItemId;
    private Long serviceSpecId;
    private String serviceItemName;
    private String serviceSpecName;

    private Long unitPriceCents;
    private Integer quantity;
    /** 订单原价（未用券前）。分成以它为基数，与实付无关。 */
    private Long originalCents;
    /** 券抵扣金额，由公会承担。 */
    private Long couponDiscountCents;
    /** 使用的 user_coupon.id，没用券时为 null。 */
    private Long userCouponId;
    /** 粉丝实付 = 原价 - 券抵扣。支付侧只认它。 */
    private Long payableCents;
    private Long paidCents;
    private Integer commissionRateBp;
    private Long guildCents;
    private Long talentCents;

    private String status;
    private String contact;
    private String remark;

    private LocalDateTime payDeadline;
    private LocalDateTime acceptDeadline;
    private LocalDateTime paidAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime completedAt;
    private LocalDateTime closedAt;
    private String closeReason;

    /**
     * 乐观锁版本号。必须有初值：MyBatis-Plus 的乐观锁拦截器在版本号为 null 时
     * 会跳过版本条件，等于完全没有并发保护。
     */
    @Version
    private Integer version = 0;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public OrderStatus statusEnum() {
        return status == null ? null : OrderStatus.valueOf(status);
    }

    public void setStatusEnum(OrderStatus value) {
        this.status = value == null ? null : value.name();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderRef() { return orderRef; }
    public void setOrderRef(String orderRef) { this.orderRef = orderRef; }
    public Long getFanUserId() { return fanUserId; }
    public void setFanUserId(Long fanUserId) { this.fanUserId = fanUserId; }
    public Long getTalentId() { return talentId; }
    public void setTalentId(Long talentId) { this.talentId = talentId; }
    public Long getServiceItemId() { return serviceItemId; }
    public void setServiceItemId(Long serviceItemId) { this.serviceItemId = serviceItemId; }
    public Long getServiceSpecId() { return serviceSpecId; }
    public void setServiceSpecId(Long serviceSpecId) { this.serviceSpecId = serviceSpecId; }
    public String getServiceItemName() { return serviceItemName; }
    public void setServiceItemName(String serviceItemName) { this.serviceItemName = serviceItemName; }
    public String getServiceSpecName() { return serviceSpecName; }
    public void setServiceSpecName(String serviceSpecName) { this.serviceSpecName = serviceSpecName; }
    public Long getUnitPriceCents() { return unitPriceCents; }
    public void setUnitPriceCents(Long unitPriceCents) { this.unitPriceCents = unitPriceCents; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Long getOriginalCents() { return originalCents; }
    public void setOriginalCents(Long originalCents) { this.originalCents = originalCents; }
    public Long getCouponDiscountCents() { return couponDiscountCents; }
    public void setCouponDiscountCents(Long couponDiscountCents) { this.couponDiscountCents = couponDiscountCents; }
    public Long getUserCouponId() { return userCouponId; }
    public void setUserCouponId(Long userCouponId) { this.userCouponId = userCouponId; }
    public Long getPayableCents() { return payableCents; }
    public void setPayableCents(Long payableCents) { this.payableCents = payableCents; }
    public Long getPaidCents() { return paidCents; }
    public void setPaidCents(Long paidCents) { this.paidCents = paidCents; }
    public Integer getCommissionRateBp() { return commissionRateBp; }
    public void setCommissionRateBp(Integer commissionRateBp) { this.commissionRateBp = commissionRateBp; }
    public Long getGuildCents() { return guildCents; }
    public void setGuildCents(Long guildCents) { this.guildCents = guildCents; }
    public Long getTalentCents() { return talentCents; }
    public void setTalentCents(Long talentCents) { this.talentCents = talentCents; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getPayDeadline() { return payDeadline; }
    public void setPayDeadline(LocalDateTime payDeadline) { this.payDeadline = payDeadline; }
    public LocalDateTime getAcceptDeadline() { return acceptDeadline; }
    public void setAcceptDeadline(LocalDateTime acceptDeadline) { this.acceptDeadline = acceptDeadline; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime acceptedAt) { this.acceptedAt = acceptedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public String getCloseReason() { return closeReason; }
    public void setCloseReason(String closeReason) { this.closeReason = closeReason; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
