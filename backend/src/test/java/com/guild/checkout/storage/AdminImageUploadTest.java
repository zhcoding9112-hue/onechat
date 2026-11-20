package com.guild.checkout.storage;

import com.guild.checkout.auth.WechatAuthController;
import com.guild.checkout.user.AppUser;
import com.guild.checkout.user.AppUserService;
import com.guild.checkout.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 运营侧通用图片上传。首页运营位靠它拿到可用的图片地址。
 */
@SpringBootTest
@Transactional
class AdminImageUploadTest {

    @Autowired private WebApplicationContext context;
    @Autowired private AppUserService userService;

    private MockMvc mockMvc;
    private MockHttpSession operator;
    private MockHttpSession fan;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        AppUser op = newUser("imgop_");
        userService.grantRole(op.getId(), UserRole.OPERATOR);
        operator = sessionFor(op);
        fan = sessionFor(newUser("imgfan_"));
    }

    @Test
    @DisplayName("上传图片 — 返回可直接写进配置的地址")
    void 上传图片() throws Exception {
        mockMvc.perform(multipart("/api/admin/uploads/image")
                        .file(image("banner.png", "image/png"))
                        .session(operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url",
                        org.hamcrest.Matchers.startsWith("/uploads/home-entry/")))
                .andExpect(jsonPath("$.data.contentType").value("image/png"));
    }

    @Test
    @DisplayName("不支持的格式 — 拒绝")
    void 格式不支持() throws Exception {
        mockMvc.perform(multipart("/api/admin/uploads/image")
                        .file(new MockMultipartFile("file", "x.gif", "image/gif", "x".getBytes()))
                        .session(operator))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        org.hamcrest.Matchers.containsString("不支持的图片格式")));
    }

    @Test
    @DisplayName("超过体积上限 — 拒绝")
    void 体积超限() throws Exception {
        mockMvc.perform(multipart("/api/admin/uploads/image")
                        .file(new MockMultipartFile("file", "big.png", "image/png",
                                new byte[3 * 1024 * 1024]))
                        .session(operator))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("2 MB")));
    }

    /**
     * category 只认白名单：它直接来自请求参数，不限制的话存储目录里会长出
     * 一堆来历不明的分类。（真正的目录穿越由 LocalFileStorage 挡住，
     * 它会把分类里的非法字符全部剔掉。）
     */
    @Test
    @DisplayName("未知分类 — 拒绝，不让调用方自由决定落盘目录")
    void 未知分类被拒() throws Exception {
        mockMvc.perform(multipart("/api/admin/uploads/image")
                        .file(image("x.png", "image/png"))
                        .param("category", "../../etc")
                        .session(operator))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message",
                        org.hamcrest.Matchers.containsString("未知的图片分类")));
    }

    @Test
    @DisplayName("粉丝调上传接口 — 无权限")
    void 粉丝不能上传() throws Exception {
        mockMvc.perform(multipart("/api/admin/uploads/image")
                        .file(image("x.png", "image/png"))
                        .session(fan))
                .andExpect(status().isForbidden());
    }

    private static MockMultipartFile image(String name, String type) {
        return new MockMultipartFile("file", name, type, "fake-image-bytes".getBytes());
    }

    private AppUser newUser(String prefix) {
        return userService.registerOrUpdate(prefix + System.nanoTime(), null, prefix, null);
    }

    private MockHttpSession sessionFor(AppUser user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(WechatAuthController.SESSION_USER_ID, user.getId());
        return session;
    }
}
