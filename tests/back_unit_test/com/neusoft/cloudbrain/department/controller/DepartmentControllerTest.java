package com.neusoft.cloudbrain.department.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neusoft.cloudbrain.auth.dto.AuthPrincipal;
import com.neusoft.cloudbrain.common.exception.GlobalExceptionHandler;
import com.neusoft.cloudbrain.common.filter.TraceIdFilter;
import com.neusoft.cloudbrain.department.dto.DepartmentCreateRequest;
import com.neusoft.cloudbrain.department.dto.DepartmentResponse;
import com.neusoft.cloudbrain.department.dto.DepartmentUpdateRequest;
import com.neusoft.cloudbrain.department.service.DepartmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * DepartmentController 单元测试
 *
 * 覆盖三类用例：
 * - 正常：科室树/列表/详情/创建/更新
 * - 异常：非管理员创建/更新返回 403
 * - 边界：空列表、不存在的科室 ID
 */
@DisplayName("DepartmentController - 科室接口测试")
class DepartmentControllerTest {

    private MockMvc mockMvc;
    private DepartmentService departmentService;

    @BeforeEach
    void setUp() {
        departmentService = Mockito.mock(DepartmentService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(
                com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(new DepartmentController(departmentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .addFilters(new TraceIdFilter())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String username, Set<String> roles) {
        AuthPrincipal principal = new AuthPrincipal(1L, username, roles, 0L);
        var authorities = roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    // ========== 正常情况测试 ==========

    @Test
    @DisplayName("getTree - 返回科室树形结构")
    void getTree_shouldReturnTree() throws Exception {
        when(departmentService.getDepartmentTree()).thenReturn(List.of());

        mockMvc.perform(get("/api/departments/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("list - 返回科室扁平列表")
    void list_shouldReturnList() throws Exception {
        DepartmentResponse dept = new DepartmentResponse(
                1L, "DEPT_INTERNAL", "内科", null, null, null, null, null, null, null, null);
        when(departmentService.getDepartmentList()).thenReturn(List.of(dept));

        mockMvc.perform(get("/api/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("内科"));
    }

    @Test
    @DisplayName("getById - 返回科室详情")
    void getById_shouldReturnDetail() throws Exception {
        DepartmentResponse dept = new DepartmentResponse(
                1L, "DEPT_INTERNAL", "内科", null, null, null, null, null, null, null, null);
        when(departmentService.getDepartmentById(1L)).thenReturn(dept);

        mockMvc.perform(get("/api/departments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.code").value("DEPT_INTERNAL"));
    }

    @Test
    @DisplayName("create - 管理员创建科室成功")
    void create_asAdmin_shouldReturn200() throws Exception {
        loginAs("admin", Set.of("ADMIN"));
        DepartmentResponse dept = new DepartmentResponse(
                10L, "DEPT_NEW", "新科室", null, null, null, null, null, null, null, null);
        when(departmentService.createDepartment(any())).thenReturn(dept);

        mockMvc.perform(post("/api/departments")
                        .contentType("application/json")
                        .content("{\"code\":\"DEPT_NEW\",\"name\":\"新科室\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.name").value("新科室"));
    }

    @Test
    @DisplayName("update - 管理员更新科室成功")
    void update_asAdmin_shouldReturn200() throws Exception {
        loginAs("admin", Set.of("ADMIN"));
        DepartmentResponse dept = new DepartmentResponse(
                1L, "DEPT_INTERNAL", "内科更新", null, null, null, null, null, null, null, null);
        when(departmentService.updateDepartment(eq(1L), any())).thenReturn(dept);

        mockMvc.perform(put("/api/departments/1")
                        .contentType("application/json")
                        .content("{\"name\":\"内科更新\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("内科更新"));
    }

    // ========== 异常情况测试 ==========

    @Test
    @DisplayName("create - 非管理员返回 403")
    void create_notAdmin_shouldReturn403() throws Exception {
        loginAs("doctor1", Set.of("DOCTOR"));

        mockMvc.perform(post("/api/departments")
                        .contentType("application/json")
                        .content("{\"code\":\"DEPT_NEW\",\"name\":\"新科室\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("update - 非管理员返回 403")
    void update_notAdmin_shouldReturn403() throws Exception {
        loginAs("doctor1", Set.of("DOCTOR"));

        mockMvc.perform(put("/api/departments/1")
                        .contentType("application/json")
                        .content("{\"name\":\"内科\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("update - 未登录返回 500（SecurityUtils 抛异常）")
    void update_notLoggedIn_shouldReturnError() throws Exception {
        // 不设置 SecurityContext，模拟未登录
        mockMvc.perform(put("/api/departments/1")
                        .contentType("application/json")
                        .content("{\"name\":\"内科\"}"))
                .andExpect(status().is5xxServerError());
    }

    // ========== 边界条件测试 ==========

    @Test
    @DisplayName("getById - 不存在的科室 ID 返回 404（由 GlobalExceptionHandler 处理）")
    void getById_notExist_shouldReturn404() throws Exception {
        when(departmentService.getDepartmentById(999L))
                .thenThrow(new com.neusoft.cloudbrain.common.exception.BusinessException(
                        "DEPARTMENT_NOT_FOUND", "科室不存在", 404));

        mockMvc.perform(get("/api/departments/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("list - 空列表时返回空数组而非 null")
    void list_empty_shouldReturnEmptyArray() throws Exception {
        when(departmentService.getDepartmentList()).thenReturn(List.of());

        mockMvc.perform(get("/api/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("create - 管理员权限校验调用 departmentService.createDepartment")
    void create_shouldCallService() throws Exception {
        loginAs("admin", Set.of("ADMIN"));
        DepartmentResponse dept = new DepartmentResponse(
                1L, "DEPT_X", "X", null, null, null, null, null, null, null, null);
        when(departmentService.createDepartment(any())).thenReturn(dept);

        mockMvc.perform(post("/api/departments")
                        .contentType("application/json")
                        .content("{\"code\":\"DEPT_X\",\"name\":\"X\"}"))
                .andExpect(status().isOk());

        verify(departmentService).createDepartment(any());
    }
}
