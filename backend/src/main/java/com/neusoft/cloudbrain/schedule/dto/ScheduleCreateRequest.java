package com.neusoft.cloudbrain.schedule.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 排班创建请求
 */
public record ScheduleCreateRequest(
        @NotNull(message = "医生 ID 不能为空")
        Long doctorId,

        @NotNull(message = "科室 ID 不能为空")
        Long departmentId,

        @NotNull(message = "排班日期不能为空")
        LocalDate scheduleDate,

        @NotNull(message = "开始时间不能为空")
        LocalDateTime startTime,

        @NotNull(message = "结束时间不能为空")
        LocalDateTime endTime,

        @NotNull(message = "最大号源数不能为空")
        @Min(value = 1, message = "最大号源数必须大于 0")
        Integer maxAppointments
) {
}
