package com.neusoft.cloudbrain.prescription.dto;

import java.util.List;

/**
 * 处方审核结果响应（B4 兼容接口）
 *
 * 聚合处方状态、AI 审核状态、风险等级、建议、警告、规则检查摘要。
 * 课程任务四要求医生点击"AI 辅助审核"后展示用药建议、相互作用检测结果和风险提示。
 */
public record PrescriptionCheckResponse(
        Long prescriptionId,
        Long encounterId,
        String prescriptionStatus,
        String aiReviewStatus,
        String riskLevel,
        List<String> allergyWarnings,
        List<String> interactionWarnings,
        List<String> dosageWarnings,
        List<String> contraindicationWarnings,
        String suggestions,
        String summary,
        String ruleCheckSummary) {

    /**
     * 从 PrescriptionResponse 提取审核结果
     */
    public static PrescriptionCheckResponse from(PrescriptionResponse p) {
        PrescriptionReviewResponse r = p.review();
        if (r == null) {
            return new PrescriptionCheckResponse(
                    p.id(), p.encounterId(), p.status(),
                    p.aiReviewStatus(),
                    null, null, null, null, null, null, null, null);
        }
        return new PrescriptionCheckResponse(
                p.id(), p.encounterId(), p.status(),
                p.aiReviewStatus(),
                r.riskLevel(),
                r.allergyWarnings(),
                r.interactionWarnings(),
                r.dosageWarnings(),
                r.contraindicationWarnings(),
                r.suggestions(),
                r.summary(),
                r.ruleCheckSummary());
    }
}
