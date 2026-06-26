package com.neusoft.cloudbrain.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neusoft.cloudbrain.ai.dto.MedicalRecordAIRequest;
import com.neusoft.cloudbrain.ai.dto.MedicalRecordAIResult;
import com.neusoft.cloudbrain.ai.exception.AIInvalidResponseException;
import com.neusoft.cloudbrain.ai.exception.AIProviderException;
import com.neusoft.cloudbrain.ai.parser.JsonSchemaParser;
import com.neusoft.cloudbrain.ai.prompt.PromptManager;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * AIMedicalRecordServiceImpl 单元测试
 *
 * 覆盖（来自任务 STAGE-AI-3a 验收条件，6 个 Mock 场景）：
 * - 正常：生成病历草稿
 * - 高风险：严重症状生成高风险草稿（不自动确诊）
 * - 空结果：空输入返回空字段而非编造
 * - 超时：Provider 超时降级为 BusinessException 504
 * - 非法 JSON：响应非法降级为 BusinessException 500
 * - 异常：Provider 异常降级为 BusinessException 504
 *
 * 输出 Schema（来自 13_AI能力集成AI任务书.md 第3.3节，6 个字段，不含 disclaimer）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AIMedicalRecordServiceImpl - 病历生成服务测试")
class AIMedicalRecordServiceImplTest {

    @Mock
    private AIInvocationRecorder recorder;

    @Mock
    private PromptManager promptManager;

    private AIMedicalRecordServiceImpl service;

    @BeforeEach
    void setUp() {
        JsonSchemaParser parser = new JsonSchemaParser(new ObjectMapper());
        service = new AIMedicalRecordServiceImpl(recorder, parser, promptManager);
        when(promptManager.getPrompt(anyString())).thenReturn("system prompt");
        when(promptManager.getPromptVersion(anyString())).thenReturn("v1");
    }

