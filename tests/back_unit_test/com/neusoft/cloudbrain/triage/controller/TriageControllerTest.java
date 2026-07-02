package com.neusoft.cloudbrain.triage.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neusoft.cloudbrain.auth.dto.AuthPrincipal;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.neusoft.cloudbrain.common.exception.GlobalExceptionHandler;
import com.neusoft.cloudbrain.common.filter.TraceIdFilter;
import com.neusoft.cloudbrain.triage.dto.TriageAnalyzeRequest;
import com.neusoft.cloudbrain.triage.dto.TriageAnalyzeResponse;
import com.neusoft.cloudbrain.triage.dto.TriageRecommendedDoctorResponse;
import com.neusoft.cloudbrain.triage.dto.TriageRecordResponse;
import com.neusoft.cloudbrain.triage.service.TriageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TriageController 单元测试
 *
 * 覆盖三类用例：
 * - 正常：分诊分析/咨询/记录详情/患者记录分页/推荐医生/管理员全量查询
 * - 异常：非管理员调用全量查询返回 403、未登录返回 500、记录不存在返回 404、参数校验 400
 * - 边界：空分页、空推荐医生列表、自定义分页参数
 */
@DisplayName("TriageController - 分诊接口测试")
class TriageControllerTest {

    private MockMvc mockMvc;
    private TriageService triageService;

