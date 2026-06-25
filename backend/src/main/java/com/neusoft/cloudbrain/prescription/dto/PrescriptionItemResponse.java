package com.neusoft.cloudbrain.prescription.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 处方明细响应
 */
public record PrescriptionItemResponse(
        Long id,
        String drugCode,
        String drugName,
        String dosage,
        BigDecimal dosageValue,
        String frequency,
        Integer duration,
        BigDecimal quantity,
        String instructions) {
}
