package com.neusoft.cloudbrain.triage.dto;

import java.time.LocalDateTime;

/**
 * 分诊记录响应
 */
public record TriageRecordResponse(
        Long id,
        Long patientId,
        String symptoms,
        String duration,
        String supplement,
        String aiDepartmentCode,
        String aiPriority,
        String aiReason,
        String aiSafetyNotice,
        Boolean aiEmergencySuggested,
        String aiSymptomKeywords,
        Long mappedDepartmentId,
        String mappingStatus,
        String aiStatus,
        String aiFailureReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
