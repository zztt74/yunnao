package com.neusoft.cloudbrain.medicalrecord.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neusoft.cloudbrain.auth.dto.AuthPrincipal;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.neusoft.cloudbrain.common.exception.GlobalExceptionHandler;
import com.neusoft.cloudbrain.common.filter.TraceIdFilter;
import com.neusoft.cloudbrain.medicalrecord.dto.MedicalRecordResponse;
import com.neusoft.cloudbrain.medicalrecord.service.MedicalRecordService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MedicalRecordController 单元测试
 *
 * Controller 层职责：将 HTTP 请求委派给 MedicalRecordService，并包装为 ApiResponse。
 * 权限校验和状态机由 Service 层 + SecurityUtils 完成（详见 MedicalRecordServiceTest）。
 *
 * 覆盖三类用例：
 * - 正常：创建草稿 / AI 生成 / 更新 / 确认 / 详情 / 按就诊查询 / 按患者分页
 * - 异常：Service 抛 BusinessException（404 不存在、409 状态冲突、AI 失败）
 * - 边界：空列表、分页参数转换、参数校验失败
 */
@DisplayName("MedicalRecordController - 病历接口测试")
class MedicalRecordControllerTest {

    private MockMvc mockMvc;
    private MedicalRecordService medicalRecordService;

