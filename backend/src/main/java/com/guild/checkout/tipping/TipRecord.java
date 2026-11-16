package com.guild.checkout.tipping;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/** 一笔打赏。 */
@TableName("tip_record")
public class TipRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String tipRef;
    private Long fanUserId;
    private Long talentId;
    private Long amountCents;
    private Long paidCents;
    private Integer commissionRateBp;
    private Long guildCents;
    private Long talentCents;
    private String status;
    private String message;
    private LocalDateTime payDeadline;
    private LocalDateTime paidAt;
    private LocalDateTime closedAt;
    private String closeReason;

    /** 与订单同理：版本号必须有初值，否则乐观锁拦截器会跳过版本条件。 */
    @Version
    private Integer version = 0;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public TipStatus statusEnum() {
        return status == null ? null : TipStatus.valueOf(status);
    }

    public void setStatusEnum(TipStatus value) {
        this.status = value == null ? null : value.name();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTipRef() { return tipRef; }
    public void setTipRef(String tipRef) { this.tipRef = tipRef; }
    public Long getFanUserId() { return fanUserId; }
    public void setFanUserId(Long fanUserId) { this.fanUserId = fanUserId; }
    public Long getTalentId() { return talentId; }
    public void setTalentId(Long talentId) { this.talentId = talentId; }
    public Long getAmountCents() { return amountCents; }
    public void setAmountCents(Long amountCents) { this.amountCents = amountCents; }
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
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public LocalDateTime getPayDeadline() { return payDeadline; }
    public void setPayDeadline(LocalDateTime payDeadline) { this.payDeadline = payDeadline; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
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
