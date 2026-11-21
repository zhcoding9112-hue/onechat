package com.guild.checkout.feed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guild.checkout.auth.WechatAuthController;
import com.guild.checkout.level.FanLevelService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 评论区的身份显示。
 *
 * 之前 CommentView 只有 (id, userId, content, createdAt)，前端连"谁说的"都显示不了，
 * 评论区实际是匿名的。这里验的是补上之后每条评论都带对了人。
 */
@SpringBootTest
@Transactional
class FeedCommentIdentityTest {

    @Autowired private WebApplicationContext context;
    @Autowired private FeedService feedService;
    @Autowired private AppUserService userService;
    @Autowired private TalentAdminService talentAdminService;
    @Autowired private FanLevelService fanLevelService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;

    private MockMvc mockMvc;
    private MockHttpSession fan;
    private long fanId;
    private long otherFanId;
    private long talentUserId;
    private long talentId;
    private FeedPost post;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        AppUser fanUser = newUser("fci_", "花了钱的粉丝");
        fanId = fanUser.getId();
        fan = sessionFor(fanUser);
        otherFanId = newUser("fco_", "没花钱的粉丝").getId();

        AppUser talentUser = newUser("fct_", "主播本人");
        talentUserId = talentUser.getId();
        userService.grantRole(talentUserId, UserRole.TALENT);

        Talent t = talentAdminService.create(new TalentAdminService.ProfileForm(
                "评论区主播" + System.nanoTime(), null, null, 0, null, null, null, null));
        talentAdminService.setListed(t.getId(), true);
        talentId = t.getId();
        talentAdminService.linkUser(talentId, talentUserId);

        post = feedService.publish(talentId, "今晚八点开麦", null);
    }

    @Test
    @DisplayName("每条评论带上昵称与头像 — 评论区不再是匿名的")
    void 评论带昵称头像() throws Exception {
        feedService.comment(fanId, post.getId(), "来了");

        JsonNode c = comments().get(0);

        assertThat(c.get("nickname").asText()).isEqualTo("花了钱的粉丝");
        assertThat(c.get("avatarUrl").asText()).isNotBlank();
        assertThat(c.get("content").asText()).isEqualTo("来了");
    }

    @Test
    @DisplayName("粉丝的评论带等级，且与粉丝自己看到的是同一个")
    void 粉丝评论带等级() throws Exception {
        spend(fanId, 80000);
        feedService.comment(fanId, post.getId(), "来了");

        JsonNode c = comments().get(0);

        // 不写死"¥800 是银卡"：门槛是运营可改的配置
        assertThat(c.get("level").asInt()).isEqualTo(fanLevelService.levelOf(fanId).level());
        assertThat(c.get("levelName").asText()).isEqualTo(fanLevelService.levelOf(fanId).name());
        assertThat(c.get("author").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("主播评论自己的动态标为作者，且不挂粉丝等级")
    void 作者评论不挂等级() throws Exception {
        feedService.comment(talentUserId, post.getId(), "等你们");

        JsonNode c = comments().get(0);

        assertThat(c.get("author").asBoolean()).isTrue();
        assertThat(c.get("level").isNull())
                .as("主播没有粉丝等级，给他挂一个「新客」反而荒唐")
                .isTrue();
        assertThat(c.get("levelName").isNull()).isTrue();
    }

    /**
     * 批量查最容易犯的错：整列用同一个人的昵称或等级。
     */
    @Test
    @DisplayName("多条评论各自对上各自的人")
    void 多条评论不串行() throws Exception {
        spend(fanId, 80000);
        feedService.comment(fanId, post.getId(), "第一条");
        feedService.comment(otherFanId, post.getId(), "第二条");
        feedService.comment(talentUserId, post.getId(), "第三条");

        List<JsonNode> list = comments();

        assertThat(list).hasSize(3);
        assertThat(list.get(0).get("nickname").asText()).isEqualTo("花了钱的粉丝");
        assertThat(list.get(1).get("nickname").asText()).isEqualTo("没花钱的粉丝");
        assertThat(list.get(2).get("nickname").asText()).isEqualTo("主播本人");

        assertThat(list.get(0).get("level").asInt())
                .as("花过钱的等级应当高于没花过的")
                .isGreaterThan(list.get(1).get("level").asInt());
        assertThat(list.get(1).get("level").asInt()).isEqualTo(1);
        assertThat(list.get(2).get("author").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("发表评论的返回值与列表里的形状一致")
    void 发表后的返回也带身份() throws Exception {
        spend(fanId, 80000);

        MvcResult result = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/feed/posts/" + post.getId() + "/comments")
                                .session(fan)
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("{\"content\":\"刚发的\"}"))
                .andExpect(status().isOk()).andReturn();

        JsonNode c = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertThat(c.get("nickname").asText()).isEqualTo("花了钱的粉丝");
        assertThat(c.get("avatarUrl").asText()).isNotBlank();
        assertThat(c.get("level").asInt()).isEqualTo(fanLevelService.levelOf(fanId).level());
    }

    // ---------- 辅助 ----------

    private List<JsonNode> comments() throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/api/feed/posts/" + post.getId() + "/comments").session(fan))
                .andExpect(status().isOk()).andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        List<JsonNode> out = new java.util.ArrayList<>();
        data.forEach(out::add);
        return out;
    }

    /** 造一笔已完成的打赏当作消费。 */
    private void spend(long userId, long cents) {
        jdbc.update("""
                INSERT INTO tip_record (tip_ref, fan_user_id, talent_id, amount_cents, paid_cents,
                  commission_rate_bp, guild_cents, talent_cents, status, version, created_at)
                VALUES (?, ?, ?, ?, ?, 3000, ?, ?, 'COMPLETED', 0, NOW())
                """,
                "F" + System.nanoTime(), userId, talentId, cents, cents,
                cents * 30 / 100, cents - cents * 30 / 100);
    }

    private AppUser newUser(String prefix, String nickname) {
        return userService.registerOrUpdate(prefix + System.nanoTime(), null, nickname, null);
    }

    private MockHttpSession sessionFor(AppUser user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(WechatAuthController.SESSION_USER_ID, user.getId());
        return session;
    }
}
