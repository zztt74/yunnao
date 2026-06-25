package com.neusoft.cloudbrain.encounter.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 开始接诊请求
 *
 * 基础版本允许医生从 BOOKED 直接开始接诊。
 * 开始接诊时创建 Encounter 并同步 Appointment 状态为 IN_PROGRESS。
 */
public record EncounterStartRequest(
        @NotNull(message = "挂号 ID 不能为空")
        Long appointmentId) {
}
