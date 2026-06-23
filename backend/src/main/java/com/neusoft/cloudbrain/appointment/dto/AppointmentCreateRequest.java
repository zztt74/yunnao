package com.neusoft.cloudbrain.appointment.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 挂号创建请求
 */
public record AppointmentCreateRequest(
        @NotNull(message = "患者 ID 不能为空")
        Long patientId,

        @NotNull(message = "排班 ID 不能为空")
        Long scheduleId
) {
}
