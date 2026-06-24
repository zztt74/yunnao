package com.neusoft.cloudbrain.patient.dto;

import java.time.LocalDateTime;

/**
 * 患者档案响应
 */
public record PatientProfileResponse(
        Long id,
        Long patientId,
        String address,
        String emergencyContact,
        String emergencyPhone,
        String allergies,
        String medicalHistory,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
