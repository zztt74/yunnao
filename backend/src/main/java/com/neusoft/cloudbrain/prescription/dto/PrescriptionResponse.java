package com.neusoft.cloudbrain.prescription.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 处方响应
 */
public record PrescriptionResponse(
        Long id,
        Long encounterId,
        Long patientId,
        Long doctorId,
        String status,
        String aiReviewStatus,
        LocalDateTime createdAt,
        LocalDateTime confirmedAt,
        Long confirmedBy,
        LocalDateTime voidedAt,
        Long voidedBy,
        String voidedReason,
        List<PrescriptionItemResponse> items,
        PrescriptionReviewResponse review) {
}
