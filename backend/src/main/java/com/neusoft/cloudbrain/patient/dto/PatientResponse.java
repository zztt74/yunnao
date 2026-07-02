package com.neusoft.cloudbrain.patient.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 患者响应
 *
 * B-HW-06：补充 username（账号）字段，管理端列表可展示账号并按账号筛选。
 */
public record PatientResponse(
        Long id,
        Long userId,
        String username,
        String name,
        String gender,
        LocalDate birthDate,
        String phone,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
