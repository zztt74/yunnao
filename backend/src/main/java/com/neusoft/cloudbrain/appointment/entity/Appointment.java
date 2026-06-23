package com.neusoft.cloudbrain.appointment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 挂号实体
 *
 * 状态流转（来自 12_业务流程与状态机.md 第4节）：
 * BOOKED → CHECKED_IN      患者签到（可选）
 * BOOKED → IN_PROGRESS     医生直接接诊（基础版）
 * BOOKED → CANCELLED       患者取消（恢复号源）
 * BOOKED → NO_SHOW         未按时就诊
 * IN_PROGRESS → COMPLETED  就诊完成
 *
 * 禁止：
 * - COMPLETED 再次接诊
 * - IN_PROGRESS 后患者取消
 * - 一个挂号存在多个进行中 Encounter
 * - 过期或取消排班继续挂号
 *
 * 规则：
 * - BOOKED → CHECKED_IN 为可选签到流程；基础版本允许医生直接执行 BOOKED → IN_PROGRESS
 * - Appointment 与 Encounter 的 IN_PROGRESS、WAITING_EXAM、COMPLETED 必须在同一业务用例中同步
 */
@Entity
@Table(name = "appointment", uniqueConstraints = {
        @UniqueConstraint(name = "uk_appointment_number", columnNames = "appointment_number")
}, indexes = {
        @Index(name = "idx_appointment_patient_id", columnList = "patient_id"),
        @Index(name = "idx_appointment_schedule_id", columnList = "schedule_id"),
        @Index(name = "idx_appointment_doctor_id", columnList = "doctor_id"),
        @Index(name = "idx_appointment_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Column(name = "appointment_number", nullable = false, unique = true, length = 32)
    private String appointmentNumber;

    @Column(nullable = false, length = 16)
    @Builder.Default
    private String status = "BOOKED";

    @Column(name = "booked_at", nullable = false)
    private LocalDateTime bookedAt;

    @Column(name = "check_in_time")
    private LocalDateTime checkInTime;

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    @Column(name = "cancellation_source", length = 16)
    private String cancellationSource;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
