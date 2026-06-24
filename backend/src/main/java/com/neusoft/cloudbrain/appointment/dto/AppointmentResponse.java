package com.neusoft.cloudbrain.appointment.dto;

import java.time.LocalDateTime;

/**
 * 挂号响应
 */
public record AppointmentResponse(
        Long id,
        Long patientId,
        String patientName,
        Long scheduleId,
        Long doctorId,
        String doctorName,
        Long departmentId,
        String departmentName,
        String appointmentNumber,
        String status,
        LocalDateTime bookedAt,
        LocalDateTime checkInTime,
        String cancellationReason,
        String cancellationSource,
        LocalDateTime cancelledAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
