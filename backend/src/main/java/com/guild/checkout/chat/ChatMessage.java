package com.guild.checkout.chat;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 一条会话消息。
 *
 * 发出即送达，任何一方都不能撤回；运营可以下架违规消息，但那是留痕的软下架，
 * 原文仍在库里，供纠纷追溯。
 */
@TableName("chat_message")
public class ChatMessage {

    /** 发送方：粉丝。 */
    public static final String FAN = "FAN";
    /** 发送方：主播。 */
    public static final String TALENT = "TALENT";

    /** 文字消息。 */
    public static final String TEXT = "TEXT";
    /** 图片消息，content 存图片地址。 */
    public static final String IMAGE = "IMAGE";

    /** 可见。 */
    public static final String VISIBLE = "VISIBLE";
    /** 已被运营下架：原文保留，只是对双方不再展示。 */
    public static final String REMOVED = "REMOVED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private String senderRole;
    private Long senderUserId;
    private String contentType;
    /** 文字正文，或图片地址。 */
    private String content;
    private String status;
    private Long removedBy;
    private LocalDateTime removedAt;
    private String removeReason;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public String getSenderRole() { return senderRole; }
    public void setSenderRole(String senderRole) { this.senderRole = senderRole; }
    public Long getSenderUserId() { return senderUserId; }
    public void setSenderUserId(Long senderUserId) { this.senderUserId = senderUserId; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
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
