package com.neusoft.cloudbrain.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neusoft.cloudbrain.ai.dto.DiagnosisAIRequest;
import com.neusoft.cloudbrain.ai.dto.DiagnosisAIResult;
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
 * AIDiagnosisServiceImpl 单元测试
 *
 * 覆盖（来自任务 STAGE-AI-1 验收条件）：
 * - 正常调用返回候选诊断列表
 * - Provider 异常降级为 BusinessException
 * - 非法 confidence 枚举值抛出异常
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AIDiagnosisServiceImpl - 诊断服务测试")
class AIDiagnosisServiceImplTest {

    @Mock
    private AIInvocationRecorder recorder;

    @Mock
    private PromptManager promptManager;

    private AIDiagnosisServiceImpl service;

    @BeforeEach
    void setUp() {
        JsonSchemaParser parser = new JsonSchemaParser(new ObjectMapper());
        service = new AIDiagnosisServiceImpl(recorder, parser, promptManager);
        when(promptManager.getPrompt(anyString())).thenReturn("system prompt");
        when(promptManager.getPromptVersion(anyString())).thenReturn("v1");
    }

    @Test
    @DisplayName("正常调用：胸痛返回候选诊断列表")
    void analyze_chestPain_returnsDiagnoses() {
        DiagnosisAIRequest request = new DiagnosisAIRequest(
                "胸痛", "无既往史", null, null, "40-50", "MALE");

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, DiagnosisAIResult> parser = invocation.getArgument(1);
            String json = "{\"possibleDiagnoses\":[{\"diagnosisCode\":\"I20.9\","
                    + "\"diagnosisName\":\"心绞痛\",\"confidence\":\"MEDIUM\","
                    + "\"explanation\":\"胸痛\"}],\"evidence\":[\"主诉\"],"
                    + "\"missingInformation\":[],\"riskFactors\":[],"
                    + "\"suggestedExaminations\":[\"心电图\"],"
                    + "\"disclaimer\":\"仅供医生参考\"}";
            return new AIInvocationRecorder.InvokeResult<>(parser.apply(json), true, 1L);
        });

        DiagnosisAIResult result = service.analyze(request);

        assertThat(result.possibleDiagnoses()).hasSize(1);
        assertThat(result.possibleDiagnoses().get(0).diagnosisCode()).isEqualTo("I20.9");
        assertThat(result.suggestedExaminations()).contains("心电图");
    }

    @Test
    @DisplayName("Provider 异常降级为 BusinessException 504")
    void analyze_providerError_throwsBusinessException() {
        DiagnosisAIRequest request = new DiagnosisAIRequest(
                "头痛", null, null, null, "30-40", "FEMALE");

        when(recorder.invoke(any(), any()))
                .thenThrow(new AIProviderException("超时", true, null));

        assertThatThrownBy(() -> service.analyze(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("AI_DIAGNOSIS_FAILED");
                    assertThat(be.getHttpStatus()).isEqualTo(504);
                });
    }

    @Test
    @DisplayName("非法 confidence 枚举值抛出 AIInvalidResponseException（最终 BusinessException）")
    void analyze_invalidConfidence_throwsBusinessException() {
        DiagnosisAIRequest request = new DiagnosisAIRequest(
                "发热", null, null, null, "20-30", "MALE");

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, DiagnosisAIResult> parser = invocation.getArgument(1);
            String json = "{\"possibleDiagnoses\":[{\"diagnosisCode\":\"J06.9\","
                    + "\"diagnosisName\":\"感冒\",\"confidence\":\"INVALID\","
                    + "\"explanation\":\"\"}],\"evidence\":[],"
                    + "\"disclaimer\":\"仅供医生参考\"}";
            // 解析时抛出 AIInvalidResponseException，由 recorder 包装
            try {
                parser.apply(json);
                throw new IllegalStateException("应该抛出异常");
            } catch (com.neusoft.cloudbrain.ai.exception.AIInvalidResponseException e) {
                throw e;
            }
        });

        assertThatThrownBy(() -> service.analyze(request))
                .isInstanceOf(BusinessException.class);
    }
}
