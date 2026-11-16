package com.guild.checkout.pricing;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 分成比例配置。talentId 为 null 表示全局默认。 */
@TableName("commission_config")
public class CommissionConfig {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long talentId;
    /** 公会分成比例，基点（万分之一）。 */
    private Integer rateBp;
    /** 数据库生成列，只读。 */
    @TableField(exist = false)
    private Long talentKey;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTalentId() { return talentId; }
    public void setTalentId(Long talentId) { this.talentId = talentId; }
    public Integer getRateBp() { return rateBp; }
    public void setRateBp(Integer rateBp) { this.rateBp = rateBp; }
    public Long getTalentKey() { return talentKey; }
    public void setTalentKey(Long talentKey) { this.talentKey = talentKey; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
