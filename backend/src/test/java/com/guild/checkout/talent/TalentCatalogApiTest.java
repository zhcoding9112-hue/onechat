package com.guild.checkout.talent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guild.checkout.auth.WechatAuthController;
import com.guild.checkout.pricing.ServiceItem;
import com.guild.checkout.pricing.ServiceSpec;
import com.guild.checkout.pricing.admin.ServiceCatalogAdminService;
import com.guild.checkout.pricing.admin.TalentPriceAdminService;
import com.guild.checkout.talent.admin.TalentAdminService;
import com.guild.checkout.user.AppUser;
import com.guild.checkout.user.AppUserService;
import com.guild.checkout.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 主播目录（tasks 4.2 - 4.7）。
 */
@SpringBootTest
@Transactional
class TalentCatalogApiTest {

    @Autowired private WebApplicationContext context;
    @Autowired private AppUserService userService;
    @Autowired private TalentAdminService talentAdminService;
    @Autowired private TalentService talentService;
    @Autowired private ServiceCatalogAdminService catalogService;
    @Autowired private TalentPriceAdminService priceService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private MockHttpSession operator;
    private MockHttpSession fan;
    private ServiceSpec spec30;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        operator = sessionFor(newUser("op_", UserRole.OPERATOR));
        fan = sessionFor(newUser("fan_", null));

