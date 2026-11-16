package com.guild.checkout.level;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 一档粉丝等级门槛。 */
@TableName("fan_level_setting")
public class FanLevelSetting {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer level;
    private String name;
    private Long thresholdCents;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getThresholdCents() { return thresholdCents; }
    public void setThresholdCents(Long thresholdCents) { this.thresholdCents = thresholdCents; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
