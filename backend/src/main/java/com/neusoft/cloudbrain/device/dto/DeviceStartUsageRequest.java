package com.neusoft.cloudbrain.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 开始设备使用请求
 */
public record DeviceStartUsageRequest(
        @NotNull(message = "设备 ID 不能为空")
        Long deviceId,

        @NotNull(message = "就诊 ID 不能为空")
        Long encounterId,

        String notes) {
}
