package com.guild.checkout.chat;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.admin.TalentAdminService;
import com.guild.checkout.user.AppUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 并发发起会话与并发发消息（tasks 10.1 / 10.3）。
 *
 * 刻意不加 @Transactional：会话唯一靠唯一索引，而测试事务会把线程隔离开，
 * 唯一索引根本不会被触发到。因此自行清理本用例造出来的数据。
 */
@SpringBootTest
class ChatConcurrencyTest {

    @Autowired private ChatService chatService;
    @Autowired private ChatConversationMapper conversationMapper;
    @Autowired private ChatMessageMapper messageMapper;
    @Autowired private TalentAdminService talentAdminService;
    @Autowired private AppUserService userService;

    private Long talentId;
    private long fanId;

    @BeforeEach
    void setUp() {
        Talent t = talentAdminService.create(new TalentAdminService.ProfileForm(
                "并发聊天主播" + System.nanoTime(), null, null, 0, null, null, null, null));
        talentAdminService.setListed(t.getId(), true);
        talentId = t.getId();
        fanId = userService.registerOrUpdate(
                "ccc_" + System.nanoTime(), null, "并发粉丝", null).getId();
    }

    @AfterEach
    void tearDown() {
        ChatConversation c = chatService.find(fanId, talentId);
        if (c != null) {
            messageMapper.delete(Wrappers.<ChatMessage>lambdaQuery()
                    .eq(ChatMessage::getConversationId, c.getId()));
            conversationMapper.deleteById(c.getId());
        }
        if (talentId != null) {
            talentAdminService.setListed(talentId, false);
        }
    }

    @Test
    @DisplayName("10.1 16 个线程同时发起会话，只建出一条")
    void 并发发起只建一条会话() throws Exception {
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger failures = new AtomicInteger();
        Set<Long> ids = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    ids.add(chatService.open(fanId, talentId).getId());
                } catch (Exception e) {
                    // 唯一索引冲突与死锁都必须在服务内部消化掉，冒到这里就算失败
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(failures.get()).isZero();
        // 全部线程拿到的是同一个会话 id
        assertThat(ids).hasSize(1);
        assertThat(conversationMapper.selectCount(Wrappers.<ChatConversation>lambdaQuery()
                .eq(ChatConversation::getFanUserId, fanId)
                .eq(ChatConversation::getTalentId, talentId))).isEqualTo(1);
    }

    @Test
    @DisplayName("10.3 并发发消息后，会话的最后消息指向真正最后的那条")
    void 并发发消息后排序键不回退() throws Exception {
        long id = chatService.open(fanId, talentId).getId();
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            final int n = i;
            pool.submit(() -> {
                try {
                    start.await();
                    chatService.sendByFan(id, fanId, "TEXT", "并发消息 " + n);
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(failures.get()).isZero();

        long maxId = chatService.messages(id, 500).stream()
                .mapToLong(ChatMessage::getId).max().orElseThrow();
        // 先提交的事务不能把排序键覆盖回更早的那条消息
        assertThat(conversationMapper.selectById(id).getLastMessageId()).isEqualTo(maxId);
        assertThat(chatService.messages(id, 500)).hasSize(threads);
        // 自己发的消息不该给自己留下未读
        assertThat(chatService.fanUnreadTotal(fanId)).isZero();
    }
}
