package com.neusoft.cloudbrain.device.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备响应
 */
public record DeviceResponse(
        Long id,
        String code,
        String name,
        String type,
        Long departmentId,
        String status,
        LocalDate purchaseDate,
        LocalDate warrantyUntil,
        LocalDateTime lastMaintenance,
        String location,
        String manufacturer,
        String model,
        String serialNumber,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
