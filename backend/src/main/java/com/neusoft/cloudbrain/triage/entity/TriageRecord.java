package com.neusoft.cloudbrain.triage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 分诊记录实体
 *
 * 状态字段说明：
 * - aiStatus: AI 调用状态（PENDING/SUCCESS/FAILED）
 * - mappingStatus: 科室映射状态（PENDING/MAPPED/MANUAL/FAILED）
 *
 * 降级规则（来自 12_业务流程与状态机.md 第14节）：
 * - AI 失败时 aiStatus=FAILED，mappingStatus=MANUAL，提示转人工选择
 * - 传统业务继续，不阻断挂号
 */
@Entity
@Table(name = "triage_record", indexes = {
        @Index(name = "idx_triage_record_patient_id", columnList = "patient_id"),
        @Index(name = "idx_triage_record_mapping_status", columnList = "mapping_status"),
        @Index(name = "idx_triage_record_ai_status", columnList = "ai_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TriageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String symptoms;

    @Column(length = 32)
    private String duration;

    @Column(columnDefinition = "TEXT")
    private String supplement;

    // AI 输出
    @Column(name = "ai_department_code", length = 32)
    private String aiDepartmentCode;

    @Column(name = "ai_priority", length = 16)
    private String aiPriority;

    @Column(name = "ai_reason", columnDefinition = "TEXT")
    private String aiReason;

    @Column(name = "ai_safety_notice", columnDefinition = "TEXT")
    private String aiSafetyNotice;

    @Column(name = "ai_emergency_suggested", nullable = false)
    @Builder.Default
    private Boolean aiEmergencySuggested = false;

    @Column(name = "ai_symptom_keywords", length = 512)
    private String aiSymptomKeywords;

    // 映射结果
    @Column(name = "mapped_department_id")
    private Long mappedDepartmentId;

    @Column(name = "mapping_status", nullable = false, length = 16)
    @Builder.Default
    private String mappingStatus = "PENDING";

    // AI 调用状态
    @Column(name = "ai_status", nullable = false, length = 16)
    @Builder.Default
    private String aiStatus = "PENDING";

    @Column(name = "ai_failure_reason", length = 255)
    private String aiFailureReason;

    @Column(name = "ai_invocation_id")
    private Long aiInvocationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
