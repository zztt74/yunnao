package com.neusoft.cloudbrain.doctor.dto;

import java.time.LocalDateTime;

/**
 * 医生响应
 */
public record DoctorResponse(
        Long id,
        Long userId,
        Long departmentId,
        String departmentName,
        String name,
        String title,
        String specialty,
        String status,
        String education,
        Integer experienceYears,
        String introduction,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
