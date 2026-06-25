package com.neusoft.cloudbrain.prescription.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 处方 AI 审核记录响应
 *
 * 规则：
 * - 确定性规则命中不得被 AI 输出降级或覆盖
 * - ruleCheckSummary 保存确定性规则检查结果（不可被 AI 覆盖）
 * - suggestions 为 AI 补充建议
 */
public record PrescriptionReviewResponse(
        Long id,
        Long prescriptionId,
        String reviewStatus,
        String riskLevel,
        List<String> allergyWarnings,
        List<String> interactionWarnings,
        List<String> dosageWarnings,
        List<String> contraindicationWarnings,
        String suggestions,
        String ruleCheckSummary,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt) {
}
