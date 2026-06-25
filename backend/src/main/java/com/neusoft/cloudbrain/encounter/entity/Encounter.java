package com.neusoft.cloudbrain.encounter.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 就诊实体
 *
 * 状态流转（来自 12_业务流程与状态机.md 第6节）：
 * CREATED → IN_PROGRESS       开始接诊
 * IN_PROGRESS → WAITING_EXAM  等待检查结果
 * WAITING_EXAM → IN_PROGRESS  检查返回继续诊疗
 * IN_PROGRESS → COMPLETED     就诊完成
 * CREATED → CANCELLED         取消就诊
 *
 * 禁止：
 * - IN_PROGRESS、WAITING_EXAM、COMPLETED 不允许取消
 * - COMPLETED 再次接诊
 * - 一个 Appointment 存在多个进行中 Encounter
 *
 * 转换约束：
 * - 开始接诊时必须同时将 Appointment 更新为 IN_PROGRESS
 * - 等待检查时必须同时将 Appointment 更新为 WAITING_EXAM
 * - 完成时必须同时将 Appointment 更新为 COMPLETED
 * - 完成就诊前必须满足病历、医生最终诊断、检查检验和已有处方前置条件
 */
@Entity
@Table(name = "encounter", uniqueConstraints = {
        @UniqueConstraint(name = "uk_encounter_appointment_id", columnNames = "appointment_id")
}, indexes = {
        @Index(name = "idx_encounter_patient_id", columnList = "patient_id"),
        @Index(name = "idx_encounter_doctor_id", columnList = "doctor_id"),
        @Index(name = "idx_encounter_department_id", columnList = "department_id"),
        @Index(name = "idx_encounter_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Encounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "appointment_id", nullable = false, unique = true)
    private Long appointmentId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Column(name = "department_id", nullable = false)
    private Long departmentId;

    @Column(nullable = false, length = 16)
    @Builder.Default
    private String status = "CREATED";

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "waiting_exam_at")
    private LocalDateTime waitingExamAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
