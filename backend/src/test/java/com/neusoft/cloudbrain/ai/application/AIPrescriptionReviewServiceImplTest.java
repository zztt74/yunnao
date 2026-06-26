package com.neusoft.cloudbrain.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neusoft.cloudbrain.ai.dto.PrescriptionReviewAIRequest;
import com.neusoft.cloudbrain.ai.dto.PrescriptionReviewAIResult;
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
 * AIPrescriptionReviewServiceImpl 单元测试
 *
 * 覆盖（来自任务 STAGE-AI-1 验收条件、11_功能需求.md 第12.6节）：
 * - 正常调用返回审核结果
 * - 确定性规则风险等级不被 AI 降低
 * - Provider 异常降级为 BusinessException
 * - 非法 riskLevel 枚举值抛出异常
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AIPrescriptionReviewServiceImpl - 处方审核服务测试")
class AIPrescriptionReviewServiceImplTest {

    @Mock
    private AIInvocationRecorder recorder;

    @Mock
    private PromptManager promptManager;

    private AIPrescriptionReviewServiceImpl service;

    @BeforeEach
    void setUp() {
        JsonSchemaParser parser = new JsonSchemaParser(new ObjectMapper());
        service = new AIPrescriptionReviewServiceImpl(recorder, parser, promptManager);
        when(promptManager.getPrompt(anyString())).thenReturn("system prompt");
        when(promptManager.getPromptVersion(anyString())).thenReturn("v1");
    }

    @Test
    @DisplayName("正常调用：安全处方返回 SAFE")
    void review_safePrescription_returnsSafe() {
        PrescriptionReviewAIRequest request = new PrescriptionReviewAIRequest(
                List.of(new PrescriptionReviewAIRequest.PrescriptionItemInfo(
                        "D001", "阿莫西林", "0.5g", "每日三次", 7)),
                "无",
                new PrescriptionReviewAIRequest.DeterministicRuleResult(
                        "SAFE", List.of(), List.of(), List.of(), List.of(), null));

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, PrescriptionReviewAIResult> parser = invocation.getArgument(1);
            String json = "{\"riskLevel\":\"SAFE\",\"allergyWarnings\":[],"
                    + "\"interactionWarnings\":[],\"dosageWarnings\":[],"
                    + "\"contraindicationWarnings\":[],\"suggestions\":\"合理\","
                    + "\"disclaimer\":\"仅供医生参考\"}";
            return new AIInvocationRecorder.InvokeResult<>(parser.apply(json), true, 1L);
        });

        PrescriptionReviewAIResult result = service.review(request);

        assertThat(result.riskLevel()).isEqualTo("SAFE");
    }

    @Test
    @DisplayName("确定性规则 HIGH 风险不被 AI 的 SAFE 降级")
    void review_ruleHighRisk_notDowngradedByAI() {
        PrescriptionReviewAIRequest request = new PrescriptionReviewAIRequest(
                List.of(new PrescriptionReviewAIRequest.PrescriptionItemInfo(
                        "D002", "青霉素", "0.5g", "每日两次", 5)),
                "青霉素过敏",
                new PrescriptionReviewAIRequest.DeterministicRuleResult(
                        "HIGH",
                        List.of("青霉素过敏"),
                        List.of(),
                        List.of(),
                        List.of(),
                        null));

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, PrescriptionReviewAIResult> parser = invocation.getArgument(1);
            // AI 返回 SAFE，但应被规则结果提升到 HIGH
            String json = "{\"riskLevel\":\"SAFE\",\"allergyWarnings\":[],"
                    + "\"interactionWarnings\":[],\"dosageWarnings\":[],"
                    + "\"contraindicationWarnings\":[],\"suggestions\":\"\","
                    + "\"disclaimer\":\"仅供医生参考\"}";
            return new AIInvocationRecorder.InvokeResult<>(parser.apply(json), true, 1L);
        });

        PrescriptionReviewAIResult result = service.review(request);

        assertThat(result.riskLevel()).isEqualTo("HIGH");
        assertThat(result.allergyWarnings()).contains("青霉素过敏");
    }

    @Test
    @DisplayName("Provider 异常降级为 BusinessException 504")
    void review_providerError_throwsBusinessException() {
        PrescriptionReviewAIRequest request = new PrescriptionReviewAIRequest(
                List.of(), "无", null);

        when(recorder.invoke(any(), any()))
                .thenThrow(new AIProviderException("超时", true, null));

        assertThatThrownBy(() -> service.review(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("AI_PRESCRIPTION_REVIEW_FAILED");
                    assertThat(be.getHttpStatus()).isEqualTo(504);
                });
    }

    @Test
    @DisplayName("非法 riskLevel 枚举值抛出 BusinessException")
    void review_invalidRiskLevel_throwsBusinessException() {
        PrescriptionReviewAIRequest request = new PrescriptionReviewAIRequest(
                List.of(), "无", null);

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, PrescriptionReviewAIResult> parser = invocation.getArgument(1);
            String json = "{\"riskLevel\":\"CRITICAL\",\"allergyWarnings\":[],"
                    + "\"disclaimer\":\"仅供医生参考\"}";
            try {
                parser.apply(json);
                throw new IllegalStateException("应该抛出异常");
            } catch (com.neusoft.cloudbrain.ai.exception.AIInvalidResponseException e) {
                throw e;
            }
        });

        assertThatThrownBy(() -> service.review(request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("无确定性规则结果时直接使用 AI 结果")
    void review_noRuleResult_usesAIResult() {
        PrescriptionReviewAIRequest request = new PrescriptionReviewAIRequest(
                List.of(new PrescriptionReviewAIRequest.PrescriptionItemInfo(
                        "D003", "布洛芬", "0.3g", "每日两次", 3)),
                "无",
                null);

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, PrescriptionReviewAIResult> parser = invocation.getArgument(1);
            String json = "{\"riskLevel\":\"LOW\",\"allergyWarnings\":[],"
                    + "\"interactionWarnings\":[],\"dosageWarnings\":[\"剂量偏大\"],"
                    + "\"contraindicationWarnings\":[],\"suggestions\":\"核实剂量\","
                    + "\"disclaimer\":\"仅供医生参考\"}";
            return new AIInvocationRecorder.InvokeResult<>(parser.apply(json), true, 1L);
        });

        PrescriptionReviewAIResult result = service.review(request);

        assertThat(result.riskLevel()).isEqualTo("LOW");
        assertThat(result.dosageWarnings()).contains("剂量偏大");
    }
}
