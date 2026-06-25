package com.neusoft.cloudbrain.encounter.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 就诊诊断实体
 *
 * 诊断隔离原则（来自 12_业务流程与状态机.md 第7节）：
 *
 * AI 诊断（候选建议）：
 *   type = PRELIMINARY, source = AI_SUGGESTION
 *
 * 医生最终诊断（正式记录）：
 *   type = FINAL, source = DOCTOR
 *
 * 规则：
 * - AI 只能产生 AI_SUGGESTION，不能创建 FINAL + DOCTOR
 * - 医生确认的最终诊断必须为 type=FINAL、source=DOCTOR
 * - 一个 Encounter 至少有一条医生最终诊断后才能完成
 * - AI 原始结果保存在 AI 调用记录，结构化候选诊断保存为 source=AI_SUGGESTION
 * - 医生诊断保存为 source=DOCTOR，两类记录不得互相覆盖
 * - 最终诊断变化属于正式医疗事实变更，基础版本完成后不得直接修改
 */
@Entity
@Table(name = "encounter_diagnosis", indexes = {
        @Index(name = "idx_encounter_diagnosis_encounter_id", columnList = "encounter_id"),
        @Index(name = "idx_encounter_diagnosis_source", columnList = "source"),
        @Index(name = "idx_encounter_diagnosis_type", columnList = "type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EncounterDiagnosis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "diagnosis_code", nullable = false, length = 32)
    private String diagnosisCode;

    @Column(name = "diagnosis_name", nullable = false, length = 128)
    private String diagnosisName;

    @Column(nullable = false, length = 16)
    private String type;

    @Column(nullable = false, length = 16)
    private String source;

    @Column(name = "ai_invocation_id")
    private Long aiInvocationId;

    @Column(name = "doctor_id")
    private Long doctorId;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
