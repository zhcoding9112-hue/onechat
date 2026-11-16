package com.guild.checkout.leaderboard;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 历史周期的榜单快照。固化后不再变化。 */
@TableName("leaderboard_snapshot")
public class LeaderboardSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String periodType;
    private String periodKey;
    private Long talentId;
    /** rank 是 MySQL 8 的保留字，列名用 rank_no。 */
    private Integer rankNo;
    private Long amountCents;
    private Integer tipCount;
    private String nickname;
    private LocalDateTime frozenAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPeriodType() { return periodType; }
    public void setPeriodType(String periodType) { this.periodType = periodType; }
    public String getPeriodKey() { return periodKey; }
    public void setPeriodKey(String periodKey) { this.periodKey = periodKey; }
    public Long getTalentId() { return talentId; }
    public void setTalentId(Long talentId) { this.talentId = talentId; }
    public Integer getRankNo() { return rankNo; }
    public void setRankNo(Integer rankNo) { this.rankNo = rankNo; }
    public Long getAmountCents() { return amountCents; }
    public void setAmountCents(Long amountCents) { this.amountCents = amountCents; }
    public Integer getTipCount() { return tipCount; }
    public void setTipCount(Integer tipCount) { this.tipCount = tipCount; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public LocalDateTime getFrozenAt() { return frozenAt; }
    public void setFrozenAt(LocalDateTime frozenAt) { this.frozenAt = frozenAt; }
}
