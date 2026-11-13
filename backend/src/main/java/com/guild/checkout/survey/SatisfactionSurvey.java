package com.guild.checkout.survey;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 一条满意度评分。
 *
 * 注意 fanUserId 的存在：它让运营可以追溯，但也正因为如此，
 * 任何给主播的响应都绝不能带上它或 orderId。
 */
@TableName("satisfaction_survey")
public class SatisfactionSurvey {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private String orderRef;
    private Long talentId;
    private Long fanUserId;
    private Integer score;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getOrderRef() { return orderRef; }
    public void setOrderRef(String orderRef) { this.orderRef = orderRef; }
    public Long getTalentId() { return talentId; }
    public void setTalentId(Long talentId) { this.talentId = talentId; }
    public Long getFanUserId() { return fanUserId; }
    public void setFanUserId(Long fanUserId) { this.fanUserId = fanUserId; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
