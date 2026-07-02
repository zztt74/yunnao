package com.neusoft.cloudbrain.encounter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neusoft.cloudbrain.auth.dto.AuthPrincipal;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.neusoft.cloudbrain.common.exception.GlobalExceptionHandler;
import com.neusoft.cloudbrain.common.filter.TraceIdFilter;
import com.neusoft.cloudbrain.encounter.dto.EncounterDiagnosisRequest;
import com.neusoft.cloudbrain.encounter.dto.EncounterDiagnosisResponse;
import com.neusoft.cloudbrain.encounter.dto.EncounterResponse;
import com.neusoft.cloudbrain.encounter.dto.EncounterStartRequest;
import com.neusoft.cloudbrain.encounter.service.EncounterService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * EncounterController 单元测试
 *
 * 覆盖三类用例：
 * - 正常：开始接诊/完成就诊/详情/医生分页/添加诊断/诊断列表
 * - 异常：就诊不存在返回 404、参数校验 400
 * - 边界：空诊断列表、无 body 取消请求、空状态就诊列表
 */
@DisplayName("EncounterController - 就诊接口测试")
class EncounterControllerTest {

    private MockMvc mockMvc;
    private EncounterService encounterService;

    @BeforeEach
    void setUp() {
        encounterService = Mockito.mock(EncounterService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(
                com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(new EncounterController(encounterService))
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

    private EncounterResponse sampleEncounter(Long id, String status) {
        return new EncounterResponse(
                id, 100L, 1L, "张三", 2L, "李医生", 1L, "内科", status,
                LocalDateTime.now(), null, null, null, null,
                LocalDateTime.now(), null);
    }

    // ========== 正常情况测试 ==========

    @Test
    @DisplayName("startEncounter - 医生开始接诊成功")
    void startEncounter_shouldReturnInProgress() throws Exception {
        loginAs("doctor1", Set.of("DOCTOR"));
        when(encounterService.startEncounter(any(EncounterStartRequest.class)))
                .thenReturn(sampleEncounter(10L, "IN_PROGRESS"));

        mockMvc.perform(post("/api/encounters/start")
                        .contentType("application/json")
                        .content("{\"appointmentId\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("completeEncounter - 完成就诊成功")
    void completeEncounter_shouldReturnCompleted() throws Exception {
        when(encounterService.completeEncounter(10L))
                .thenReturn(sampleEncounter(10L, "COMPLETED"));

        mockMvc.perform(post("/api/encounters/10/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("getById - 返回就诊详情")
    void getById_shouldReturnDetail() throws Exception {
        when(encounterService.getEncounterById(5L))
                .thenReturn(sampleEncounter(5L, "IN_PROGRESS"));

        mockMvc.perform(get("/api/encounters/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.patientName").value("张三"));
    }

    @Test
    @DisplayName("getByDoctor - 返回医生就诊分页列表")
    void getByDoctor_shouldReturnPage() throws Exception {
        Page<EncounterResponse> page = new PageImpl<>(
                List.of(sampleEncounter(1L, "IN_PROGRESS")), PageRequest.of(0, 20), 1);
        when(encounterService.getEncountersByDoctor(eq(2L), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/encounters/doctor/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("addAIDiagnosis - 添加 AI 候选诊断成功")
    void addAIDiagnosis_shouldReturnDiagnosis() throws Exception {
        EncounterDiagnosisResponse diag = new EncounterDiagnosisResponse(
                1L, 10L, "J00", "急性鼻咽炎", "PRELIMINARY", "AI_SUGGESTION",
                55L, null, null, "AI 候选诊断", LocalDateTime.now(), null);
        when(encounterService.addAIDiagnosis(eq(10L), any(EncounterDiagnosisRequest.class)))
                .thenReturn(diag);

        mockMvc.perform(post("/api/encounters/10/diagnoses/ai")
                        .contentType("application/json")
                        .content("{\"diagnosisCode\":\"J00\",\"diagnosisName\":\"急性鼻咽炎\","
                                + "\"type\":\"PRELIMINARY\",\"source\":\"AI_SUGGESTION\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.source").value("AI_SUGGESTION"));
    }

    @Test
    @DisplayName("getDiagnoses - 返回就诊诊断列表")
    void getDiagnoses_shouldReturnList() throws Exception {
        EncounterDiagnosisResponse diag = new EncounterDiagnosisResponse(
                1L, 10L, "J00", "急性鼻咽炎", "FINAL", "DOCTOR",
                null, 2L, LocalDateTime.now(), null, LocalDateTime.now(), null);
        when(encounterService.getEncounterDiagnoses(10L)).thenReturn(List.of(diag));

        mockMvc.perform(get("/api/encounters/10/diagnoses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].diagnosisCode").value("J00"))
                .andExpect(jsonPath("$.data[0].source").value("DOCTOR"));
    }

    // ========== 异常情况测试 ==========

    @Test
    @DisplayName("getById - 不存在就诊返回 404（由 GlobalExceptionHandler 处理）")
    void getById_notExist_shouldReturn404() throws Exception {
        when(encounterService.getEncounterById(999L))
                .thenThrow(new BusinessException("ENCOUNTER_NOT_FOUND", "就诊不存在", 404));

        mockMvc.perform(get("/api/encounters/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENCOUNTER_NOT_FOUND"));
    }

    @Test
    @DisplayName("startEncounter - 挂号 ID 为空触发参数校验 400")
    void startEncounter_blankAppointmentId_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/encounters/start")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("addDoctorDiagnosis - 诊断编码为空触发参数校验 400")
    void addDoctorDiagnosis_blankCode_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/encounters/10/diagnoses/doctor")
                        .contentType("application/json")
                        .content("{\"diagnosisCode\":\"\",\"diagnosisName\":\"x\","
                                + "\"type\":\"FINAL\",\"source\":\"DOCTOR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ========== 边界条件测试 ==========

    @Test
    @DisplayName("getDiagnoses - 无诊断时返回空数组")
    void getDiagnoses_empty_shouldReturnEmptyArray() throws Exception {
        when(encounterService.getEncounterDiagnoses(5L)).thenReturn(List.of());

        mockMvc.perform(get("/api/encounters/5/diagnoses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("cancel - 不传 body 时使用默认取消请求")
    void cancel_withoutBody_shouldUseDefaultRequest() throws Exception {
        when(encounterService.cancelEncounter(eq(10L), any()))
                .thenReturn(sampleEncounter(10L, "CANCELLED"));

        mockMvc.perform(post("/api/encounters/10/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        verify(encounterService).cancelEncounter(eq(10L), any());
    }

    @Test
    @DisplayName("getByDoctorAndStatus - 空结果返回空数组")
    void getByDoctorAndStatus_empty_shouldReturnEmptyArray() throws Exception {
        when(encounterService.getEncountersByDoctorAndStatus(2L, "COMPLETED"))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/encounters/doctor/2/status/COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ========== 补充端点测试 ==========

    @Test
    @DisplayName("waitForExam - 等待检查状态转换成功")
    void waitForExam_shouldReturnWaitingExam() throws Exception {
        when(encounterService.waitForExam(10L))
                .thenReturn(sampleEncounter(10L, "WAITING_EXAM"));

        mockMvc.perform(post("/api/encounters/10/wait-exam"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.status").value("WAITING_EXAM"));
    }

    @Test
    @DisplayName("resumeEncounter - 继续诊疗状态转换成功")
    void resumeEncounter_shouldReturnInProgress() throws Exception {
        when(encounterService.resumeEncounter(10L))
                .thenReturn(sampleEncounter(10L, "IN_PROGRESS"));

        mockMvc.perform(post("/api/encounters/10/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("getByAppointmentId - 按挂号 ID 查询就诊成功")
    void getByAppointmentId_shouldReturnEncounter() throws Exception {
        when(encounterService.getEncounterByAppointmentId(100L))
                .thenReturn(sampleEncounter(5L, "IN_PROGRESS"));

        mockMvc.perform(get("/api/encounters/appointment/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.appointmentId").value(100));
    }

    @Test
    @DisplayName("getByPatient - 返回患者就诊分页列表")
    void getByPatient_shouldReturnPage() throws Exception {
        Page<EncounterResponse> page = new PageImpl<>(
                List.of(sampleEncounter(1L, "COMPLETED")), PageRequest.of(0, 20), 1);
        when(encounterService.getEncountersByPatient(eq(1L), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/encounters/patient/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("addDoctorDiagnosis - 添加医生最终诊断成功")
    void addDoctorDiagnosis_shouldReturnDiagnosis() throws Exception {
        EncounterDiagnosisResponse diag = new EncounterDiagnosisResponse(
                2L, 10L, "J00", "急性鼻咽炎", "FINAL", "DOCTOR",
                null, 2L, LocalDateTime.now(), null, LocalDateTime.now(), null);
        when(encounterService.addDoctorDiagnosis(eq(10L), any(EncounterDiagnosisRequest.class)))
                .thenReturn(diag);

        mockMvc.perform(post("/api/encounters/10/diagnoses/doctor")
                        .contentType("application/json")
                        .content("{\"diagnosisCode\":\"J00\",\"diagnosisName\":\"急性鼻咽炎\","
                                + "\"type\":\"FINAL\",\"source\":\"DOCTOR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.source").value("DOCTOR"));
    }
}