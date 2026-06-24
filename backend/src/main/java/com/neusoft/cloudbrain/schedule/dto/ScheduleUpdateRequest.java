package com.neusoft.cloudbrain.schedule.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 排班更新请求（仅允许修改未开始排班）
 */
public record ScheduleUpdateRequest(
        @NotNull(message = "开始时间不能为空")
        LocalDateTime startTime,

        @NotNull(message = "结束时间不能为空")
        LocalDateTime endTime,

        @NotNull(message = "最大号源数不能为空")
        @Min(value = 1, message = "最大号源数必须大于 0")
        Integer maxAppointments
) {
}
