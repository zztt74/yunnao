package com.neusoft.cloudbrain.device.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 设备状态变更请求
 *
 * 用于异常上报、送修、修复完成、停用、重新启用等场景。
 */
public record DeviceStatusChangeRequest(
        @NotBlank(message = "目标状态不能为空")
        String targetStatus,

        String reason) {
}
