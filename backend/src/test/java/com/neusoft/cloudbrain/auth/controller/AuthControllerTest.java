package com.neusoft.cloudbrain.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neusoft.cloudbrain.auth.config.SecurityConfig;
import com.neusoft.cloudbrain.auth.dto.ChangePasswordRequest;
import com.neusoft.cloudbrain.auth.dto.LoginRequest;
import com.neusoft.cloudbrain.auth.dto.LoginResponse;
import com.neusoft.cloudbrain.auth.security.JwtAuthenticationFilter;
import com.neusoft.cloudbrain.auth.service.AuthService;
import com.neusoft.cloudbrain.auth.service.JwtService;
import com.neusoft.cloudbrain.auth.repository.UserAccountRepository;
import com.neusoft.cloudbrain.common.exception.GlobalExceptionHandler;
import com.neusoft.cloudbrain.common.filter.TraceIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AuthController 单元测试
 *
 * 使用 @WebMvcTest + 手动构建带安全过滤器的 MockMvc，
 * 确保受保护接口在无 Token 时正确返回 401。
 *
 * 测试场景（来自 41_质量测试与完成定义.md 11.1）：
 * - 登录接口参数校验
 * - Token 缺失时返回 401
 * - 正确登录返回 Token
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@DisplayName("AuthController - 认证接口测试")
class AuthControllerTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FilterChainProxy filterChainProxy;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserAccountRepository userAccountRepository;

    private MockMvc mockMvc;

    private LoginRequest validLoginRequest;
    private ChangePasswordRequest validChangePasswordRequest;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new TraceIdFilter(), filterChainProxy)
                .build();

        validLoginRequest = new LoginRequest("testuser", "Password123!");
        validChangePasswordRequest = new ChangePasswordRequest("OldPassword123!", "NewPassword123!");
    }

    // ========== 登录接口测试 ==========

    @Test
    @DisplayName("POST /api/auth/login - 缺少用户名应返回 400")
    void login_missingUsername_shouldReturn400() throws Exception {
        LoginRequest request = new LoginRequest("", "password");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/login - 密码太短应返回 400")
    void login_passwordTooShort_shouldReturn400() throws Exception {
        LoginRequest request = new LoginRequest("testuser", "short");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/login - 缺少密码应返回 400")
    void login_missingPassword_shouldReturn400() throws Exception {
        LoginRequest request = new LoginRequest("testuser", "");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/login - 正确请求应返回 200")
    void login_validRequest_shouldReturn200() throws Exception {
        LoginResponse mockResponse = new LoginResponse(
                "jwt.token",
                "Bearer",
                1L,
                "testuser",
                List.of("ADMIN"),
                false,
                7200L
        );

        when(authService.login(any(LoginRequest.class), anyString())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").value("jwt.token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    // ========== 受保护接口测试（需要 Bearer Token）==========

    @Test
    @DisplayName("POST /api/auth/logout - 无 Token 应返回 401")
    void logout_noToken_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/change-password - 无 Token 应返回 401")
    void changePassword_noToken_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChangePasswordRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/login - 响应头应包含 X-Trace-Id")
    void login_shouldReturnTraceIdHeader() throws Exception {
        LoginResponse mockResponse = new LoginResponse(
                "jwt.token",
                "Bearer",
                1L,
                "testuser",
                List.of("ADMIN"),
                false,
                7200L
        );

        when(authService.login(any(LoginRequest.class), anyString())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoginRequest)))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    @DisplayName("POST /api/auth/login - 自定义 X-Trace-Id 应被回传")
    void login_customTraceId_shouldReturnSameTraceId() throws Exception {
        LoginResponse mockResponse = new LoginResponse(
                "jwt.token",
                "Bearer",
                1L,
                "testuser",
                List.of("ADMIN"),
                false,
                7200L
        );

        when(authService.login(any(LoginRequest.class), anyString())).thenReturn(mockResponse);

        String customTraceId = "my-trace-id-123";
        mockMvc.perform(post("/api/auth/login")
                        .header("X-Trace-Id", customTraceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoginRequest)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", customTraceId))
                .andExpect(jsonPath("$.traceId").value(customTraceId));
    }
}
