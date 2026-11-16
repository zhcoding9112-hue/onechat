package com.guild.checkout.audition;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 粉丝发布的试音需求。 */
@TableName("audition_request")
public class AuditionRequest {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long fanUserId;
    /** 想要的服务项目；为空表示不限。 */
    private Long serviceItemId;
    private String voiceDesc;
    /** OPEN / CLOSED。 */
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFanUserId() { return fanUserId; }
    public void setFanUserId(Long fanUserId) { this.fanUserId = fanUserId; }
    public Long getServiceItemId() { return serviceItemId; }
    public void setServiceItemId(Long serviceItemId) { this.serviceItemId = serviceItemId; }
    public String getVoiceDesc() { return voiceDesc; }
    public void setVoiceDesc(String voiceDesc) { this.voiceDesc = voiceDesc; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
