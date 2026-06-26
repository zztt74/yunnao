package com.neusoft.cloudbrain.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neusoft.cloudbrain.ai.dto.PrescriptionReviewAIRequest;
import com.neusoft.cloudbrain.ai.dto.PrescriptionReviewAIRequest.DeterministicRuleResult;
import com.neusoft.cloudbrain.ai.dto.PrescriptionReviewAIResult;
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
 * AIPrescriptionReviewServiceImpl 单元测试
 *
 * 覆盖（来自任务 STAGE-AI-5 验收条件，7 Mock 场景 + 风险不降级 + 冲突记录）：
 * - 正常：安全处方返回 SAFE（6 字段）
 * - 禁忌（CONTRAINDICATED）：规则命中 CONTRAINDICATED，AI 不得降为 HIGH
 * - 相互作用（HIGH）：规则命中 HIGH，AI 不得降为 MEDIUM
 * - 剂量异常（MEDIUM）：规则命中 MEDIUM，AI 不得降为 LOW
 * - 超时：Provider 超时降级为 BusinessException 504
 * - 非法 JSON：响应非法降级为 BusinessException 500
 * - 异常：Provider 异常降级为 BusinessException 504
 * - 风险不降级：规则风险等级作为下限，AI 不得降低
 * - 冲突记录：AI 风险等级低于规则命中等级时，summary 中记录冲突
 * - 不得自动确认：DTO 无 approved 状态字段
 *
 * 输出 Schema（来自 13_AI能力集成AI任务书.md 第3.4节，6 字段）：
 * riskLevel、allergyWarnings、interactionWarnings、dosageWarnings、recommendations、summary
 *
 * 风险等级枚举：SAFE < LOW < MEDIUM < HIGH < CONTRAINDICATED
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
    @DisplayName("正常：安全处方返回 SAFE（6 字段）")
    void review_safePrescription_returnsSafe() {
        PrescriptionReviewAIRequest request = new PrescriptionReviewAIRequest(
                List.of(new PrescriptionReviewAIRequest.PrescriptionItemInfo(
                        "D001", "阿莫西林", "0.5g", "每日三次", 7)),
                "无",
                new DeterministicRuleResult(
                        "SAFE", List.of(), List.of(), List.of(), List.of(), null));

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, PrescriptionReviewAIResult> parser = invocation.getArgument(1);
            String json = "{\"riskLevel\":\"SAFE\",\"allergyWarnings\":[],"
                    + "\"interactionWarnings\":[],\"dosageWarnings\":[],"
                    + "\"recommendations\":\"处方用药基本合理\",\"summary\":\"审核通过\"}";
            return new AIInvocationRecorder.InvokeResult<>(parser.apply(json), true, 1L);
        });

        PrescriptionReviewAIResult result = service.review(request);

        assertThat(result.riskLevel()).isEqualTo("SAFE");
        assertThat(result.allergyWarnings()).isEmpty();
        assertThat(result.recommendations()).isEqualTo("处方用药基本合理");
        // summary 经合并逻辑处理（规则结果 + AI summary + 最终风险等级）
        assertThat(result.summary()).contains("审核通过");
        assertThat(result.summary()).contains("最终风险等级：SAFE");
        // 向后兼容访问器
        assertThat(result.suggestions()).isEqualTo("处方用药基本合理");
        assertThat(result.contraindicationWarnings()).isEmpty();
        assertThat(result.disclaimer()).contains("仅供医生参考");
    }

    @Test
    @DisplayName("禁忌（CONTRAINDICATED）：规则命中 CONTRAINDICATED，AI 不得降为 HIGH")
    void review_contraindicated_notDowngradedToHigh() {
        PrescriptionReviewAIRequest request = new PrescriptionReviewAIRequest(
                List.of(new PrescriptionReviewAIRequest.PrescriptionItemInfo(
                        "D002", "青霉素", "0.5g", "每日两次", 5)),
                "青霉素过敏",
                new DeterministicRuleResult(
                        "CONTRAINDICATED",
                        List.of("青霉素过敏禁忌"),
                        List.of(),
                        List.of(),
                        List.of("青霉素绝对禁忌"),
                        "禁忌：青霉素过敏"));

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, PrescriptionReviewAIResult> parser = invocation.getArgument(1);
            // AI 尝试降级为 HIGH，应被规则提升到 CONTRAINDICATED
            String json = "{\"riskLevel\":\"HIGH\",\"allergyWarnings\":[],"
                    + "\"interactionWarnings\":[],\"dosageWarnings\":[],"
                    + "\"recommendations\":\"\",\"summary\":\"AI 认为高风险\"}";
            return new AIInvocationRecorder.InvokeResult<>(parser.apply(json), true, 1L);
        });

        PrescriptionReviewAIResult result = service.review(request);

        // 禁忌场景 riskLevel ≥ CONTRAINDICATED，AI 不得降为 HIGH（验收条件）
        assertThat(result.riskLevel()).isEqualTo("CONTRAINDICATED");
        assertThat(result.allergyWarnings()).contains("青霉素过敏禁忌");
        // 冲突被记录（summary 含冲突标记）
        assertThat(result.summary()).contains("[冲突]");
        assertThat(result.summary()).contains("CONTRAINDICATED");
    }

    @Test
    @DisplayName("相互作用（HIGH）：规则命中 HIGH，AI 不得降为 MEDIUM")
    void review_interactionHigh_notDowngradedToMedium() {
        PrescriptionReviewAIRequest request = new PrescriptionReviewAIRequest(
                List.of(new PrescriptionReviewAIRequest.PrescriptionItemInfo(
                        "D003", "华法林", "2.5mg", "每日一次", 30)),
                "无",
                new DeterministicRuleResult(
                        "HIGH",
                        List.of(),
                        List.of("华法林与阿司匹林相互作用"),
                        List.of(),
                        List.of(),
                        "相互作用：华法林+阿司匹林"));

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, PrescriptionReviewAIResult> parser = invocation.getArgument(1);
            // AI 尝试降级为 MEDIUM，应被规则提升到 HIGH
            String json = "{\"riskLevel\":\"MEDIUM\",\"allergyWarnings\":[],"
                    + "\"interactionWarnings\":[],\"dosageWarnings\":[],"
                    + "\"recommendations\":\"\",\"summary\":\"AI 认为中等风险\"}";
            return new AIInvocationRecorder.InvokeResult<>(parser.apply(json), true, 1L);
        });

        PrescriptionReviewAIResult result = service.review(request);

        // 规则命中 HIGH，AI 不得降为 MEDIUM
        assertThat(result.riskLevel()).isEqualTo("HIGH");
        assertThat(result.interactionWarnings()).contains("华法林与阿司匹林相互作用");
        // 冲突被记录
        assertThat(result.summary()).contains("[冲突]");
    }

    @Test
    @DisplayName("剂量异常（MEDIUM）：规则命中 MEDIUM，AI 不得降为 LOW")
    void review_dosageMedium_notDowngradedToLow() {
        PrescriptionReviewAIRequest request = new PrescriptionReviewAIRequest(
                List.of(new PrescriptionReviewAIRequest.PrescriptionItemInfo(
                        "D004", "二甲双胍", "2g", "每日三次", 14)),
                "无",
                new DeterministicRuleResult(
                        "MEDIUM",
                        List.of(),
                        List.of(),
                        List.of("单次剂量超过常规上限"),
                        List.of(),
                        "剂量异常：二甲双胍超量"));

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, PrescriptionReviewAIResult> parser = invocation.getArgument(1);
            // AI 尝试降级为 LOW，应被规则提升到 MEDIUM
            String json = "{\"riskLevel\":\"LOW\",\"allergyWarnings\":[],"
                    + "\"interactionWarnings\":[],\"dosageWarnings\":[],"
                    + "\"recommendations\":\"\",\"summary\":\"AI 认为低风险\"}";
            return new AIInvocationRecorder.InvokeResult<>(parser.apply(json), true, 1L);
        });

        PrescriptionReviewAIResult result = service.review(request);

        // 规则命中 MEDIUM 时 AI 不得返回 LOW（验收条件）
        assertThat(result.riskLevel()).isEqualTo("MEDIUM");
        assertThat(result.dosageWarnings()).contains("单次剂量超过常规上限");
        // 冲突被记录
        assertThat(result.summary()).contains("[冲突]");
    }

    @Test
    @DisplayName("超时：Provider 超时降级为 BusinessException 504")
    void review_timeout_throwsBusinessException504() {
        PrescriptionReviewAIRequest request = new PrescriptionReviewAIRequest(
                List.of(), "无",
                new DeterministicRuleResult("SAFE", List.of(), List.of(), List.of(), List.of(), null));

        when(recorder.invoke(any(), any()))
                .thenThrow(new AIProviderException("Mock 超时", true, null));

        assertThatThrownBy(() -> service.review(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("AI_PRESCRIPTION_REVIEW_FAILED");
                    assertThat(be.getHttpStatus()).isEqualTo(504);
                });
    }

    @Test
    @DisplayName("非法 JSON：响应非法降级为 BusinessException 500")
    void review_invalidJson_throwsBusinessException500() {
        PrescriptionReviewAIRequest request = new PrescriptionReviewAIRequest(
                List.of(), "无",
                new DeterministicRuleResult("SAFE", List.of(), List.of(), List.of(), List.of(), null));

        when(recorder.invoke(any(), any()))
                .thenThrow(new AIInvalidResponseException("AI 响应非合法 JSON"));

        assertThatThrownBy(() -> service.review(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("AI_PRESCRIPTION_REVIEW_FAILED");
                    assertThat(be.getHttpStatus()).isEqualTo(500);
                });
    }

    @Test
    @DisplayName("异常：Provider 异常降级为 BusinessException 504")
    void review_providerError_throwsBusinessException504() {
        PrescriptionReviewAIRequest request = new PrescriptionReviewAIRequest(
                List.of(), "无",
                new DeterministicRuleResult("SAFE", List.of(), List.of(), List.of(), List.of(), null));

        when(recorder.invoke(any(), any()))
                .thenThrow(new AIProviderException("500 错误", false, 500));

        assertThatThrownBy(() -> service.review(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("AI_PRESCRIPTION_REVIEW_FAILED");
                    assertThat(be.getHttpStatus()).isEqualTo(504);
                });
    }

    @Test
    @DisplayName("非法 riskLevel 枚举值降级为 BusinessException")
    void review_invalidRiskLevel_throwsBusinessException() {
        PrescriptionReviewAIRequest request = new PrescriptionReviewAIRequest(
                List.of(), "无",
                new DeterministicRuleResult("SAFE", List.of(), List.of(), List.of(), List.of(), null));

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, PrescriptionReviewAIResult> parser = invocation.getArgument(1);
            // riskLevel=CRITICAL 非合法枚举
            String json = "{\"riskLevel\":\"CRITICAL\",\"allergyWarnings\":[],"
                    + "\"recommendations\":\"\",\"summary\":\"\"}";
            return new AIInvocationRecorder.InvokeResult<>(parser.apply(json), true, 1L);
        });

        assertThatThrownBy(() -> service.review(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("AI_PRESCRIPTION_REVIEW_FAILED");
                    assertThat(be.getHttpStatus()).isEqualTo(500);
                });
    }

    @Test
    @DisplayName("无确定性规则结果时直接使用 AI 结果")
    void review_noRuleResult_usesAIResult() {
        PrescriptionReviewAIRequest request = new PrescriptionReviewAIRequest(
                List.of(new PrescriptionReviewAIRequest.PrescriptionItemInfo(
                        "D005", "布洛芬", "0.3g", "每日两次", 3)),
                "无",
                null);

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, PrescriptionReviewAIResult> parser = invocation.getArgument(1);
            String json = "{\"riskLevel\":\"LOW\",\"allergyWarnings\":[],"
                    + "\"interactionWarnings\":[],\"dosageWarnings\":[\"剂量偏大\"],"
                    + "\"recommendations\":\"核实剂量\",\"summary\":\"剂量偏大\"}";
            return new AIInvocationRecorder.InvokeResult<>(parser.apply(json), true, 1L);
        });

        PrescriptionReviewAIResult result = service.review(request);

        assertThat(result.riskLevel()).isEqualTo("LOW");
        assertThat(result.dosageWarnings()).contains("剂量偏大");
        assertThat(result.recommendations()).isEqualTo("核实剂量");
        // 无规则结果时 summary 保持 AI 原值
        assertThat(result.summary()).isEqualTo("剂量偏大");
    }

    @Test
    @DisplayName("不得自动确认处方：DTO 无 approved 状态字段")
    void review_noApprovedStatus_inDTO() {
        PrescriptionReviewAIRequest request = new PrescriptionReviewAIRequest(
                List.of(), "无",
                new DeterministicRuleResult("SAFE", List.of(), List.of(), List.of(), List.of(), null));

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, PrescriptionReviewAIResult> parser = invocation.getArgument(1);
            String json = "{\"riskLevel\":\"SAFE\",\"allergyWarnings\":[],"
                    + "\"interactionWarnings\":[],\"dosageWarnings\":[],"
                    + "\"recommendations\":\"合理\",\"summary\":\"通过\"}";
            return new AIInvocationRecorder.InvokeResult<>(parser.apply(json), true, 1L);
        });

        PrescriptionReviewAIResult result = service.review(request);

        // DTO 仅 6 字段，无 approved / status / confirmed 等确认字段
        assertThat(result).hasFieldOrProperty("riskLevel");
        assertThat(result).hasFieldOrProperty("allergyWarnings");
        assertThat(result).hasFieldOrProperty("interactionWarnings");
        assertThat(result).hasFieldOrProperty("dosageWarnings");
        assertThat(result).hasFieldOrProperty("recommendations");
        assertThat(result).hasFieldOrProperty("summary");
        assertThat(result.getClass().getRecordComponents()).hasSize(6);
    }

    @Test
    @DisplayName("冲突记录：AI 风险等级低于规则命中等级时 summary 含冲突标记")
    void review_conflict_recordedInSummary() {
        PrescriptionReviewAIRequest request = new PrescriptionReviewAIRequest(
                List.of(), "无",
                new DeterministicRuleResult(
                        "HIGH",
                        List.of("过敏禁忌"),
                        List.of(),
                        List.of(),
                        List.of(),
                        "规则命中 HIGH"));

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, PrescriptionReviewAIResult> parser = invocation.getArgument(1);
            // AI 返回 SAFE，冲突明显
            String json = "{\"riskLevel\":\"SAFE\",\"allergyWarnings\":[],"
                    + "\"interactionWarnings\":[],\"dosageWarnings\":[],"
                    + "\"recommendations\":\"\",\"summary\":\"AI 认为安全\"}";
            return new AIInvocationRecorder.InvokeResult<>(parser.apply(json), true, 1L);
        });

        PrescriptionReviewAIResult result = service.review(request);

        // 冲突被记录可查（AIInvocation 可追溯）
        assertThat(result.riskLevel()).isEqualTo("HIGH");
        assertThat(result.summary()).contains("[冲突]");
        assertThat(result.summary()).contains("SAFE");
        assertThat(result.summary()).contains("HIGH");
        assertThat(result.summary()).contains("规则命中 HIGH");
    }

    @Test
    @DisplayName("缺失必填字段 recommendations 抛出 BusinessException")
    void review_missingRecommendations_throwsBusinessException() {
        PrescriptionReviewAIRequest request = new PrescriptionReviewAIRequest(
                List.of(), "无",
                new DeterministicRuleResult("SAFE", List.of(), List.of(), List.of(), List.of(), null));

        when(recorder.invoke(any(), any())).thenAnswer(invocation -> {
            java.util.function.Function<String, PrescriptionReviewAIResult> parser = invocation.getArgument(1);
            // 缺少 recommendations 和 summary
            String invalidJson = "{\"riskLevel\":\"SAFE\",\"allergyWarnings\":[]}";
            return new AIInvocationRecorder.InvokeResult<>(parser.apply(invalidJson), true, 1L);
        });

        assertThatThrownBy(() -> service.review(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("AI_PRESCRIPTION_REVIEW_FAILED");
                    assertThat(be.getHttpStatus()).isEqualTo(500);
                });
    }
}
