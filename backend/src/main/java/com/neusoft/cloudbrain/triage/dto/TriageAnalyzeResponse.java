package com.neusoft.cloudbrain.triage.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分诊分析响应
 *
 * 包含 AI 分诊结果、科室映射结果和推荐可预约排班。
 * AI 失败时返回降级标记，提示转人工选择。
 */
public record TriageAnalyzeResponse(
        Long triageRecordId,
        Long patientId,
        String symptoms,
        String duration,
        String supplement,
        // AI 结果
        String aiDepartmentCode,
        String aiPriority,
        String aiReason,
        String aiSafetyNotice,
        Boolean aiEmergencySuggested,
        List<String> aiSymptomKeywords,
        String aiStatus,
        String aiFailureReason,
        // 映射结果
        Long mappedDepartmentId,
        String mappedDepartmentName,
        String mappingStatus,
        // 推荐排班
        List<RecommendedSchedule> recommendedSchedules,
        // 审计
        LocalDateTime createdAt) {

    /**
     * 推荐可预约排班
     */
    public record RecommendedSchedule(
            Long scheduleId,
            Long doctorId,
            String doctorName,
            String doctorTitle,
            Long departmentId,
            String departmentName,
            String scheduleDate,
            String startTime,
            String endTime,
            Integer remainingCount) {
    }
}
