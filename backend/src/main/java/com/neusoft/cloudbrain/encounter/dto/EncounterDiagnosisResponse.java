package com.neusoft.cloudbrain.encounter.dto;

import java.time.LocalDateTime;

/**
 * 就诊诊断响应
 */
public record EncounterDiagnosisResponse(
        Long id,
        Long encounterId,
        String diagnosisCode,
        String diagnosisName,
        String type,
        String source,
        Long aiInvocationId,
        Long doctorId,
        LocalDateTime confirmedAt,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
