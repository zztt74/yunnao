package com.neusoft.cloudbrain.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.neusoft.cloudbrain.ai.api.AIPrescriptionReviewService;
import com.neusoft.cloudbrain.ai.dto.PrescriptionReviewAIRequest;
import com.neusoft.cloudbrain.ai.dto.PrescriptionReviewAIRequest.DeterministicRuleResult;
import com.neusoft.cloudbrain.ai.dto.PrescriptionReviewAIResult;
import com.neusoft.cloudbrain.ai.exception.AIInvalidResponseException;
import com.neusoft.cloudbrain.ai.exception.AIProviderException;
import com.neusoft.cloudbrain.ai.parser.JsonSchemaParser;
import com.neusoft.cloudbrain.ai.prompt.PromptManager;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * AI 处方审核服务实现
 *
 * 重构后（任务 STAGE-AI-5）：
 * - 通过 AIInvocationRecorder 统一调用 Provider（含重试和调用记录）
 * - 通过 JsonSchemaParser 解析和校验 AI 响应（6 字段 Schema）
 * - 通过 PromptManager 获取 system prompt 和版本
 * - 关键词 Mock 逻辑已下沉到 MockAIProvider
 * - 确定性规则结果与 AI 结果合并，AI 不得降低规则命中的风险等级
 * - 冲突检测：AI 风险等级低于规则命中等级时，以规则为准，并记录冲突
 *
 * 规则（来自 13_AI能力集成AI任务书.md 第3.4节、32_AI能力契约规范.md 第3节）：
 * - 输入必须包含后端确定性规则检查结果
 * - 确定性规则命中不得被 AI 输出降级或覆盖
 * - AI 只负责解释风险和补充建议
 * - 不得自动确认处方（无 approved 状态产出）
 * - AI 结果与规则结果冲突时以规则为准，并记录冲突
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIPrescriptionReviewServiceImpl implements AIPrescriptionReviewService {

    private static final String CAPABILITY = "prescription_review";
    private static final Set<String> ALLOWED_RISK_LEVELS =
            Set.of("SAFE", "LOW", "MEDIUM", "HIGH", "CONTRAINDICATED");

    private final AIInvocationRecorder recorder;
    private final JsonSchemaParser jsonSchemaParser;
    private final PromptManager promptManager;

    @Override
    public PrescriptionReviewAIResult review(PrescriptionReviewAIRequest request) {
        DeterministicRuleResult ruleResult = request.deterministicRuleResult();
        String sanitizedInput = buildSanitizedInput(request);

        AIInvocationRecorder.InvocationSpec spec = new AIInvocationRecorder.InvocationSpec(
                CAPABILITY, CAPABILITY, null, null,
                sanitizedInput,
                promptManager.getPrompt(CAPABILITY),
                promptManager.getPromptVersion(CAPABILITY));

        try {
            AIInvocationRecorder.InvokeResult<PrescriptionReviewAIResult> result =
                    recorder.invoke(spec, this::parsePrescriptionReviewResponse);
            return mergeWithRuleResult(result.result(), ruleResult);
        } catch (AIProviderException e) {
            throw new BusinessException(
                    "AI_PRESCRIPTION_REVIEW_FAILED",
                    "AI 处方审核服务暂时不可用，请医生手工确认",
                    504);
        } catch (AIInvalidResponseException e) {
            throw new BusinessException(
                    "AI_PRESCRIPTION_REVIEW_FAILED",
                    "AI 处方审核响应异常: " + e.getMessage(),
                    500);
        }
    }

    /**
     * 解析处方审核响应（由 AIInvocationRecorder 调用）
     *
     * Schema（6 字段，来自 13_AI能力集成AI任务书.md 第3.4节）：
     * riskLevel, allergyWarnings, interactionWarnings, dosageWarnings, recommendations, summary
     */
    private PrescriptionReviewAIResult parsePrescriptionReviewResponse(String content) {
        JsonNode node = jsonSchemaParser.parse(content);
        jsonSchemaParser.validateRequired(node, "riskLevel", "recommendations", "summary");
        jsonSchemaParser.validateEnum(node, "riskLevel", ALLOWED_RISK_LEVELS);

        return new PrescriptionReviewAIResult(
                node.get("riskLevel").asText(),
                jsonSchemaParser.parseStringArray(node, "allergyWarnings"),
                jsonSchemaParser.parseStringArray(node, "interactionWarnings"),
                jsonSchemaParser.parseStringArray(node, "dosageWarnings"),
                node.get("recommendations").asText(),
                node.get("summary").asText());
    }

    /**
     * 合并 AI 结果与确定性规则结果
     *
     * 关键规则（来自 13_AI能力集成AI任务书.md 第3.4节）：
     * - AI 风险等级不得低于规则命中等级（沿用 determineAIRiskLevel / maxLevel 逻辑）
     * - AI 结果与规则冲突时以规则为准，并记录冲突
     */
    private PrescriptionReviewAIResult mergeWithRuleResult(
            PrescriptionReviewAIResult aiResult, DeterministicRuleResult ruleResult) {
        if (ruleResult == null) {
            return aiResult;
        }

        String ruleRiskLevel = ruleResult.riskLevel() == null ? "SAFE" : ruleResult.riskLevel();
        String aiRiskLevel = aiResult.riskLevel();
        String mergedRiskLevel = maxLevel(ruleRiskLevel, aiRiskLevel);

        // 冲突检测：AI 风险等级低于规则命中等级，记录冲突
        boolean conflict = riskOrder(ruleRiskLevel) > riskOrder(aiRiskLevel);
        if (conflict) {
            log.warn("处方审核冲突：AI 风险等级 {} 低于规则命中等级 {}，以规则为准。ruleSummary={}",
                    aiRiskLevel, ruleRiskLevel, ruleResult.summary());
        }

        List<String> allergyWarnings = mergeLists(
                ruleResult.allergyWarnings(), aiResult.allergyWarnings());
        List<String> interactionWarnings = mergeLists(
                ruleResult.interactionWarnings(), aiResult.interactionWarnings());
        List<String> dosageWarnings = mergeLists(
                ruleResult.dosageWarnings(), aiResult.dosageWarnings());

        String recommendations = aiResult.recommendations();
        if (recommendations == null || recommendations.isBlank()) {
            recommendations = buildRecommendations(mergedRiskLevel, allergyWarnings,
                    interactionWarnings, dosageWarnings);
        }

        // summary 合并：规则 summary + 冲突记录 + AI summary
        String summary = buildMergedSummary(ruleResult, aiResult, conflict,
                ruleRiskLevel, aiRiskLevel, mergedRiskLevel);

        return new PrescriptionReviewAIResult(
                mergedRiskLevel,
                allergyWarnings,
                interactionWarnings,
                dosageWarnings,
                recommendations,
                summary);
    }

    /**
     * 合并两个列表（去重，保持顺序）
     */
    private List<String> mergeLists(List<String> rule, List<String> ai) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (rule != null) {
            merged.addAll(rule);
        }
        if (ai != null) {
            merged.addAll(ai);
        }
        return new ArrayList<>(merged);
    }

    /**
     * 取两个风险等级中较高的一个
     * 等级：SAFE < LOW < MEDIUM < HIGH < CONTRAINDICATED
     */
    private String maxLevel(String a, String b) {
        return riskOrder(a) >= riskOrder(b) ? a : b;
    }

    private int riskOrder(String level) {
        return switch (level == null ? "SAFE" : level) {
            case "SAFE" -> 0;
            case "LOW" -> 1;
            case "MEDIUM" -> 2;
            case "HIGH" -> 3;
            case "CONTRAINDICATED" -> 4;
            default -> 0;
        };
    }

    private String buildRecommendations(String riskLevel, List<String> allergyWarnings,
                                        List<String> interactionWarnings, List<String> dosageWarnings) {
        List<String> parts = new ArrayList<>();
        if (allergyWarnings != null && !allergyWarnings.isEmpty()) {
            parts.add("存在过敏禁忌，建议更换药品。");
        }
        if (interactionWarnings != null && !interactionWarnings.isEmpty()) {
            parts.add("存在药物相互作用，建议调整用药方案。");
        }
        if (dosageWarnings != null && !dosageWarnings.isEmpty()) {
            parts.add("存在剂量异常，建议核实剂量。");
        }
        if (parts.isEmpty()) {
            parts.add("处方用药基本合理，请医生结合临床最终确认。");
        }
        parts.add("风险等级：" + riskLevel + "。");
        return String.join(" ", parts);
    }

    /**
     * 构建合并后的 summary，包含冲突记录
     *
     * 冲突记录格式（可追溯，写入 summary 供 AIInvocation 记录）：
     * "[冲突] AI 风险等级 X 低于规则命中等级 Y，已以规则为准"
     */
    private String buildMergedSummary(DeterministicRuleResult ruleResult,
                                      PrescriptionReviewAIResult aiResult,
                                      boolean conflict,
                                      String ruleRiskLevel,
                                      String aiRiskLevel,
                                      String mergedRiskLevel) {
        List<String> parts = new ArrayList<>();
        if (ruleResult.summary() != null && !ruleResult.summary().isBlank()) {
            parts.add("规则结果：" + ruleResult.summary());
        }
        if (aiResult.summary() != null && !aiResult.summary().isBlank()) {
            parts.add("AI 解读：" + aiResult.summary());
        }
        if (conflict) {
            parts.add("[冲突] AI 风险等级 " + aiRiskLevel
                    + " 低于规则命中等级 " + ruleRiskLevel
                    + "，已以规则为准，最终风险等级 " + mergedRiskLevel);
        }
        parts.add("最终风险等级：" + mergedRiskLevel + "。");
        return String.join(" ", parts);
    }

    private String buildSanitizedInput(PrescriptionReviewAIRequest request) {
        StringBuilder sb = new StringBuilder("处方审核: ");
        if (request.items() != null) {
            for (PrescriptionReviewAIRequest.PrescriptionItemInfo item : request.items()) {
                sb.append(String.format("[%s %s %s %s天] ",
                        safe(item.drugName()), safe(item.dosage()),
                        safe(item.frequency()), item.duration()));
            }
        }
        sb.append("过敏史: ").append(safe(request.patientAllergies())).append("; ");
        if (request.deterministicRuleResult() != null) {
            sb.append("规则风险: ").append(safe(request.deterministicRuleResult().riskLevel()));
            if (request.deterministicRuleResult().summary() != null) {
                sb.append("; 规则摘要: ").append(request.deterministicRuleResult().summary());
            }
        }
        return sb.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
