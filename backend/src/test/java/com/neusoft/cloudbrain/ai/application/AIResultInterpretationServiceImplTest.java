package com.neusoft.cloudbrain.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neusoft.cloudbrain.ai.dto.ResultInterpretationAIRequest;
import com.neusoft.cloudbrain.ai.dto.ResultInterpretationAIResult;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * AIResultInterpretationServiceImpl 单元测试
 *
 * 覆盖（来自任务 STAGE-AI-3b 验收条件，6 个 Mock 场景）：
 * - 正常：解读异常指标（5 字段）
 * - 高风险（危急值）：危急值解读，不修改原始数值
 * - 空结果：空输入返回空集合+说明而非编造
 * - 超时：Provider 超时降级为 BusinessException 504
 * - 非法 JSON：响应非法降级为 BusinessException 500
 * - 异常：Provider 异常降级为 BusinessException 504
 *
 * 输出 Schema（来自 13_AI能力集成AI任务书.md 第3.5节，5 个字段）：
 * abnormalItems、plainLanguageExplanation、possibleAttentionPoints、followUpSuggestion、disclaimer
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AIResultInterpretationServiceImpl - 检查检验解读服务测试")
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
    @DisplayName("正常调用：解读异常指标（5 字段）")
    void interpret_normal_returnsInterpretation() {
        ResultInterpretationAIRequest request = new ResultInterpretationAIRequest(
                "血糖", "7.8 mmol/L", "3.9-6.1 mmol/L", "LAB");

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, ResultInterpretationAIResult> parser = invocation.getArgument(1);
            String json = "{\"abnormalItems\":[\"血糖偏高\"],"
                    + "\"plainLanguageExplanation\":\"血糖轻度升高，建议结合临床进一步评估\","
                    + "\"possibleAttentionPoints\":[\"关注血糖变化\",\"必要时复查\"],"
                    + "\"followUpSuggestion\":\"建议复查并关注血糖变化，必要时就诊\","
                    + "\"disclaimer\":\"本解读由 AI 辅助生成，仅供医生参考，不能替代医生专业判断\"}";
            return new AIInvocationRecorder.InvokeResult<>(parser.apply(json), true, 1L);
        });

        ResultInterpretationAIResult result = service.interpret(request);

        assertThat(result.abnormalItems()).containsExactly("血糖偏高");
        assertThat(result.plainLanguageExplanation()).contains("血糖");
        assertThat(result.possibleAttentionPoints()).contains("关注血糖变化");
        assertThat(result.followUpSuggestion()).contains("复查");
        assertThat(result.disclaimer()).contains("仅供医生参考");
        // 向后兼容访问器：followUpAdvice() 等价于 followUpSuggestion
        assertThat(result.followUpAdvice()).isEqualTo(result.followUpSuggestion());
    }

    @Test
    @DisplayName("高风险（危急值）：危急值解读，不修改原始数值")
    void interpret_criticalValue_returnsUrgentInterpretation() {
        ResultInterpretationAIRequest request = new ResultInterpretationAIRequest(
                "血钾", "6.8 mmol/L", "3.5-5.5 mmol/L", "LAB");

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, ResultInterpretationAIResult> parser = invocation.getArgument(1);
            String json = "{\"abnormalItems\":[\"危急值\"],"
                    + "\"plainLanguageExplanation\":\"结果为危急值，已超出安全范围，需立即就医处理，原始数值未被修改，请以报告为准\","
                    + "\"possibleAttentionPoints\":[\"立即就诊\",\"持续监护生命体征\",\"复核原始数值\"],"
                    + "\"followUpSuggestion\":\"建议立即启动急诊流程，及时处理危急值，并复查确认\","
                    + "\"disclaimer\":\"本解读由 AI 辅助生成，仅供医生参考，不能替代医生专业判断\"}";
            return new AIInvocationRecorder.InvokeResult<>(parser.apply(json), true, 1L);
        });

        ResultInterpretationAIResult result = service.interpret(request);

        assertThat(result.abnormalItems()).contains("危急值");
        // 不得修改原始数值：解释中必须明确说明原始数值未被修改
        assertThat(result.plainLanguageExplanation()).contains("原始数值未被修改");
        assertThat(result.followUpSuggestion()).contains("立即");
    }

    @Test
    @DisplayName("空结果：空输入返回空集合+说明而非编造")
    void interpret_emptyInput_returnsEmptyAbnormalItems() {
        ResultInterpretationAIRequest request = new ResultInterpretationAIRequest(
                null, null, null, null);

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, ResultInterpretationAIResult> parser = invocation.getArgument(1);
            // 空结果：异常项和关注点为空数组，不编造异常
            String json = "{\"abnormalItems\":[],"
                    + "\"plainLanguageExplanation\":\"暂无明显异常\","
                    + "\"possibleAttentionPoints\":[],"
                    + "\"followUpSuggestion\":\"建议保持健康生活方式，按需复查\","
                    + "\"disclaimer\":\"本解读由 AI 辅助生成，仅供医生参考，不能替代医生专业判断\"}";
            return new AIInvocationRecorder.InvokeResult<>(parser.apply(json), true, 1L);
        });

        ResultInterpretationAIResult result = service.interpret(request);

        assertThat(result.abnormalItems()).isEmpty();
        assertThat(result.possibleAttentionPoints()).isEmpty();
        assertThat(result.plainLanguageExplanation()).contains("暂无明显异常");
        // 不得编造异常项
        assertThat(result.abnormalItems()).doesNotContain("异常");
    }

    @Test
    @DisplayName("超时：Provider 超时降级为 BusinessException 504")
    void interpret_timeout_throwsBusinessException504() {
        ResultInterpretationAIRequest request = new ResultInterpretationAIRequest(
                "血糖", "7.8 mmol/L", "3.9-6.1 mmol/L", "LAB");

        when(recorder.invoke(any(), any()))
                .thenThrow(new AIProviderException("Mock 超时", true, null));

        assertThatThrownBy(() -> service.interpret(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("AI_RESULT_INTERPRETATION_FAILED");
                    assertThat(be.getHttpStatus()).isEqualTo(504);
                });
    }

    @Test
    @DisplayName("非法 JSON：响应非法降级为 BusinessException 500")
    void interpret_invalidJson_throwsBusinessException500() {
        ResultInterpretationAIRequest request = new ResultInterpretationAIRequest(
                "血糖", "7.8 mmol/L", "3.9-6.1 mmol/L", "LAB");

        when(recorder.invoke(any(), any()))
                .thenThrow(new AIInvalidResponseException("AI 响应非合法 JSON"));

        assertThatThrownBy(() -> service.interpret(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("AI_RESULT_INTERPRETATION_FAILED");
                    assertThat(be.getHttpStatus()).isEqualTo(500);
                });
    }

    @Test
    @DisplayName("异常：Provider 异常降级为 BusinessException 504")
    void interpret_providerError_throwsBusinessException504() {
        ResultInterpretationAIRequest request = new ResultInterpretationAIRequest(
                "血糖", "7.8 mmol/L", "3.9-6.1 mmol/L", "LAB");

        when(recorder.invoke(any(), any()))
                .thenThrow(new AIProviderException("500 错误", false, 500));

        assertThatThrownBy(() -> service.interpret(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("AI_RESULT_INTERPRETATION_FAILED");
                    assertThat(be.getHttpStatus()).isEqualTo(504);
                });
    }

    @Test
    @DisplayName("缺失必填字段 followUpSuggestion 抛出 BusinessException")
    void interpret_missingRequiredField_throwsBusinessException() {
        ResultInterpretationAIRequest request = new ResultInterpretationAIRequest(
                "血糖", "7.8 mmol/L", "3.9-6.1 mmol/L", "LAB");

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, ResultInterpretationAIResult> parser = invocation.getArgument(1);
            // 缺少 followUpSuggestion 字段，validateRequired 抛出 AIInvalidResponseException
            String invalidJson = "{\"abnormalItems\":[\"血糖偏高\"],"
                    + "\"plainLanguageExplanation\":\"血糖轻度升高\","
                    + "\"possibleAttentionPoints\":[],"
                    + "\"disclaimer\":\"本解读由 AI 辅助生成\"}";
            // parser.apply 抛出异常，InvokeResult 构造不会执行
            return new AIInvocationRecorder.InvokeResult<>(parser.apply(invalidJson), true, 1L);
        });

        assertThatThrownBy(() -> service.interpret(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("AI_RESULT_INTERPRETATION_FAILED");
                    assertThat(be.getHttpStatus()).isEqualTo(500);
                });
    }
}
