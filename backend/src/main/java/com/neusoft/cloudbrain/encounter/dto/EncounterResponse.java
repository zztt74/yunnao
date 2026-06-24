package com.neusoft.cloudbrain.encounter.dto;

import java.time.LocalDateTime;

/**
 * 就诊响应
 */
public record EncounterResponse(
        Long id,
        Long appointmentId,
        Long patientId,
        String patientName,
        Long doctorId,
        String doctorName,
        Long departmentId,
        String departmentName,
        String status,
        LocalDateTime startedAt,
        LocalDateTime waitingExamAt,
        LocalDateTime completedAt,
        LocalDateTime cancelledAt,
        String cancelReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
