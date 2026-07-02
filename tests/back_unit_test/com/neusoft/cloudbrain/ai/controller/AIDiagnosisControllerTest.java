package com.neusoft.cloudbrain.ai.controller;

import com.neusoft.cloudbrain.ai.api.AIDiagnosisService;
import com.neusoft.cloudbrain.ai.dto.DiagnosisAIRequest;
import com.neusoft.cloudbrain.ai.dto.DiagnosisAIResult;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.neusoft.cloudbrain.common.exception.GlobalExceptionHandler;
import com.neusoft.cloudbrain.common.filter.TraceIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AIDiagnosisController 单元测试
 *
 * Controller 层职责：将前端请求映射为内部 DiagnosisAIRequest，调用 AIDiagnosisService.analyze，
 * 再将结果映射为 AIAssistDiagnosisResponse。诊断隔离原则要求 AI 只产生候选建议。
 *
 * 关键契约（来自 32_AI能力契约规范.md 第3节）：
 * - AI 成功：返回 candidates + aiStatus=SUCCESS
 * - AI 失败（BusinessException 或其他异常）：返回 200 + aiStatus=FAILED + 降级说明，由医生手工诊断
 *
 * 覆盖三类用例：
 * - 正常：成功返回候选诊断 / 置信度转换 / null 可选字段处理
 * - 异常：BusinessException 降级 / 通用异常降级
 * - 边界：空候选诊断列表
 */
@DisplayName("AIDiagnosisController - AI 辅助诊断接口测试")
class AIDiagnosisControllerTest {

    private MockMvc mockMvc;
    private AIDiagnosisService aiDiagnosisService;

