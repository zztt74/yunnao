package com.neusoft.cloudbrain.device.dto;

import java.time.LocalDateTime;

/**
 * 设备状态变更历史响应
 */
public record DeviceStatusHistoryResponse(
        Long id,
        Long deviceId,
        String fromStatus,
        String toStatus,
        Long operatorId,
        String reason,
        LocalDateTime changedAt) {
}
