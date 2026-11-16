package com.guild.checkout.order;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 订单时限配置，全站单例。 */
@TableName("order_deadline_setting")
public class OrderDeadlineSetting {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer singletonKey;
    private Integer payMinutes;
    private Integer acceptMinutes;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getSingletonKey() { return singletonKey; }
    public void setSingletonKey(Integer singletonKey) { this.singletonKey = singletonKey; }
    public Integer getPayMinutes() { return payMinutes; }
    public void setPayMinutes(Integer payMinutes) { this.payMinutes = payMinutes; }
    public Integer getAcceptMinutes() { return acceptMinutes; }
    public void setAcceptMinutes(Integer acceptMinutes) { this.acceptMinutes = acceptMinutes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
