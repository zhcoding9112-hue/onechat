package com.guild.checkout.chat;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.common.exception.ForbiddenException;
import com.guild.checkout.common.exception.NotFoundException;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.TalentService;
import com.guild.checkout.user.AppUser;
import com.guild.checkout.user.AppUserService;
import com.guild.checkout.user.UserDisplay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 站内聊天。
 *
 * 三条贯穿本类的规矩：
 *
 * 1. 会话对 (fanUserId, talentId) 唯一，重复发起只是打开同一个会话。
 * 2. 消息发出即送达，任何一方都不能撤回（见 {@link #recall}）。
 *    运营可以下架违规消息，但那是留痕的软下架，原文仍在库里。
 * 3. 已下架主播的会话只读：历史消息照常展示，发送入口关闭。
 *    这条在服务端拦，不只在前端灰掉按钮——前端的禁用挡不住任何人。
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private static final int MAX_TEXT_LENGTH = 1000;

    /** 主播已下架时，给粉丝看的说明。只说不可约，不解释主播为什么下架。 */
    public static final String UNAVAILABLE_HINT = "该主播暂不可约，仅可查看历史消息";

    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatUnreadMapper unreadMapper;
    private final TalentService talentService;
    private final AppUserService userService;

    public ChatService(ChatConversationMapper conversationMapper,
                       ChatMessageMapper messageMapper,
                       ChatUnreadMapper unreadMapper,
                       TalentService talentService,
                       AppUserService userService) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.unreadMapper = unreadMapper;
        this.talentService = talentService;
        this.userService = userService;
    }

    /**
     * 会话连同双方资料、未读数与「现在还能不能发消息」。
     *
     * 两边的资料都带上，由调用方按自己的身份挑「对方是谁」：
     * 粉丝看到的是主播，主播看到的是粉丝。在服务层就挑好，会让这个方法
     * 多一个「我是谁」的参数，而两端的列表查询本来就各走各的入口。
     */
    public record ConversationView(ChatConversation conversation, Talent talent,
                                   String fanNickname, String fanAvatarUrl,
                                   ChatMessage lastMessage, long unread,
                                   boolean canSend, String disabledReason) {
    }

    // ---------- 发起与打开 ----------

    /**
     * 粉丝发起或打开与某主播的会话。
     *
     * 重复发起不产生第二条会话：唯一索引 (fan_user_id, talent_id) 兜底，
     * 这里只负责「不存在就建」。
     *
     * 已下架的主播不能**新建**会话——粉丝根本看不到他的详情页，没有入口；
     * 但已有的历史会话仍可打开，只是只读。
     */
    @Transactional
    public ChatConversation open(long fanUserId, long talentId) {
        ChatConversation existing = find(fanUserId, talentId);
        if (existing != null) {
            return existing;
        }
        Talent talent = talentService.require(talentId);
        if (!Boolean.TRUE.equals(talent.getListed())) {
            throw new BusinessException(UNAVAILABLE_HINT);
        }
        conversationMapper.openIfAbsent(fanUserId, talentId);
        // 加锁读，不是普通读：并发下这行可能是别的事务刚插的，
        // 普通读受本事务读视图所限根本看不到它
        ChatConversation opened = conversationMapper.findForUpdate(fanUserId, talentId);
        if (opened == null) {
            throw new IllegalStateException("会话创建失败: fan=" + fanUserId + " talent=" + talentId);
        }
        return opened;
    }

    public ChatConversation find(long fanUserId, long talentId) {
        return conversationMapper.selectOne(Wrappers.<ChatConversation>lambdaQuery()
                .eq(ChatConversation::getFanUserId, fanUserId)
                .eq(ChatConversation::getTalentId, talentId));
    }

    public ChatConversation require(long conversationId) {
        ChatConversation c = conversationMapper.selectById(conversationId);
        if (c == null) {
            throw new NotFoundException("会话不存在: " + conversationId);
        }
        return c;
    }

    /** 会话的参与方校验。运营走的是另一套入口，不经过这里。 */
    public ChatConversation requireFanOf(long conversationId, long fanUserId) {
        ChatConversation c = require(conversationId);
        if (!Objects.equals(c.getFanUserId(), fanUserId)) {
            throw new ForbiddenException("无权查看该会话");
        }
        return c;
    }

    public ChatConversation requireTalentOf(long conversationId, long talentId) {
        ChatConversation c = require(conversationId);
        if (!Objects.equals(c.getTalentId(), talentId)) {
            throw new ForbiddenException("无权查看该会话");
        }
        return c;
    }

    // ---------- 收发 ----------

    @Transactional
    public ChatMessage sendByFan(long conversationId, long fanUserId, String contentType, String content) {
        ChatConversation c = requireFanOf(conversationId, fanUserId);
        requireSendable(c);
        return append(c, ChatMessage.FAN, fanUserId, contentType, content);
    }

    @Transactional
    public ChatMessage sendByTalent(long conversationId, long talentId, long talentUserId,
                                    String contentType, String content) {
        ChatConversation c = requireTalentOf(conversationId, talentId);
        requireSendable(c);
        return append(c, ChatMessage.TALENT, talentUserId, contentType, content);
    }

    /**
     * 已下架主播的会话只读。
     *
     * 两侧都拦：粉丝发不出去是产品规则，主播也发不出去是同一条规则的另一半——
     * 已经不接单的主播继续在站内联系粉丝，和「暂不可约」是矛盾的。
     */
    private void requireSendable(ChatConversation c) {
        if (!canSend(c)) {
            throw new BusinessException(UNAVAILABLE_HINT);
        }
    }

    public boolean canSend(ChatConversation c) {
        Talent talent = talentService.find(c.getTalentId());
        return talent != null && Boolean.TRUE.equals(talent.getListed());
    }

    private ChatMessage append(ChatConversation c, String senderRole, long senderUserId,
                               String contentType, String rawContent) {
        String type = contentType == null ? ChatMessage.TEXT : contentType.trim().toUpperCase(java.util.Locale.ROOT);
        if (!ChatMessage.TEXT.equals(type) && !ChatMessage.IMAGE.equals(type)) {
            // 不承载音视频通话，也不承载别的附件类型
            throw new BusinessException("仅支持文字与图片消息");
        }
        String content = rawContent == null ? "" : rawContent.trim();
        if (content.isEmpty()) {
            throw new BusinessException(ChatMessage.IMAGE.equals(type) ? "图片地址不能为空" : "消息内容不能为空");
        }
        if (content.length() > MAX_TEXT_LENGTH) {
            throw new BusinessException("消息不超过 %d 字符".formatted(MAX_TEXT_LENGTH));
        }

        ChatMessage message = new ChatMessage();
        message.setConversationId(c.getId());
        message.setSenderRole(senderRole);
        message.setSenderUserId(senderUserId);
        message.setContentType(type);
        message.setContent(content);
        message.setStatus(ChatMessage.VISIBLE);
        messageMapper.insert(message);

        conversationMapper.touchLastMessage(c.getId(), message.getId(), message.getCreatedAt());

        // 发言即视为已读：既不让自己发的消息算进自己的未读，也把对方此前的消息一并标记为
        // 已读——能回复就说明已经看过了，否则回完一句，对方更早的消息还挂着未读
        if (ChatMessage.FAN.equals(senderRole)) {
            conversationMapper.markFanRead(c.getId(), message.getId());
        } else {
            conversationMapper.markTalentRead(c.getId(), message.getId());
        }
        return message;
    }

    /**
     * 撤回。永远失败。
     *
     * 留一个会明确拒绝的入口，而不是干脆不提供接口：不提供时客户端拿到的是 404，
     * 分不清「没有这个功能」和「这条消息不存在」。规格要求的是**拒绝**，
     * 拒绝就该说清楚为什么。
     */
    public void recall(long messageId, long requesterUserId) {
        ChatMessage message = requireMessage(messageId);
        log.info("用户 {} 试图撤回消息 {}，已拒绝", requesterUserId, message.getId());
        throw new BusinessException("消息发出后不能撤回。如有违规内容请举报，由运营处理");
    }

    /** 会话内的可见消息，按时间（id）正序。双方读到的是同一个序列。 */
    public List<ChatMessage> messages(long conversationId, int limit) {
        return messageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getConversationId, conversationId)
                .eq(ChatMessage::getStatus, ChatMessage.VISIBLE)
                // 按自增 id 而不是 created_at：同一毫秒内的两条消息靠时间分不出先后，
                // 双方的展示顺序就可能不一致
                .orderByAsc(ChatMessage::getId)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 500)));
    }

    public ChatMessage requireMessage(long messageId) {
        ChatMessage message = messageMapper.selectById(messageId);
        if (message == null) {
            throw new NotFoundException("消息不存在: " + messageId);
        }
        return message;
    }

    // ---------- 已读与未读 ----------

    /** 粉丝打开会话：把已读水位线推到当前最后一条消息，未读因此归零。 */
    @Transactional
    public void markReadByFan(long conversationId, long fanUserId) {
        ChatConversation c = requireFanOf(conversationId, fanUserId);
        conversationMapper.markFanRead(c.getId(), latestMessageId(c.getId()));
    }

    @Transactional
    public void markReadByTalent(long conversationId, long talentId) {
        ChatConversation c = requireTalentOf(conversationId, talentId);
        conversationMapper.markTalentRead(c.getId(), latestMessageId(c.getId()));
    }

    /**
     * 会话内当前最大的消息 id。
     *
     * 不用 conversation.last_message_id：那一列只在发消息时更新，
     * 而「最后一条」在这里要包含刚刚并发插入、尚未更新会话行的那条。
     */
    private long latestMessageId(long conversationId) {
        ChatMessage last = messageMapper.selectOne(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT 1"));
        return last == null ? 0L : last.getId();
    }

    public long fanUnreadTotal(long fanUserId) {
        return unreadMapper.fanUnreadTotal(fanUserId);
    }

    public long talentUnreadTotal(long talentId) {
        return unreadMapper.talentUnreadTotal(talentId);
    }

    // ---------- 会话列表 ----------

    /** 粉丝的会话列表，按最后消息时间倒序。 */
    public List<ConversationView> fanConversations(long fanUserId, int limit) {
        List<ChatConversation> list = conversationMapper.selectList(
                Wrappers.<ChatConversation>lambdaQuery()
                        .eq(ChatConversation::getFanUserId, fanUserId)
                        .orderByDesc(ChatConversation::getLastMessageAt)
                        // 同一毫秒内的两条消息靠时间分不出先后，用单调递增的消息 id 兜底；
                        // 尚无消息的会话 last_message_id 为 NULL，在 DESC 下排在同一时刻的最后
                        .orderByDesc(ChatConversation::getLastMessageId)
                        .orderByDesc(ChatConversation::getId)
                        .last("LIMIT " + Math.min(Math.max(limit, 1), 100)));
        return assemble(list, toCountMap(unreadMapper.fanUnreadByConversation(fanUserId)));
    }

    /** 主播的会话列表，按最后消息时间倒序。 */
    public List<ConversationView> talentConversations(long talentId, int limit) {
        List<ChatConversation> list = conversationMapper.selectList(
                Wrappers.<ChatConversation>lambdaQuery()
                        .eq(ChatConversation::getTalentId, talentId)
                        .orderByDesc(ChatConversation::getLastMessageAt)
                        .orderByDesc(ChatConversation::getLastMessageId)
                        .orderByDesc(ChatConversation::getId)
                        .last("LIMIT " + Math.min(Math.max(limit, 1), 100)));
        return assemble(list, toCountMap(unreadMapper.talentUnreadByConversation(talentId)));
    }

    /** 单个会话的视图，含未读数与可否发送。 */
    public ConversationView view(ChatConversation c, long unread) {
        return assemble(List.of(c), Map.of(c.getId(), unread)).get(0);
    }

    private List<ConversationView> assemble(List<ChatConversation> list, Map<Long, Long> unread) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, Talent> talents = talentService.findByIds(
                list.stream().map(ChatConversation::getTalentId).toList());

        Map<Long, ChatMessage> lastMessages = new HashMap<>();
        for (ChatConversation c : list) {
            if (c.getLastMessageId() != null) {
                ChatMessage m = messageMapper.selectById(c.getLastMessageId());
                // 最后一条被运营下架时，列表摘要不该还显示它的原文
                if (m != null && ChatMessage.VISIBLE.equals(m.getStatus())) {
                    lastMessages.put(c.getId(), m);
                }
            }
        }

        return list.stream().map(c -> {
            Talent t = talents.get(c.getTalentId());
            boolean listed = t != null && Boolean.TRUE.equals(t.getListed());
            AppUser fan = userService.requireById(c.getFanUserId());
            return new ConversationView(c, t,
                    UserDisplay.nickname(fan.getNickname(), fan.getId()),
                    UserDisplay.avatarUrl(fan.getAvatarUrl()),
                    lastMessages.get(c.getId()),
                    unread.getOrDefault(c.getId(), 0L),
                    listed, listed ? null : UNAVAILABLE_HINT);
        }).toList();
    }

    private static Map<Long, Long> toCountMap(List<Map<String, Object>> rows) {
        Map<Long, Long> out = new HashMap<>();
        for (Map<String, Object> row : rows) {
            out.put(((Number) row.get("conversationId")).longValue(),
                    ((Number) row.get("c")).longValue());
        }
        return out;
    }

    // ---------- 运营 ----------

    /** 运营侧：一个会话的完整消息记录，含已下架的。 */
    public List<ChatMessage> allMessagesForOperator(long conversationId) {
        return messageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByAsc(ChatMessage::getId));
    }

    /** 运营侧：某个用户参与的全部会话（作为粉丝，或作为主播本人）。 */
    public List<ChatConversation> conversationsOfUser(long userId) {
        Talent asTalent = talentService.findByUserId(userId);
        return conversationMapper.selectList(Wrappers.<ChatConversation>lambdaQuery()
                .eq(ChatConversation::getFanUserId, userId)
                .or(asTalent != null,
                        q -> q.eq(ChatConversation::getTalentId, asTalent.getId()))
                .orderByDesc(ChatConversation::getLastMessageAt));
    }

    /** 运营侧：下架一条违规消息。原文保留，另记操作者、时间与原因。 */
    @Transactional
    public ChatMessage removeMessage(long messageId, long operatorId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("下架原因为必填");
        }
        ChatMessage message = requireMessage(messageId);
        if (ChatMessage.REMOVED.equals(message.getStatus())) {
            // 重复下架不覆盖首次留痕：第一条记录才是追溯链的起点
            return message;
        }
        message.setStatus(ChatMessage.REMOVED);
        message.setRemovedBy(operatorId);
        message.setRemovedAt(LocalDateTime.now());
        message.setRemoveReason(reason.trim());
        messageMapper.updateById(message);
        log.info("消息 {} 已下架，操作者 {}，原因：{}", messageId, operatorId, reason);
        return message;
    }
}
