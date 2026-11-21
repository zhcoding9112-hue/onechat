package com.guild.checkout.chat;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.common.exception.BusinessException;
import com.guild.checkout.common.exception.ForbiddenException;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.admin.TalentAdminService;
import com.guild.checkout.user.AppUser;
import com.guild.checkout.user.AppUserService;
import com.guild.checkout.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 站内聊天（tasks 10.1 - 10.7）。
 *
 * 并发发起会话的幂等单独放在 {@link ChatConcurrencyTest}：它必须在真实提交下跑。
 */
@SpringBootTest
@Transactional
class ChatTest {

    @Autowired private ChatService chatService;
    @Autowired private ChatConversationMapper conversationMapper;
    @Autowired private ChatMessageMapper messageMapper;
    @Autowired private TalentAdminService talentAdminService;
    @Autowired private AppUserService userService;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired private com.guild.checkout.level.FanLevelService fanLevelService;

    private long fanId;
    private long otherFanId;
    private long operatorId;
    private long talentId;
    private long talentUserId;

    @BeforeEach
    void setUp() {
        fanId = newUser("cf_").getId();
        otherFanId = newUser("co_").getId();
        operatorId = newUser("cp_").getId();

        AppUser talentUser = newUser("ct_");
        talentUserId = talentUser.getId();
        userService.grantRole(talentUserId, UserRole.TALENT);
        talentId = newTalent("聊天主播");
        talentAdminService.linkUser(talentId, talentUserId);
    }

    // ===== 主播在会话里看得见粉丝等级 =====

    @Test
    @DisplayName("主播侧的会话带着粉丝等级，粉丝侧不带")
    void 会话带粉丝等级() {
        spend(fanId, 80000);  // 银卡门槛是 600 元
        long id = openConversation();
        ChatConversation c = conversationMapper.selectById(id);

        ChatService.ConversationView view = chatService.view(c, 0L);

        assertThat(view.fanLevel()).isNotNull();
        assertThat(view.fanLevelName()).isNotBlank();
        // 服务层两边的资料都带，由控制器按身份挑；这里验的是「算出来了」
        assertThat(view.fanNickname()).isNotBlank();
    }

    /**
     * 等级是按当下的消费额算的，不是建会话时定死的。
     *
     * 刻意不写成「读一次 → 花钱 → 再读一次」：那样会撞上 MyBatis 的一级缓存——
     * 同一个事务里相同参数的查询直接返回上次结果，而本用例的消费是用
     * JdbcTemplate 插的，MyBatis 不知道有人写过，缓存不会失效。
     * 生产里两次读分属两个请求、两个会话，不存在这个问题，
     * 所以那样写只会测出一个测试环境特有的假象。
     */
    @Test
    @DisplayName("会话里的等级与粉丝自己看到的是同一个")
    void 等级与粉丝自己看到的一致() {
        spend(fanId, 80000);
        long id = openConversation();
        ChatConversation c = conversationMapper.selectById(id);

        ChatService.ConversationView view = chatService.view(c, 0L);
        var authoritative = fanLevelService.levelOf(fanId);

        // 不写死「¥800 是银卡」：门槛是运营可改的配置，写死等于把配置钉进测试。
        // 真正要守的是「两处显示的是同一个等级」。
        assertThat(view.fanLevel()).isEqualTo(authoritative.level());
        assertThat(view.fanLevelName()).isEqualTo(authoritative.name());
        assertThat(view.fanLevel()).as("花了钱就不该还停在最低档").isGreaterThan(1);
    }

    @Test
    @DisplayName("没花过钱的粉丝是最低级，不是 null")
    void 零消费也有等级() {
        long id = openConversation();
        ChatConversation c = conversationMapper.selectById(id);

        ChatService.ConversationView view = chatService.view(c, 0L);

        assertThat(view.fanLevel()).as("0 消费也该有等级，不能让前端拿到 null").isEqualTo(1);
    }

