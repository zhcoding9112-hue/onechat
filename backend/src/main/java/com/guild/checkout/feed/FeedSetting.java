package com.guild.checkout.feed;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 动态相关的可配参数。单例。 */
@TableName("feed_setting")
public class FeedSetting {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer singletonKey;
    /** 单条动态的图片数量上限 */
    private Integer maxImages;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getSingletonKey() { return singletonKey; }
    public void setSingletonKey(Integer singletonKey) { this.singletonKey = singletonKey; }
    public Integer getMaxImages() { return maxImages; }
    public void setMaxImages(Integer maxImages) { this.maxImages = maxImages; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
