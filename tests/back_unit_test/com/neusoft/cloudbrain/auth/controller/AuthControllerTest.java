package com.neusoft.cloudbrain.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neusoft.cloudbrain.auth.dto.AuthPrincipal;
import com.neusoft.cloudbrain.auth.dto.ChangePasswordRequest;
import com.neusoft.cloudbrain.auth.dto.LoginRequest;
import com.neusoft.cloudbrain.auth.dto.LoginResponse;
import com.neusoft.cloudbrain.auth.service.AuthService;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.neusoft.cloudbrain.common.exception.GlobalExceptionHandler;
import com.neusoft.cloudbrain.common.filter.TraceIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthController 单元测试
 *
 * 使用 standaloneSetup（不加载 Spring 上下文、不挂载安全过滤链），
 * 与其他 Controller 测试保持一致。受保护接口（logout / changePassword）
 * 通过直接写入 SecurityContextHolder 模拟已登录用户。
 *
 * 测试场景：
 * - 登录接口参数校验
 * - 登录正确请求返回 Token
 * - 登录凭据无效返回 401
 * - 已登录用户登出返回 200
 * - 已登录用户修改密码返回 200
 * - 修改密码参数校验失败返回 400
 */
@DisplayName("AuthController - 认证接口测试")
class AuthControllerTest {

    private MockMvc mockMvc;
    private AuthService authService;
    private ObjectMapper objectMapper;

    private LoginRequest validLoginRequest;
    private ChangePasswordRequest validChangePasswordRequest;

    @BeforeEach
    void setUp() {
        authService = Mockito.mock(AuthService.class);
        // 配置带 JavaTimeModule 的 ObjectMapper，模拟生产环境 Jackson 配置
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(
                com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .addFilters(new TraceIdFilter())
                .build();

        validLoginRequest = new LoginRequest("testuser", "Password123!");
        validChangePasswordRequest = new ChangePasswordRequest("OldPassword123!", "NewPassword123!");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 模拟登录主体（直接写入 SecurityContextHolder，不经过 JwtAuthenticationFilter）
     */
    private void loginAs(String username, Set<String> roles) {
        AuthPrincipal principal = new AuthPrincipal(1L, username, roles, 0L);
        var authorities = roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
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

    @Test
    @DisplayName("POST /api/auth/login - 凭据无效应返回 401")
    void login_invalidCredentials_shouldReturn401() throws Exception {
        when(authService.login(any(LoginRequest.class), anyString()))
                .thenThrow(new BusinessException("AUTH_FAILED", "用户名或密码错误", 401));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_FAILED"))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    // ========== 登出接口测试 ==========

    @Test
    @DisplayName("POST /api/auth/logout - 已登录用户登出应返回 200")
    void logout_authenticated_shouldReturn200() throws Exception {
        loginAs("testuser", Set.of("USER"));
        doNothing().when(authService).logout(1L);

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    // ========== 修改密码接口测试 ==========

    @Test
    @DisplayName("POST /api/auth/change-password - 已登录用户修改密码应返回 200")
    void changePassword_authenticated_shouldReturn200() throws Exception {
        loginAs("testuser", Set.of("USER"));
        doNothing().when(authService).changePassword(eq(1L), any(ChangePasswordRequest.class));

        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChangePasswordRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("POST /api/auth/change-password - 原密码为空应返回 400")
    void changePassword_missingOldPassword_shouldReturn400() throws Exception {
        loginAs("testuser", Set.of("USER"));
        ChangePasswordRequest request = new ChangePasswordRequest("", "NewPassword123!");

        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/change-password - 新密码太短应返回 400")
    void changePassword_newPasswordTooShort_shouldReturn400() throws Exception {
        loginAs("testuser", Set.of("USER"));
        ChangePasswordRequest request = new ChangePasswordRequest("OldPassword123!", "short");

        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
