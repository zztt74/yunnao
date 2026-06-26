package com.neusoft.cloudbrain.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neusoft.cloudbrain.ai.dto.MedicalRecordAIRequest;
import com.neusoft.cloudbrain.ai.dto.MedicalRecordAIResult;
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
 * 覆盖（来自任务 STAGE-AI-1 验收条件）：
 * - 正常调用返回病历草稿
 * - Provider 异常降级为 BusinessException
 * - 响应缺失必填字段抛出异常
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
    @DisplayName("正常调用：生成病历草稿")
    void generate_normal_returnsDraft() {
        MedicalRecordAIRequest request = new MedicalRecordAIRequest(
                "头痛", "持续3天", "无", "无异常",
                List.of("偏头痛"), "对症止痛");

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, MedicalRecordAIResult> parser = invocation.getArgument(1);
            String json = "{\"chiefComplaint\":\"头痛\",\"presentIllness\":\"持续3天\","
                    + "\"pastHistory\":\"无\",\"physicalExamination\":\"无异常\","
                    + "\"preliminaryDiagnosis\":\"偏头痛\",\"treatmentSuggestion\":\"对症止痛\","
                    + "\"disclaimer\":\"本病历草稿仅供医生参考\"}";
            return new AIInvocationRecorder.InvokeResult<>(parser.apply(json), true, 1L);
        });

        MedicalRecordAIResult result = service.generate(request);

        assertThat(result.chiefComplaint()).isEqualTo("头痛");
        assertThat(result.preliminaryDiagnosis()).isEqualTo("偏头痛");
    }

    @Test
    @DisplayName("Provider 异常降级为 BusinessException 504")
    void generate_providerError_throwsBusinessException() {
        MedicalRecordAIRequest request = new MedicalRecordAIRequest(
                "发热", null, null, null, null, null);

        when(recorder.invoke(any(), any()))
                .thenThrow(new AIProviderException("500 错误", true, 500));

        assertThatThrownBy(() -> service.generate(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("AI_MEDICAL_RECORD_FAILED");
                    assertThat(be.getHttpStatus()).isEqualTo(504);
                });
    }

    @Test
    @DisplayName("缺失必填字段 chiefComplaint 抛出异常")
    void generate_missingRequired_throwsBusinessException() {
        MedicalRecordAIRequest request = new MedicalRecordAIRequest(
                "头痛", null, null, null, null, null);

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, MedicalRecordAIResult> parser = invocation.getArgument(1);
            String invalidJson = "{\"presentIllness\":\"无\",\"disclaimer\":\"仅供参考\"}";
            try {
                parser.apply(invalidJson);
                throw new IllegalStateException("应该抛出异常");
            } catch (com.neusoft.cloudbrain.ai.exception.AIInvalidResponseException e) {
                throw e;
            }
        });

        assertThatThrownBy(() -> service.generate(request))
                .isInstanceOf(BusinessException.class);
    }
}