    @Test
    @DisplayName("正常调用：生成病历草稿（6 字段）")
    void generate_normal_returnsDraft() {
        MedicalRecordAIRequest request = new MedicalRecordAIRequest(
                "头痛", "持续3天", "无", "无异常",
                List.of("偏头痛"), "对症止痛");

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, MedicalRecordAIResult> parser = invocation.getArgument(1);
            String json = "{\"chiefComplaint\":\"头痛\",\"presentIllness\":\"持续3天\","
                    + "\"pastHistory\":\"无\",\"physicalExamination\":\"无异常\","
                    + "\"preliminaryDiagnosis\":\"偏头痛\",\"treatmentSuggestion\":\"对症止痛\"}";
            return new AIInvocationRecorder.InvokeResult<>(parser.apply(json), true, 1L);
        });

        MedicalRecordAIResult result = service.generate(request);

        assertThat(result.chiefComplaint()).isEqualTo("头痛");
        assertThat(result.presentIllness()).isEqualTo("持续3天");
        assertThat(result.pastHistory()).isEqualTo("无");
        assertThat(result.physicalExamination()).isEqualTo("无异常");
        assertThat(result.preliminaryDiagnosis()).isEqualTo("偏头痛");
        assertThat(result.treatmentSuggestion()).isEqualTo("对症止痛");
        // disclaimer 为固定安全声明（向后兼容），非 AI 输出字段
        assertThat(result.disclaimer()).contains("仅供医生参考");
    }

    @Test
    @DisplayName("高风险场景：胸痛症状生成高风险草稿，不自动确诊")
    void generate_highRisk_returnsUrgentDraft() {
        MedicalRecordAIRequest request = new MedicalRecordAIRequest(
                "胸痛伴大汗", "突发胸痛持续不缓解", "高血压史", null,
                null, null);

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, MedicalRecordAIResult> parser = invocation.getArgument(1);
            String json = "{\"chiefComplaint\":\"胸痛伴大汗，持续不缓解\","
                    + "\"presentIllness\":\"患者突发胸痛，呈压榨样，伴大汗及放射痛，症状持续不缓解\","
                    + "\"pastHistory\":\"高血压史\",\"physicalExamination\":\"待查（建议立即测量血压、心电图）\","
                    + "\"preliminaryDiagnosis\":\"胸痛待查，警惕急性心肌梗死\","
                    + "\"treatmentSuggestion\":\"建议立即启动急诊流程，完善心电图、心肌酶谱检查\"}";
            return new AIInvocationRecorder.InvokeResult<>(parser.apply(json), true, 1L);
        });

        MedicalRecordAIResult result = service.generate(request);

        assertThat(result.chiefComplaint()).contains("胸痛");
        assertThat(result.preliminaryDiagnosis()).contains("待查");
        // 不得自动确诊：诊断必须含"待查"字样
        assertThat(result.preliminaryDiagnosis()).doesNotContain("确诊");
    }

    @Test
    @DisplayName("空结果：空输入返回空字段而非编造")
    void generate_emptyInput_returnsEmptyFields() {
        MedicalRecordAIRequest request = new MedicalRecordAIRequest(
                null, null, null, null, null, null);

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, MedicalRecordAIResult> parser = invocation.getArgument(1);
            // 空结果：所有字段为空字符串，不编造内容
            String json = "{\"chiefComplaint\":\"\",\"presentIllness\":\"\","
                    + "\"pastHistory\":\"\",\"physicalExamination\":\"\","
                    + "\"preliminaryDiagnosis\":\"\",\"treatmentSuggestion\":\"\"}";
            return new AIInvocationRecorder.InvokeResult<>(parser.apply(json), true, 1L);
        });

        MedicalRecordAIResult result = service.generate(request);

        assertThat(result.chiefComplaint()).isEmpty();
        assertThat(result.presentIllness()).isEmpty();
        assertThat(result.pastHistory()).isEmpty();
        assertThat(result.physicalExamination()).isEmpty();
        assertThat(result.preliminaryDiagnosis()).isEmpty();
        assertThat(result.treatmentSuggestion()).isEmpty();
    }

    @Test
    @DisplayName("超时：Provider 超时降级为 BusinessException 504")
    void generate_timeout_throwsBusinessException504() {
        MedicalRecordAIRequest request = new MedicalRecordAIRequest(
                "发热", "发热2天", null, null, null, null);

        when(recorder.invoke(any(), any()))
                .thenThrow(new AIProviderException("Mock 超时", true, null));

        assertThatThrownBy(() -> service.generate(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("AI_MEDICAL_RECORD_FAILED");
                    assertThat(be.getHttpStatus()).isEqualTo(504);
                });
    }

    @Test
    @DisplayName("非法 JSON：响应非法降级为 BusinessException 500")
    void generate_invalidJson_throwsBusinessException500() {
        MedicalRecordAIRequest request = new MedicalRecordAIRequest(
                "头痛", null, null, null, null, null);

        when(recorder.invoke(any(), any()))
                .thenThrow(new AIInvalidResponseException("AI 响应非合法 JSON"));

        assertThatThrownBy(() -> service.generate(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("AI_MEDICAL_RECORD_FAILED");
                    assertThat(be.getHttpStatus()).isEqualTo(500);
                });
    }

    @Test
    @DisplayName("异常：Provider 异常降级为 BusinessException 504")
    void generate_providerError_throwsBusinessException504() {
        MedicalRecordAIRequest request = new MedicalRecordAIRequest(
                "发热", null, null, null, null, null);

        when(recorder.invoke(any(), any()))
                .thenThrow(new AIProviderException("500 错误", false, 500));

        assertThatThrownBy(() -> service.generate(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("AI_MEDICAL_RECORD_FAILED");
                    assertThat(be.getHttpStatus()).isEqualTo(504);
                });
    }

    @Test
    @DisplayName("缺失必填字段 treatmentSuggestion 抛出 BusinessException")
    void generate_missingRequiredField_throwsBusinessException() {
        MedicalRecordAIRequest request = new MedicalRecordAIRequest(
                "头痛", null, null, null, null, null);

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, MedicalRecordAIResult> parser = invocation.getArgument(1);
            // 缺少 treatmentSuggestion 字段，validateRequired 抛出 AIInvalidResponseException
            String invalidJson = "{\"chiefComplaint\":\"头痛\",\"presentIllness\":\"\","
                    + "\"pastHistory\":\"\",\"physicalExamination\":\"\","
                    + "\"preliminaryDiagnosis\":\"\"}";
            // parser.apply 抛出异常，InvokeResult 构造不会执行
            return new AIInvocationRecorder.InvokeResult<>(parser.apply(invalidJson), true, 1L);
        });

        assertThatThrownBy(() -> service.generate(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("AI_MEDICAL_RECORD_FAILED");
                    assertThat(be.getHttpStatus()).isEqualTo(500);
                });
    }
}
