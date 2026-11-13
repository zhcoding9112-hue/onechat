package com.guild.checkout.tipping;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 打赏金额上下限，全站单例。 */
@TableName("tip_setting")
public class TipSetting {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer singletonKey;
    private Long minCents;
    private Long maxCents;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getSingletonKey() { return singletonKey; }
    public void setSingletonKey(Integer singletonKey) { this.singletonKey = singletonKey; }
    public Long getMinCents() { return minCents; }
    public void setMinCents(Long minCents) { this.minCents = minCents; }
    public Long getMaxCents() { return maxCents; }
    public void setMaxCents(Long maxCents) { this.maxCents = maxCents; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
