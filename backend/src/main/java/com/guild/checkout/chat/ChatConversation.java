package com.guild.checkout.chat;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 一位粉丝与一位主播之间的会话。(fanUserId, talentId) 唯一。 */
@TableName("chat_conversation")
public class ChatConversation {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long fanUserId;
    private Long talentId;
    private Long lastMessageId;
    /** 排序键。无消息时等于会话创建时间，新会话因此也排在列表前面。 */
    private LocalDateTime lastMessageAt;
    /** 粉丝已读到的消息 id。只增不减。 */
    private Long fanReadUntil;
    /** 主播已读到的消息 id。只增不减。 */
    private Long talentReadUntil;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFanUserId() { return fanUserId; }
    public void setFanUserId(Long fanUserId) { this.fanUserId = fanUserId; }
    public Long getTalentId() { return talentId; }
    public void setTalentId(Long talentId) { this.talentId = talentId; }
    public Long getLastMessageId() { return lastMessageId; }
    public void setLastMessageId(Long lastMessageId) { this.lastMessageId = lastMessageId; }
    public LocalDateTime getLastMessageAt() { return lastMessageAt; }
    public void setLastMessageAt(LocalDateTime lastMessageAt) { this.lastMessageAt = lastMessageAt; }
    public Long getFanReadUntil() { return fanReadUntil; }
    public void setFanReadUntil(Long fanReadUntil) { this.fanReadUntil = fanReadUntil; }
    public Long getTalentReadUntil() { return talentReadUntil; }
    public void setTalentReadUntil(Long talentReadUntil) { this.talentReadUntil = talentReadUntil; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
