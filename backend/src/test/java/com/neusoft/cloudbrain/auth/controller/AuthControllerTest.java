package com.neusoft.cloudbrain.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neusoft.cloudbrain.common.config.SecurityConfig;
import com.neusoft.cloudbrain.auth.dto.ChangePasswordRequest;
import com.neusoft.cloudbrain.auth.dto.LoginRequest;
import com.neusoft.cloudbrain.auth.security.JwtAuthenticationFilter;
import com.neusoft.cloudbrain.auth.service.AuthService;
import com.neusoft.cloudbrain.auth.service.JwtService;
import com.neusoft.cloudbrain.auth.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AuthController 单元测试
 *
 * 测试场景（来自 41_质量测试与完成定义.md 11.1）：
 * - 登录接口参数校验
 * - Token 缺失时返回 401
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@DisplayName("AuthController - 认证接口测试")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserAccountRepository userAccountRepository;

    private LoginRequest validLoginRequest;
    private ChangePasswordRequest validChangePasswordRequest;

    @BeforeEach
    void setUp() {
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
        LoginRequest request = new LoginRequest("testuser", "short"); // 少于 8 位

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

    // ========== 受保护接口测试 ==========

    @Test
    @DisplayName("POST /api/auth/logout - 无 Token 应返回未授权")
    void logout_noToken_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403,
                            "Expected 401 or 403 but got " + status);
                });
    }

    @Test
    @DisplayName("POST /api/auth/change-password - 无 Token 应返回未授权")
    void changePassword_noToken_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validChangePasswordRequest)))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403,
                            "Expected 401 or 403 but got " + status);
                });
    }
}
