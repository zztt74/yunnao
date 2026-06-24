package com.neusoft.cloudbrain.schedule.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 排班实体
 *
 * 状态流转（来自 12_业务流程与状态机.md 第3节）：
 * AVAILABLE → FULL       号源已满
 * AVAILABLE → CANCELLED  管理员取消
 * FULL → AVAILABLE       取消预约后恢复号源
 * CANCELLED/COMPLETED    终态，不可恢复
 *
 * 规则：
 * - AVAILABLE 和 FULL 由时间、取消标记和号源计算，不作为可被任意修改的独立业务事实
 * - 到达结束时间后转为 COMPLETED
 * - CANCELLED 和 COMPLETED 不得恢复为可预约状态
 */
@Entity
@Table(name = "schedule", indexes = {
        @Index(name = "idx_schedule_doctor_id", columnList = "doctor_id"),
        @Index(name = "idx_schedule_department_id", columnList = "department_id"),
        @Index(name = "idx_schedule_date", columnList = "schedule_date"),
        @Index(name = "idx_schedule_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Column(name = "department_id", nullable = false)
    private Long departmentId;

    @Column(name = "schedule_date", nullable = false)
    private LocalDate scheduleDate;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "max_appointments", nullable = false)
    private Integer maxAppointments;

    @Column(name = "booked_count", nullable = false)
    @Builder.Default
    private Integer bookedCount = 0;

    @Column(nullable = false, length = 16)
    @Builder.Default
    private String status = "AVAILABLE";

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
