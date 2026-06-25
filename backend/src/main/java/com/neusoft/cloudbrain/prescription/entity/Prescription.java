package com.neusoft.cloudbrain.prescription.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 处方主表实体
 *
 * 状态流转（来自 12_业务流程与状态机.md 第9节）：
 * 处方业务状态：
 *   DRAFT → CONFIRMED       医生确认
 *   CONFIRMED → VOIDED      作废（需记录原因）
 *
 * AI 审核状态（独立保存）：
 *   NOT_REQUESTED → PENDING       提交审核
 *   PENDING → REVIEWED            审核通过
 *   PENDING → FAILED              审核不通过（高风险）
 *   FAILED → PENDING              重新提交
 *   REVIEWED → PENDING            修改后重新审核
 *
 * 规则：
 * - AI 审核状态不等于处方确认状态
 * - 高风险需要二次确认
 * - CONFIRMED 后只能作废，不能物理删除
 * - 处方可不存在，不得为了完成就诊创建空处方
 */
@Entity
@Table(name = "prescription", indexes = {
        @Index(name = "idx_prescription_encounter_id", columnList = "encounter_id"),
        @Index(name = "idx_prescription_patient_id", columnList = "patient_id"),
        @Index(name = "idx_prescription_doctor_id", columnList = "doctor_id"),
        @Index(name = "idx_prescription_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Prescription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Column(nullable = false, length = 16)
    @Builder.Default
    private String status = "DRAFT";

    @Column(name = "ai_review_status", nullable = false, length = 16)
    @Builder.Default
    private String aiReviewStatus = "NOT_REQUESTED";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @Column(name = "voided_by")
    private Long voidedBy;

    @Column(name = "voided_reason", length = 255)
    private String voidedReason;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
