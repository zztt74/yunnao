package com.neusoft.cloudbrain.schedule.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 排班响应
 */
public record ScheduleResponse(
        Long id,
        Long doctorId,
        String doctorName,
        Long departmentId,
        String departmentName,
        LocalDate scheduleDate,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer maxAppointments,
        Integer bookedCount,
        Integer remainingCount,
        String status,
        LocalDateTime cancelledAt,
        String cancelReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
