package com.neusoft.cloudbrain.device.dto;

import java.time.LocalDateTime;

/**
 * 设备使用记录响应
 */
public record DeviceUsageResponse(
        Long id,
        Long deviceId,
        Long encounterId,
        Long usedBy,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
