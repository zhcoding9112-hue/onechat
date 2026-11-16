package com.guild.checkout.audition;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.admin.TalentAdminService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 试音选人（tasks 5.3 - 5.6）。
 */
@SpringBootTest
@Transactional
class AuditionTest {

    private static final long FAN_ID = 999_000_003L;
    private static final long OTHER_FAN_ID = 999_000_004L;

    @Autowired private AuditionService auditionService;
    @Autowired private AuditionRequestMapper requestMapper;
    @Autowired private TalentAdminService talentAdminService;
    @Autowired private JdbcTemplate jdbc;

    /** 建一位带试听片段的主播——没有片段的主播不允许应征。 */
    private Talent newTalentWithAudio(String nickname) {
        Talent t = talentAdminService.create(new TalentAdminService.ProfileForm(
                nickname, null, null, 0, null, null, null, null));
        talentAdminService.setListed(t.getId(), true);
        jdbc.update("UPDATE talent SET audio_url = ?, audio_seconds = 12 WHERE id = ?",
                "/uploads/talent-audio/test.mp3", t.getId());
        return t;
    }

    private void expireRequest(long requestId) {
        jdbc.update("UPDATE audition_request SET expires_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(1), requestId);
    }

    @Test
    @DisplayName("5.3 发布需求后可读回，状态为进行中")
    void 发布需求() {
        AuditionRequest req = auditionService.publish(FAN_ID, null, "声音偏低，语速慢一点");

        assertThat(req.getStatus()).isEqualTo(AuditionService.OPEN);
        assertThat(auditionService.findActive(FAN_ID)).isPresent();
    }

    @Test
    @DisplayName("5.3 空描述被拒绝")
    void 空描述被拒绝() {
        assertThatThrownBy(() -> auditionService.publish(FAN_ID, null, "   "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    @DisplayName("5.3 已有进行中的需求时再次发布被拒绝")
    void 同时只允许一条进行中() {
        auditionService.publish(FAN_ID, null, "第一条");

        assertThatThrownBy(() -> auditionService.publish(FAN_ID, null, "第二条"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有一条进行中");
    }

    @Test
    @DisplayName("5.3 关闭旧需求后可以再发一条")
    void 关闭后可再发() {
        AuditionRequest first = auditionService.publish(FAN_ID, null, "第一条");
        auditionService.closeOwn(FAN_ID, first.getId());

        AuditionRequest second = auditionService.publish(FAN_ID, null, "第二条");
        assertThat(second.getId()).isNotEqualTo(first.getId());
    }

    @Test
    @DisplayName("5.3 不能关闭别人的需求")
    void 不能关闭他人需求() {
        AuditionRequest req = auditionService.publish(FAN_ID, null, "我的需求");

        assertThatThrownBy(() -> auditionService.closeOwn(OTHER_FAN_ID, req.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能关闭自己");
    }

    @Test
    @DisplayName("5.4 过期需求在被读到时自动关闭，且不再算作进行中")
    void 过期自动关闭() {
        AuditionRequest req = auditionService.publish(FAN_ID, null, "会过期的需求");
        expireRequest(req.getId());

        assertThat(auditionService.findActive(FAN_ID)).isEmpty();

        AuditionRequest reloaded = requestMapper.selectById(req.getId());
        assertThat(reloaded.getStatus()).isEqualTo(AuditionService.CLOSED);
        assertThat(reloaded.getClosedAt()).isNotNull();
    }

    @Test
    @DisplayName("5.4 过期后可以立刻发布新的，不被旧需求挡住")
    void 过期后可再发() {
        AuditionRequest req = auditionService.publish(FAN_ID, null, "会过期的需求");
        expireRequest(req.getId());

        AuditionRequest fresh = auditionService.publish(FAN_ID, null, "新需求");
        assertThat(fresh.getId()).isNotEqualTo(req.getId());
    }

    @Test
    @DisplayName("5.4 过期需求不出现在主播的开放列表中")
    void 过期需求不在开放列表() {
        AuditionRequest alive = auditionService.publish(FAN_ID, null, "还活着");
        AuditionRequest dead = auditionService.publish(OTHER_FAN_ID, null, "已过期");
        expireRequest(dead.getId());

        List<Long> openIds = auditionService.listOpen(100).stream()
                .map(AuditionRequest::getId).toList();

        assertThat(openIds).contains(alive.getId()).doesNotContain(dead.getId());
    }

    @Test
    @DisplayName("5.5 主播应征成功，重复应征被拒绝")
    void 应征与重复应征() {
        AuditionRequest req = auditionService.publish(FAN_ID, null, "求温柔声音");
        Talent talent = newTalentWithAudio("应征的主播");

        auditionService.reply(talent.getId(), req.getId(), "我来试试");

        assertThatThrownBy(() -> auditionService.reply(talent.getId(), req.getId(), "再来一次"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已应征过");

        assertThat(auditionService.listReplies(req.getId())).hasSize(1);
    }

    @Test
    @DisplayName("5.5 没有试听片段的主播不能应征")
    void 无试听片段不能应征() {
        AuditionRequest req = auditionService.publish(FAN_ID, null, "求温柔声音");
        Talent noAudio = talentAdminService.create(new TalentAdminService.ProfileForm(
                "没传片段的", null, null, 0, null, null, null, null));

        assertThatThrownBy(() -> auditionService.reply(noAudio.getId(), req.getId(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("先上传试听片段");
    }

    @Test
    @DisplayName("5.5 对已关闭的需求应征被拒绝")
    void 对已关闭需求应征被拒绝() {
        AuditionRequest req = auditionService.publish(FAN_ID, null, "马上关掉");
        Talent talent = newTalentWithAudio("晚来的主播");
        auditionService.closeOwn(FAN_ID, req.getId());

        assertThatThrownBy(() -> auditionService.reply(talent.getId(), req.getId(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已关闭");
    }

    @Test
    @DisplayName("5.5 对已过期的需求应征被拒绝，并顺手把它关掉")
    void 对已过期需求应征被拒绝() {
        AuditionRequest req = auditionService.publish(FAN_ID, null, "会过期");
        Talent talent = newTalentWithAudio("太晚的主播");
        expireRequest(req.getId());

        assertThatThrownBy(() -> auditionService.reply(talent.getId(), req.getId(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已关闭");

        assertThat(requestMapper.selectById(req.getId()).getStatus())
                .isEqualTo(AuditionService.CLOSED);
    }

    @Test
    @DisplayName("5.6 应征列表按应征时间正序，多位主播互不覆盖")
    void 应征列表按时间正序() throws Exception {
        AuditionRequest req = auditionService.publish(FAN_ID, null, "求声音");
        Talent first = newTalentWithAudio("先应征的");
        Talent second = newTalentWithAudio("后应征的");

        auditionService.reply(first.getId(), req.getId(), "我先来");
        Thread.sleep(5);
        auditionService.reply(second.getId(), req.getId(), "我也来");

        List<Long> ids = auditionService.listReplies(req.getId()).stream()
                .map(AuditionReply::getTalentId).toList();

        assertThat(ids).containsExactly(first.getId(), second.getId());
    }

    @Test
    @DisplayName("5.6 关闭需求后，已有的应征记录仍可查看")
    void 关闭后仍可查看应征() {
        AuditionRequest req = auditionService.publish(FAN_ID, null, "求声音");
        Talent talent = newTalentWithAudio("应征的");
        auditionService.reply(talent.getId(), req.getId(), "我来");

        auditionService.closeOwn(FAN_ID, req.getId());

        assertThat(auditionService.listReplies(req.getId())).hasSize(1);
    }

    @Test
    @DisplayName("5.5 唯一索引确实存在：绕过服务层直插重复行会被数据库拒绝")
    void 唯一索引兜底() {
        AuditionRequest req = auditionService.publish(FAN_ID, null, "求声音");
        Talent talent = newTalentWithAudio("应征的");
        auditionService.reply(talent.getId(), req.getId(), null);

        // 幂等的最终保证在数据库而不是应用层判断，这里直接验证那道闸
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO audition_reply (request_id, talent_id) VALUES (?, ?)",
                req.getId(), talent.getId()))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test
    @DisplayName("5.4 已关闭的需求不会被再次写入关闭时间")
    void 重复关闭不覆盖关闭时间() {
        AuditionRequest req = auditionService.publish(FAN_ID, null, "求声音");
        auditionService.closeOwn(FAN_ID, req.getId());
        LocalDateTime firstClosedAt = requestMapper.selectById(req.getId()).getClosedAt();

        auditionService.closeOwn(FAN_ID, req.getId());

        assertThat(requestMapper.selectById(req.getId()).getClosedAt()).isEqualTo(firstClosedAt);
    }

    @Test
    @DisplayName("5.3 清理：本用例产生的需求不影响他人")
    void 需求按粉丝隔离() {
        auditionService.publish(FAN_ID, null, "我的");
        auditionService.publish(OTHER_FAN_ID, null, "别人的");

        assertThat(auditionService.findActive(FAN_ID)).isPresent();
        assertThat(auditionService.findActive(OTHER_FAN_ID)).isPresent();
        assertThat(requestMapper.selectCount(Wrappers.<AuditionRequest>lambdaQuery()
                .eq(AuditionRequest::getFanUserId, FAN_ID)
                .eq(AuditionRequest::getStatus, AuditionService.OPEN))).isEqualTo(1);
    }
}
