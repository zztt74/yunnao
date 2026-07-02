package com.neusoft.cloudbrain.doctor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neusoft.cloudbrain.auth.dto.AuthPrincipal;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.neusoft.cloudbrain.common.exception.GlobalExceptionHandler;
import com.neusoft.cloudbrain.common.filter.TraceIdFilter;
import com.neusoft.cloudbrain.doctor.dto.DoctorResponse;
import com.neusoft.cloudbrain.doctor.service.DoctorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
 * DoctorController 单元测试
 *
 * 覆盖三类用例：
 * - 正常：列表/详情/按科室查询/创建/更新/本人资料更新
 * - 异常：非管理员创建/更新返回 403，非医生更新本人资料返回 403
 * - 边界：空列表、不存在的医生 ID、分页参数
 */
@DisplayName("DoctorController - 医生接口测试")
class DoctorControllerTest {

    private MockMvc mockMvc;
    private DoctorService doctorService;

    @BeforeEach
    void setUp() {
        doctorService = Mockito.mock(DoctorService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(
                com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(new DoctorController(doctorService))
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

    private DoctorResponse sampleDoctor() {
        return new DoctorResponse(
                1L, 100L, 10L, "内科", "李医生", "ATTENDING",
                "心血管", "ENABLED", "博士", 10, "擅长心血管疾病",
                null, null);
    }

    // ========== 正常情况测试 ==========

    @Test
    @DisplayName("list - 返回医生分页列表")
    void list_shouldReturnPage() throws Exception {
        Page<DoctorResponse> page = new PageImpl<>(
                List.of(sampleDoctor()), PageRequest.of(0, 20), 1);
        when(doctorService.getDoctorList(1, 20)).thenReturn(page);

        mockMvc.perform(get("/api/doctors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("李医生"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(1));
    }

    @Test
    @DisplayName("list - 带 name 参数时调用 searchByName")
    void list_withName_shouldCallSearchByName() throws Exception {
        Page<DoctorResponse> page = new PageImpl<>(
                List.of(sampleDoctor()), PageRequest.of(0, 20), 1);
        when(doctorService.searchByName(eq("李"), eq(1), eq(20))).thenReturn(page);

        mockMvc.perform(get("/api/doctors").param("name", "李"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].name").value("李医生"));

        verify(doctorService).searchByName("李", 1, 20);
    }

    @Test
    @DisplayName("getById - 返回医生详情")
    void getById_shouldReturnDetail() throws Exception {
        when(doctorService.getDoctorById(1L)).thenReturn(sampleDoctor());

        mockMvc.perform(get("/api/doctors/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").value("ATTENDING"))
                .andExpect(jsonPath("$.data.departmentName").value("内科"));
    }

    @Test
    @DisplayName("getByDepartment - 按科室查询医生")
    void getByDepartment_shouldReturnList() throws Exception {
        when(doctorService.getDoctorsByDepartment(10L)).thenReturn(List.of(sampleDoctor()));

        mockMvc.perform(get("/api/doctors/by-department/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].departmentId").value(10))
                .andExpect(jsonPath("$.data[0].name").value("李医生"));
    }

    @Test
    @DisplayName("create - 管理员创建医生成功")
    void create_asAdmin_shouldReturn200() throws Exception {
        loginAs("admin", Set.of("ADMIN"));
        DoctorResponse doctor = new DoctorResponse(
                5L, 101L, 10L, "内科", "王医生", "RESIDENT",
                "呼吸科", "ENABLED", "硕士", 2, "呼吸科医师",
                null, null);
        when(doctorService.createDoctor(any())).thenReturn(doctor);

        mockMvc.perform(post("/api/doctors")
                        .contentType("application/json")
                        .content("{\"username\":\"wangyi\",\"password\":\"12345678\","
                                + "\"departmentId\":10,\"name\":\"王医生\","
                                + "\"title\":\"RESIDENT\",\"specialty\":\"呼吸科\","
                                + "\"education\":\"硕士\",\"experienceYears\":2,"
                                + "\"introduction\":\"呼吸科医师\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.name").value("王医生"));
    }

    @Test
    @DisplayName("update - 管理员更新医生成功")
    void update_asAdmin_shouldReturn200() throws Exception {
        loginAs("admin", Set.of("ADMIN"));
        DoctorResponse doctor = new DoctorResponse(
                1L, 100L, 10L, "内科", "李医生更新", "CHIEF",
                "心血管", "ENABLED", "博士", 15, "主任医师",
                null, null);
        when(doctorService.updateDoctor(eq(1L), any())).thenReturn(doctor);

        mockMvc.perform(put("/api/doctors/1")
                        .contentType("application/json")
                        .content("{\"departmentId\":10,\"name\":\"李医生更新\","
                                + "\"title\":\"CHIEF\",\"specialty\":\"心血管\","
                                + "\"status\":\"ENABLED\",\"education\":\"博士\","
                                + "\"experienceYears\":15,\"introduction\":\"主任医师\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("李医生更新"))
                .andExpect(jsonPath("$.data.title").value("CHIEF"));
    }

    @Test
    @DisplayName("updateMyProfile - 医生更新本人资料成功")
    void updateMyProfile_asDoctor_shouldReturn200() throws Exception {
        loginAs("liyi", Set.of("DOCTOR"));
        DoctorResponse doctor = new DoctorResponse(
                1L, 1L, 10L, "内科", "李医生", "ATTENDING",
                "心血管更新", "ENABLED", "博士", 12, "擅长介入治疗",
                null, null);
        when(doctorService.updateMyProfile(eq(1L), any())).thenReturn(doctor);

        mockMvc.perform(put("/api/doctors/me")
                        .contentType("application/json")
                        .content("{\"specialty\":\"心血管更新\","
                                + "\"education\":\"博士\","
                                + "\"experienceYears\":12,"
                                + "\"introduction\":\"擅长介入治疗\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.specialty").value("心血管更新"))
                .andExpect(jsonPath("$.data.experienceYears").value(12));
    }

    // ========== 异常情况测试 ==========

    @Test
    @DisplayName("create - 非管理员返回 403")
    void create_notAdmin_shouldReturn403() throws Exception {
        loginAs("liyi", Set.of("DOCTOR"));

        mockMvc.perform(post("/api/doctors")
                        .contentType("application/json")
                        .content("{\"username\":\"newdoc\",\"password\":\"12345678\","
                                + "\"departmentId\":10,\"name\":\"新医生\","
                                + "\"title\":\"RESIDENT\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("update - 非管理员返回 403")
    void update_notAdmin_shouldReturn403() throws Exception {
        loginAs("liyi", Set.of("DOCTOR"));

        mockMvc.perform(put("/api/doctors/1")
                        .contentType("application/json")
                        .content("{\"departmentId\":10,\"name\":\"李医生\","
                                + "\"title\":\"ATTENDING\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("updateMyProfile - 非医生角色返回 403")
    void updateMyProfile_notDoctor_shouldReturn403() throws Exception {
        loginAs("admin", Set.of("ADMIN"));

        mockMvc.perform(put("/api/doctors/me")
                        .contentType("application/json")
                        .content("{\"specialty\":\"心血管\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("getById - 医生不存在返回 404")
    void getById_notExist_shouldReturn404() throws Exception {
        when(doctorService.getDoctorById(999L))
                .thenThrow(new BusinessException("DOCTOR_NOT_FOUND", "医生不存在", 404));

        mockMvc.perform(get("/api/doctors/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCTOR_NOT_FOUND"));
    }

    // ========== 边界条件测试 ==========

    @Test
    @DisplayName("getByDepartment - 科室下无医生时返回空数组")
    void getByDepartment_empty_shouldReturnEmptyArray() throws Exception {
        when(doctorService.getDoctorsByDepartment(99L)).thenReturn(List.of());

        mockMvc.perform(get("/api/doctors/by-department/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("list - page=2 时返回 1-based 页号 2")
    void list_pageParamShouldConvert() throws Exception {
        Page<DoctorResponse> page = new PageImpl<>(
                List.of(), PageRequest.of(1, 20), 0);
        when(doctorService.getDoctorList(2, 20)).thenReturn(page);

        mockMvc.perform(get("/api/doctors").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2));
    }
}
