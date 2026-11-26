package com.guild.checkout.talent;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
    /** 性别：FEMALE / MALE。可空——已有档案未填写。 */
    private String gender;
    /** 年龄。可空。 */
    private Integer age;
    /** 主播等级，复用现有 talent.level 字段。 */
    private Integer level;
    /** 地区。可空。 */
    private String region;
    private String coverUrl;
    private String intro;
    // 必须显式声明列名：MyBatis-Plus 的驼峰转下划线不会在字母与数字之间加分隔，
    // promoTag1 会被映射成 promo_tag1，与迁移建的 promo_tag_1 对不上。
    /** 宣传语标签一。主播或运营自填的自由文本，系统不提供文案模板。 */
    @TableField("promo_tag_1")
    private String promoTag1;
    /** 宣传语标签二。同上。 */
    @TableField("promo_tag_2")
    private String promoTag2;
    private String audioUrl;
    private Integer audioSeconds;
    /** 在线状态。仅作展示与预期管理，不阻止粉丝下单。 */
    private Boolean online;
    /** 是否上架。未上架不出现在粉丝端列表与详情。 */
    private Boolean listed;
    private Integer sortOrder;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getPromoTag1() { return promoTag1; }
    public void setPromoTag1(String promoTag1) { this.promoTag1 = promoTag1; }
    public String getPromoTag2() { return promoTag2; }
    public void setPromoTag2(String promoTag2) { this.promoTag2 = promoTag2; }
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
