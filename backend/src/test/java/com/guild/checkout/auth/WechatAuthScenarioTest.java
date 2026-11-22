package com.guild.checkout.auth;

import com.guild.checkout.talent.Talent;
import com.guild.checkout.talent.admin.TalentAdminService;
import com.guild.checkout.user.AppUser;
import com.guild.checkout.user.AppUserMapper;
import com.guild.checkout.user.AppUserService;
import com.guild.checkout.user.Roles;
import com.guild.checkout.user.UserRole;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * wechat-auth spec 的场景测试，逐条对应规格中的 Scenario。
 *
 * 微信侧走本机桩（未配置 AppID 时自动启用），因此这些场景不依赖真实测试号；
 * 真实测试号的联调是 tasks 2.2 的验收，另行执行。
 */
@SpringBootTest
@Transactional
class WechatAuthScenarioTest {

    private static final String WECHAT_UA =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) MicroMessenger/8.0.44 NetType/WIFI";
    private static final String BROWSER_UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/120 Safari/537.36";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AppUserService userService;

    @Autowired
    private AppUserMapper userMapper;

    @Autowired
    private TalentAdminService talentAdminService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    // ---------- Requirement: 服务号网页授权登录 ----------

    @Test
    @DisplayName("Scenario: 首次进入并完成授权 — 建用户、建会话、送回原页面")
    void 首次进入并完成授权() throws Exception {
        // 桩固定返回同一个 openid，先清掉可能已存在的那条，确保"首次"这个前提成立。
        // 本类带 @Transactional，删除会随测试回滚。
        userMapper.delete(Wrappers.<AppUser>lambdaQuery().likeRight(AppUser::getOpenId, "stub_"));

        MockHttpSession session = new MockHttpSession();

        MvcResult authorize = mockMvc.perform(get("/api/auth/wechat/authorize")
                        .param("redirect", "/talent/8")
                        .header("User-Agent", WECHAT_UA)
                        .session(session))
                .andExpect(status().isFound())
                .andReturn();

        URI wechatUrl = URI.create(authorize.getResponse().getRedirectedUrl());
        String code = queryParam(wechatUrl, "code");
        String state = queryParam(wechatUrl, "state");
        assertThat(code).isNotBlank();
        assertThat(state).isNotBlank();

        long before = countUsers();

        mockMvc.perform(get("/api/auth/wechat/callback")
                        .param("code", code)
                        .param("state", state)
                        .header("User-Agent", WECHAT_UA)
                        .session(session))
                .andExpect(status().isFound())
                // 送回原本请求的页面
                .andExpect(header().string("Location", "/talent/8"));

        // 建立了系统用户
        assertThat(countUsers()).isEqualTo(before + 1);
        AppUser created = findByOpenIdPrefix();
        assertThat(created).isNotNull();

        // 建立了会话
        assertThat(session.getAttribute(WechatAuthController.SESSION_USER_ID)).isNotNull();
    }

    /**
     * 手机连同一个 Wi-Fi、从局域网地址打开时，授权必须回到同一个地址。
     *
     * 配置里的 APP_BASE_URL 默认是 127.0.0.1:5173，而手机上的 127.0.0.1 是手机自己，
     * 跳过去就是一页「无法打开网页」。开发期用桩时按请求自己的 Host 回跳，
     * localhost / 127.0.0.1 / 局域网 IP 三种进法才都能走通。
     */
    @Test
    @DisplayName("用桩时按请求的 Host 回跳，手机从局域网地址进来才不会被甩到 127.0.0.1")
    void 桩按请求Host回跳() throws Exception {
        MvcResult authorize = mockMvc.perform(get("/api/auth/wechat/authorize")
                        .param("redirect", "/")
                        .header("User-Agent", WECHAT_UA)
                        .header("Host", "192.168.1.6:5173")
                        .session(new MockHttpSession()))
                .andExpect(status().isFound())
                .andReturn();

        String redirect = authorize.getResponse().getRedirectedUrl();
        assertThat(redirect)
                .as("回调地址要落在请求进来的那个 Host 上")
                .startsWith("http://192.168.1.6:5173/api/auth/wechat/callback")
                .doesNotContain("127.0.0.1");
    }

    /**
     * 隧道外面是 https、进到后端是 http，按请求自己的协议拼回跳就成了降级跳转。
     */
    @Test
    @DisplayName("隧道转发时认 X-Forwarded-Proto，回跳用 https 而不是 http")
    void 桩认转发协议头() throws Exception {
        MvcResult authorize = mockMvc.perform(get("/api/auth/wechat/authorize")
                        .param("redirect", "/")
                        .header("User-Agent", WECHAT_UA)
                        .header("Host", "demo.trycloudflare.com")
                        .header("X-Forwarded-Proto", "https")
                        .session(new MockHttpSession()))
                .andExpect(status().isFound())
                .andReturn();

        assertThat(authorize.getResponse().getRedirectedUrl())
                .startsWith("https://demo.trycloudflare.com/api/auth/wechat/callback");
    }

    @Test
    @DisplayName("Scenario: 已登录用户再次进入 — 不再发起授权跳转")
    void 已登录用户再次进入() throws Exception {
        MockHttpSession session = loggedInSession();

        mockMvc.perform(get("/api/auth/wechat/authorize")
                        .param("redirect", "/my")
                        .header("User-Agent", WECHAT_UA)
                        .session(session))
                .andExpect(status().isFound())
                // 直接回目标页，而不是跳微信授权页
                .andExpect(header().string("Location", "/my"));
    }

    @Test
    @DisplayName("Scenario: 用户拒绝授权 — 展示说明页且不创建用户")
    void 用户拒绝授权() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(get("/api/auth/wechat/authorize")
                .param("redirect", "/talent/8")
                .header("User-Agent", WECHAT_UA)
                .session(session));

        long before = countUsers();

        // 微信在用户点拒绝时不带 code 回跳
        mockMvc.perform(get("/api/auth/wechat/callback")
                        .param("state", "anything")
                        .header("User-Agent", WECHAT_UA)
                        .session(session))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.startsWith("/auth/denied")));

        assertThat(countUsers()).isEqualTo(before);
        assertThat(session.getAttribute(WechatAuthController.SESSION_USER_ID)).isNull();
    }

    @Test
    @DisplayName("Scenario: 在微信以外的浏览器打开 — 引导页且不执行授权流程")
    void 在微信以外的浏览器打开() throws Exception {
        long before = countUsers();
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(get("/api/auth/wechat/authorize")
                        .param("redirect", "/talent/8")
                        .header("User-Agent", BROWSER_UA)
                        .session(session))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.startsWith("/auth/wechat-only")));

        assertThat(countUsers()).isEqualTo(before);
        assertThat(session.getAttribute(WechatAuthController.SESSION_USER_ID)).isNull();
    }

    // ---------- Requirement: 用户角色识别 ----------

    @Test
    @DisplayName("Scenario: 新用户默认角色 — 仅粉丝")
    void 新用户默认角色为粉丝() {
        AppUser user = userService.registerOrUpdate("o_new_" + System.nanoTime(), null, "新粉丝", null);

        assertThat(user.roleSet()).containsExactly(UserRole.FAN);
        assertThat(user.getRoles()).isEqualTo(Roles.DEFAULT);
    }

    @Test
    @DisplayName("Scenario: 运营将用户授予主播角色 — 主播态可用且粉丝态仍可用")
    void 授予主播角色后两种身份态都可用() throws Exception {
        AppUser user = userService.registerOrUpdate("o_grant_" + System.nanoTime(), null, "待授权", null);
        MockHttpSession session = sessionFor(user.getId());

        // 关联前：主播工作台无权限
        mockMvc.perform(get("/api/talent/workspace/pending-orders").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 规格里主播角色是由「关联到一条主播档案」授予的，而不是单独发角色
        Talent talent = talentAdminService.create(new TalentAdminService.ProfileForm("小雨", null, null, 0, null, null, null, null));
        talentAdminService.linkUser(talent.getId(), user.getId());

        // 关联后：主播工作台可用
        mockMvc.perform(get("/api/talent/workspace/pending-orders").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 粉丝身份态仍然可用
        mockMvc.perform(get("/api/session/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles", org.hamcrest.Matchers.hasItem("FAN")))
                .andExpect(jsonPath("$.data.talent").value(true));
    }

    @Test
    @DisplayName("只有主播角色但未关联档案时，工作台仍无权限")
    void 有角色无档案时工作台不可用() throws Exception {
        AppUser user = userService.registerOrUpdate("o_norole_" + System.nanoTime(), null, "孤儿角色", null);
        userService.grantRole(user.getId(), UserRole.TALENT);

        mockMvc.perform(get("/api/talent/workspace/pending-orders").session(sessionFor(user.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("未关联")));
    }

    @Test
    @DisplayName("Scenario: 普通用户尝试进入主播身份态 — 无权限")
    void 普通用户进入主播身份态被拒() throws Exception {
        AppUser user = userService.registerOrUpdate("o_fan_" + System.nanoTime(), null, "普通粉丝", null);

        mockMvc.perform(get("/api/talent/workspace/pending-orders").session(sessionFor(user.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ---------- Requirement: 会话与接口鉴权 ----------

    @Test
    @DisplayName("Scenario: 未登录访问受保护接口 — 未认证且不返回业务数据")
    void 未登录访问受保护接口() throws Exception {
        mockMvc.perform(get("/api/session/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("Scenario: 会话过期 — 重新授权后恢复到原请求页面")
    void 会话过期后重新授权恢复原页面() throws Exception {
        AppUser user = userService.registerOrUpdate("o_exp_" + System.nanoTime(), null, "会话用户", null);
        MockHttpSession session = sessionFor(user.getId());

        mockMvc.perform(get("/api/session/me").session(session)).andExpect(status().isOk());

        // 会话过期
        session.clearAttributes();
        mockMvc.perform(get("/api/session/me").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        // 重新授权，回到原本请求的页面
        MockHttpSession fresh = new MockHttpSession();
        MvcResult authorize = mockMvc.perform(get("/api/auth/wechat/authorize")
                        .param("redirect", "/order/77")
                        .header("User-Agent", WECHAT_UA)
                        .session(fresh))
                .andReturn();
        URI wechatUrl = URI.create(authorize.getResponse().getRedirectedUrl());

        mockMvc.perform(get("/api/auth/wechat/callback")
                        .param("code", queryParam(wechatUrl, "code"))
                        .param("state", queryParam(wechatUrl, "state"))
                        .header("User-Agent", WECHAT_UA)
                        .session(fresh))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/order/77"));

        mockMvc.perform(get("/api/session/me").session(fresh)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Scenario: 越权访问 — 看板仅运营可见")
    void 非运营访问看板被拒() throws Exception {
        AppUser fan = userService.registerOrUpdate("o_board_" + System.nanoTime(), null, "粉丝", null);
        mockMvc.perform(get("/api/admin/analytics/funnel").session(sessionFor(fan.getId())))
                .andExpect(status().isForbidden());

        userService.grantRole(fan.getId(), UserRole.TALENT);
        mockMvc.perform(get("/api/admin/analytics/funnel").session(sessionFor(fan.getId())))
                .andExpect(status().isForbidden());

        userService.grantRole(fan.getId(), UserRole.OPERATOR);
        mockMvc.perform(get("/api/admin/analytics/funnel").session(sessionFor(fan.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.funnel").isArray());
    }

    // ---------- Requirement: 用户基础资料获取 ----------

    @Test
    @DisplayName("Scenario: 成功获取资料 — 昵称被保存并展示")
    void 成功获取资料时保存昵称() throws Exception {
        AppUser user = userService.registerOrUpdate("o_prof_" + System.nanoTime(), null, "有昵称的人", "https://img/a.png");

        mockMvc.perform(get("/api/session/me").session(sessionFor(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("有昵称的人"))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://img/a.png"))
                .andExpect(jsonPath("$.data.placeholderProfile").value(false));
    }

    @Test
    @DisplayName("Scenario: 资料获取失败 — 仍完成登录并以占位展示")
    void 资料获取失败时以占位展示() throws Exception {
        AppUser user = userService.registerOrUpdate("o_noprof_" + System.nanoTime(), null, null, null);

        mockMvc.perform(get("/api/session/me").session(sessionFor(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nickname",
                        org.hamcrest.Matchers.startsWith("微信用户")))
                .andExpect(jsonPath("$.data.avatarUrl").value("/images/avatar-placeholder.svg"))
                .andExpect(jsonPath("$.data.placeholderProfile").value(true));
    }

    @Test
    @DisplayName("再次授权不得用空资料覆盖已有昵称")
    void 再次授权不覆盖已有昵称() {
        String openId = "o_keep_" + System.nanoTime();
        userService.registerOrUpdate(openId, null, "原昵称", "https://img/a.png");

        AppUser again = userService.registerOrUpdate(openId, null, null, null);

        assertThat(again.getNickname()).isEqualTo("原昵称");
        assertThat(again.getAvatarUrl()).isEqualTo("https://img/a.png");
    }

    // ---------- 辅助 ----------

    private MockHttpSession loggedInSession() {
        AppUser user = userService.registerOrUpdate("o_login_" + System.nanoTime(), null, "已登录", null);
        return sessionFor(user.getId());
    }

    private MockHttpSession sessionFor(Long userId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(WechatAuthController.SESSION_USER_ID, userId);
        return session;
    }

    private long countUsers() {
        return userMapper.selectCount(Wrappers.emptyWrapper());
    }

    private AppUser findByOpenIdPrefix() {
        return userMapper.selectOne(Wrappers.<AppUser>lambdaQuery()
                .likeRight(AppUser::getOpenId, "stub_")
                .last("limit 1"));
    }

    private static String queryParam(URI uri, String name) {
        for (String pair : uri.getQuery().split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }
}
