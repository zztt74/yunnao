package com.neusoft.cloudbrain.prescription.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neusoft.cloudbrain.auth.dto.AuthPrincipal;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.neusoft.cloudbrain.common.exception.GlobalExceptionHandler;
import com.neusoft.cloudbrain.common.filter.TraceIdFilter;
import com.neusoft.cloudbrain.prescription.dto.PrescriptionItemResponse;
import com.neusoft.cloudbrain.prescription.dto.PrescriptionResponse;
import com.neusoft.cloudbrain.prescription.dto.PrescriptionReviewResponse;
import com.neusoft.cloudbrain.prescription.service.PrescriptionService;
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

import java.math.BigDecimal;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PrescriptionController 单元测试
 *
 * Controller 层职责：将 HTTP 请求委派给 PrescriptionService，并包装为 ApiResponse。
 * 确定性规则检查、AI 审核和状态机由 Service 层完成（详见 PrescriptionServiceTest）。
 *
 * 覆盖三类用例：
 * - 正常：创建 / 确认 / 作废 / 详情 / 按就诊查询 / 按患者分页 / 按医生分页
 * - 异常：Service 抛 BusinessException（404 不存在、409 状态冲突、403 权限不足）
 * - 边界：空列表、分页参数转换、参数校验失败
 */
@DisplayName("PrescriptionController - 处方接口测试")
class PrescriptionControllerTest {

    private MockMvc mockMvc;
    private PrescriptionService prescriptionService;

