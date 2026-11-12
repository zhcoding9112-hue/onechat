package com.guild.checkout.talent;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 主播档案。与微信账号可空关联：运营可先建档案，主播登录后再关联。 */
@TableName("talent")
public class Talent {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String nickname;
    private String coverUrl;
    private String intro;
    private String audioUrl;
    private Integer audioSeconds;
    /** 在线状态。仅作展示与预期管理，不阻止粉丝下单。 */
    private Boolean online;
    /** 是否上架。未上架不出现在粉丝端列表与详情。 */
    private Boolean listed;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }
    public String getAudioUrl() { return audioUrl; }
    public void setAudioUrl(String audioUrl) { this.audioUrl = audioUrl; }
    public Integer getAudioSeconds() { return audioSeconds; }
    public void setAudioSeconds(Integer audioSeconds) { this.audioSeconds = audioSeconds; }
    public Boolean getOnline() { return online; }
    public void setOnline(Boolean online) { this.online = online; }
    public Boolean getListed() { return listed; }
    public void setListed(Boolean listed) { this.listed = listed; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
