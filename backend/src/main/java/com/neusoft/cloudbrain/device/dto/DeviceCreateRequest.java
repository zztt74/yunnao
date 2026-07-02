package com.neusoft.cloudbrain.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 设备档案创建请求
 *
 * 字段对应现有 Device 表。任务书建议的 category 映射为 type，
 * applicableItems 暂无对应字段（不擅自改表），enabled 由 status 表达。
 * 创建后 status 默认 AVAILABLE，不在此接口设置。
 */
public record DeviceCreateRequest(
        @NotBlank(message = "设备编码不能为空")
        @Size(max = 32, message = "设备编码长度不能超过 32")
        String code,

        @NotBlank(message = "设备名称不能为空")
        @Size(max = 128, message = "设备名称长度不能超过 128")
        String name,

        @NotBlank(message = "设备类型不能为空")
        @Size(max = 32, message = "设备类型长度不能超过 32")
        String type,

        Long departmentId,

        @Size(max = 128, message = "存放位置长度不能超过 128")
        String location,

        @Size(max = 128, message = "厂商长度不能超过 128")
        String manufacturer,

        @Size(max = 64, message = "型号长度不能超过 64")
        String model,

        @Size(max = 64, message = "序列号长度不能超过 64")
        String serialNumber,

        @Size(max = 512, message = "备注长度不能超过 512")
        String notes,

        LocalDate purchaseDate,

        LocalDate warrantyUntil
) {
}
