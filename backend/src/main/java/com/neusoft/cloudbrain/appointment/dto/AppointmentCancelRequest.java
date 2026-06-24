package com.neusoft.cloudbrain.appointment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 挂号取消请求
 */
public record AppointmentCancelRequest(
        @NotBlank(message = "取消原因不能为空")
        @Size(max = 255, message = "取消原因长度不能超过 255")
        String reason
) {
}
