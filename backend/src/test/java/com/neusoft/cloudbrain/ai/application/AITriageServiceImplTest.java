package com.neusoft.cloudbrain.ai.application;

import com.neusoft.cloudbrain.ai.dto.TriageAIRequest;
import com.neusoft.cloudbrain.ai.dto.TriageAIResult;
import com.neusoft.cloudbrain.ai.exception.AIInvalidResponseException;
import com.neusoft.cloudbrain.ai.exception.AIProviderException;
import com.neusoft.cloudbrain.ai.parser.JsonSchemaParser;
import com.neusoft.cloudbrain.ai.prompt.PromptManager;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * AITriageServiceImpl 单元测试
 *
 * 覆盖（来自任务 STAGE-AI-2 验收条件、41_质量测试与完成定义.md 第11.3节 AI 分诊必测场景）：
 * - 5 类主诉路径：胸痛/头痛/发热/腹痛/外伤
 * - 7 Mock 场景：正常/高风险（胸痛→HIGH）/空/超时/非法JSON/不存在科室/异常
 * - 降级：超时降级到手动流程、非法 JSON 映射 AI_INVALID_RESPONSE
 * - Schema 校验：priority 枚举受控、departmentCode 枚举受控
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AITriageServiceImpl - 分诊服务测试")
class AITriageServiceImplTest {

    @Mock
    private AIInvocationRecorder recorder;

    @Mock
    private PromptManager promptManager;

    private AITriageServiceImpl service;

    @BeforeEach
    void setUp() {
        JsonSchemaParser parser = new JsonSchemaParser(new ObjectMapper());
        service = new AITriageServiceImpl(recorder, parser, promptManager);
        when(promptManager.getPrompt(anyString())).thenReturn("system prompt");
        when(promptManager.getPromptVersion(anyString())).thenReturn("v1");
    }

    // ============================================================
    // 5 类主诉路径
    // ============================================================

    @Nested
    @DisplayName("5 类主诉路径")
    class ChiefComplaintPaths {

        @Test
        @DisplayName("胸痛 → 心血管内科 HIGH")
        void analyze_chestPain_returnsCardiologyHigh() {
            TriageAIRequest request = new TriageAIRequest(
                    "40-50", "MALE", "胸痛 2 小时", "2小时", "伴出汗");

            mockRecorderReturn("""
                    {"departmentCode":"DEPT_CARDIOLOGY","priority":"HIGH",
                    "symptomKeywords":["胸痛","心血管"],
                    "reason":"症状提示可能的心血管问题",
                    "safetyNotice":"本结果由 AI 辅助生成，仅供辅助参考，最终诊断请由医生确认",
                    "emergencySuggested":true}""");

            TriageAIResult result = service.analyze(request);

            assertThat(result.departmentCode()).isEqualTo("DEPT_CARDIOLOGY");
            assertThat(result.priority()).isEqualTo("HIGH");
            assertThat(result.emergencySuggested()).isTrue();
            assertThat(result.symptomKeywords()).contains("胸痛");
        }

        @Test
        @DisplayName("头痛 → 神经内科 MEDIUM")
        void analyze_headache_returnsNeurologyMedium() {
            TriageAIRequest request = new TriageAIRequest(
                    "30-40", "FEMALE", "头痛", "3天", null);

            mockRecorderReturn("""
                    {"departmentCode":"DEPT_NEUROLOGY","priority":"MEDIUM",
                    "symptomKeywords":["头痛","神经"],
                    "reason":"症状提示可能的神经系统问题",
                    "safetyNotice":"本结果由 AI 辅助生成，仅供辅助参考，最终诊断请由医生确认",
                    "emergencySuggested":false}""");

            TriageAIResult result = service.analyze(request);

            assertThat(result.departmentCode()).isEqualTo("DEPT_NEUROLOGY");
            assertThat(result.priority()).isEqualTo("MEDIUM");
            assertThat(result.emergencySuggested()).isFalse();
        }

