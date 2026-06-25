package com.neusoft.cloudbrain.examination.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 检查检验申请实体
 *
 * 状态流转（来自 12_业务流程与状态机.md 第10节）：
 * ORDERED → IN_PROGRESS         执行中
 * IN_PROGRESS → RESULT_ENTERED   结果录入
 * RESULT_ENTERED → REVIEWED      医生审核
 * ORDERED → CANCELLED            取消
 * IN_PROGRESS → CANCELLED        取消
 * RESULT_ENTERED → IN_PROGRESS   退回重录（需记录原因）
 *
 * 终态：CANCELLED、REVIEWED
 * 患者只能查看 REVIEWED 结果
 *
 * 转换约束：
 * - RESULT_ENTERED → IN_PROGRESS 仅用于审核退回，必须记录原因
 * - REVIEWED 后不得直接覆盖原始结果
 * - CANCELLED 和 REVIEWED 为终态
 */
@Entity
@Table(name = "examination_order", indexes = {
        @Index(name = "idx_examination_order_encounter_id", columnList = "encounter_id"),
        @Index(name = "idx_examination_order_patient_id", columnList = "patient_id"),
        @Index(name = "idx_examination_order_doctor_id", columnList = "doctor_id"),
        @Index(name = "idx_examination_order_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExaminationOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Column(name = "order_type", nullable = false, length = 16)
    private String orderType;

    @Column(name = "item_code", length = 64)
    private String itemCode;

    @Column(name = "item_name", nullable = false, length = 128)
    private String itemName;

    @Column(nullable = false, length = 16)
    @Builder.Default
    private String status = "ORDERED";

    @Column(name = "ordered_at", nullable = false)
    private LocalDateTime orderedAt;

    @Column(name = "in_progress_at")
    private LocalDateTime inProgressAt;

    @Column(name = "result_entered_at")
    private LocalDateTime resultEnteredAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Column(name = "return_reason", length = 255)
    private String returnReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
