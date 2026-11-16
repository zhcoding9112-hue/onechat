package com.guild.checkout.credit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/** 一位粉丝的元子账户。余额为整数计数，不可为负。 */
@TableName("credit_account")
public class CreditAccount {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long balance;

    /** 与订单、打赏同理：版本号必须有初值，否则乐观锁拦截器会跳过版本条件。 */
    @Version
    private Integer version = 0;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getBalance() { return balance; }
    public void setBalance(Long balance) { this.balance = balance; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
