package com.neusoft.cloudbrain.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neusoft.cloudbrain.auth.dto.AuthPrincipal;
import com.neusoft.cloudbrain.common.exception.GlobalExceptionHandler;
import com.neusoft.cloudbrain.common.filter.TraceIdFilter;
import com.neusoft.cloudbrain.user.dto.AdminUserCreateRequest;
import com.neusoft.cloudbrain.user.dto.AdminUserResponse;
import com.neusoft.cloudbrain.user.dto.AdminUserUpdateRequest;
import com.neusoft.cloudbrain.user.dto.ResetPasswordRequest;
import com.neusoft.cloudbrain.user.dto.UserStatusChangeRequest;
import com.neusoft.cloudbrain.user.service.AdminUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminUserController MockMvc 测试（B3）
 *
 * Controller 层独有逻辑：管理员权限拦截（checkAdminPermission）。
 * Service 层单元测试覆盖业务逻辑，此处聚焦权限边界与分页参数转换。
 */
@DisplayName("AdminUserController - 权限拦截测试")
class AdminUserControllerTest {

    private MockMvc mockMvc;
    private AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        adminUserService = Mockito.mock(AdminUserService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(
                com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(new AdminUserController(adminUserService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .addFilters(new TraceIdFilter())
                .build();
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

    @Test
    @DisplayName("用户列表 - 非管理员访问返回 403")
    void list_shouldReturn403WhenNotAdmin() throws Exception {
        loginAs("doctor1", Set.of("DOCTOR"));

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("用户列表 - 管理员访问返回 200 且含分页结构")
    void list_shouldReturn200WhenAdmin() throws Exception {
        loginAs("admin", Set.of("ADMIN"));
        Page<AdminUserResponse> emptyPage = new PageImpl<>(
                List.of(), PageRequest.of(0, 20), 0);
        when(adminUserService.listUsers(any(), any(), any(), any())).thenReturn(emptyPage);

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.page").value(1));
    }

    @Test
    @DisplayName("创建用户 - 非管理员访问返回 403")
    void create_shouldReturn403WhenNotAdmin() throws Exception {
        loginAs("doctor1", Set.of("DOCTOR"));

        mockMvc.perform(post("/api/admin/users")
                        .contentType("application/json")
                        .content("{\"username\":\"newuser\",\"password\":\"12345678\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("重置密码 - 非管理员访问返回 403")
    void resetPassword_shouldReturn403WhenNotAdmin() throws Exception {
        loginAs("doctor1", Set.of("DOCTOR"));

        mockMvc.perform(post("/api/admin/users/1/reset-password")
                        .contentType("application/json")
                        .content("{\"newPassword\":\"newpass123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("用户列表 - 管理员访问时 page=2 转换为 0-based offset=1")
    void list_pageParamShouldConvertToZeroBased() throws Exception {
        loginAs("admin", Set.of("ADMIN"));
        Page<AdminUserResponse> emptyPage = new PageImpl<>(
                List.of(), PageRequest.of(1, 20), 0);
        when(adminUserService.listUsers(any(), any(), any(), any())).thenReturn(emptyPage);

        mockMvc.perform(get("/api/admin/users").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2));
    }

    // ============== 更新用户测试（update）==============

    @Test
    @DisplayName("更新用户 - 管理员访问返回 200")
    void update_asAdmin_shouldReturn200() throws Exception {
        loginAs("admin", Set.of("ADMIN"));
        AdminUserResponse response = new AdminUserResponse(
                1L, "user1", "张三", "13800138000", "user1@example.com",
                true, true, true, true, false,
                Set.of("DOCTOR"),
                LocalDateTime.of(2026, 6, 25, 10, 0, 0),
                LocalDateTime.of(2026, 6, 26, 10, 0, 0));
        when(adminUserService.updateUser(eq(1L), any(AdminUserUpdateRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/admin/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"DOCTOR\",\"realName\":\"张三\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.username").value("user1"))
                .andExpect(jsonPath("$.data.realName").value("张三"))
                .andExpect(jsonPath("$.data.roles[0]").value("DOCTOR"));
    }

    @Test
    @DisplayName("更新用户 - 非管理员访问返回 403")
    void update_asNonAdmin_shouldReturn403() throws Exception {
        loginAs("doctor1", Set.of("DOCTOR"));

        mockMvc.perform(put("/api/admin/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"DOCTOR\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    // ============== 变更用户状态测试（changeStatus）==============

    @Test
    @DisplayName("变更用户状态 - 管理员访问返回 200")
    void changeStatus_asAdmin_shouldReturn200() throws Exception {
        loginAs("admin", Set.of("ADMIN"));
        AdminUserResponse response = new AdminUserResponse(
                1L, "user1", "张三", "13800138000", "user1@example.com",
                false, true, true, true, false,
                Set.of("DOCTOR"),
                LocalDateTime.of(2026, 6, 25, 10, 0, 0),
                LocalDateTime.of(2026, 6, 26, 10, 0, 0));
        when(adminUserService.changeStatus(eq(1L), any(UserStatusChangeRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/admin/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"DISABLE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    @DisplayName("变更用户状态 - 非管理员访问返回 403")
    void changeStatus_asNonAdmin_shouldReturn403() throws Exception {
        loginAs("doctor1", Set.of("DOCTOR"));

        mockMvc.perform(post("/api/admin/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"DISABLE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    // ============== 创建用户测试（create）==============

    @Test
    @DisplayName("创建用户 - 管理员访问返回 200")
    void create_asAdmin_shouldReturn200() throws Exception {
        loginAs("admin", Set.of("ADMIN"));
        AdminUserResponse response = new AdminUserResponse(
                10L, "newuser", null, null, null,
                true, true, true, true, false,
                Set.of("ADMIN"),
                LocalDateTime.of(2026, 6, 25, 10, 0, 0),
                LocalDateTime.of(2026, 6, 25, 10, 0, 0));
        when(adminUserService.createUser(any(AdminUserCreateRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"newuser\",\"password\":\"12345678\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.username").value("newuser"))
                .andExpect(jsonPath("$.data.roles[0]").value("ADMIN"));
    }

    @Test
    @DisplayName("创建用户 - 缺少必填字段应返回 400")
    void create_invalidBody_shouldReturn400() throws Exception {
        loginAs("admin", Set.of("ADMIN"));
        // 缺少 username 必填字段，应触发 @NotBlank 校验失败
        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"12345678\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());
    }

    // ============== 重置密码测试（resetPassword）==============

    @Test
    @DisplayName("重置密码 - 管理员访问返回 200")
    void resetPassword_asAdmin_shouldReturn200() throws Exception {
        loginAs("admin", Set.of("ADMIN"));
        doNothing().when(adminUserService)
                .resetPassword(eq(1L), any(ResetPasswordRequest.class));

        mockMvc.perform(post("/api/admin/users/1/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"newpass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }
}
