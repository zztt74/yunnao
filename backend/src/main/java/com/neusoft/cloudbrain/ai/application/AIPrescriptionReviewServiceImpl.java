package com.neusoft.cloudbrain.ai.application;

import com.neusoft.cloudbrain.ai.api.AIPrescriptionReviewService;
import com.neusoft.cloudbrain.ai.dto.PrescriptionReviewAIRequest;
import com.neusoft.cloudbrain.ai.dto.PrescriptionReviewAIRequest.DeterministicRuleResult;
import com.neusoft.cloudbrain.ai.dto.PrescriptionReviewAIResult;
import com.neusoft.cloudbrain.ai.provider.AIProvider;
import com.neusoft.cloudbrain.ai.provider.AIProviderRequest;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 处方审核服务 Mock 实现
 *
 * 说明：此为阶段6 的最小可用实现，使用 MockAIProvider。
 * AI 能力集成角色后续将替换为真实 Provider + Prompt + Schema 校验实现。
 *
 * 规则（来自 32_AI能力契约规范.md 第3节 和 11_功能需求.md 第12.6节）：
 * - 确定性规则命中不得被 AI 输出降级或覆盖
 * - AI 只负责解释风险和补充建议
 * - 不得自动确认处方
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIPrescriptionReviewServiceImpl implements AIPrescriptionReviewService {

    private final AIProvider aiProvider;

    @Override
    public PrescriptionReviewAIResult review(PrescriptionReviewAIRequest request) {
        try {
            DeterministicRuleResult ruleResult = request.deterministicRuleResult();

            // 调用 Provider（记录调用，真实实现由 AI 集成角色提供）
            String sanitizedInput = buildSanitizedInput(request);
            AIProviderRequest providerRequest = new AIProviderRequest("prescription_review", sanitizedInput);
            aiProvider.generate(providerRequest);

            return buildMockResult(ruleResult);
        } catch (Exception e) {
            log.error("AI 处方审核调用失败: {}", e.getMessage(), e);
            throw new BusinessException(
                    "AI_PRESCRIPTION_REVIEW_FAILED",
                    "AI 处方审核服务暂时不可用，请医生手工确认",
                    504);
        }
    }

    /**
     * 基于确定性规则结果的 Mock 审核
     *
     * 关键规则：AI 不得降低确定性规则命中的风险等级
     */
    private PrescriptionReviewAIResult buildMockResult(DeterministicRuleResult ruleResult) {
        String ruleRiskLevel = ruleResult.riskLevel();
        List<String> allergyWarnings = new ArrayList<>(ruleResult.allergyWarnings());
        List<String> interactionWarnings = new ArrayList<>(ruleResult.interactionWarnings());
        List<String> dosageWarnings = new ArrayList<>(ruleResult.dosageWarnings());
        List<String> contraindicationWarnings = new ArrayList<>(ruleResult.contraindicationWarnings());

        // AI 风险等级不得低于确定性规则命中的风险等级
        String aiRiskLevel = determineAIRiskLevel(ruleRiskLevel, allergyWarnings, interactionWarnings,
                dosageWarnings, contraindicationWarnings);

        String suggestions = buildSuggestions(aiRiskLevel, allergyWarnings, interactionWarnings,
                dosageWarnings, contraindicationWarnings);

        return new PrescriptionReviewAIResult(
                aiRiskLevel,
                allergyWarnings,
                interactionWarnings,
                dosageWarnings,
                contraindicationWarnings,
                suggestions,
                "本审核由 AI 辅助生成，仅供医生参考，不能替代医生专业判断");
    }

    /**
     * 确定风险等级
     *
     * 规则：AI 不得降低确定性规则命中的风险等级
     * 等级：SAFE < LOW < MEDIUM < HIGH < CONTRAINDICATED
     */
    private String determineAIRiskLevel(String ruleRiskLevel,
                                        List<String> allergyWarnings,
                                        List<String> interactionWarnings,
                                        List<String> dosageWarnings,
                                        List<String> contraindicationWarnings) {
        // 确定性规则风险等级作为下限
        String baseLevel = ruleRiskLevel == null ? "SAFE" : ruleRiskLevel;

        // AI 补充判断（不能降低规则命中的等级）
        if (!allergyWarnings.isEmpty() || !contraindicationWarnings.isEmpty()) {
            return maxLevel(baseLevel, "CONTRAINDICATED");
        }
        if (!interactionWarnings.isEmpty()) {
            return maxLevel(baseLevel, "HIGH");
        }
        if (!dosageWarnings.isEmpty()) {
            return maxLevel(baseLevel, "MEDIUM");
        }
        return baseLevel;
    }

    /**
     * 取两个风险等级中较高的一个
     */
    private String maxLevel(String a, String b) {
        int levelA = riskOrder(a);
        int levelB = riskOrder(b);
        return levelA >= levelB ? a : b;
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

    private String buildSuggestions(String riskLevel, List<String> allergyWarnings,
                                    List<String> interactionWarnings, List<String> dosageWarnings,
                                    List<String> contraindicationWarnings) {
        List<String> parts = new ArrayList<>();
        if (!allergyWarnings.isEmpty()) {
            parts.add("存在过敏禁忌，建议更换药品。");
        }
        if (!contraindicationWarnings.isEmpty()) {
            parts.add("存在用药禁忌，建议重新评估。");
        }
        if (!interactionWarnings.isEmpty()) {
            parts.add("存在药物相互作用，建议调整用药方案。");
        }
        if (!dosageWarnings.isEmpty()) {
            parts.add("存在剂量异常，建议核实剂量。");
        }
        if (parts.isEmpty()) {
            parts.add("处方用药基本合理，请医生结合临床最终确认。");
        }
        parts.add("风险等级：" + riskLevel + "。");
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
        }
        return sb.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
