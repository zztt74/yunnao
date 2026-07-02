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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PrescriptionCheckController 单元测试
 *
 * Controller 层职责：根据 prescriptionId 查询处方，并通过 PrescriptionCheckResponse.from()
 * 聚合审核结果（风险等级、警告、建议）。本接口为 B4 兼容入口。
 *
 * 覆盖三类用例：
 * - 正常：处方有审核结果 / 处方无审核结果（review 为 null）
 * - 异常：Service 抛 BusinessException（404 处方不存在、403 权限不足）
 * - 边界：参数校验失败（缺 prescriptionId）、审核结果字段为空
 */
@DisplayName("PrescriptionCheckController - 处方审核兼容接口测试")
class PrescriptionCheckControllerTest {

    private MockMvc mockMvc;
    private PrescriptionService prescriptionService;

    @BeforeEach
    void setUp() {
        prescriptionService = Mockito.mock(PrescriptionService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(
                com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(new PrescriptionCheckController(prescriptionService))
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

    private PrescriptionItemResponse sampleItem() {
        return new PrescriptionItemResponse(
                1L, "DRG_001", "阿莫西林胶囊", "0.5g", new BigDecimal("0.5"),
                "每日三次", 7, new BigDecimal("21"), "饭后服用");
    }

    private PrescriptionResponse prescriptionWithReview(Long id, String riskLevel) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 2, 10, 0, 0);
        PrescriptionReviewResponse review = new PrescriptionReviewResponse(
                1L, id, "REVIEWED", riskLevel,
                List.of("药物过敏禁忌：青霉素"),
                List.of("严重药物相互作用"),
                List.of(), List.of(),
                "请观察不良反应，必要时停药", "审核完成", "确定性规则风险等级：" + riskLevel,
                now, now);
        return new PrescriptionResponse(
                id, 100L, 200L, 300L, "陈医生", "内科", "张三",
                "DRAFT", "REVIEWED",
                now, null, null, null, null, null, now,
                List.of(sampleItem()), review);
    }

    private PrescriptionResponse prescriptionWithoutReview(Long id) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 2, 10, 0, 0);
        return new PrescriptionResponse(
                id, 100L, 200L, 300L, "陈医生", "内科", "张三",
                "DRAFT", "NOT_REQUESTED",
                now, null, null, null, null, null, now,
                List.of(sampleItem()), null);
    }

    @Test
    @DisplayName("check - 处方有审核结果时返回完整审核数据")
    void check_withReview_shouldReturnFullReviewData() throws Exception {
        loginAs("doctor1", Set.of("DOCTOR"));
        when(prescriptionService.getPrescriptionById(1L))
                .thenReturn(prescriptionWithReview(1L, "HIGH"));

        mockMvc.perform(post("/api/prescription/check")
                        .contentType("application/json")
                        .content("{\"prescriptionId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.prescriptionId").value(1))
                .andExpect(jsonPath("$.data.encounterId").value(100))
                .andExpect(jsonPath("$.data.prescriptionStatus").value("DRAFT"))
                .andExpect(jsonPath("$.data.aiReviewStatus").value("REVIEWED"))
                .andExpect(jsonPath("$.data.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.data.allergyWarnings[0]")
                        .value("药物过敏禁忌：青霉素"))
                .andExpect(jsonPath("$.data.interactionWarnings[0]")
                        .value("严重药物相互作用"))
                .andExpect(jsonPath("$.data.suggestions").value("请观察不良反应，必要时停药"));

        verify(prescriptionService).getPrescriptionById(1L);
    }

    @Test
    @DisplayName("check - 处方无审核结果时返回 review 字段为 null")
    void check_withoutReview_shouldReturnNullReviewFields() throws Exception {
        loginAs("doctor1", Set.of("DOCTOR"));
        when(prescriptionService.getPrescriptionById(2L))
                .thenReturn(prescriptionWithoutReview(2L));

        mockMvc.perform(post("/api/prescription/check")
                        .contentType("application/json")
                        .content("{\"prescriptionId\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.prescriptionId").value(2))
                .andExpect(jsonPath("$.data.prescriptionStatus").value("DRAFT"))
                .andExpect(jsonPath("$.data.aiReviewStatus").value("NOT_REQUESTED"))
                .andExpect(jsonPath("$.data.riskLevel").doesNotExist())
                .andExpect(jsonPath("$.data.suggestions").doesNotExist());
    }

    @Test
    @DisplayName("check - SAFE 风险等级正确返回")
    void check_safeRiskLevel_shouldReturnSafe() throws Exception {
        loginAs("doctor1", Set.of("DOCTOR"));
        when(prescriptionService.getPrescriptionById(1L))
                .thenReturn(prescriptionWithReview(1L, "SAFE"));

        mockMvc.perform(post("/api/prescription/check")
                        .contentType("application/json")
                        .content("{\"prescriptionId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.riskLevel").value("SAFE"))
                .andExpect(jsonPath("$.data.ruleCheckSummary")
                        .value("确定性规则风险等级：SAFE"));
    }

    @Test
    @DisplayName("check - 处方不存在返回 404")
    void check_prescriptionNotFound_shouldReturn404() throws Exception {
        loginAs("doctor1", Set.of("DOCTOR"));
        when(prescriptionService.getPrescriptionById(999L))
                .thenThrow(new BusinessException("PRESCRIPTION_NOT_FOUND", "处方不存在", 404));

        mockMvc.perform(post("/api/prescription/check")
                        .contentType("application/json")
                        .content("{\"prescriptionId\":999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRESCRIPTION_NOT_FOUND"));
    }

    @Test
    @DisplayName("check - 权限不足返回 403")
    void check_permissionDenied_shouldReturn403() throws Exception {
        loginAs("doctor2", Set.of("DOCTOR"));
        when(prescriptionService.getPrescriptionById(1L))
                .thenThrow(new BusinessException(
                        "PRESCRIPTION_PERMISSION_DENIED",
                        "无权操作该处方，医生只能处理本人接诊就诊的处方", 403));

        mockMvc.perform(post("/api/prescription/check")
                        .contentType("application/json")
                        .content("{\"prescriptionId\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRESCRIPTION_PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("check - 缺少 prescriptionId 触发参数校验返回 400")
    void check_missingPrescriptionId_shouldReturn400() throws Exception {
        loginAs("doctor1", Set.of("DOCTOR"));

        mockMvc.perform(post("/api/prescription/check")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("check - 应调用 prescriptionService.getPrescriptionById")
    void check_shouldCallService() throws Exception {
        loginAs("doctor1", Set.of("DOCTOR"));
        when(prescriptionService.getPrescriptionById(any()))
                .thenReturn(prescriptionWithoutReview(1L));

        mockMvc.perform(post("/api/prescription/check")
                        .contentType("application/json")
                        .content("{\"prescriptionId\":1}"))
                .andExpect(status().isOk());

        verify(prescriptionService).getPrescriptionById(any());
    }
}
