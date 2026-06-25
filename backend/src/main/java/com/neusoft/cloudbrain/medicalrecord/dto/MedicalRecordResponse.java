package com.neusoft.cloudbrain.medicalrecord.dto;

import java.time.LocalDateTime;

/**
 * 病历响应
 */
public record MedicalRecordResponse(
        Long id,
        Long encounterId,
        Long patientId,
        Long doctorId,
        String content,
        String source,
        String status,
        Long createdBy,
        Long confirmedBy,
        LocalDateTime confirmedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