    @BeforeEach
    void setUp() {
        triageService = Mockito.mock(TriageService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(
                com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(new TriageController(triageService))
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
    @DisplayName("analyze - AI 分诊分析成功")
    void analyze_shouldReturnResult() throws Exception {
        TriageAnalyzeResponse resp = new TriageAnalyzeResponse(
                100L, 1L, "头痛发热", "2天", null,
                "DEPT_INTERNAL", "ROUTINE", "普通感冒", null, false,
                List.of("发热"), "SUCCESS", null,
                1L, "内科", "MATCHED",
                List.of(), LocalDateTime.now(),
                null, 1, true, null);
        when(triageService.analyze(any(TriageAnalyzeRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/triage/analyze")
                        .contentType("application/json")
                        .content("{\"patientId\":1,\"symptoms\":\"头痛发热\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.triageRecordId").value(100))
                .andExpect(jsonPath("$.data.aiDepartmentCode").value("DEPT_INTERNAL"))
                .andExpect(jsonPath("$.data.aiPriority").value("ROUTINE"));
    }

    @Test
    @DisplayName("consult - 课程兼容路径，等价于 analyze")
    void consult_shouldCallAnalyze() throws Exception {
        TriageAnalyzeResponse resp = new TriageAnalyzeResponse(
                101L, 1L, "咳嗽", null, null,
                "DEPT_RESPIRATORY", "URGENT", "急性支气管炎", null, false,
                List.of("咳嗽"), "SUCCESS", null,
                2L, "呼吸科", "MATCHED",
                List.of(), LocalDateTime.now(),
                null, 1, true, null);
        when(triageService.analyze(any(TriageAnalyzeRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/triage/consult")
                        .contentType("application/json")
                        .content("{\"patientId\":1,\"symptoms\":\"咳嗽\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.triageRecordId").value(101))
                .andExpect(jsonPath("$.data.aiPriority").value("URGENT"));

        verify(triageService).analyze(any(TriageAnalyzeRequest.class));
    }

    @Test
    @DisplayName("getById - 返回分诊记录详情")
    void getById_shouldReturnDetail() throws Exception {
        TriageRecordResponse record = new TriageRecordResponse(
                10L, 1L, "头痛", "1天", null,
                "DEPT_INTERNAL", "ROUTINE", "普通感冒", null, false,
                "发热", 1L, "MATCHED", "SUCCESS", null,
                LocalDateTime.now(), LocalDateTime.now());
        when(triageService.getTriageRecordById(10L)).thenReturn(record);

        mockMvc.perform(get("/api/triage/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.aiDepartmentCode").value("DEPT_INTERNAL"))
                .andExpect(jsonPath("$.data.mappingStatus").value("MATCHED"));
    }

    @Test
    @DisplayName("getByPatient - 返回患者分诊记录分页")
    void getByPatient_shouldReturnPage() throws Exception {
        TriageRecordResponse record = new TriageRecordResponse(
                1L, 1L, "头痛", null, null,
                "DEPT_INTERNAL", "ROUTINE", null, null, false,
                null, 1L, "MATCHED", "SUCCESS", null,
                LocalDateTime.now(), null);
        Page<TriageRecordResponse> page = new PageImpl<>(
                List.of(record), PageRequest.of(0, 20), 1);
        when(triageService.getTriageRecordsByPatient(eq(1L), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/triage/patient/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("getRecommendedDoctors - 返回推荐医生列表")
    void getRecommendedDoctors_shouldReturnList() throws Exception {
        TriageRecommendedDoctorResponse doctor = new TriageRecommendedDoctorResponse(
                5L, "张医生", "主任医师", 1L, "内科", "呼吸内科",
                List.of());
        when(triageService.getRecommendedDoctors(eq(1L), anyInt())).thenReturn(List.of(doctor));

        mockMvc.perform(get("/api/triage/recommended-doctors").param("departmentId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].doctorId").value(5))
                .andExpect(jsonPath("$.data[0].doctorName").value("张医生"))
                .andExpect(jsonPath("$.data[0].departmentName").value("内科"));
    }

    @Test
    @DisplayName("list - 管理员全量查询成功")
    void list_asAdmin_shouldReturnPage() throws Exception {
        loginAs("admin", Set.of("ADMIN"));
        TriageRecordResponse record = new TriageRecordResponse(
                1L, 1L, "头痛", null, null,
                "DEPT_INTERNAL", "ROUTINE", null, null, false,
                null, 1L, "MATCHED", "SUCCESS", null,
                LocalDateTime.now(), null);
        Page<TriageRecordResponse> page = new PageImpl<>(
                List.of(record), PageRequest.of(0, 20), 1);
        when(triageService.listTriageRecords(
                any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/triage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(1))
                .andExpect(jsonPath("$.data.total").value(1));

        verify(triageService).listTriageRecords(
                any(), any(), any(), any(), any(), any(Pageable.class));
    }

    // ========== 异常情况测试 ==========

    @Test
    @DisplayName("list - 非管理员返回 403")
    void list_notAdmin_shouldReturn403() throws Exception {
        loginAs("patient1", Set.of("PATIENT"));

        mockMvc.perform(get("/api/triage"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("list - 未登录返回 500（SecurityUtils 抛异常）")
    void list_notLoggedIn_shouldReturnError() throws Exception {
        // 不设置 SecurityContext，模拟未登录
        mockMvc.perform(get("/api/triage"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    @DisplayName("getById - 不存在记录返回 404（由 GlobalExceptionHandler 处理）")
    void getById_notExist_shouldReturn404() throws Exception {
        when(triageService.getTriageRecordById(999L))
                .thenThrow(new BusinessException(
                        "TRIAGE_RECORD_NOT_FOUND", "分诊记录不存在", 404));

        mockMvc.perform(get("/api/triage/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIAGE_RECORD_NOT_FOUND"));
    }

    @Test
    @DisplayName("analyze - 主诉为空触发参数校验 400")
    void analyze_blankSymptoms_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/triage/analyze")
                        .contentType("application/json")
                        .content("{\"patientId\":1,\"symptoms\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ========== 边界条件测试 ==========

    @Test
    @DisplayName("getByPatient - 空记录时返回空分页")
    void getByPatient_empty_shouldReturnEmptyPage() throws Exception {
        Page<TriageRecordResponse> empty = new PageImpl<>(
                List.of(), PageRequest.of(0, 20), 0);
        when(triageService.getTriageRecordsByPatient(eq(2L), any(Pageable.class))).thenReturn(empty);

        mockMvc.perform(get("/api/triage/patient/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("getRecommendedDoctors - 无可用医生时返回空数组")
    void getRecommendedDoctors_empty_shouldReturnEmptyArray() throws Exception {
        when(triageService.getRecommendedDoctors(eq(99L), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/triage/recommended-doctors").param("departmentId", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("getByPatient - 自定义 page/size 参数正确传递给 Service")
    void getByPatient_customPaging_shouldPassPageable() throws Exception {
        Page<TriageRecordResponse> empty = new PageImpl<>(
                List.of(), PageRequest.of(0, 50), 0);
        when(triageService.getTriageRecordsByPatient(eq(1L), any(Pageable.class))).thenReturn(empty);

        mockMvc.perform(get("/api/triage/patient/1")
                        .param("page", "1")
                        .param("size", "50"))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(triageService).getTriageRecordsByPatient(eq(1L), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getPageSize())
                .as("自定义 size=50 应正确传递")
                .isEqualTo(50);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getPageNumber())
                .as("page=1 应转换为 0-based offset=0")
                .isZero();
    }
}