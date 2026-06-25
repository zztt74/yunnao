package com.neusoft.cloudbrain.common.exception;

import com.neusoft.cloudbrain.common.api.ApiResponse;
import com.neusoft.cloudbrain.common.filter.TraceIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GlobalExceptionHandler 单元测试
 *
 * 验证规则（来自 30_接口数据与错误契约.md 第7节、33_错误码与时间规范.md 第6节）：
 * - 业务异常返回对应 HTTP 状态码和错误码
 * - 参数校验失败返回 400
 * - 认证失败返回 401
 * - 权限不足返回 403
 * - 未捕获异常返回 500
 * - 响应结构统一为 code/message/data/traceId
 */
@DisplayName("GlobalExceptionHandler - 全局异常处理测试")
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @DisplayName("GlobalExceptionHandler - 全局异常处理测试")
    @RestController
    @RequestMapping("/api/test")
    static class TestController {

        @GetMapping("/business-404")
        public ApiResponse<Void> throwBusinessNotFound() {
            throw new BusinessException("PATIENT_NOT_FOUND", "患者不存在", 404);
        }

        @GetMapping("/business-409")
        public ApiResponse<Void> throwBusinessConflict() {
            throw new BusinessException("APPOINTMENT_DUPLICATED", "重复挂号", 409);
        }

        @GetMapping("/business-400")
        public ApiResponse<Void> throwBusinessValidation() {
            throw new BusinessException("VALIDATION_FAILED", "参数错误", 400);
        }

        @GetMapping("/auth")
        public ApiResponse<Void> throwAuthentication() {
            throw new AuthenticationException("未登录") {
            };
        }

        @GetMapping("/access-denied")
        public ApiResponse<Void> throwAccessDenied() {
            throw new AccessDeniedException("无权限");
        }

        @GetMapping("/security")
        public ApiResponse<Void> throwSecurity() {
            throw new SecurityException("AUTH_INVALID_CREDENTIALS:用户名或密码错误");
        }

        @GetMapping("/illegal-arg")
        public ApiResponse<Void> throwIllegalArg() {
            throw new IllegalArgumentException("VALIDATION_FAILED:参数非法");
        }

        @GetMapping("/unexpected")
        public ApiResponse<Void> throwUnexpected() {
            throw new RuntimeException("数据库连接失败");
        }

        @PostMapping("/validation")
        public ApiResponse<String> validate(@Valid @RequestBody SampleRequest request) {
            return ApiResponse.success("ok", null);
        }
    }

    record SampleRequest(@NotBlank(message = "名称不能为空") String name) {
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new TraceIdFilter())
                .build();
    }

    @Test
    @DisplayName("BusinessException(404) 应返回 404 和对应错误码")
    void businessException_notFound_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/test/business-404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("患者不存在"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    @DisplayName("BusinessException(409) 应返回 409 和对应错误码")
    void businessException_conflict_shouldReturn409() throws Exception {
        mockMvc.perform(get("/api/test/business-409"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPOINTMENT_DUPLICATED"))
                .andExpect(jsonPath("$.message").value("重复挂号"));
    }

    @Test
    @DisplayName("BusinessException(400) 应返回 400 和对应错误码")
    void businessException_validation_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/test/business-400"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("AuthenticationException 应返回 401")
    void authenticationException_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/test/auth"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_REVOKED"));
    }

    @Test
    @DisplayName("AccessDeniedException 应返回 403")
    void accessDeniedException_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/test/access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("SecurityException(AUTH_INVALID_CREDENTIALS) 应返回 401")
    void securityException_invalidCredentials_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/test/security"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("IllegalArgumentException(VALIDATION_FAILED) 应返回 400")
    void illegalArgumentException_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/test/illegal-arg"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("参数非法"));
    }

    @Test
    @DisplayName("未捕获异常应返回 500 和系统错误码")
    void unexpectedException_shouldReturn500() throws Exception {
        mockMvc.perform(get("/api/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("SYSTEM_INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("系统内部错误"));
    }

    @Test
    @DisplayName("参数校验失败应返回 400 和校验信息")
    void validationException_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("名称不能为空"));
    }

    @Test
    @DisplayName("响应头应包含 X-Trace-Id")
    void responseShouldContainTraceIdHeader() throws Exception {
        mockMvc.perform(get("/api/test/business-404")
                        .header("X-Trace-Id", "trace-handler-001"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.traceId").value("trace-handler-001"));
    }
}