    /**
     * 列表里每条会话的等级要对上各自的粉丝，不能整列都用同一个人的。
     * 批量查询最容易犯的就是这个错。
     */
    @Test
    @DisplayName("列表里每个粉丝对应自己的等级")
    void 列表等级不串行() {
        spend(fanId, 80000);
        chatService.open(fanId, talentId);
        chatService.open(otherFanId, talentId);

        var views = chatService.talentConversations(talentId, 50);

        var rich = views.stream()
                .filter(v -> v.conversation().getFanUserId().equals(fanId)).findFirst().orElseThrow();
        var poor = views.stream()
                .filter(v -> v.conversation().getFanUserId().equals(otherFanId)).findFirst().orElseThrow();
        assertThat(rich.fanLevel()).isEqualTo(fanLevelService.levelOf(fanId).level());
        assertThat(poor.fanLevel()).as("没花过钱的是最低档").isEqualTo(1);
        assertThat(rich.fanLevel()).isGreaterThan(poor.fanLevel());
    }

    /** 造一笔已完成的打赏当作消费。走服务层太绕，这里只需要一条流水。 */
    private void spend(long userId, long cents) {
        jdbc.update("""
                INSERT INTO tip_record (tip_ref, fan_user_id, talent_id, amount_cents, paid_cents,
                  commission_rate_bp, guild_cents, talent_cents, status, version, created_at)
                VALUES (?, ?, ?, ?, ?, 3000, ?, ?, 'COMPLETED', 0, NOW())
                """,
                "T" + System.nanoTime(), userId, talentId, cents, cents,
                cents * 30 / 100, cents - cents * 30 / 100);
    }

    private AppUser newUser(String prefix) {
        return userService.registerOrUpdate(prefix + System.nanoTime(), null, "测试用户", null);
    }

    private long newTalent(String nickname) {
        Talent t = talentAdminService.create(new TalentAdminService.ProfileForm(
                nickname + System.nanoTime(), null, null, 0, null, null, null, null));
        talentAdminService.setListed(t.getId(), true);
        return t.getId();
    }

    private long openConversation() {
        return chatService.open(fanId, talentId).getId();
    }

    // ===== 10.1 会话唯一 =====

    @Test
    @DisplayName("10.1 重复发起会话不产生第二条记录")
    void 重复发起不新建会话() {
        ChatConversation first = chatService.open(fanId, talentId);
        ChatConversation again = chatService.open(fanId, talentId);
        ChatConversation third = chatService.open(fanId, talentId);

        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(third.getId()).isEqualTo(first.getId());

        long rows = conversationMapper.selectCount(Wrappers.<ChatConversation>lambdaQuery()
                .eq(ChatConversation::getFanUserId, fanId)
                .eq(ChatConversation::getTalentId, talentId));
        assertThat(rows).isEqualTo(1);
    }

    @Test
    @DisplayName("10.1 不同粉丝与同一主播是各自独立的会话")
    void 不同粉丝各自一条会话() {
        ChatConversation mine = chatService.open(fanId, talentId);
        ChatConversation theirs = chatService.open(otherFanId, talentId);
        assertThat(theirs.getId()).isNotEqualTo(mine.getId());
    }

    @Test
    @DisplayName("10.1 新建的会话自带创建时间与排序时间")
    void 新会话自带时间() {
        ChatConversation c = chatService.open(fanId, talentId);
        assertThat(c.getCreatedAt()).isNotNull();
        assertThat(c.getLastMessageAt()).isNotNull();
        assertThat(c.getLastMessageId()).isNull();
    }

    @Test
    @DisplayName("10.1 别人的会话读不到")
    void 非参与方读不到会话() {
        long id = openConversation();
        assertThatThrownBy(() -> chatService.requireFanOf(id, otherFanId))
                .isInstanceOf(ForbiddenException.class);
    }

    // ===== 10.2 消息收发与顺序 =====

