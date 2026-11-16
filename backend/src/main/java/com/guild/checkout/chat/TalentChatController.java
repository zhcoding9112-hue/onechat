package com.guild.checkout.chat;

import com.guild.checkout.auth.CurrentUserHolder;
import com.guild.checkout.auth.RequireRole;
import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.TalentService;
import com.guild.checkout.user.UserRole;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 主播侧的会话列表与聊天。与粉丝侧同一张表，只是身份相反。 */
@RestController
@RequestMapping("/api/talent/chat")
@RequireRole(UserRole.TALENT)
public class TalentChatController {

    private final ChatService chatService;
    private final TalentService talentService;

    public TalentChatController(ChatService chatService, TalentService talentService) {
        this.chatService = chatService;
        this.talentService = talentService;
    }

    @GetMapping("/conversations")
    public ApiResponse<List<ChatController.ConversationView>> conversations(
            @RequestParam(defaultValue = "50") int limit) {
        Talent me = requireMe();
        return ApiResponse.ok(chatService.talentConversations(me.getId(), limit).stream()
                .map(v -> ChatController.toView(v, ChatMessage.TALENT)).toList());
    }

    @GetMapping("/unread")
    public ApiResponse<Map<String, Long>> unread() {
        return ApiResponse.ok(Map.of("total", chatService.talentUnreadTotal(requireMe().getId())));
    }

    @GetMapping("/conversations/{conversationId}")
    public ApiResponse<ChatController.ChatView> open(@PathVariable long conversationId,
                                                     @RequestParam(defaultValue = "200") int limit) {
        Talent me = requireMe();
        ChatConversation c = chatService.requireTalentOf(conversationId, me.getId());
        List<ChatMessage> messages = chatService.messages(conversationId, limit);
        chatService.markReadByTalent(conversationId, me.getId());
        return ApiResponse.ok(new ChatController.ChatView(
                ChatController.toView(chatService.view(c, 0L), ChatMessage.TALENT),
                messages.stream()
                        .map(m -> ChatController.toMessage(m, ChatMessage.TALENT)).toList()));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ApiResponse<ChatController.MessageView> send(
            @PathVariable long conversationId,
            @Valid @RequestBody ChatController.SendRequest req) {
        Talent me = requireMe();
        ChatMessage m = chatService.sendByTalent(conversationId, me.getId(),
                CurrentUserHolder.requireUserId(), req.contentType(), req.content());
        return ApiResponse.ok(ChatController.toMessage(m, ChatMessage.TALENT));
    }

    private Talent requireMe() {
        Talent me = talentService.findByUserId(CurrentUserHolder.requireUserId());
        if (me == null) {
            throw new BusinessException("当前账号未关联主播档案");
        }
        return me;
    }
}
