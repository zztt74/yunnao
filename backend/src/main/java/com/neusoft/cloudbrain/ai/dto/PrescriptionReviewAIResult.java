package com.neusoft.cloudbrain.ai.dto;

import java.util.List;

/**
 * AI 处方审核结果
 *
 * 输出字段来自 32_AI能力契约规范.md 第3节（处方审核能力）：
 * 风险等级、过敏警告、相互作用警告、剂量警告。
 *
 * 规则：
 * - AI 输出仅供辅助参考，不能自动确认处方
 * - AI 不得降低或覆盖确定性规则命中的高风险
 * - 高风险需要医生二次确认
 */
public record PrescriptionReviewAIResult(
        String riskLevel,
        List<String> allergyWarnings,
        List<String> interactionWarnings,
        List<String> dosageWarnings,
        List<String> contraindicationWarnings,
        String suggestions,
        String disclaimer) {
}
