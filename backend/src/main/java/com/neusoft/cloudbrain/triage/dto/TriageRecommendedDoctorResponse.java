package com.neusoft.cloudbrain.triage.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 分诊推荐医生卡片响应
 *
 * 课程任务三要求分诊结果页直接展示推荐科室下的可预约医生列表，
 * 并能跳转到该医生挂号页。本 DTO 聚合医生基本信息和最近可预约排班摘要，
 * 供前端一次性加载医生卡片。
 *
 * 数据来源：真实医生表 + 真实排班表，不使用前端 mock。
 */
public record TriageRecommendedDoctorResponse(
        Long doctorId,
        String doctorName,
        String doctorTitle,
        Long departmentId,
        String departmentName,
        String specialty,
        List<ScheduleSummary> availableSchedules) {

    /**
     * 排班摘要：日期 + 号源
     */
    public record ScheduleSummary(
            Long scheduleId,
            LocalDate scheduleDate,
            String startTime,
            String endTime,
            Integer remainingCount) {
    }
}
