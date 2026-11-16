package com.guild.checkout.feed;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 主播发布的一条动态。下架是软的，内容保留。 */
@TableName("feed_post")
public class FeedPost {

    /** 可见。 */
    public static final String VISIBLE = "VISIBLE";
    /** 已下架：内容原样保留，只是对粉丝不可见。 */
    public static final String REMOVED = "REMOVED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long talentId;
    /** 正文；可为空，但不能与图片同时为空 */
    private String content;
    private String status;
    /** 下架操作者 app_user.id */
    private Long removedBy;
    private LocalDateTime removedAt;
    private String removeReason;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTalentId() { return talentId; }
    public void setTalentId(Long talentId) { this.talentId = talentId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getRemovedBy() { return removedBy; }
    public void setRemovedBy(Long removedBy) { this.removedBy = removedBy; }
    public LocalDateTime getRemovedAt() { return removedAt; }
    public void setRemovedAt(LocalDateTime removedAt) { this.removedAt = removedAt; }
    public String getRemoveReason() { return removeReason; }
    public void setRemoveReason(String removeReason) { this.removeReason = removeReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
