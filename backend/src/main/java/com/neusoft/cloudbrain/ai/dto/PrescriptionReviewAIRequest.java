package com.neusoft.cloudbrain.ai.dto;

import java.util.List;

/**
 * AI 处方审核请求
 *
 * 输入字段来自 32_AI能力契约规范.md 第3节（处方审核能力）：
 * 药品明细、患者过敏史、确定性规则检查结果。
 *
 * 规则：
 * - 后端先执行确定性规则检查，再将规则结果与最小必要上下文提交给 AI
 * - 确定性规则命中不得被 AI 输出降级或覆盖
 * - AI 只负责解释风险和补充建议
 * - 不包含患者 ID、姓名、手机号等隐私信息（最小化原则）
 */
public record PrescriptionReviewAIRequest(
        List<PrescriptionItemInfo> items,
        String patientAllergies,
        DeterministicRuleResult deterministicRuleResult) {

    /**
     * 处方药品明细信息（最小化，不含隐私）
     */
    public record PrescriptionItemInfo(
            String drugCode,
            String drugName,
            String dosage,
            String frequency,
            Integer duration) {
    }

    /**
     * 确定性规则检查结果（AI 不得降级）
     */
    public record DeterministicRuleResult(
            String riskLevel,
            List<String> allergyWarnings,
            List<String> interactionWarnings,
            List<String> dosageWarnings,
            List<String> contraindicationWarnings,
            String summary) {
    }
}