    @Test
    @DisplayName("10.2 文字与图片都能发，双方看到的消息顺序一致")
    void 双方看到的顺序一致() {
        long id = openConversation();

        chatService.sendByFan(id, fanId, "TEXT", "在吗");
        chatService.sendByTalent(id, talentId, talentUserId, "TEXT", "在的");
        chatService.sendByFan(id, fanId, "IMAGE", "/uploads/chat/a.jpg");
        chatService.sendByTalent(id, talentId, talentUserId, "TEXT", "收到");

        List<ChatMessage> asFan = chatService.messages(id, 200);
        List<ChatMessage> asTalent = chatService.messages(id, 200);

        assertThat(asFan).extracting(ChatMessage::getContent)
                .containsExactly("在吗", "在的", "/uploads/chat/a.jpg", "收到");
        assertThat(asTalent).extracting(ChatMessage::getId)
                .containsExactlyElementsOf(asFan.stream().map(ChatMessage::getId).toList());
        assertThat(asFan).extracting(ChatMessage::getSenderRole)
                .containsExactly("FAN", "TALENT", "FAN", "TALENT");
        assertThat(asFan.get(2).getContentType()).isEqualTo("IMAGE");
    }

    @Test
    @DisplayName("10.2 消息记录发送者与发送时间")
    void 消息记录发送者与时间() {
        long id = openConversation();
        ChatMessage m = chatService.sendByFan(id, fanId, "TEXT", "你好");

        assertThat(m.getSenderUserId()).isEqualTo(fanId);
        assertThat(m.getSenderRole()).isEqualTo("FAN");
        // 插入后直接返回给调用方，时间必须已经在实体上，而不是等下次读库才有
        assertThat(m.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("10.2 只支持文字与图片：音视频一类的消息类型被拒")
    void 不支持音视频消息() {
        long id = openConversation();
        assertThatThrownBy(() -> chatService.sendByFan(id, fanId, "VOICE", "/uploads/a.mp3"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅支持文字与图片");
        assertThatThrownBy(() -> chatService.sendByFan(id, fanId, "VIDEO", "/uploads/a.mp4"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("10.2 空消息与超长消息被拒")
    void 空消息与超长消息被拒() {
        long id = openConversation();
        assertThatThrownBy(() -> chatService.sendByFan(id, fanId, "TEXT", "   "))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> chatService.sendByFan(id, fanId, "TEXT", "字".repeat(1001)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("10.2 发消息会把会话的最后消息时间前移")
    void 发消息前移会话时间() {
        long id = openConversation();
        ChatMessage m = chatService.sendByFan(id, fanId, "TEXT", "一");

        ChatConversation after = conversationMapper.selectById(id);
        assertThat(after.getLastMessageId()).isEqualTo(m.getId());
        // 与库里的消息时间比，而不是与内存实体比：LocalDateTime 带微秒，
        // 而 DATETIME(3) 只存到毫秒并且是四舍五入，直接比必然对不上
        assertThat(after.getLastMessageAt())
                .isEqualTo(messageMapper.selectById(m.getId()).getCreatedAt());
    }

    // ===== 10.3 会话列表与未读 =====

    @Test
    @DisplayName("10.3 会话列表按最后消息时间倒序")
    void 会话列表按最后消息倒序() {
        long talentB = newTalent("主播乙");
        long talentC = newTalent("主播丙");

        long a = chatService.open(fanId, talentId).getId();
        long b = chatService.open(fanId, talentB).getId();
        long c = chatService.open(fanId, talentC).getId();

        // 发送顺序 a → c → b，列表应为 b、c、a
        chatService.sendByFan(a, fanId, "TEXT", "给甲");
        chatService.sendByFan(c, fanId, "TEXT", "给丙");
        chatService.sendByFan(b, fanId, "TEXT", "给乙");

        assertThat(chatService.fanConversations(fanId, 50).stream()
                .map(v -> v.conversation().getId()).toList())
                .containsExactly(b, c, a);
    }

    @Test
    @DisplayName("10.3 未读只算对方发的，打开会话后归零")
    void 打开会话后未读归零() {
        long id = openConversation();

        chatService.sendByTalent(id, talentId, talentUserId, "TEXT", "一");
        chatService.sendByTalent(id, talentId, talentUserId, "TEXT", "二");
        assertThat(unreadOf(id)).isEqualTo(2);

        chatService.markReadByFan(id, fanId);
        assertThat(unreadOf(id)).isZero();
    }

    @Test
    @DisplayName("10.3 自己发的消息不算自己的未读")
    void 自己发的不算未读() {
        long id = openConversation();
        chatService.sendByFan(id, fanId, "TEXT", "我说的");
        chatService.sendByFan(id, fanId, "TEXT", "我又说的");

        assertThat(unreadOf(id)).isZero();
        // 对主播来说这两条是未读
        assertThat(chatService.talentUnreadTotal(talentId)).isEqualTo(2);
    }

    @Test
    @DisplayName("10.3 回复即视为已读：回完之后对方更早的消息不再挂未读")
    void 回复即已读() {
        long id = openConversation();
        chatService.sendByTalent(id, talentId, talentUserId, "TEXT", "在吗");
        assertThat(unreadOf(id)).isEqualTo(1);

        chatService.sendByFan(id, fanId, "TEXT", "在");
        assertThat(unreadOf(id)).isZero();
    }

    @Test
    @DisplayName("10.3 已读水位线只增不减：重复标记不会把已读变回未读")
    void 已读水位线只增不减() {
        long id = openConversation();
        chatService.sendByTalent(id, talentId, talentUserId, "TEXT", "一");
        chatService.markReadByFan(id, fanId);

        long watermark = conversationMapper.selectById(id).getFanReadUntil();
        // 手动把水位线往回压，模拟一个带着旧水位线迟到的请求
        conversationMapper.markFanRead(id, 1L);
        assertThat(conversationMapper.selectById(id).getFanReadUntil()).isEqualTo(watermark);
    }

    @Test
    @DisplayName("10.3 被运营下架的消息不再计入未读")
    void 下架的消息不计未读() {
        long id = openConversation();
        ChatMessage m = chatService.sendByTalent(id, talentId, talentUserId, "TEXT", "违规内容");
        assertThat(unreadOf(id)).isEqualTo(1);

        chatService.removeMessage(m.getId(), operatorId, "违规");
        assertThat(unreadOf(id)).isZero();
    }

    private long unreadOf(long conversationId) {
        return chatService.fanConversations(fanId, 50).stream()
                .filter(v -> v.conversation().getId() == conversationId)
                .findFirst().orElseThrow().unread();
    }

    @Test
    @DisplayName("10.3 两端看到的「对方」不同：粉丝看到主播，主播看到粉丝")
    void 两端各自看到对方() {
        long id = openConversation();
        chatService.sendByFan(id, fanId, "TEXT", "你好");

        ChatService.ConversationView v = chatService.fanConversations(fanId, 50).get(0);
        assertThat(ChatController.toView(v, ChatMessage.FAN).nickname())
                .isEqualTo(v.talent().getNickname());

        ChatService.ConversationView t = chatService.talentConversations(talentId, 50).get(0);
        assertThat(ChatController.toView(t, ChatMessage.TALENT).nickname())
                .isEqualTo(t.fanNickname())
                .isNotEqualTo(t.talent().getNickname());
    }

    @Test
    @DisplayName("10.3 列表摘要不把图片地址摊开，显示为「[图片]」")
    void 图片消息摘要() {
        long id = openConversation();
        chatService.sendByFan(id, fanId, "IMAGE", "/uploads/chat/x.jpg");

        ChatService.ConversationView v = chatService.fanConversations(fanId, 50).get(0);
        assertThat(ChatController.toView(v, ChatMessage.FAN).lastMessage()).isEqualTo("[图片]");
    }

    // ===== 10.4 全局未读标记 =====

    @Test
    @DisplayName("10.4 有任意未读时总数大于零，全部已读后归零")
    void 全局未读随已读归零() {
        long talentB = newTalent("主播乙");
        long a = chatService.open(fanId, talentId).getId();
        long b = chatService.open(fanId, talentB).getId();

        assertThat(chatService.fanUnreadTotal(fanId)).isZero();

        chatService.sendByTalent(a, talentId, talentUserId, "TEXT", "甲一");
        chatService.sendByTalent(b, talentB, talentUserId, "TEXT", "乙一");
        chatService.sendByTalent(b, talentB, talentUserId, "TEXT", "乙二");
        assertThat(chatService.fanUnreadTotal(fanId)).isEqualTo(3);

        chatService.markReadByFan(a, fanId);
        // 还剩乙的两条，标记应当仍在
        assertThat(chatService.fanUnreadTotal(fanId)).isEqualTo(2);

        chatService.markReadByFan(b, fanId);
        assertThat(chatService.fanUnreadTotal(fanId)).isZero();
    }

    // ===== 10.5 已下架主播的会话只读 =====

    @Test
    @DisplayName("10.5 主播下架后历史消息仍可见，但发送被拒并说明暂不可约")
    void 下架主播会话只读() {
        long id = openConversation();
        chatService.sendByFan(id, fanId, "TEXT", "下架前说的");
        chatService.sendByTalent(id, talentId, talentUserId, "TEXT", "下架前回的");

        talentAdminService.setListed(talentId, false);

        // 历史消息照常
        assertThat(chatService.messages(id, 200)).hasSize(2);

        ChatService.ConversationView view = chatService.fanConversations(fanId, 50).stream()
                .filter(v -> v.conversation().getId() == id).findFirst().orElseThrow();
        assertThat(view.canSend()).isFalse();
        assertThat(view.disabledReason()).contains("暂不可约");

        assertThatThrownBy(() -> chatService.sendByFan(id, fanId, "TEXT", "还能发吗"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("暂不可约");
    }

    @Test
    @DisplayName("10.5 主播下架后自己也发不出去，不只是粉丝侧被挡")
    void 下架后主播也发不出() {
        long id = openConversation();
        talentAdminService.setListed(talentId, false);
        assertThatThrownBy(() ->
                chatService.sendByTalent(id, talentId, talentUserId, "TEXT", "我还能发"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("暂不可约");
    }

    @Test
    @DisplayName("10.5 对已下架主播不能新建会话")
    void 下架主播不能新建会话() {
        talentAdminService.setListed(talentId, false);
        assertThatThrownBy(() -> chatService.open(otherFanId, talentId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("暂不可约");
    }

    @Test
    @DisplayName("10.5 主播重新上架后会话恢复可发送")
    void 重新上架后恢复() {
        long id = openConversation();
        talentAdminService.setListed(talentId, false);
        talentAdminService.setListed(talentId, true);
        assertThat(chatService.sendByFan(id, fanId, "TEXT", "又能发了").getId()).isNotNull();
    }

    // ===== 10.6 不可单方撤回 =====

    @Test
    @DisplayName("10.6 撤回自己发的消息被拒绝，消息仍在")
    void 撤回被拒绝() {
        long id = openConversation();
        ChatMessage m = chatService.sendByFan(id, fanId, "TEXT", "说错了");

        assertThatThrownBy(() -> chatService.recall(m.getId(), fanId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能撤回");

        assertThat(messageMapper.selectById(m.getId()).getStatus()).isEqualTo("VISIBLE");
        assertThat(chatService.messages(id, 200)).hasSize(1);
    }

    @Test
    @DisplayName("10.6 对方试图撤回我发的消息同样被拒")
    void 对方撤回也被拒() {
        long id = openConversation();
        ChatMessage m = chatService.sendByFan(id, fanId, "TEXT", "我发的");
        assertThatThrownBy(() -> chatService.recall(m.getId(), talentUserId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("10.6 运营下架是留痕的软下架，原文仍在库里")
    void 运营下架保留原文() {
        long id = openConversation();
        ChatMessage m = chatService.sendByFan(id, fanId, "TEXT", "违规内容原文");

        chatService.removeMessage(m.getId(), operatorId, "涉嫌违规");

        assertThat(chatService.messages(id, 200)).isEmpty();
        ChatMessage stored = messageMapper.selectById(m.getId());
        assertThat(stored.getContent()).isEqualTo("违规内容原文");
        assertThat(stored.getRemovedBy()).isEqualTo(operatorId);
        assertThat(stored.getRemovedAt()).isNotNull();
        assertThat(stored.getRemoveReason()).isEqualTo("涉嫌违规");
    }

    @Test
    @DisplayName("10.6 重复下架不覆盖首次留痕")
    void 重复下架不覆盖留痕() {
        long id = openConversation();
        ChatMessage m = chatService.sendByFan(id, fanId, "TEXT", "违规");
        chatService.removeMessage(m.getId(), operatorId, "第一次的原因");

        long secondOperator = newUser("cq_").getId();
        chatService.removeMessage(m.getId(), secondOperator, "第二次的原因");

        ChatMessage stored = messageMapper.selectById(m.getId());
        assertThat(stored.getRemovedBy()).isEqualTo(operatorId);
        assertThat(stored.getRemoveReason()).isEqualTo("第一次的原因");
    }

    @Test
    @DisplayName("10.6 下架必须填原因")
    void 下架必须填原因() {
        long id = openConversation();
        ChatMessage m = chatService.sendByFan(id, fanId, "TEXT", "违规");
        assertThatThrownBy(() -> chatService.removeMessage(m.getId(), operatorId, "  "))
                .isInstanceOf(BusinessException.class);
    }

    // ===== 10.7 运营查会话 =====

    @Test
    @DisplayName("10.7 运营按用户能查到其全部会话")
    void 运营按用户查会话() {
        long talentB = newTalent("主播乙");
        long a = chatService.open(fanId, talentId).getId();
        long b = chatService.open(fanId, talentB).getId();

        assertThat(chatService.conversationsOfUser(fanId))
                .extracting(ChatConversation::getId)
                .containsExactlyInAnyOrder(a, b);
    }

    @Test
    @DisplayName("10.7 运营按主播账号也能查到会话")
    void 运营按主播账号查会话() {
        long id = openConversation();
        assertThat(chatService.conversationsOfUser(talentUserId))
                .extracting(ChatConversation::getId)
                .contains(id);
    }

    @Test
    @DisplayName("10.7 运营看到的是完整记录，含已下架的消息")
    void 运营看到完整记录() {
        long id = openConversation();
        chatService.sendByFan(id, fanId, "TEXT", "一");
        ChatMessage removed = chatService.sendByFan(id, fanId, "TEXT", "二（将被下架）");
        chatService.sendByTalent(id, talentId, talentUserId, "TEXT", "三");
        chatService.removeMessage(removed.getId(), operatorId, "违规");

        // 粉丝侧只剩两条
        assertThat(chatService.messages(id, 200)).hasSize(2);
        // 运营侧三条都在，且顺序不变
        assertThat(chatService.allMessagesForOperator(id))
                .extracting(ChatMessage::getContent)
                .containsExactly("一", "二（将被下架）", "三");
    }

    @Test
    @DisplayName("10.7 下架最后一条后，会话列表摘要不再显示它的原文")
    void 下架后列表摘要不显示原文() {
        long id = openConversation();
        ChatMessage m = chatService.sendByTalent(id, talentId, talentUserId, "TEXT", "违规原文");
        chatService.removeMessage(m.getId(), operatorId, "违规");

        ChatService.ConversationView view = chatService.fanConversations(fanId, 50).stream()
                .filter(v -> v.conversation().getId() == id).findFirst().orElseThrow();
        assertThat(view.lastMessage()).isNull();
    }
}
