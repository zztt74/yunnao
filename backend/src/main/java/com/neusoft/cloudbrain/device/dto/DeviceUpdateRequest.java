package com.neusoft.cloudbrain.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 设备档案更新请求
 *
 * 仅更新基础档案信息，不含 status（状态变更走 POST /api/devices/{id}/status），
 * 不含 code（业务唯一标识，不通过此接口修改，避免破坏历史关联）。
 */
public record DeviceUpdateRequest(
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
