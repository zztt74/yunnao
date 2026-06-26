package com.neusoft.cloudbrain.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neusoft.cloudbrain.ai.dto.ResultInterpretationAIRequest;
import com.neusoft.cloudbrain.ai.dto.ResultInterpretationAIResult;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * AIResultInterpretationServiceImpl 单元测试
 *
 * 覆盖（来自任务 STAGE-AI-1 验收条件）：
 * - 正常调用返回结果解读
 * - Provider 异常降级为 BusinessException
 * - 缺失必填字段抛出异常
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AIResultInterpretationServiceImpl - 结果解读服务测试")
class AIResultInterpretationServiceImplTest {

    @Mock
    private AIInvocationRecorder recorder;

    @Mock
    private PromptManager promptManager;

    private AIResultInterpretationServiceImpl service;

    @BeforeEach
    void setUp() {
        JsonSchemaParser parser = new JsonSchemaParser(new ObjectMapper());
        service = new AIResultInterpretationServiceImpl(recorder, parser, promptManager);
        when(promptManager.getPrompt(anyString())).thenReturn("system prompt");
        when(promptManager.getPromptVersion(anyString())).thenReturn("v1");
    }

    @Test
    @DisplayName("正常调用：偏高结果返回异常项和解读")
    void interpret_highValue_returnsAbnormal() {
        ResultInterpretationAIRequest request = new ResultInterpretationAIRequest(
                "血糖", "8.5 mmol/L 偏高", "3.9-6.1 mmol/L", "LAB");

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, ResultInterpretationAIResult> parser = invocation.getArgument(1);
            String json = "{\"abnormalItems\":[\"血糖偏高\"],"
                    + "\"plainLanguageExplanation\":\"血糖超出正常范围\","
                    + "\"followUpAdvice\":\"建议复查\","
                    + "\"disclaimer\":\"仅供医生参考\"}";
            return new AIInvocationRecorder.InvokeResult<>(parser.apply(json), true, 1L);
        });

        ResultInterpretationAIResult result = service.interpret(request);

        assertThat(result.abnormalItems()).contains("血糖偏高");
        assertThat(result.plainLanguageExplanation()).contains("血糖");
    }

    @Test
    @DisplayName("Provider 异常降级为 BusinessException 504")
    void interpret_providerError_throwsBusinessException() {
        ResultInterpretationAIRequest request = new ResultInterpretationAIRequest(
                "血压", "120/80", null, "VITAL");

        when(recorder.invoke(any(), any()))
                .thenThrow(new AIProviderException("超时", true, null));

        assertThatThrownBy(() -> service.interpret(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("AI_RESULT_INTERPRETATION_FAILED");
                    assertThat(be.getHttpStatus()).isEqualTo(504);
                });
    }

    @Test
    @DisplayName("缺失必填字段 plainLanguageExplanation 抛出 BusinessException")
    void interpret_missingRequired_throwsBusinessException() {
        ResultInterpretationAIRequest request = new ResultInterpretationAIRequest(
                "血糖", "5.0", null, "LAB");

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, ResultInterpretationAIResult> parser = invocation.getArgument(1);
            String invalidJson = "{\"abnormalItems\":[],\"followUpAdvice\":\"无\","
                    + "\"disclaimer\":\"仅供参考\"}";
            try {
                parser.apply(invalidJson);
                throw new IllegalStateException("应该抛出异常");
            } catch (com.neusoft.cloudbrain.ai.exception.AIInvalidResponseException e) {
                throw e;
            }
        });

        assertThatThrownBy(() -> service.interpret(request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("正常值结果返回空异常项")
    void interpret_normalValue_returnsEmptyAbnormalItems() {
        ResultInterpretationAIRequest request = new ResultInterpretationAIRequest(
                "血红蛋白", "135 g/L 正常", "120-160 g/L", "LAB");

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, ResultInterpretationAIResult> parser = invocation.getArgument(1);
            String json = "{\"abnormalItems\":[],"
                    + "\"plainLanguageExplanation\":\"结果正常\","
                    + "\"followUpAdvice\":\"保持健康生活方式\","
                    + "\"disclaimer\":\"仅供医生参考\"}";
            return new AIInvocationRecorder.InvokeResult<>(parser.apply(json), true, 1L);
        });

        ResultInterpretationAIResult result = service.interpret(request);

        assertThat(result.abnormalItems()).isEmpty();
        assertThat(result.followUpAdvice()).contains("健康");
    }
}
