package com.neusoft.cloudbrain.patient.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 患者响应
 */
public record PatientResponse(
        Long id,
        Long userId,
        String name,
        String gender,
        LocalDate birthDate,
        String phone,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
