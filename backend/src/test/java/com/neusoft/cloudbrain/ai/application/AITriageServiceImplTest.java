package com.neusoft.cloudbrain.ai.application;

import com.neusoft.cloudbrain.ai.dto.TriageAIRequest;
import com.neusoft.cloudbrain.ai.dto.TriageAIResult;
import com.neusoft.cloudbrain.ai.exception.AIProviderException;
import com.neusoft.cloudbrain.ai.parser.JsonSchemaParser;
import com.neusoft.cloudbrain.ai.prompt.PromptManager;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * AITriageServiceImpl 单元测试
 *
 * 覆盖（来自任务 STAGE-AI-1 验收条件）：
 * - 正常调用返回结构化分诊结果
 * - Provider 异常降级为 BusinessException
 * - 响应非法时抛出 BusinessException
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

    @Test
    @DisplayName("正常调用：胸痛分诊到心血管内科")
    void analyze_chestPain_returnsCardiology() {
        TriageAIRequest request = new TriageAIRequest(
                "40-50", "MALE", "胸痛 2 小时", "2小时", "伴出汗");

        when(recorder.invoke(any(), any()))
                .thenReturn(new AIInvocationRecorder.InvokeResult<>(
                        new TriageAIResult(
                                "DEPT_CARDIOLOGY", "HIGH",
                                java.util.List.of("胸痛"),
                                "心血管问题",
                                "本结果由 AI 辅助生成，仅供参考",
                                true),
                        true, 1L));

        TriageAIResult result = service.analyze(request);

        assertThat(result.departmentCode()).isEqualTo("DEPT_CARDIOLOGY");
        assertThat(result.priority()).isEqualTo("HIGH");
        assertThat(result.emergencySuggested()).isTrue();
    }

    @Test
    @DisplayName("Provider 异常降级为 BusinessException 504")
    void analyze_providerError_throwsBusinessException() {
        TriageAIRequest request = new TriageAIRequest(
                "30-40", "FEMALE", "头痛", null, null);

        when(recorder.invoke(any(), any()))
                .thenThrow(new AIProviderException("超时", true, null));

        assertThatThrownBy(() -> service.analyze(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("AI_TRIAGE_FAILED");
                    assertThat(be.getHttpStatus()).isEqualTo(504);
                });
    }

    @Test
    @DisplayName("解析器内部将 JSON 解析为 TriageAIResult")
    void analyze_validJson_parsedCorrectly() {
        TriageAIRequest request = new TriageAIRequest(
                "20-30", "MALE", "发热咳嗽", "1天", null);

        String validJson = "{\"departmentCode\":\"DEPT_INTERNAL\",\"priority\":\"MEDIUM\","
                + "\"symptomKeywords\":[\"发热\",\"咳嗽\"],"
                + "\"reason\":\"呼吸道感染\",\"safetyNotice\":\"仅供参考\","
                + "\"emergencySuggested\":false}";

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, TriageAIResult> parser = invocation.getArgument(1);
            TriageAIResult parsed = parser.apply(validJson);
            return new AIInvocationRecorder.InvokeResult<>(parsed, true, 1L);
        });

        TriageAIResult result = service.analyze(request);

        assertThat(result.departmentCode()).isEqualTo("DEPT_INTERNAL");
        assertThat(result.symptomKeywords()).containsExactly("发热", "咳嗽");
    }
}
