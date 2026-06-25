package com.neusoft.cloudbrain.medicalrecord.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 病历实体
 *
 * 状态流转（来自 12_业务流程与状态机.md 第8节）：
 * DRAFT → CONFIRMED            医生手工草稿确认
 * AI_GENERATED → CONFIRMED     AI 草稿医生确认
 * DRAFT ↔ AI_GENERATED         来源切换（非正式确认）
 * AMENDED 为扩展版本保留状态，基础版本不得进入
 *
 * 规则：
 * - AI 只能生成 DRAFT 或 AI_GENERATED
 * - CONFIRMED 必须由医生完成
 * - AI 原始草稿永久保留，不可被覆盖
 * - 基础版本每个 Encounter 只能有一条当前有效的 CONFIRMED 记录
 * - CONFIRMED 后基础版本不允许修改（扩展版本支持 AMENDED）
 */
@Entity
@Table(name = "medical_record", indexes = {
        @Index(name = "idx_medical_record_encounter_id", columnList = "encounter_id"),
        @Index(name = "idx_medical_record_patient_id", columnList = "patient_id"),
        @Index(name = "idx_medical_record_doctor_id", columnList = "doctor_id"),
        @Index(name = "idx_medical_record_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, length = 16)
    private String source;

    @Column(nullable = false, length = 16)
    @Builder.Default
    private String status = "DRAFT";

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
