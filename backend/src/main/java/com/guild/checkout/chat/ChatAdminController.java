package com.guild.checkout.chat;

import com.guild.checkout.auth.CurrentUserHolder;
import com.guild.checkout.auth.RequireRole;
import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.order.OrderService;
import com.guild.checkout.order.ServiceOrder;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.TalentService;
import com.guild.checkout.user.AppUser;
import com.guild.checkout.user.AppUserService;
import com.guild.checkout.user.UserDisplay;
import com.guild.checkout.user.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 运营侧：按订单或用户找到会话，查看完整消息记录，下架违规消息。
 *
 * 这是纠纷追溯用的入口，看到的是全集：已被下架的消息在这里仍然原样可见。
 */
@RestController
@RequestMapping("/api/admin/chat")
@RequireRole(UserRole.OPERATOR)
public class ChatAdminController {

    private final ChatService chatService;
    private final OrderService orderService;
    private final TalentService talentService;
    private final AppUserService userService;

    public ChatAdminController(ChatService chatService,
                               OrderService orderService,
                               TalentService talentService,
                               AppUserService userService) {
        this.chatService = chatService;
        this.orderService = orderService;
        this.talentService = talentService;
        this.userService = userService;
    }

    public record ConversationRow(Long id, Long fanUserId, String fanNickname,
                                  Long talentId, String talentNickname,
                                  LocalDateTime lastMessageAt, LocalDateTime createdAt) {
    }

    public record MessageRow(Long id, String senderRole, Long senderUserId, String senderNickname,
                             String contentType, String content, String status,
                             Long removedBy, LocalDateTime removedAt, String removeReason,
                             LocalDateTime createdAt) {
    }

    public record TranscriptView(ConversationRow conversation, List<MessageRow> messages) {
    }

    public record ReasonRequest(
            @NotBlank(message = "原因为必填")
            @Size(max = 255, message = "原因不超过 255 字符") String reason) {
    }

    /**
     * 按订单或用户查会话。
     *
     * 订单号是运营手里最常有的线索——粉丝来申诉时报的是订单号，不是会话 id。
     * 按订单查会落到「这单的粉丝 × 这单的主播」那一个会话上。
     */
    @GetMapping("/conversations")
    public ApiResponse<List<ConversationRow>> search(@RequestParam(required = false) Long orderId,
                                                     @RequestParam(required = false) String orderRef,
                                                     @RequestParam(required = false) Long userId) {
        List<ChatConversation> found;
        if (orderId != null || (orderRef != null && !orderRef.isBlank())) {
            ServiceOrder order = orderId != null
                    ? orderService.requireOrder(orderId)
                    : orderService.findByRef(orderRef.trim());
            if (order == null) {
                throw new BusinessException("订单不存在: " + orderRef);
            }
            ChatConversation c = chatService.find(order.getFanUserId(), order.getTalentId());
            found = c == null ? List.of() : List.of(c);
        } else if (userId != null) {
            found = chatService.conversationsOfUser(userId);
        } else {
            throw new BusinessException("请提供订单号或用户 id");
        }
        return ApiResponse.ok(found.stream().map(this::row).toList());
    }

    /** 一个会话的完整消息记录，含已下架的。 */
    @GetMapping("/conversations/{conversationId}")
    public ApiResponse<TranscriptView> transcript(@PathVariable long conversationId) {
        ChatConversation c = chatService.require(conversationId);
        List<MessageRow> messages = chatService.allMessagesForOperator(conversationId).stream()
                .map(m -> new MessageRow(m.getId(), m.getSenderRole(), m.getSenderUserId(),
                        nickname(m.getSenderUserId()),
                        m.getContentType(), m.getContent(), m.getStatus(),
                        m.getRemovedBy(), m.getRemovedAt(), m.getRemoveReason(),
                        m.getCreatedAt()))
                .toList();
        return ApiResponse.ok(new TranscriptView(row(c), messages));
    }

    @PutMapping("/messages/{messageId}/remove")
    public ApiResponse<Void> remove(@PathVariable long messageId,
                                    @Valid @RequestBody ReasonRequest req) {
        chatService.removeMessage(messageId, CurrentUserHolder.requireUserId(), req.reason());
        return ApiResponse.ok(null);
    }

    private ConversationRow row(ChatConversation c) {
        Talent t = talentService.find(c.getTalentId());
        return new ConversationRow(c.getId(), c.getFanUserId(), nickname(c.getFanUserId()),
                c.getTalentId(), t == null ? null : t.getNickname(),
                c.getLastMessageAt(), c.getCreatedAt());
    }

    private String nickname(Long userId) {
        if (userId == null) {
            return null;
        }
        AppUser user = userService.requireById(userId);
        return UserDisplay.nickname(user.getNickname(), userId);
    }
}