    @BeforeEach
    void setUp() {
        medicalRecordService = Mockito.mock(MedicalRecordService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(
                com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(new MedicalRecordController(medicalRecordService))
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

    private MedicalRecordResponse sampleRecord(Long id, String status, String source) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 2, 10, 0, 0);
        return new MedicalRecordResponse(
                id, 100L, 200L, 300L, "病历内容示例", source, status,
                300L, null, null, now, now);
    }

    @Test
    @DisplayName("getById - 返回病历详情")
    void getById_shouldReturnDetail() throws Exception {
        loginAs("doctor1", Set.of("DOCTOR"));
        MedicalRecordResponse record = sampleRecord(1L, "DRAFT", "DOCTOR");
        when(medicalRecordService.getRecordById(1L)).thenReturn(record);

        mockMvc.perform(get("/api/medical-records/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.encounterId").value(100))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.source").value("DOCTOR"));
    }

    @Test
    @DisplayName("createDraft - 创建医生手工草稿成功")
    void createDraft_shouldReturnCreatedRecord() throws Exception {
        loginAs("doctor1", Set.of("DOCTOR"));
        MedicalRecordResponse record = sampleRecord(10L, "DRAFT", "DOCTOR");
        when(medicalRecordService.createDraft(any())).thenReturn(record);

        mockMvc.perform(post("/api/medical-records")
                        .contentType("application/json")
                        .content("{\"encounterId\":100,\"content\":\"病历内容示例\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        verify(medicalRecordService).createDraft(any());
    }

    @Test
    @DisplayName("generateByAI - AI 生成病历草稿成功")
    void generateByAI_shouldReturnAiGeneratedRecord() throws Exception {
        loginAs("doctor1", Set.of("DOCTOR"));
        MedicalRecordResponse record = sampleRecord(11L, "AI_GENERATED", "AI");
        when(medicalRecordService.generateByAI(any())).thenReturn(record);

        mockMvc.perform(post("/api/medical-records/ai-generate")
                        .contentType("application/json")
                        .content("{\"encounterId\":100,\"chiefComplaint\":\"头痛\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(11))
                .andExpect(jsonPath("$.data.status").value("AI_GENERATED"))
                .andExpect(jsonPath("$.data.source").value("AI"));
    }

    @Test
    @DisplayName("updateRecord - 更新病历成功")
    void updateRecord_shouldReturnUpdatedRecord() throws Exception {
        loginAs("doctor1", Set.of("DOCTOR"));
        MedicalRecordResponse record = sampleRecord(1L, "DRAFT", "DOCTOR");
        when(medicalRecordService.updateRecord(eq(1L), any())).thenReturn(record);

        mockMvc.perform(put("/api/medical-records/1")
                        .contentType("application/json")
                        .content("{\"content\":\"更新后的病历内容\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("confirmRecord - 医生确认病历成功")
    void confirmRecord_shouldReturnConfirmedRecord() throws Exception {
        loginAs("doctor1", Set.of("DOCTOR"));
        MedicalRecordResponse record = sampleRecord(1L, "CONFIRMED", "DOCTOR");
        when(medicalRecordService.confirmRecord(1L)).thenReturn(record);

        mockMvc.perform(post("/api/medical-records/1/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("getByEncounter - 按就诊 ID 查询病历列表")
    void getByEncounter_shouldReturnList() throws Exception {
        when(medicalRecordService.getRecordsByEncounter(100L))
                .thenReturn(List.of(sampleRecord(1L, "DRAFT", "DOCTOR"),
                        sampleRecord(2L, "AI_GENERATED", "AI")));

        mockMvc.perform(get("/api/medical-records/encounter/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[1].source").value("AI"));
    }

    @Test
    @DisplayName("getByPatient - 按患者 ID 分页查询病历列表")
    void getByPatient_shouldReturnPage() throws Exception {
        MedicalRecordResponse record = sampleRecord(1L, "CONFIRMED", "DOCTOR");
        Page<MedicalRecordResponse> page = new PageImpl<>(
                List.of(record), PageRequest.of(0, 20), 1);
        when(medicalRecordService.getRecordsByPatient(eq(200L), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/medical-records/patient/200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("getConfirmedByEncounter - 查询就诊的已确认病历")
    void getConfirmedByEncounter_shouldReturnConfirmedRecord() throws Exception {
        MedicalRecordResponse record = sampleRecord(1L, "CONFIRMED", "DOCTOR");
        when(medicalRecordService.getConfirmedRecordByEncounter(100L)).thenReturn(record);

        mockMvc.perform(get("/api/medical-records/encounter/100/confirmed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("getById - 病历不存在返回 404（由 GlobalExceptionHandler 处理）")
    void getById_notExist_shouldReturn404() throws Exception {
        when(medicalRecordService.getRecordById(999L))
                .thenThrow(new BusinessException("MEDICAL_RECORD_NOT_FOUND", "病历不存在", 404));

        mockMvc.perform(get("/api/medical-records/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDICAL_RECORD_NOT_FOUND"));
    }

    @Test
    @DisplayName("updateRecord - 已确认病历更新抛 409 状态冲突")
    void updateRecord_confirmed_shouldReturn409() throws Exception {
        loginAs("doctor1", Set.of("DOCTOR"));
        when(medicalRecordService.updateRecord(eq(1L), any()))
                .thenThrow(new BusinessException(
                        "MEDICAL_RECORD_ALREADY_CONFIRMED", "病历已确认，不允许修改", 409));

        mockMvc.perform(put("/api/medical-records/1")
                        .contentType("application/json")
                        .content("{\"content\":\"尝试更新\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEDICAL_RECORD_ALREADY_CONFIRMED"));
    }

    @Test
    @DisplayName("generateByAI - AI 失败抛 BusinessException 返回 500")
    void generateByAI_aiFailed_shouldReturnError() throws Exception {
        loginAs("doctor1", Set.of("DOCTOR"));
        when(medicalRecordService.generateByAI(any()))
                .thenThrow(new BusinessException(
                        "AI_MEDICAL_RECORD_FAILED", "AI 生成失败，请手动填写", 500));

        mockMvc.perform(post("/api/medical-records/ai-generate")
                        .contentType("application/json")
                        .content("{\"encounterId\":100,\"chiefComplaint\":\"头痛\"}"))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.code").value("AI_MEDICAL_RECORD_FAILED"));
    }

    @Test
    @DisplayName("getByEncounter - 空列表时返回空数组而非 null")
    void getByEncounter_empty_shouldReturnEmptyArray() throws Exception {
        when(medicalRecordService.getRecordsByEncounter(999L)).thenReturn(List.of());

        mockMvc.perform(get("/api/medical-records/encounter/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("getByPatient - page=2 应转换为 0-based offset=1 传给 Service")
    void getByPatient_pageParamShouldConvertToZeroBased() throws Exception {
        Page<MedicalRecordResponse> page = new PageImpl<>(
                List.of(), PageRequest.of(1, 20), 0);
        when(medicalRecordService.getRecordsByPatient(eq(200L), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/medical-records/patient/200").param("page", "2"))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<Pageable> captor =
                org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(medicalRecordService).getRecordsByPatient(eq(200L), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getPageNumber())
                .as("page=2 应转换为 0-based offset=1")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("getByPatient - size 超过 100 上限被校验拒绝且不调用服务")
    void getByPatient_sizeOverLimitShouldBeCapped() throws Exception {
        mockMvc.perform(get("/api/medical-records/patient/200")
                        .param("size", "500"))
                .andExpect(status().is5xxServerError());

        verify(medicalRecordService, never()).getRecordsByPatient(any(), any(Pageable.class));
    }

    @Test
    @DisplayName("createDraft - 缺少 content 触发参数校验返回 400")
    void createDraft_missingContent_shouldReturn400() throws Exception {
        loginAs("doctor1", Set.of("DOCTOR"));

        mockMvc.perform(post("/api/medical-records")
                        .contentType("application/json")
                        .content("{\"encounterId\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