        @Test
        @DisplayName("发热 → 内科 MEDIUM")
        void analyze_fever_returnsInternalMedium() {
            TriageAIRequest request = new TriageAIRequest(
                    "20-30", "MALE", "发热咳嗽", "1天", null);

            mockRecorderReturn("""
                    {"departmentCode":"DEPT_INTERNAL","priority":"MEDIUM",
                    "symptomKeywords":["发热","咳嗽"],
                    "reason":"症状提示呼吸道感染可能",
                    "safetyNotice":"本结果由 AI 辅助生成，仅供辅助参考，最终诊断请由医生确认",
                    "emergencySuggested":false}""");

            TriageAIResult result = service.analyze(request);

            assertThat(result.departmentCode()).isEqualTo("DEPT_INTERNAL");
            assertThat(result.priority()).isEqualTo("MEDIUM");
            assertThat(result.symptomKeywords()).containsExactly("发热", "咳嗽");
        }

        @Test
        @DisplayName("腹痛 → 内科 MEDIUM")
        void analyze_abdominalPain_returnsInternalMedium() {
            TriageAIRequest request = new TriageAIRequest(
                    "50-60", "MALE", "腹痛腹泻", "半天", "进食后加重");

            mockRecorderReturn("""
                    {"departmentCode":"DEPT_INTERNAL","priority":"MEDIUM",
                    "symptomKeywords":["腹痛","消化"],
                    "reason":"症状提示消化系统问题",
                    "safetyNotice":"本结果由 AI 辅助生成，仅供辅助参考，最终诊断请由医生确认",
                    "emergencySuggested":false}""");

            TriageAIResult result = service.analyze(request);

            assertThat(result.departmentCode()).isEqualTo("DEPT_INTERNAL");
            assertThat(result.priority()).isEqualTo("MEDIUM");
        }

        @Test
        @DisplayName("外伤 → 普通外科 MEDIUM")
        void analyze_trauma_returnsGeneralSurgeryMedium() {
            TriageAIRequest request = new TriageAIRequest(
                    "20-30", "FEMALE", "外伤骨折", "1小时", "摔伤");

            mockRecorderReturn("""
                    {"departmentCode":"DEPT_GENERAL_SURGERY","priority":"MEDIUM",
                    "symptomKeywords":["外伤"],
                    "reason":"症状提示外科问题",
                    "safetyNotice":"本结果由 AI 辅助生成，仅供辅助参考，最终诊断请由医生确认",
                    "emergencySuggested":false}""");

            TriageAIResult result = service.analyze(request);

            assertThat(result.departmentCode()).isEqualTo("DEPT_GENERAL_SURGERY");
            assertThat(result.priority()).isEqualTo("MEDIUM");
        }
    }

    // ============================================================
    // 7 Mock 场景
    // ============================================================

    @Nested
    @DisplayName("7 Mock 场景")
    class MockScenarios {

        @Test
        @DisplayName("正常场景：返回有效分诊结果")
        void scenario_normal() {
            TriageAIRequest request = new TriageAIRequest(
                    "30-40", "MALE", "发热", "2天", null);

            mockRecorderReturn("""
                    {"departmentCode":"DEPT_INTERNAL","priority":"MEDIUM",
                    "symptomKeywords":["发热"],
                    "reason":"呼吸道感染可能",
                    "safetyNotice":"本结果由 AI 辅助生成，仅供辅助参考，最终诊断请由医生确认",
                    "emergencySuggested":false}""");

            TriageAIResult result = service.analyze(request);

            assertThat(result.departmentCode()).isEqualTo("DEPT_INTERNAL");
            assertThat(result.priority()).isEqualTo("MEDIUM");
        }

        @Test
        @DisplayName("高风险场景：胸痛 → HIGH + emergencySuggested=true")
        void scenario_highRisk_chestPain() {
            TriageAIRequest request = new TriageAIRequest(
                    "50-60", "MALE", "胸痛伴出汗", "1小时", "放射至左臂");

            mockRecorderReturn("""
                    {"departmentCode":"DEPT_CARDIOLOGY","priority":"HIGH",
                    "symptomKeywords":["胸痛","心血管"],
                    "reason":"症状提示可能的心血管问题，建议尽快就诊",
                    "safetyNotice":"本结果由 AI 辅助生成，仅供辅助参考，最终诊断请由医生确认",
                    "emergencySuggested":true}""");

            TriageAIResult result = service.analyze(request);

            assertThat(result.priority()).isEqualTo("HIGH");
            assertThat(result.emergencySuggested()).isTrue();
            assertThat(result.departmentCode()).isEqualTo("DEPT_CARDIOLOGY");
        }

