package com.neusoft.cloudbrain.examination.dto;

import java.time.LocalDateTime;

/**
 * 检查检验申请响应
 */
public record ExaminationOrderResponse(
        Long id,
        Long encounterId,
        Long patientId,
        Long doctorId,
        String orderType,
        String itemCode,
        String itemName,
        String status,
        LocalDateTime orderedAt,
        LocalDateTime inProgressAt,
        LocalDateTime resultEnteredAt,
        LocalDateTime reviewedAt,
        LocalDateTime cancelledAt,
        String cancelReason,
        String returnReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