        ServiceItem item = catalogService.createItem("tc_" + System.nanoTime(), "情感倾听", null, 0);
        spec30 = catalogService.createSpec(item.getId(), "m30", "30分钟", 30, 30);
    }

    @Test
    @DisplayName("视觉复刻：昵称搜索按字面匹配，等级字段可维护且参与筛选")
    void 昵称搜索与等级贯通() throws Exception {
        String name = "视觉%搜索_" + System.nanoTime();
        long id = listedTalent(name);
        mockMvc.perform(put("/api/admin/talents/" + id).session(operator)
                .contentType(MediaType.APPLICATION_JSON).content("{\"level\":6}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.level").value(6));
        mockMvc.perform(get("/api/talents").session(fan).param("keyword", name).param("level", "6"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(id))
                .andExpect(jsonPath("$.data.items[0].level").value(6));
        mockMvc.perform(get("/api/talents").session(fan).param("keyword", name).param("level", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(get("/api/talents/" + id).session(fan))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.level").value(6))
                .andExpect(jsonPath("$.data.photos").isArray());
        mockMvc.perform(put("/api/admin/talents/" + id).session(operator)
                .contentType(MediaType.APPLICATION_JSON).content("{\"level\":7}"))
                .andExpect(status().isConflict());
    }

    // ---------- 4.2 档案增改与上下架 ----------

    @Test
    @DisplayName("4.2 新建主播默认不上架，上架后出现在粉丝端列表")
    void 新建后上架才出现在粉丝端() throws Exception {
        long talentId = createTalent("小雨");

        // 默认不上架
        assertThat(findInList(talentId)).isNull();

        mockMvc.perform(put("/api/admin/talents/" + talentId + "/listed/true").session(operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.listed").value(true));

        JsonNode card = findInList(talentId);
        assertThat(card).isNotNull();
        assertThat(card.get("nickname").asText()).isEqualTo("小雨");
    }

    @Test
    @DisplayName("4.2 下架后粉丝端列表不再返回该主播")
    void 下架后列表不再返回() throws Exception {
        long talentId = listedTalent("小雨");
        assertThat(findInList(talentId)).isNotNull();

        mockMvc.perform(put("/api/admin/talents/" + talentId + "/listed/false").session(operator))
                .andExpect(status().isOk());

        assertThat(findInList(talentId)).isNull();
        // 运营端仍可见
        mockMvc.perform(get("/api/admin/talents/" + talentId).session(operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.listed").value(false));
    }

    @Test
    @DisplayName("4.2 编辑资料与标签")
    void 编辑资料与标签() throws Exception {
        long talentId = createTalent("小雨");

        mockMvc.perform(put("/api/admin/talents/" + talentId)
                        .session(operator).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"小雨改\",\"intro\":\"温柔倾听者\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("小雨改"))
                .andExpect(jsonPath("$.data.intro").value("温柔倾听者"));

        mockMvc.perform(put("/api/admin/talents/" + talentId + "/tags")
                        .session(operator).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tags\":[\"温柔\",\"声控福利\",\"温柔\"]}"))
                .andExpect(status().isOk())
                // 重复标签被去重
                .andExpect(jsonPath("$.data.tags.length()").value(2));
    }

    @Test
    @DisplayName("4.2 昵称为空被拒绝")
    void 昵称为空被拒() throws Exception {
        mockMvc.perform(post("/api/admin/talents")
                        .session(operator).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAM"));
    }

    // ---------- 4.3 粉丝端列表 ----------

    @Test
    @DisplayName("4.3 列表按运营设定的顺序展示，含封面昵称标签在线状态")
    void 列表按顺序展示() throws Exception {
        long second = createTalent("后面的");
        talentAdminService.update(second, new TalentAdminService.ProfileForm(null, null, null, 20, null, null, null, null));
        talentAdminService.setListed(second, true);

        long first = createTalent("前面的");
        talentAdminService.update(first, new TalentAdminService.ProfileForm(null, null, null, 10, null, null, null, null));
        talentAdminService.replaceTags(first, List.of("温柔"));
        talentAdminService.setListed(first, true);

        // 列表已分页，顺序要跨页收集后再比，只看第一页会漏
        List<Long> ids = allListedIds().stream()
                .filter(id -> id == first || id == second)
                .toList();
        assertThat(ids).containsExactly(first, second);

        JsonNode card = findInList(first);
        assertThat(card.get("nickname").asText()).isEqualTo("前面的");
        assertThat(card.get("tags").get(0).asText()).isEqualTo("温柔");
        assertThat(card.get("online").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("4.3 没有已上架主播时返回空列表而非错误")
    void 无可用主播返回空列表() throws Exception {
        // 把所有已上架的都下架
        for (Talent t : talentService.listListed()) {
            talentAdminService.setListed(t.getId(), false);
        }
        mockMvc.perform(get("/api/talents").session(fan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.hasMore").value(false));
    }

    // ---------- 4.4 粉丝端详情 ----------

    @Test
    @DisplayName("4.4 详情展示资料与可购服务及价格")
    void 详情展示资料与可购服务() throws Exception {
        long talentId = listedTalent("小雨");
        talentAdminService.replaceTags(talentId, List.of("温柔", "耐心"));
        priceService.setPrice(talentId, spec30.getId(), 1500, true);

        JsonNode detail = dataOf(mockMvc.perform(
                get("/api/talents/" + talentId).session(fan)).andReturn());

        assertThat(detail.get("nickname").asText()).isEqualTo("小雨");
        assertThat(detail.get("tags")).hasSize(2);
        assertThat(detail.get("available").asBoolean()).isTrue();
        assertThat(detail.get("unavailableReason").isNull()).isTrue();
        assertThat(detail.get("services")).hasSize(1);
        assertThat(detail.get("services").get(0).get("specs").get(0).get("unitPriceCents").asLong())
                .isEqualTo(1500);
    }

    @Test
    @DisplayName("4.4 已下架主播通过旧链接访问时不展示下单入口")
    void 已下架主播详情不给下单入口() throws Exception {
        long talentId = listedTalent("小雨");
        priceService.setPrice(talentId, spec30.getId(), 1500, true);
        talentAdminService.setListed(talentId, false);

        JsonNode detail = dataOf(mockMvc.perform(
                get("/api/talents/" + talentId).session(fan)).andReturn());

        assertThat(detail.get("nickname").asText()).isEqualTo("小雨");
        assertThat(detail.get("available").asBoolean()).isFalse();
        assertThat(detail.get("unavailableReason").asText()).isEqualTo("该主播暂不可约");
        // 价格也不暴露
        assertThat(detail.get("services")).isEmpty();
    }

    @Test
    @DisplayName("4.4 未配置任何可售服务时展示资料但无下单入口")
    void 未配价主播详情无下单入口() throws Exception {
        long talentId = listedTalent("小雨");

        JsonNode detail = dataOf(mockMvc.perform(
                get("/api/talents/" + talentId).session(fan)).andReturn());

        assertThat(detail.get("nickname").asText()).isEqualTo("小雨");
        assertThat(detail.get("available").asBoolean()).isFalse();
        assertThat(detail.get("unavailableReason").asText()).isEqualTo("该主播暂无可购服务");
    }

    @Test
    @DisplayName("4.4 不存在的主播返回 404")
    void 不存在的主播返回404() throws Exception {
        mockMvc.perform(get("/api/talents/99999999").session(fan))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ---------- 4.5 试听片段 ----------

    @Test
    @DisplayName("4.5 上传试听后详情返回地址与时长；未配置时不返回")
    void 试听片段上传与展示() throws Exception {
        long talentId = listedTalent("小雨");
        priceService.setPrice(talentId, spec30.getId(), 1500, true);

        // 未配置时不返回试听
        JsonNode before = dataOf(mockMvc.perform(
                get("/api/talents/" + talentId).session(fan)).andReturn());
        assertThat(before.get("audioUrl").isNull()).isTrue();

        MockMultipartFile file = new MockMultipartFile(
                "file", "demo.mp3", "audio/mpeg", "fake-audio-bytes".getBytes());
        mockMvc.perform(multipart("/api/admin/talents/" + talentId + "/media/audio")
                        .file(file).param("seconds", "12").session(operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url", org.hamcrest.Matchers.startsWith("/uploads/talent-audio/")));

        JsonNode after = dataOf(mockMvc.perform(
                get("/api/talents/" + talentId).session(fan)).andReturn());
        assertThat(after.get("audioUrl").asText()).startsWith("/uploads/talent-audio/");
        assertThat(after.get("audioSeconds").asInt()).isEqualTo(12);
    }

    @Test
    @DisplayName("4.5 非音频格式被拒绝")
    void 非音频格式被拒() throws Exception {
        long talentId = createTalent("小雨");
        MockMultipartFile file = new MockMultipartFile(
                "file", "evil.exe", "application/octet-stream", "x".getBytes());

        mockMvc.perform(multipart("/api/admin/talents/" + talentId + "/media/audio")
                        .file(file).param("seconds", "12").session(operator))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("不支持")));
    }

    @Test
    @DisplayName("4.5 上传的文件名不被沿用，防路径穿越")
    void 文件名不被沿用() throws Exception {
        long talentId = createTalent("小雨");
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../../etc/passwd.mp3", "audio/mpeg", "x".getBytes());

        MvcResult result = mockMvc.perform(multipart("/api/admin/talents/" + talentId + "/media/audio")
                        .file(file).param("seconds", "5").session(operator))
                .andExpect(status().isOk())
                .andReturn();

        String url = dataOf(result).get("url").asText();
        assertThat(url).doesNotContain("..").doesNotContain("passwd").endsWith(".mp3");
    }

    // ---------- 4.6 在线状态 ----------

    @Test
    @DisplayName("4.6 主播切换在线状态即时反映到粉丝端，且离线仍可下单")
    void 在线状态切换与离线可下单() throws Exception {
        long talentId = listedTalent("小雨");
        priceService.setPrice(talentId, spec30.getId(), 1500, true);
        AppUser talentUser = newUser("talent_", null);
        talentAdminService.linkUser(talentId, talentUser.getId());
        MockHttpSession talentSession = sessionFor(talentUser);

        assertThat(findInList(talentId).get("online").asBoolean()).isFalse();

        mockMvc.perform(put("/api/talent/workspace/online/true").session(talentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.online").value(true));
        assertThat(findInList(talentId).get("online").asBoolean()).isTrue();

        mockMvc.perform(put("/api/talent/workspace/online/false").session(talentSession))
                .andExpect(status().isOk());
        assertThat(findInList(talentId).get("online").asBoolean()).isFalse();

        // 离线主播仍可被下单：详情页依然 available
        JsonNode detail = dataOf(mockMvc.perform(
                get("/api/talents/" + talentId).session(fan)).andReturn());
        assertThat(detail.get("online").asBoolean()).isFalse();
        assertThat(detail.get("available").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("4.6 退出后重新进入仍保持上次设置的在线状态")
    void 在线状态持久化() throws Exception {
        long talentId = listedTalent("小雨");
        AppUser talentUser = newUser("talent_", null);
        talentAdminService.linkUser(talentId, talentUser.getId());

        mockMvc.perform(put("/api/talent/workspace/online/true").session(sessionFor(talentUser)))
                .andExpect(status().isOk());

        // 换一个全新会话，相当于退出后重新进入
        mockMvc.perform(get("/api/talent/workspace/me").session(sessionFor(talentUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.online").value(true));
    }

    // ---------- 4.7 关联账号与授予角色 ----------

    @Test
    @DisplayName("4.7 关联微信用户后该用户获得主播角色并可进工作台")
    void 关联后获得主播角色() throws Exception {
        long talentId = createTalent("小雨");
        AppUser user = newUser("tobe_", null);
        MockHttpSession userSession = sessionFor(user);

        // 关联前进不了工作台
        mockMvc.perform(get("/api/talent/workspace/me").session(userSession))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/admin/talents/" + talentId + "/user")
                        .session(operator).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + user.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(user.getId()));

        // 关联后可进工作台
        mockMvc.perform(get("/api/talent/workspace/me").session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.talentId").value(talentId));

        // 粉丝身份态仍然可用
        mockMvc.perform(get("/api/session/me").session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles", org.hamcrest.Matchers.hasItem("FAN")))
                .andExpect(jsonPath("$.data.talent").value(true));
    }

    @Test
    @DisplayName("4.7 同一微信用户不能关联到第二条档案")
    void 同一用户不能关联两条档案() throws Exception {
        long first = createTalent("甲");
        long second = createTalent("乙");
        AppUser user = newUser("dup_", null);

        link(first, user.getId());

        mockMvc.perform(put("/api/admin/talents/" + second + "/user")
                        .session(operator).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + user.getId() + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("已关联")));
    }

    @Test
    @DisplayName("4.7 解除关联后回收主播角色")
    void 解除关联回收角色() throws Exception {
        long talentId = createTalent("小雨");
        AppUser user = newUser("unlink_", null);
        link(talentId, user.getId());

        mockMvc.perform(get("/api/talent/workspace/me").session(sessionFor(user)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/talents/" + talentId + "/user").session(operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").doesNotExist());

        mockMvc.perform(get("/api/talent/workspace/me").session(sessionFor(user)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("4.7 未关联账号的档案可正常展示与配价")
    void 未关联账号的档案可正常使用() throws Exception {
        long talentId = listedTalent("无账号主播");
        priceService.setPrice(talentId, spec30.getId(), 1500, true);

        JsonNode detail = dataOf(mockMvc.perform(
                get("/api/talents/" + talentId).session(fan)).andReturn());
        assertThat(detail.get("available").asBoolean()).isTrue();
        assertThat(talentService.require(talentId).getUserId()).isNull();
    }

    // ---------- 鉴权 ----------

    @Test
    @DisplayName("粉丝访问运营侧档案接口无权限")
    void 粉丝访问运营接口被拒() throws Exception {
        mockMvc.perform(get("/api/admin/talents").session(fan))
                .andExpect(status().isForbidden());
    }

    // ---------- 辅助 ----------

    private long createTalent(String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/talents")
                        .session(operator).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.guild.checkout.talent.admin.TalentAdminController
                                        .CreateRequest(nickname, null, null, 0, null, null, null, null, null))))
                .andExpect(status().isOk())
                .andReturn();
        return dataOf(result).get("id").asLong();
    }

    private long listedTalent(String nickname) throws Exception {
        long id = createTalent(nickname);
        talentAdminService.setListed(id, true);
        return id;
    }

    private void link(long talentId, long userId) throws Exception {
        mockMvc.perform(put("/api/admin/talents/" + talentId + "/user")
                        .session(operator).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + userId + "}"))
                .andExpect(status().isOk());
    }

    /** 跨页收集粉丝端列表的全部主播 id，保持接口给出的顺序。 */
    private List<Long> allListedIds() throws Exception {
        List<Long> all = new java.util.ArrayList<>();
        for (int page = 0; page < 200; page++) {
            JsonNode data = dataOf(mockMvc.perform(
                    get("/api/talents?page=" + page + "&size=50").session(fan)).andReturn());
            for (JsonNode n : data.get("items")) {
                all.add(n.get("id").asLong());
            }
            if (!data.get("hasMore").asBoolean()) {
                break;
            }
        }
        return all;
    }

    /**
     * 在粉丝端列表中查找某位主播。
     *
     * 列表自第 2 组起分页，单页上限 50，而开发库里的已上架主播远不止一页，
     * 所以必须翻页找完，不能只看第一页。
     */
    private JsonNode findInList(long talentId) throws Exception {
        for (int page = 0; page < 200; page++) {
            JsonNode data = dataOf(mockMvc.perform(
                    get("/api/talents?page=" + page + "&size=50").session(fan)).andReturn());
            JsonNode items = data.get("items");
            for (JsonNode node : items) {
                if (node.get("id").asLong() == talentId) {
                    return node;
                }
            }
            if (!data.get("hasMore").asBoolean()) {
                return null;
            }
        }
        return null;
    }

    private JsonNode dataOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private AppUser newUser(String prefix, UserRole extraRole) {
        AppUser user = userService.registerOrUpdate(prefix + System.nanoTime(), null, prefix, null);
        if (extraRole != null) {
            userService.grantRole(user.getId(), extraRole);
        }
        return user;
    }

    private MockHttpSession sessionFor(AppUser user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(WechatAuthController.SESSION_USER_ID, user.getId());
        return session;
    }
}