        @Test
        @DisplayName("空结果场景：返回 LOW 优先级和默认科室")
        void scenario_empty() {
            TriageAIRequest request = new TriageAIRequest(
                    "20-30", "FEMALE", "MOCK_EMPTY", null, null);

            mockRecorderReturn("""
                    {"departmentCode":"DEPT_INTERNAL","priority":"LOW",
                    "symptomKeywords":[],
                    "reason":"信息不足，无法精确分诊，建议先就诊内科进行初步检查",
                    "safetyNotice":"本结果由 AI 辅助生成，仅供辅助参考，最终诊断请由医生确认",
                    "emergencySuggested":false}""");

            TriageAIResult result = service.analyze(request);

            assertThat(result.priority()).isEqualTo("LOW");
            assertThat(result.symptomKeywords()).isEmpty();
            assertThat(result.departmentCode()).isEqualTo("DEPT_INTERNAL");
        }

        @Test
        @DisplayName("超时场景：Provider 超时 → 降级 BusinessException 504")
        void scenario_timeout() {
            TriageAIRequest request = new TriageAIRequest(
                    "30-40", "MALE", "头痛", "2天", null);

            when(recorder.invoke(any(), any()))
                    .thenThrow(new AIProviderException("Mock 超时", true, null));

            assertThatThrownBy(() -> service.analyze(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("AI_TRIAGE_FAILED");
                        assertThat(be.getHttpStatus()).isEqualTo(504);
                    });
        }

        @Test
        @DisplayName("非法 JSON 场景：映射 AI_INVALID_RESPONSE")
        void scenario_invalidJson() {
            TriageAIRequest request = new TriageAIRequest(
                    "30-40", "FEMALE", "MOCK_INVALID_JSON", null, null);

            when(recorder.invoke(any(), any()))
                    .thenThrow(new AIInvalidResponseException("AI 响应非合法 JSON"));

            assertThatThrownBy(() -> service.analyze(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("AI_INVALID_RESPONSE");
                    });
        }

        @Test
        @DisplayName("不存在科室场景：departmentCode 校验失败 → AI_INVALID_RESPONSE")
        void scenario_notExistDept() {
            TriageAIRequest request = new TriageAIRequest(
                    "30-40", "MALE", "MOCK_NOT_EXIST_DEPT", null, null);

            mockRecorderReturn("""
                    {"departmentCode":"DEPT_NOT_EXIST_999","priority":"MEDIUM",
                    "symptomKeywords":["测试"],
                    "reason":"测试不存在科室场景",
                    "safetyNotice":"本结果由 AI 辅助生成，仅供辅助参考，最终诊断请由医生确认",
                    "emergencySuggested":false}""");

            assertThatThrownBy(() -> service.analyze(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("AI_INVALID_RESPONSE");
                    });
        }

        @Test
        @DisplayName("Provider 异常场景：不可重试异常 → 降级 BusinessException 504")
        void scenario_providerError() {
            TriageAIRequest request = new TriageAIRequest(
                    "30-40", "FEMALE", "MOCK_PROVIDER_ERROR", null, null);

            when(recorder.invoke(any(), any()))
                    .thenThrow(new AIProviderException("Mock Provider 异常", false, 500));

            assertThatThrownBy(() -> service.analyze(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("AI_TRIAGE_FAILED");
                        assertThat(be.getHttpStatus()).isEqualTo(504);
                    });
        }
    }

    // ============================================================
    // Schema 校验
    // ============================================================

    @Nested
    @DisplayName("Schema 校验")
    class SchemaValidation {

