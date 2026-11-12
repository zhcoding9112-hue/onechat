package com.guild.checkout.pricing.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guild.checkout.auth.WechatAuthController;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 运营配置接口的集成测试（tasks 3.2 / 3.3 / 3.4）。
 * 全程走真实 HTTP 与真实数据库。
 */
@SpringBootTest
@Transactional
class PricingAdminApiTest {

    private static final long TALENT = 2001L;
    private static final long OTHER_TALENT = 2002L;

    @Autowired private WebApplicationContext context;
    @Autowired private AppUserService userService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private MockHttpSession operator;
    private MockHttpSession fan;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        operator = sessionFor(newUser("op_", UserRole.OPERATOR));
        fan = sessionFor(newUser("fan_", null));
    }

    // ---------- 3.2 服务项目与档位 ----------

    @Test
    @DisplayName("3.2 新增项目与档位后可被配置与展示")
    void 新增项目与档位() throws Exception {
        long itemId = createItem("listen", "情感倾听");

        mockMvc.perform(post("/api/admin/pricing/items/" + itemId + "/specs")
                        .session(operator).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"m30","name":"30分钟","durationMinutes":30}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("30分钟"))
                .andExpect(jsonPath("$.data.enabled").value(true));

        mockMvc.perform(get("/api/admin/pricing/items").session(operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id==" + itemId + ")].specs.length()").value(1));
    }

    @Test
    @DisplayName("3.2 项目编码重复被拒绝")
    void 项目编码重复被拒() throws Exception {
        String code = "dup_code_" + System.nanoTime();
        createItemWithCode(code, "项目甲");

        mockMvc.perform(post("/api/admin/pricing/items")
                        .session(operator).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"项目乙\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_REJECTED"))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("已存在")));
    }

    @Test
    @DisplayName("3.2 停用项目后粉丝端不再返回，运营端仍可见")
    void 停用项目后粉丝端不再返回() throws Exception {
        long itemId = createItem("stop_item", "将被停用");
        long specId = createSpec(itemId, "m30", "30分钟", 30);
        setPrice(TALENT, specId, 1500);

        // 停用前粉丝端可见
        assertThat(findById(dataOf(mockMvc.perform(
                get("/api/talents/" + TALENT + "/services").session(fan)).andReturn()), itemId)).isNotNull();

        mockMvc.perform(put("/api/admin/pricing/items/" + itemId + "/enabled/false").session(operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        // 停用后粉丝端不再返回该项目
        assertThat(findById(dataOf(mockMvc.perform(
                get("/api/talents/" + TALENT + "/services").session(fan)).andReturn()), itemId)).isNull();

        // 运营端仍能看到，以便重新启用
        mockMvc.perform(get("/api/admin/pricing/items").session(operator))
                .andExpect(jsonPath("$.data[?(@.id==" + itemId + ")].enabled").value(false));

        // 重新启用后恢复
        mockMvc.perform(put("/api/admin/pricing/items/" + itemId + "/enabled/true").session(operator))
                .andExpect(status().isOk());
        assertThat(findById(dataOf(mockMvc.perform(
                get("/api/talents/" + TALENT + "/services").session(fan)).andReturn()), itemId)).isNotNull();
    }

    @Test
    @DisplayName("3.2 停用档位只影响该档位，同项目其他档位不受影响")
    void 停用档位只影响该档位() throws Exception {
        long itemId = createItem("multi_spec", "多档位项目");
        long spec30 = createSpec(itemId, "m30", "30分钟", 30);
        long spec60 = createSpec(itemId, "m60", "60分钟", 60);
        setPrice(TALENT, spec30, 1500);
        setPrice(TALENT, spec60, 2500);

        mockMvc.perform(put("/api/admin/pricing/specs/" + spec30 + "/enabled/false").session(operator))
                .andExpect(status().isOk());

        JsonNode services = dataOf(mockMvc.perform(
                get("/api/talents/" + TALENT + "/services").session(fan)).andReturn());
        JsonNode group = findById(services, itemId);
        assertThat(group).isNotNull();
        assertThat(group.get("specs")).hasSize(1);
        assertThat(group.get("specs").get(0).get("serviceSpecId").asLong()).isEqualTo(spec60);
    }

    @Test
    @DisplayName("3.2 参数校验：项目名称为空被拒绝并给出字段明细")
    void 项目名称为空被拒() throws Exception {
        mockMvc.perform(post("/api/admin/pricing/items")
                        .session(operator).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"blank_name\",\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAM"))
                .andExpect(jsonPath("$.errors[?(@.field=='name')]").exists());
    }

    @Test
    @DisplayName("3.2 不存在的项目返回 404")
    void 不存在的项目返回404() throws Exception {
        mockMvc.perform(get("/api/admin/pricing/items/99999999/specs").session(operator))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ---------- 3.3 主播价格覆写 ----------

    @Test
    @DisplayName("3.3 未配置价格的「主播×档位」不出现在该主播可购列表")
    void 未配价的档位不出现在可购列表() throws Exception {
        long itemId = createItem("price_scope", "价格范围");
        long spec30 = createSpec(itemId, "m30", "30分钟", 30);
        long spec60 = createSpec(itemId, "m60", "60分钟", 60);

        // 只给 30 分钟配价，60 分钟不配
        setPrice(TALENT, spec30, 1500);

        JsonNode group = findById(dataOf(mockMvc.perform(
                get("/api/talents/" + TALENT + "/services").session(fan)).andReturn()), itemId);
        assertThat(group.get("specs")).hasSize(1);
        assertThat(group.get("specs").get(0).get("serviceSpecId").asLong()).isEqualTo(spec30);

        // 另一位主播完全没配价，整个列表为空
        mockMvc.perform(get("/api/talents/" + OTHER_TALENT + "/services").session(fan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // 补配 60 分钟后立即出现
        setPrice(TALENT, spec60, 2500);
        group = findById(dataOf(mockMvc.perform(
                get("/api/talents/" + TALENT + "/services").session(fan)).andReturn()), itemId);
        assertThat(group.get("specs")).hasSize(2);
    }

    @Test
    @DisplayName("3.3 不同主播在同一档位上看到各自的专属价格")
    void 不同主播各自价格() throws Exception {
        long itemId = createItem("diff_price", "差异化价格");
        long specId = createSpec(itemId, "m30", "30分钟", 30);
        setPrice(TALENT, specId, 1500);
        setPrice(OTHER_TALENT, specId, 8800);

        assertThat(priceOf(TALENT, itemId)).isEqualTo(1500);
        assertThat(priceOf(OTHER_TALENT, itemId)).isEqualTo(8800);
    }

    @Test
    @DisplayName("3.3 重复设置价格是更新而非新建")
    void 重复设置价格为更新() throws Exception {
        long itemId = createItem("upsert_price", "覆盖价格");
        long specId = createSpec(itemId, "m30", "30分钟", 30);

        setPrice(TALENT, specId, 1500);
        setPrice(TALENT, specId, 2000);

        JsonNode prices = dataOf(mockMvc.perform(
                get("/api/admin/pricing/talents/" + TALENT + "/prices").session(operator)).andReturn());
        long matched = 0;
        for (JsonNode node : prices) {
            if (node.get("serviceSpecId").asLong() == specId) {
                matched++;
            }
        }
        assertThat(matched).as("同一组合只应有一条价格记录").isEqualTo(1);
        assertThat(priceOf(TALENT, itemId)).isEqualTo(2000);
    }

    @Test
    @DisplayName("3.3 停用价格后该档位不可购，重新启用后恢复")
    void 停用价格后不可购() throws Exception {
        long itemId = createItem("toggle_price", "启停价格");
        long specId = createSpec(itemId, "m30", "30分钟", 30);
        setPrice(TALENT, specId, 1500);

        mockMvc.perform(put("/api/admin/pricing/talents/" + TALENT + "/prices/" + specId + "/enabled/false")
                        .session(operator))
                .andExpect(status().isOk());
        assertThat(findById(dataOf(mockMvc.perform(
                get("/api/talents/" + TALENT + "/services").session(fan)).andReturn()), itemId)).isNull();

        mockMvc.perform(put("/api/admin/pricing/talents/" + TALENT + "/prices/" + specId + "/enabled/true")
                        .session(operator))
                .andExpect(status().isOk());
        assertThat(findById(dataOf(mockMvc.perform(
                get("/api/talents/" + TALENT + "/services").session(fan)).andReturn()), itemId)).isNotNull();
    }

    @Test
    @DisplayName("3.3 单价为零或负数被拒绝")
    void 非法单价被拒() throws Exception {
        long itemId = createItem("bad_price", "非法单价");
        long specId = createSpec(itemId, "m30", "30分钟", 30);

        for (String body : new String[]{"{\"unitPriceCents\":0}", "{\"unitPriceCents\":-100}"}) {
            mockMvc.perform(put("/api/admin/pricing/talents/" + TALENT + "/prices/" + specId)
                            .session(operator).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PARAM"));
        }
    }

    @Test
    @DisplayName("3.3 单价超出上限被拒绝，防止少打小数点")
    void 单价超上限被拒() throws Exception {
        long itemId = createItem("huge_price", "天价");
        long specId = createSpec(itemId, "m30", "30分钟", 30);

        mockMvc.perform(put("/api/admin/pricing/talents/" + TALENT + "/prices/" + specId)
                        .session(operator).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitPriceCents\":100000001}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("小数点")));
    }

    // ---------- 3.4 分成比例 ----------

    @Test
    @DisplayName("3.4 按主播覆写优先于全局默认")
    void 按主播覆写优先于全局默认() throws Exception {
        setDefaultRate(3000);
        setTalentRate(TALENT, 4500);

        JsonNode data = dataOf(mockMvc.perform(
                get("/api/admin/pricing/commission").session(operator)).andReturn());

        assertThat(data.get("defaultConfig").get("rateBp").asInt()).isEqualTo(3000);
        JsonNode override = null;
        for (JsonNode node : data.get("overrides")) {
            if (node.get("talentId").asLong() == TALENT) {
                override = node;
            }
        }
        assertThat(override).isNotNull();
        assertThat(override.get("rateBp").asInt()).isEqualTo(4500);
        assertThat(override.get("rateText").asText()).isEqualTo("45%");
    }

    @Test
    @DisplayName("3.4 删除覆写后该主播回落到全局默认")
    void 删除覆写后回落全局默认() throws Exception {
        setDefaultRate(2500);
        setTalentRate(TALENT, 4500);

        mockMvc.perform(delete("/api/admin/pricing/commission/talents/" + TALENT).session(operator))
                .andExpect(status().isOk());

        JsonNode data = dataOf(mockMvc.perform(
                get("/api/admin/pricing/commission").session(operator)).andReturn());
        for (JsonNode node : data.get("overrides")) {
            assertThat(node.get("talentId").asLong()).isNotEqualTo(TALENT);
        }
    }

    @Test
    @DisplayName("3.4 越界比例被拒绝")
    void 越界比例被拒() throws Exception {
        for (String body : new String[]{"{\"rateBp\":-1}", "{\"rateBp\":10001}"}) {
            mockMvc.perform(put("/api/admin/pricing/commission/default")
                            .session(operator).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PARAM"));
        }
        // 边界值允许
        setDefaultRate(0);
        setDefaultRate(10000);
    }

    @Test
    @DisplayName("3.4 全局默认始终只有一条，重复设置是更新")
    void 全局默认只有一条() throws Exception {
        setDefaultRate(2000);
        setDefaultRate(3500);

        JsonNode data = dataOf(mockMvc.perform(
                get("/api/admin/pricing/commission").session(operator)).andReturn());
        assertThat(data.get("defaultConfig").get("rateBp").asInt()).isEqualTo(3500);
        assertThat(data.get("defaultConfig").get("talentId").isNull()).isTrue();
    }

    // ---------- 鉴权 ----------

    @Test
    @DisplayName("非运营访问配置接口一律无权限")
    void 非运营访问配置接口被拒() throws Exception {
        mockMvc.perform(get("/api/admin/pricing/items").session(fan))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/pricing/commission").session(fan))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/pricing/commission/default")
                        .session(fan).contentType(MediaType.APPLICATION_JSON).content("{\"rateBp\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("未登录访问配置接口返回未认证")
    void 未登录访问配置接口() throws Exception {
        mockMvc.perform(get("/api/admin/pricing/items"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 辅助 ----------

    private long createItem(String code, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/pricing/items")
                        .session(operator).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PricingAdminController.CreateItemRequest(
                                        code + "_" + System.nanoTime(), name, null, 0))))
                .andExpect(status().isOk())
                .andReturn();
        return dataOf(result).get("id").asLong();
    }

    private long createItemWithCode(String code, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/pricing/items")
                        .session(operator).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PricingAdminController.CreateItemRequest(code, name, null, 0))))
                .andExpect(status().isOk())
                .andReturn();
        return dataOf(result).get("id").asLong();
    }

    private long createSpec(long itemId, String code, String name, int minutes) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/pricing/items/" + itemId + "/specs")
                        .session(operator).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PricingAdminController.CreateSpecRequest(code, name, minutes, minutes))))
                .andExpect(status().isOk())
                .andReturn();
        return dataOf(result).get("id").asLong();
    }

    private void setPrice(long talentId, long specId, long cents) throws Exception {
        mockMvc.perform(put("/api/admin/pricing/talents/" + talentId + "/prices/" + specId)
                        .session(operator).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitPriceCents\":" + cents + "}"))
                .andExpect(status().isOk());
    }

    private void setDefaultRate(int rateBp) throws Exception {
        mockMvc.perform(put("/api/admin/pricing/commission/default")
                        .session(operator).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rateBp\":" + rateBp + "}"))
                .andExpect(status().isOk());
    }

    private void setTalentRate(long talentId, int rateBp) throws Exception {
        mockMvc.perform(put("/api/admin/pricing/commission/talents/" + talentId)
                        .session(operator).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rateBp\":" + rateBp + "}"))
                .andExpect(status().isOk());
    }

    private long priceOf(long talentId, long itemId) throws Exception {
        JsonNode group = findById(dataOf(mockMvc.perform(
                get("/api/talents/" + talentId + "/services").session(fan)).andReturn()), itemId);
        return group.get("specs").get(0).get("unitPriceCents").asLong();
    }

    private JsonNode dataOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private static JsonNode findById(JsonNode array, long itemId) {
        for (JsonNode node : array) {
            if (node.get("serviceItemId") != null && node.get("serviceItemId").asLong() == itemId) {
                return node;
            }
        }
        return null;
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
