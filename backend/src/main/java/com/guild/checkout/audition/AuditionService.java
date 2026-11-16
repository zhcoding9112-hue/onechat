package com.guild.checkout.audition;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.TalentService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 试音选人。
 *
 * 过期关闭是惰性的：状态在被读到时按 expires_at 判定并落库，没有定时任务。
 * 所有在意「是否进行中」的路径都会先读一次（发布前查重、主播看开放列表、
 * 粉丝看自己的需求），所以不存在「已过期却没人发现」的窗口；
 * 多一个定时任务只会多一处可能漏跑的地方。
 */
@Service
public class AuditionService {

    public static final String OPEN = "OPEN";
    public static final String CLOSED = "CLOSED";

    /** 需求默认有效期。 */
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);
    private static final int MAX_DESC_LENGTH = 200;

    private final AuditionRequestMapper requestMapper;
    private final AuditionReplyMapper replyMapper;
    private final TalentService talentService;

    public AuditionService(AuditionRequestMapper requestMapper,
                           AuditionReplyMapper replyMapper,
                           TalentService talentService) {
        this.requestMapper = requestMapper;
        this.replyMapper = replyMapper;
        this.talentService = talentService;
    }

    /** 发布需求。同一粉丝同时只允许一条进行中。 */
    @Transactional
    public AuditionRequest publish(long fanUserId, Long serviceItemId, String voiceDesc) {
        String desc = voiceDesc == null ? "" : voiceDesc.trim();
        if (desc.isEmpty()) {
            throw new BusinessException("声音描述不能为空");
        }
        if (desc.length() > MAX_DESC_LENGTH) {
            throw new BusinessException("声音描述不超过 %d 字符".formatted(MAX_DESC_LENGTH));
        }
        if (findActive(fanUserId).isPresent()) {
            throw new BusinessException("你已有一条进行中的试音需求，请先关闭后再发布");
        }

        AuditionRequest req = new AuditionRequest();
        req.setFanUserId(fanUserId);
        req.setServiceItemId(serviceItemId);
        req.setVoiceDesc(desc);
        req.setStatus(OPEN);
        req.setExpiresAt(LocalDateTime.now().plus(DEFAULT_TTL));
        requestMapper.insert(req);
        return req;
    }

    /**
     * 取该粉丝当前进行中的需求。
     *
     * 读的同时做过期判定：取出来标着 OPEN 但已过 expires_at 的，就地关闭并视为无。
     */
    @Transactional
    public Optional<AuditionRequest> findActive(long fanUserId) {
        AuditionRequest req = requestMapper.selectOne(Wrappers.<AuditionRequest>lambdaQuery()
                .eq(AuditionRequest::getFanUserId, fanUserId)
                .eq(AuditionRequest::getStatus, OPEN)
                .orderByDesc(AuditionRequest::getId)
                .last("LIMIT 1"));
        if (req == null) {
            return Optional.empty();
        }
        if (isExpired(req)) {
            close(req.getId());
            return Optional.empty();
        }
        return Optional.of(req);
    }

    /** 粉丝主动关闭自己的需求。 */
    @Transactional
    public void closeOwn(long fanUserId, long requestId) {
        AuditionRequest req = require(requestId);
        if (!java.util.Objects.equals(req.getFanUserId(), fanUserId)) {
            throw new BusinessException("只能关闭自己发布的试音需求");
        }
        close(requestId);
    }

    /** 主播可应征的开放需求。读取时顺手关掉已过期的，不把它们返回给主播。 */
    @Transactional
    public List<AuditionRequest> listOpen(int limit) {
        List<AuditionRequest> all = requestMapper.selectList(Wrappers.<AuditionRequest>lambdaQuery()
                .eq(AuditionRequest::getStatus, OPEN)
                .orderByDesc(AuditionRequest::getId)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 100)));
        return all.stream().filter(req -> {
            if (isExpired(req)) {
                close(req.getId());
                return false;
            }
            return true;
        }).toList();
    }

    /**
     * 主播应征。
     *
     * 重复应征的拒绝靠唯一索引：先查后插在并发下有双写窗口，
     * 主播连点两次就可能插出两条。
     */
    @Transactional
    public AuditionReply reply(long talentId, long requestId, String message) {
        Talent talent = talentService.require(talentId);
        if (talent.getAudioUrl() == null) {
            throw new BusinessException("请先上传试听片段再应征");
        }
        AuditionRequest req = require(requestId);
        if (!OPEN.equals(req.getStatus()) || isExpired(req)) {
            close(req.getId());
            throw new BusinessException("该试音需求已关闭");
        }

        AuditionReply reply = new AuditionReply();
        reply.setRequestId(requestId);
        reply.setTalentId(talentId);
        reply.setMessage(message == null || message.isBlank() ? null : message.trim());
        try {
            replyMapper.insert(reply);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("你已应征过这条试音需求");
        }
        return reply;
    }

    /** 某条需求的应征列表，按应征时间正序。 */
    public List<AuditionReply> listReplies(long requestId) {
        return replyMapper.selectList(Wrappers.<AuditionReply>lambdaQuery()
                .eq(AuditionReply::getRequestId, requestId)
                .orderByAsc(AuditionReply::getCreatedAt)
                .orderByAsc(AuditionReply::getId));
    }

    public AuditionRequest require(long requestId) {
        AuditionRequest req = requestMapper.selectById(requestId);
        if (req == null) {
            throw new BusinessException("试音需求不存在: " + requestId);
        }
        return req;
    }

    private boolean isExpired(AuditionRequest req) {
        return req.getExpiresAt() != null && req.getExpiresAt().isBefore(LocalDateTime.now());
    }

    private void close(long requestId) {
        // 显式 set：updateById 会跳过 null 字段，而这里要把 closed_at 从 null 写成具体时间
        requestMapper.update(null, Wrappers.<AuditionRequest>lambdaUpdate()
                .eq(AuditionRequest::getId, requestId)
                .eq(AuditionRequest::getStatus, OPEN)
                .set(AuditionRequest::getStatus, CLOSED)
                .set(AuditionRequest::getClosedAt, LocalDateTime.now()));
    }
}
