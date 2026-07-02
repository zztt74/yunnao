package com.neusoft.cloudbrain.patient.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neusoft.cloudbrain.auth.dto.AuthPrincipal;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.neusoft.cloudbrain.common.exception.GlobalExceptionHandler;
import com.neusoft.cloudbrain.common.filter.TraceIdFilter;
import com.neusoft.cloudbrain.patient.dto.PatientProfileResponse;
import com.neusoft.cloudbrain.patient.dto.PatientResponse;
import com.neusoft.cloudbrain.patient.service.PatientService;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * PatientController 单元测试
 *
 * 覆盖三类用例：
 * - 正常：注册/本人信息/详情/更新/档案/管理员搜索/分页
 * - 异常：非管理员搜索/列表返回 403，患者不存在返回 404
 * - 边界：搜索无参返回空数组，分页 page 参数转换
 */
@DisplayName("PatientController - 患者接口测试")
class PatientControllerTest {

    private MockMvc mockMvc;
    private PatientService patientService;

    @BeforeEach
    void setUp() {
        patientService = Mockito.mock(PatientService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(
                com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(new PatientController(patientService))
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
    @DisplayName("register - 患者注册成功")
    void register_shouldReturn200() throws Exception {
        PatientResponse patient = new PatientResponse(
                10L, 100L, "zhangsan", "张三", "MALE", LocalDate.of(1990, 1, 1),
                "13800000000", "ACTIVE", null, null);
        when(patientService.register(any())).thenReturn(patient);

        mockMvc.perform(post("/api/patients/register")
                        .contentType("application/json")
                        .content("{\"username\":\"zhangsan\",\"password\":\"12345678\","
                                + "\"name\":\"张三\",\"gender\":\"MALE\","
                                + "\"birthDate\":\"1990-01-01\",\"phone\":\"13800000000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.name").value("张三"))
                .andExpect(jsonPath("$.data.phone").value("13800000000"));
    }

    @Test
    @DisplayName("getCurrentPatient - 查询本人信息")
    void getCurrentPatient_shouldReturnMyInfo() throws Exception {
        loginAs("zhangsan", Set.of("PATIENT"));
        PatientResponse patient = new PatientResponse(
                1L, 1L, "zhangsan", "张三", "MALE", LocalDate.of(1990, 1, 1),
                "13800000000", "ACTIVE", null, null);
        when(patientService.getCurrentPatient(1L)).thenReturn(patient);

        mockMvc.perform(get("/api/patients/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("张三"));
    }

    @Test
    @DisplayName("getById - 查询患者详情")
    void getById_shouldReturnDetail() throws Exception {
        loginAs("zhangsan", Set.of("PATIENT"));
        PatientResponse patient = new PatientResponse(
                1L, 1L, "zhangsan", "张三", "MALE", LocalDate.of(1990, 1, 1),
                "13800000000", "ACTIVE", null, null);
        when(patientService.getPatientById(eq(1L), eq(1L), any())).thenReturn(patient);

        mockMvc.perform(get("/api/patients/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.gender").value("MALE"));
    }

    @Test
    @DisplayName("update - 更新患者信息成功")
    void update_shouldReturn200() throws Exception {
        loginAs("zhangsan", Set.of("PATIENT"));
        PatientResponse patient = new PatientResponse(
                1L, 1L, "zhangsan", "张三更新", "FEMALE", LocalDate.of(1990, 1, 1),
                "13900000000", "ACTIVE", null, null);
        when(patientService.updatePatient(eq(1L), any(), eq(1L))).thenReturn(patient);

        mockMvc.perform(put("/api/patients/1")
                        .contentType("application/json")
                        .content("{\"name\":\"张三更新\",\"gender\":\"FEMALE\","
                                + "\"birthDate\":\"1990-01-01\",\"phone\":\"13900000000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("张三更新"))
                .andExpect(jsonPath("$.data.phone").value("13900000000"));
    }

    @Test
    @DisplayName("getProfile - 获取患者档案")
    void getProfile_shouldReturnProfile() throws Exception {
        loginAs("zhangsan", Set.of("PATIENT"));
        PatientProfileResponse profile = new PatientProfileResponse(
                1L, 1L, "北京市朝阳区", "张父", "13800000000",
                "青霉素", "高血压", null, null);
        when(patientService.getPatientProfile(eq(1L), eq(1L), any())).thenReturn(profile);

        mockMvc.perform(get("/api/patients/1/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.patientId").value(1))
                .andExpect(jsonPath("$.data.allergies").value("青霉素"));
    }

    @Test
    @DisplayName("updateProfile - 更新患者档案成功")
    void updateProfile_shouldReturn200() throws Exception {
        loginAs("zhangsan", Set.of("PATIENT"));
        PatientProfileResponse profile = new PatientProfileResponse(
                1L, 1L, "北京市海淀区", "张母", "13900000000",
                "无", "糖尿病", null, null);
        when(patientService.updatePatientProfile(eq(1L), any(), eq(1L))).thenReturn(profile);

        mockMvc.perform(put("/api/patients/1/profile")
                        .contentType("application/json")
                        .content("{\"address\":\"北京市海淀区\",\"emergencyContact\":\"张母\","
                                + "\"emergencyPhone\":\"13900000000\","
                                + "\"allergies\":\"无\",\"medicalHistory\":\"糖尿病\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.address").value("北京市海淀区"));
    }

    @Test
    @DisplayName("search - 管理员按姓名搜索患者成功")
    void search_asAdmin_shouldReturn200() throws Exception {
        loginAs("admin", Set.of("ADMIN"));
        PatientResponse patient = new PatientResponse(
                1L, 1L, "zhangsan", "张三", "MALE", LocalDate.of(1990, 1, 1),
                "13800000000", "ACTIVE", null, null);
        when(patientService.searchByName("张")).thenReturn(List.of(patient));

        mockMvc.perform(get("/api/patients/search").param("name", "张"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("张三"));
    }

    @Test
    @DisplayName("list - 管理员分页查询患者成功")
    void list_asAdmin_shouldReturn200() throws Exception {
        loginAs("admin", Set.of("ADMIN"));
        Page<PatientResponse> page = new PageImpl<>(
                List.of(), PageRequest.of(0, 20), 0);
        when(patientService.listPatients(any(), any(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/patients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.page").value(1));
    }

    // ========== 异常情况测试 ==========

    @Test
    @DisplayName("search - 非管理员搜索返回 403")
    void search_notAdmin_shouldReturn403() throws Exception {
        loginAs("zhangsan", Set.of("PATIENT"));

        mockMvc.perform(get("/api/patients/search").param("name", "张"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("list - 非管理员查询列表返回 403")
    void list_notAdmin_shouldReturn403() throws Exception {
        loginAs("zhangsan", Set.of("PATIENT"));

        mockMvc.perform(get("/api/patients"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("getById - 患者不存在返回 404")
    void getById_notExist_shouldReturn404() throws Exception {
        loginAs("zhangsan", Set.of("PATIENT"));
        when(patientService.getPatientById(eq(999L), eq(1L), any()))
                .thenThrow(new BusinessException("PATIENT_NOT_FOUND", "患者不存在", 404));

        mockMvc.perform(get("/api/patients/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("getCurrentPatient - 未登录返回 500（SecurityUtils 抛异常）")
    void getCurrentPatient_notLoggedIn_shouldReturnError() throws Exception {
        mockMvc.perform(get("/api/patients/me"))
                .andExpect(status().is5xxServerError());
    }

    // ========== 边界条件测试 ==========

    @Test
    @DisplayName("search - 无参数时返回空数组")
    void search_noParams_shouldReturnEmptyArray() throws Exception {
        loginAs("admin", Set.of("ADMIN"));

        mockMvc.perform(get("/api/patients/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());

        verify(patientService, Mockito.never()).searchByName(any());
        verify(patientService, Mockito.never()).searchByPhone(any());
    }

    @Test
    @DisplayName("list - page=2 转换为 0-based offset 且返回 1-based 页号")
    void list_pageParamShouldConvert() throws Exception {
        loginAs("admin", Set.of("ADMIN"));
        Page<PatientResponse> page = new PageImpl<>(
                List.of(), PageRequest.of(1, 20), 0);
        when(patientService.listPatients(any(), any(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/patients").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2));
    }
}
