package com.neusoft.cloudbrain.prescription.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 处方响应
 *
 * B-HW-02：补齐患者端展示字段（doctorName、departmentName、patientName、updatedAt），
 * 患者列表/详情不再依赖 encounter 上下文补字段。
 */
public record PrescriptionResponse(
        Long id,
        Long encounterId,
        Long patientId,
        Long doctorId,
        String doctorName,
        String departmentName,
        String patientName,
        String status,
        String aiReviewStatus,
        LocalDateTime createdAt,
        LocalDateTime confirmedAt,
        Long confirmedBy,
        LocalDateTime voidedAt,
        Long voidedBy,
        String voidedReason,
        LocalDateTime updatedAt,
        List<PrescriptionItemResponse> items,
        PrescriptionReviewResponse review) {
}
