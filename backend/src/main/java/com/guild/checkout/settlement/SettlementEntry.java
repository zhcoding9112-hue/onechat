package com.guild.checkout.settlement;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 一条分账记录。金额全部取自来源上的快照。 */
@TableName("settlement_entry")
public class SettlementEntry {

    public static final String SOURCE_ORDER = "ORDER";
    public static final String SOURCE_TIP = "TIP";
    public static final String ACTIVE = "ACTIVE";
    public static final String REVERSED = "REVERSED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String sourceType;
    private Long sourceId;
    private String sourceRef;
    private Long talentId;
    private Long amountCents;
    private Integer commissionRateBp;
    private Long guildCents;
    private Long talentCents;
    /** 促销成本（券抵扣），由公会承担。不影响 amountCents 与分成。 */
    private Long promoCostCents;
    private String status;
    private LocalDateTime reversedAt;
    private String reverseReason;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isActive() {
        return ACTIVE.equals(status);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public String getSourceRef() { return sourceRef; }
    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }
    public Long getTalentId() { return talentId; }
    public void setTalentId(Long talentId) { this.talentId = talentId; }
    public Long getAmountCents() { return amountCents; }
    public void setAmountCents(Long amountCents) { this.amountCents = amountCents; }
    public Integer getCommissionRateBp() { return commissionRateBp; }
    public void setCommissionRateBp(Integer commissionRateBp) { this.commissionRateBp = commissionRateBp; }
    public Long getGuildCents() { return guildCents; }
    public void setGuildCents(Long guildCents) { this.guildCents = guildCents; }
    public Long getTalentCents() { return talentCents; }
    public void setTalentCents(Long talentCents) { this.talentCents = talentCents; }
    public Long getPromoCostCents() { return promoCostCents; }
    public void setPromoCostCents(Long promoCostCents) { this.promoCostCents = promoCostCents; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getReversedAt() { return reversedAt; }
    public void setReversedAt(LocalDateTime reversedAt) { this.reversedAt = reversedAt; }
    public String getReverseReason() { return reverseReason; }
    public void setReverseReason(String reverseReason) { this.reverseReason = reverseReason; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