    @BeforeEach
    void setUp() {
        aiDiagnosisService = Mockito.mock(AIDiagnosisService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(
                com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(new AIDiagnosisController(aiDiagnosisService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .addFilters(new TraceIdFilter())
                .build();
    }

    private DiagnosisAIResult.PossibleDiagnosis diagnosis(String code, String name,
                                                          String confidence, String explanation) {
        return new DiagnosisAIResult.PossibleDiagnosis(code, name, confidence, explanation);
    }

    private DiagnosisAIResult resultWith(List<DiagnosisAIResult.PossibleDiagnosis> diagnoses) {
        return new DiagnosisAIResult(
                diagnoses,
                List.of("体温 38.5℃"),
                List.of("需补充既往手术史"),
                List.of("高血压病史"),
                List.of("血常规", "胸部 X 光"),
                "AI 建议仅供参考，最终诊断由医生确认");
    }

    @Test
    @DisplayName("assistDiagnosis - 成功返回候选诊断列表")
    void assistDiagnosis_shouldReturnCandidates() throws Exception {
        when(aiDiagnosisService.analyze(any(DiagnosisAIRequest.class)))
                .thenReturn(resultWith(List.of(
                        diagnosis("D001", "上呼吸道感染", "HIGH", "症状符合"),
                        diagnosis("D002", "急性支气管炎", "MEDIUM", "需进一步检查"))));

        String body = "{\"encounterId\":1,\"chiefComplaint\":\"咳嗽发热\","
                + "\"presentIllness\":\"咳嗽 3 天\",\"pastHistory\":\"高血压\","
                + "\"physicalExam\":\"咽部红肿\"}";

        mockMvc.perform(post("/api/ai/assist-diagnosis")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encounterId").value(1))
                .andExpect(jsonPath("$.aiStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.aiFailureReason").doesNotExist())
                .andExpect(jsonPath("$.candidates").isArray())
                .andExpect(jsonPath("$.candidates.length()").value(2))
                .andExpect(jsonPath("$.candidates[0].diagnosisCode").value("D001"))
                .andExpect(jsonPath("$.candidates[0].diagnosisName").value("上呼吸道感染"))
                .andExpect(jsonPath("$.candidates[0].reason").value("症状符合"))
                .andExpect(jsonPath("$.candidates[0].confidence").value(0.85))
                .andExpect(jsonPath("$.candidates[0].riskFactors[0]").value("高血压病史"))
                .andExpect(jsonPath("$.candidates[0].informationGaps[0]").value("需补充既往手术史"))
                .andExpect(jsonPath("$.candidates[0].recommendedExaminations[0]").value("血常规"));

        verify(aiDiagnosisService).analyze(any(DiagnosisAIRequest.class));
    }

    @Test
    @DisplayName("assistDiagnosis - 置信度 HIGH/MEDIUM/LOW 正确转换为数值")
    void assistDiagnosis_confidenceShouldConvertToNumber() throws Exception {
        when(aiDiagnosisService.analyze(any(DiagnosisAIRequest.class)))
                .thenReturn(resultWith(List.of(
                        diagnosis("D001", "高置信诊断", "HIGH", "高"),
                        diagnosis("D002", "中置信诊断", "MEDIUM", "中"),
                        diagnosis("D003", "低置信诊断", "LOW", "低"))));

        String body = "{\"encounterId\":2,\"chiefComplaint\":\"头痛\"}";

        mockMvc.perform(post("/api/ai/assist-diagnosis")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates[0].confidence").value(0.85))
                .andExpect(jsonPath("$.candidates[1].confidence").value(0.65))
                .andExpect(jsonPath("$.candidates[2].confidence").value(0.45));
    }

    @Test
    @DisplayName("assistDiagnosis - 可选字段为 null 时映射为空字符串")
    void assistDiagnosis_nullOptionalFields_shouldDefaultToEmptyString() throws Exception {
        when(aiDiagnosisService.analyze(any(DiagnosisAIRequest.class)))
                .thenReturn(resultWith(List.of(
                        diagnosis("D001", "诊断", "HIGH", "说明"))));

        // presentIllness/pastHistory/physicalExam 均不传（null）
        String body = "{\"encounterId\":3,\"chiefComplaint\":\"头痛\"}";

        mockMvc.perform(post("/api/ai/assist-diagnosis")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.candidates[0].diagnosisCode").value("D001"));
    }

    @Test
    @DisplayName("assistDiagnosis - 置信度为 null 时默认为 LOW（0.45）")
    void assistDiagnosis_nullConfidence_shouldDefaultToLow() throws Exception {
        when(aiDiagnosisService.analyze(any(DiagnosisAIRequest.class)))
                .thenReturn(resultWith(List.of(
                        diagnosis("D001", "诊断", null, "说明"))));

        String body = "{\"encounterId\":4,\"chiefComplaint\":\"发热\"}";

        mockMvc.perform(post("/api/ai/assist-diagnosis")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates[0].confidence").value(0.45));
    }

    @Test
    @DisplayName("assistDiagnosis - BusinessException 时降级返回 aiStatus=FAILED")
    void assistDiagnosis_businessException_shouldReturnFailed() throws Exception {
        when(aiDiagnosisService.analyze(any(DiagnosisAIRequest.class)))
                .thenThrow(new BusinessException("AI_DIAGNOSIS_FAILED", "AI 服务调用失败", 500));

        String body = "{\"encounterId\":5,\"chiefComplaint\":\"头痛\"}";

        mockMvc.perform(post("/api/ai/assist-diagnosis")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encounterId").value(5))
                .andExpect(jsonPath("$.aiStatus").value("FAILED"))
                .andExpect(jsonPath("$.candidates").isArray())
                .andExpect(jsonPath("$.candidates").isEmpty())
                .andExpect(jsonPath("$.aiFailureReason").exists());
    }

    @Test
    @DisplayName("assistDiagnosis - 通用异常时降级返回 aiStatus=FAILED 并提示手工诊断")
    void assistDiagnosis_genericException_shouldReturnFailedWithFallback() throws Exception {
        when(aiDiagnosisService.analyze(any(DiagnosisAIRequest.class)))
                .thenThrow(new RuntimeException("AI 服务连接超时"));

        String body = "{\"encounterId\":6,\"chiefComplaint\":\"发热\"}";

        mockMvc.perform(post("/api/ai/assist-diagnosis")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encounterId").value(6))
                .andExpect(jsonPath("$.aiStatus").value("FAILED"))
                .andExpect(jsonPath("$.candidates").isEmpty())
                .andExpect(jsonPath("$.aiFailureReason")
                        .value(org.hamcrest.Matchers.containsString("AI_PROVIDER_UNAVAILABLE")));
    }

    @Test
    @DisplayName("assistDiagnosis - 空候选诊断列表时返回空数组")
    void assistDiagnosis_emptyCandidates_shouldReturnEmptyArray() throws Exception {
        when(aiDiagnosisService.analyze(any(DiagnosisAIRequest.class)))
                .thenReturn(resultWith(List.of()));

        String body = "{\"encounterId\":7,\"chiefComplaint\":\"体检\"}";

        mockMvc.perform(post("/api/ai/assist-diagnosis")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.candidates").isArray())
                .andExpect(jsonPath("$.candidates").isEmpty());
    }

    @Test
    @DisplayName("assistDiagnosis - encounterId 为 null 时仍能正常处理")
    void assistDiagnosis_nullEncounterId_shouldStillWork() throws Exception {
        when(aiDiagnosisService.analyze(any(DiagnosisAIRequest.class)))
                .thenReturn(resultWith(List.of(
                        diagnosis("D001", "诊断", "HIGH", "说明"))));

        // encounterId 不传（null）
        String body = "{\"chiefComplaint\":\"头痛\"}";

        mockMvc.perform(post("/api/ai/assist-diagnosis")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.candidates[0].diagnosisCode").value("D001"));
    }
}