        @Test
        @DisplayName("priority 枚举非法 → AI_INVALID_RESPONSE")
        void validate_priorityInvalid() {
            TriageAIRequest request = new TriageAIRequest(
                    "30-40", "MALE", "头痛", "1天", null);

            mockRecorderReturn("""
                    {"departmentCode":"DEPT_NEUROLOGY","priority":"CRITICAL",
                    "symptomKeywords":["头痛"],
                    "reason":"测试",
                    "safetyNotice":"本结果由 AI 辅助生成，仅供辅助参考，最终诊断请由医生确认",
                    "emergencySuggested":false}""");

            assertThatThrownBy(() -> service.analyze(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("AI_INVALID_RESPONSE");
                    });
        }

        @Test
        @DisplayName("departmentCode 枚举非法 → AI_INVALID_RESPONSE")
        void validate_departmentCodeInvalid() {
            TriageAIRequest request = new TriageAIRequest(
                    "30-40", "FEMALE", "腹痛", "1天", null);

            mockRecorderReturn("""
                    {"departmentCode":"DEPT_FAKE","priority":"MEDIUM",
                    "symptomKeywords":["腹痛"],
                    "reason":"测试",
                    "safetyNotice":"本结果由 AI 辅助生成，仅供辅助参考，最终诊断请由医生确认",
                    "emergencySuggested":false}""");

            assertThatThrownBy(() -> service.analyze(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("AI_INVALID_RESPONSE");
                    });
        }

        @Test
        @DisplayName("缺少必填字段 reason → AI_INVALID_RESPONSE")
        void validate_missingRequiredField() {
            TriageAIRequest request = new TriageAIRequest(
                    "30-40", "MALE", "发热", "1天", null);

            mockRecorderReturn("""
                    {"departmentCode":"DEPT_INTERNAL","priority":"MEDIUM",
                    "symptomKeywords":["发热"],
                    "safetyNotice":"本结果由 AI 辅助生成，仅供辅助参考，最终诊断请由医生确认",
                    "emergencySuggested":false}""");

            assertThatThrownBy(() -> service.analyze(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("AI_INVALID_RESPONSE");
                    });
        }
    }

    // ============================================================
    // 安全性验证
    // ============================================================

    @Nested
    @DisplayName("安全性验证")
    class SafetyChecks {

        @Test
        @DisplayName("安全提示包含'仅供辅助参考'")
        void safetyNotice_containsDisclaimer() {
            TriageAIRequest request = new TriageAIRequest(
                    "30-40", "MALE", "发热", "1天", null);

            mockRecorderReturn("""
                    {"departmentCode":"DEPT_INTERNAL","priority":"MEDIUM",
                    "symptomKeywords":["发热"],
                    "reason":"呼吸道感染可能",
                    "safetyNotice":"本结果由 AI 辅助生成，仅供辅助参考，最终诊断请由医生确认",
                    "emergencySuggested":false}""");

            TriageAIResult result = service.analyze(request);

            assertThat(result.safetyNotice()).contains("仅供辅助参考");
        }

        @Test
        @DisplayName("markdown fence 包裹的 JSON 可正常解析")
        void parse_markdownFencedJson() {
            TriageAIRequest request = new TriageAIRequest(
                    "30-40", "MALE", "发热", "1天", null);

            mockRecorderReturn("""
                    ```json
                    {"departmentCode":"DEPT_INTERNAL","priority":"MEDIUM",
                    "symptomKeywords":["发热"],
                    "reason":"呼吸道感染可能",
                    "safetyNotice":"本结果由 AI 辅助生成，仅供辅助参考，最终诊断请由医生确认",
                    "emergencySuggested":false}
                    ```""");

            TriageAIResult result = service.analyze(request);

            assertThat(result.departmentCode()).isEqualTo("DEPT_INTERNAL");
        }
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    /**
     * 模拟 recorder.invoke 返回解析后的结果
     */
    @SuppressWarnings("unchecked")
    private void mockRecorderReturn(String jsonResponse) {
        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            Function<String, TriageAIResult> parser = invocation.getArgument(1);
            TriageAIResult parsed = parser.apply(jsonResponse);
            return new AIInvocationRecorder.InvokeResult<>(parsed, true, 1L);
        });
    }
}