    @BeforeEach
    void setUp() {
        prescriptionService = Mockito.mock(PrescriptionService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(
                com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(new PrescriptionController(prescriptionService))
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

    private PrescriptionItemResponse sampleItem(Long id) {
        return new PrescriptionItemResponse(
                id, "DRG_001", "阿莫西林胶囊", "0.5g", new BigDecimal("0.5"),
                "每日三次", 7, new BigDecimal("21"), "饭后服用");
    }

    private PrescriptionReviewResponse sampleReview(Long id, String riskLevel) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 2, 10, 0, 0);
        return new PrescriptionReviewResponse(
                id, 1L, "REVIEWED", riskLevel,
                List.of(), List.of(), List.of(), List.of(),
                "请观察不良反应", "审核通过", "确定性规则风险等级：" + riskLevel,
                now, now);
    }

    private PrescriptionResponse samplePrescription(Long id, String status, String aiReviewStatus) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 2, 10, 0, 0);
        return new PrescriptionResponse(
                id, 100L, 200L, 300L, "陈医生", "内科", "张三",
                status, aiReviewStatus,
                now, null, null, null, null, null, now,
                List.of(sampleItem(1L)), sampleReview(1L, "SAFE"));
    }

    @Test
    @DisplayName("getById - 返回处方详情")
    void getById_shouldReturnDetail() throws Exception {
        loginAs("doctor1", Set.of("DOCTOR"));
        PrescriptionResponse p = samplePrescription(1L, "DRAFT", "REVIEWED");
        when(prescriptionService.getPrescriptionById(1L)).thenReturn(p);

        mockMvc.perform(get("/api/prescriptions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.encounterId").value(100))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.aiReviewStatus").value("REVIEWED"))
                .andExpect(jsonPath("$.data.items[0].drugCode").value("DRG_001"))
                .andExpect(jsonPath("$.data.review.riskLevel").value("SAFE"));
    }

    @Test
    @DisplayName("createPrescription - 创建处方成功")
    void createPrescription_shouldReturnCreatedPrescription() throws Exception {
        loginAs("doctor1", Set.of("DOCTOR"));
        PrescriptionResponse p = samplePrescription(10L, "DRAFT", "REVIEWED");
        when(prescriptionService.createPrescription(any())).thenReturn(p);

        String body = "{\"encounterId\":100,\"items\":[{\"drugCode\":\"DRG_001\","
                + "\"drugName\":\"阿莫西林胶囊\",\"dosage\":\"0.5g\","
                + "\"frequency\":\"每日三次\",\"duration\":7,\"quantity\":21}]}";

        mockMvc.perform(post("/api/prescriptions")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        verify(prescriptionService).createPrescription(any());
    }

    @Test
    @DisplayName("confirmPrescription - 医生确认处方成功")
    void confirmPrescription_shouldReturnConfirmedPrescription() throws Exception {
        loginAs("doctor1", Set.of("DOCTOR"));
        PrescriptionResponse p = samplePrescription(1L, "CONFIRMED", "REVIEWED");
        when(prescriptionService.confirmPrescription(1L)).thenReturn(p);

        mockMvc.perform(post("/api/prescriptions/1/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("voidPrescription - 作废处方成功")
    void voidPrescription_shouldReturnVoidedPrescription() throws Exception {
        loginAs("doctor1", Set.of("DOCTOR"));
        PrescriptionResponse p = samplePrescription(1L, "VOIDED", "REVIEWED");
        when(prescriptionService.voidPrescription(eq(1L), any())).thenReturn(p);

        mockMvc.perform(post("/api/prescriptions/1/void")
                        .contentType("application/json")
                        .content("{\"reason\":\"患者不良反应\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VOIDED"));
    }

    @Test
    @DisplayName("getByEncounter - 按就诊 ID 查询处方列表")
    void getByEncounter_shouldReturnList() throws Exception {
        when(prescriptionService.getPrescriptionsByEncounter(100L))
                .thenReturn(List.of(samplePrescription(1L, "CONFIRMED", "REVIEWED"),
                        samplePrescription(2L, "VOIDED", "REVIEWED")));

        mockMvc.perform(get("/api/prescriptions/encounter/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[1].status").value("VOIDED"));
    }

    @Test
    @DisplayName("getByPatient - 按患者 ID 分页查询处方列表")
    void getByPatient_shouldReturnPage() throws Exception {
        PrescriptionResponse p = samplePrescription(1L, "CONFIRMED", "REVIEWED");
        Page<PrescriptionResponse> page = new PageImpl<>(
                List.of(p), PageRequest.of(0, 20), 1);
        when(prescriptionService.getPrescriptionsByPatient(eq(200L), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/prescriptions/patient/200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("getByDoctor - 按医生 ID 分页查询处方列表")
    void getByDoctor_shouldReturnPage() throws Exception {
        PrescriptionResponse p = samplePrescription(1L, "CONFIRMED", "REVIEWED");
        Page<PrescriptionResponse> page = new PageImpl<>(
                List.of(p), PageRequest.of(0, 20), 1);
        when(prescriptionService.getPrescriptionsByDoctor(eq(300L), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/prescriptions/doctor/300"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(1))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("getById - 处方不存在返回 404（由 GlobalExceptionHandler 处理）")
    void getById_notExist_shouldReturn404() throws Exception {
        when(prescriptionService.getPrescriptionById(999L))
                .thenThrow(new BusinessException("PRESCRIPTION_NOT_FOUND", "处方不存在", 404));

        mockMvc.perform(get("/api/prescriptions/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRESCRIPTION_NOT_FOUND"));
    }

    @Test
    @DisplayName("confirmPrescription - 状态冲突抛 409")
    void confirmPrescription_statusConflict_shouldReturn409() throws Exception {
        loginAs("doctor1", Set.of("DOCTOR"));
        when(prescriptionService.confirmPrescription(1L))
                .thenThrow(new BusinessException(
                        "PRESCRIPTION_STATUS_CONFLICT", "处方状态冲突，不允许该状态转换", 409));

        mockMvc.perform(post("/api/prescriptions/1/confirm"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRESCRIPTION_STATUS_CONFLICT"));
    }

    @Test
    @DisplayName("createPrescription - 确定性规则违反抛 409")
    void createPrescription_ruleViolation_shouldReturn409() throws Exception {
        loginAs("doctor1", Set.of("DOCTOR"));
        when(prescriptionService.createPrescription(any()))
                .thenThrow(new BusinessException(
                        "PRESCRIPTION_RULE_VIOLATION", "处方确定性规则校验未通过", 409));

        String body = "{\"encounterId\":100,\"items\":[{\"drugCode\":\"DRG_001\","
                + "\"drugName\":\"阿莫西林胶囊\",\"dosage\":\"0.5g\","
                + "\"frequency\":\"每日三次\",\"duration\":7,\"quantity\":21}]}";

        mockMvc.perform(post("/api/prescriptions")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRESCRIPTION_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("getById - 权限不足抛 403")
    void getById_permissionDenied_shouldReturn403() throws Exception {
        loginAs("doctor2", Set.of("DOCTOR"));
        when(prescriptionService.getPrescriptionById(1L))
                .thenThrow(new BusinessException(
                        "PRESCRIPTION_PERMISSION_DENIED",
                        "无权操作该处方，医生只能处理本人接诊就诊的处方", 403));

        mockMvc.perform(get("/api/prescriptions/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRESCRIPTION_PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("getByEncounter - 空列表时返回空数组而非 null")
    void getByEncounter_empty_shouldReturnEmptyArray() throws Exception {
        when(prescriptionService.getPrescriptionsByEncounter(999L)).thenReturn(List.of());

        mockMvc.perform(get("/api/prescriptions/encounter/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("getByPatient - page=2 应转换为 0-based offset=1 传给 Service")
    void getByPatient_pageParamShouldConvertToZeroBased() throws Exception {
        Page<PrescriptionResponse> page = new PageImpl<>(
                List.of(), PageRequest.of(1, 20), 0);
        when(prescriptionService.getPrescriptionsByPatient(eq(200L), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/prescriptions/patient/200").param("page", "2"))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<Pageable> captor =
                org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(prescriptionService).getPrescriptionsByPatient(eq(200L), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getPageNumber())
                .as("page=2 应转换为 0-based offset=1")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("getByPatient - size 超过 100 上限被校验拒绝且不调用服务")
    void getByPatient_sizeOverLimitShouldBeCapped() throws Exception {
        mockMvc.perform(get("/api/prescriptions/patient/200")
                        .param("size", "500"))
                .andExpect(status().is5xxServerError());

        verify(prescriptionService, never()).getPrescriptionsByPatient(any(), any(Pageable.class));
    }

    @Test
    @DisplayName("createPrescription - 药品明细为空触发参数校验返回 400")
    void createPrescription_emptyItems_shouldReturn400() throws Exception {
        loginAs("doctor1", Set.of("DOCTOR"));

        mockMvc.perform(post("/api/prescriptions")
                        .contentType("application/json")
                        .content("{\"encounterId\":100,\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
