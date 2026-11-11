package com.guild.checkout.common.web;

import com.guild.checkout.common.exception.ForbiddenException;
import com.guild.checkout.common.exception.UnauthenticatedException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证三类异常各自的 HTTP 状态码、业务码与响应结构。
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ProbeController.class)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.ProbeController.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 未认证返回401与UNAUTHENTICATED() throws Exception {
        mockMvc.perform(post("/__probe/unauthenticated"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("登录状态已失效，请重新授权"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void 无权限返回403与FORBIDDEN() throws Exception {
        mockMvc.perform(post("/__probe/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("没有访问权限"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void 参数校验失败返回400与逐字段明细() throws Exception {
        mockMvc.perform(post("/__probe/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_PARAM"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[?(@.field=='name')].message").value("昵称不能为空"))
                .andExpect(jsonPath("$.errors[?(@.field=='amount')].message").value("金额必须大于零"));
    }

    @Test
    void 校验通过时返回统一成功结构() throws Exception {
        mockMvc.perform(post("/__probe/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"小张\",\"amount\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").value("小张"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void 未预期异常返回500且不泄露堆栈() throws Exception {
        mockMvc.perform(post("/__probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("服务暂时不可用，请稍后再试"));
    }

    @Test
    void 路由不存在返回404而非500() throws Exception {
        mockMvc.perform(post("/__probe/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    record ProbeRequest(
            @NotBlank(message = "昵称不能为空") String name,
            @Min(value = 1, message = "金额必须大于零") long amount) {
    }

    @RestController
    @RequestMapping("/__probe")
    static class ProbeController {

        @PostMapping("/unauthenticated")
        void unauthenticated() {
            throw new UnauthenticatedException();
        }

        @PostMapping("/forbidden")
        void forbidden() {
            throw new ForbiddenException();
        }

        @PostMapping("/boom")
        void boom() {
            throw new IllegalStateException("内部细节不应外泄");
        }

        @PostMapping("/validate")
        com.guild.checkout.common.api.ApiResponse<String> validate(@Valid @RequestBody ProbeRequest req) {
            return com.guild.checkout.common.api.ApiResponse.ok(req.name());
        }
    }
}
