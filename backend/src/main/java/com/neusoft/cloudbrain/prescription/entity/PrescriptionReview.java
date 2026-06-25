package com.neusoft.cloudbrain.prescription.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 处方 AI 审核记录实体
 *
 * 状态流转（来自 12_业务流程与状态机.md 第9节）：
 *   PENDING → REVIEWED            审核通过
 *   PENDING → FAILED              审核不通过（高风险）
 *
 * 规则：
 * - AI 审核状态独立于处方业务状态
 * - 确定性规则命中不得被 AI 输出降级或覆盖
 * - AI 负责解释风险和补充建议，不作为确定性用药校验的唯一来源
 * - AI 失败允许医生手工继续，但记录失败原因
 */
@Entity
@Table(name = "prescription_review", indexes = {
        @Index(name = "idx_prescription_review_prescription_id", columnList = "prescription_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrescriptionReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "prescription_id", nullable = false)
    private Long prescriptionId;

    @Column(name = "review_status", nullable = false, length = 16)
    private String reviewStatus;

    @Column(name = "risk_level", nullable = false, length = 16)
    private String riskLevel;

    @Column(name = "allergy_warnings", columnDefinition = "TEXT")
    private String allergyWarnings;

    @Column(name = "interaction_warnings", columnDefinition = "TEXT")
    private String interactionWarnings;

    @Column(name = "dosage_warnings", columnDefinition = "TEXT")
    private String dosageWarnings;

    @Column(name = "contraindication_warnings", columnDefinition = "TEXT")
    private String contraindicationWarnings;

    @Column(columnDefinition = "TEXT")
    private String suggestions;

    @Column(name = "rule_check_summary", columnDefinition = "TEXT")
    private String ruleCheckSummary;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
