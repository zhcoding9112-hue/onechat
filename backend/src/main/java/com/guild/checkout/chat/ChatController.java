package com.guild.checkout.chat;

import com.guild.checkout.auth.CurrentUserHolder;
import com.guild.checkout.common.api.ApiResponse;
import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.storage.FileStorage;
import com.guild.checkout.storage.StoredFile;
import com.guild.checkout.talent.Talent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 粉丝侧的会话列表与聊天。 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    /** 聊天配图上限 2 MB，与动态配图一致。 */
    private static final long MAX_IMAGE_BYTES = 2L * 1024 * 1024;
    private static final List<String> IMAGE_TYPES = List.of("image/jpeg", "image/png", "image/webp");

    private final ChatService chatService;
    private final FileStorage storage;

    public ChatController(ChatService chatService, FileStorage storage) {
        this.chatService = chatService;
        this.storage = storage;
    }

    public record ConversationView(Long id, Long talentId, String nickname, String coverUrl,
                                   String lastMessage, String lastMessageType,
                                   LocalDateTime lastMessageAt,
                                   long unread, boolean canSend, String disabledReason) {
    }

    public record MessageView(Long id, String senderRole, String contentType, String content,
                              boolean mine, LocalDateTime createdAt) {
    }

    public record ChatView(ConversationView conversation, List<MessageView> messages) {
    }

    public record SendRequest(
            String contentType,
            @NotBlank(message = "消息内容不能为空")
            @Size(max = 1000, message = "消息不超过 1000 字符") String content) {
    }

    // ---------- 会话 ----------

    @GetMapping("/conversations")
    public ApiResponse<List<ConversationView>> conversations(
            @RequestParam(defaultValue = "50") int limit) {
        long me = CurrentUserHolder.requireUserId();
        return ApiResponse.ok(chatService.fanConversations(me, limit).stream()
                .map(v -> toView(v, ChatMessage.FAN)).toList());
    }

    /** 未读总数。底部导航据此决定是否点红点。 */
    @GetMapping("/unread")
    public ApiResponse<Map<String, Long>> unread() {
        long me = CurrentUserHolder.requireUserId();
        return ApiResponse.ok(Map.of("total", chatService.fanUnreadTotal(me)));
    }

    /** 从主播详情页点「聊天」：有则打开，无则新建。重复点击不会建出第二个会话。 */
    @PostMapping("/talents/{talentId}/conversation")
    public ApiResponse<Map<String, Long>> openWithTalent(@PathVariable long talentId) {
        long me = CurrentUserHolder.requireUserId();
        ChatConversation c = chatService.open(me, talentId);
        return ApiResponse.ok(Map.of("conversationId", c.getId()));
    }

    /**
     * 打开会话：读消息并把未读清零。
     *
     * 清零与读取在同一次请求里完成，前端不必再单独调一次「标记已读」——
     * 少一次往返，也就少一种「读了但没标记」的状态。
     */
    @GetMapping("/conversations/{conversationId}")
    public ApiResponse<ChatView> openConversation(@PathVariable long conversationId,
                                                  @RequestParam(defaultValue = "200") int limit) {
        long me = CurrentUserHolder.requireUserId();
        ChatConversation c = chatService.requireFanOf(conversationId, me);
        List<ChatMessage> messages = chatService.messages(conversationId, limit);
        chatService.markReadByFan(conversationId, me);
        return ApiResponse.ok(new ChatView(
                toView(chatService.view(c, 0L), ChatMessage.FAN),
                messages.stream().map(m -> toMessage(m, ChatMessage.FAN)).toList()));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ApiResponse<MessageView> send(@PathVariable long conversationId,
                                         @Valid @RequestBody SendRequest req) {
        long me = CurrentUserHolder.requireUserId();
        ChatMessage m = chatService.sendByFan(conversationId, me, req.contentType(), req.content());
        return ApiResponse.ok(toMessage(m, ChatMessage.FAN));
    }

    /**
     * 撤回：永远返回失败。
     *
     * 接口存在但明确拒绝，而不是干脆没有这个路由：404 分不清「没有这个功能」
     * 和「这条消息不存在」，而规格要的是一个说得出理由的拒绝。
     */
    @DeleteMapping("/messages/{messageId}")
    public ApiResponse<Void> recall(@PathVariable long messageId) {
        chatService.recall(messageId, CurrentUserHolder.requireUserId());
        return ApiResponse.ok(null);
    }

    /** 显式标记已读。打开会话已经会清零，这里是给「列表上左滑标已读」一类操作留的。 */
    @PutMapping("/conversations/{conversationId}/read")
    public ApiResponse<Void> markRead(@PathVariable long conversationId) {
        chatService.markReadByFan(conversationId, CurrentUserHolder.requireUserId());
        return ApiResponse.ok(null);
    }

    // ---------- 配图 ----------

    @PostMapping("/images")
    public ApiResponse<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        CurrentUserHolder.requireUserId();
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择图片");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException("图片不超过 2 MB");
        }
        String type = file.getContentType() == null ? ""
                : file.getContentType().toLowerCase(Locale.ROOT);
        if (!IMAGE_TYPES.contains(type)) {
            throw new BusinessException("仅支持 JPG、PNG 与 WebP");
        }
        try (var in = file.getInputStream()) {
            StoredFile stored = storage.store("chat-image", file.getOriginalFilename(),
                    file.getContentType(), file.getSize(), in);
            return ApiResponse.ok(Map.of("url", stored.url()));
        } catch (IOException e) {
            throw new BusinessException("图片上传失败，请重试");
        }
    }

    // ---------- 内部 ----------

    /**
     * 列表项。{@code myRole} 决定「对方」是谁：粉丝看到的是主播，主播看到的是粉丝。
     * 两端共用一个列表组件，差别只在这里。
     */
    static ConversationView toView(ChatService.ConversationView v, String myRole) {
        Talent t = v.talent();
        ChatMessage last = v.lastMessage();
        boolean asFan = ChatMessage.FAN.equals(myRole);
        return new ConversationView(
                v.conversation().getId(), v.conversation().getTalentId(),
                asFan ? (t == null ? null : t.getNickname()) : v.fanNickname(),
                asFan ? (t == null ? null : t.getCoverUrl()) : v.fanAvatarUrl(),
                last == null ? null : summary(last),
                last == null ? null : last.getContentType(),
                v.conversation().getLastMessageAt(),
                v.unread(), v.canSend(), v.disabledReason());
    }

    static MessageView toMessage(ChatMessage m, String myRole) {
        return new MessageView(m.getId(), m.getSenderRole(), m.getContentType(), m.getContent(),
                myRole.equals(m.getSenderRole()), m.getCreatedAt());
    }

    /** 列表摘要：图片消息不把地址摊在列表上。 */
    private static String summary(ChatMessage m) {
        return ChatMessage.IMAGE.equals(m.getContentType()) ? "[图片]" : m.getContent();
    }
}
