package com.guild.checkout.analytics;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 看板配置，全站单例。 */
@TableName("analytics_setting")
public class AnalyticsSetting {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer singletonKey;
    private Integer acceptRateThresholdBp;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getSingletonKey() { return singletonKey; }
    public void setSingletonKey(Integer singletonKey) { this.singletonKey = singletonKey; }
    public Integer getAcceptRateThresholdBp() { return acceptRateThresholdBp; }
    public void setAcceptRateThresholdBp(Integer v) { this.acceptRateThresholdBp = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
