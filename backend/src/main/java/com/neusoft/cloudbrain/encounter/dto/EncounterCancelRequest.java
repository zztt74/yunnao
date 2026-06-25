package com.neusoft.cloudbrain.encounter.dto;

import jakarta.validation.constraints.Size;

/**
 * 取消就诊请求
 *
 * 仅 CREATED 状态可取消；IN_PROGRESS、WAITING_EXAM、COMPLETED 不允许取消。
 */
public record EncounterCancelRequest(
        @Size(max = 255, message = "取消原因长度不能超过 255 字符")
        String reason) {
}
