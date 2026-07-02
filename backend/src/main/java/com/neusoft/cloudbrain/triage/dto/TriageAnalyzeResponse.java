package com.neusoft.cloudbrain.triage.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分诊分析响应
 *
 * 包含 AI 分诊结果、科室映射结果和推荐可预约排班。
 * AI 失败时返回降级标记，提示转人工选择。
 *
 * 多轮扩展（UF-01）：
 * - conversationId：回显请求中的 conversationId（无则 null）
 * - round：回显当前轮次
 * - isFinal：AI 是否认为已可终结会话（true=已给最终建议，前端展示最终建议卡片；false=仍有追问）
 * - followUpQuestion：AI 主动追问的问题（isFinal=true 时为 null）
 *
 * 单轮兼容：isFinal 默认 true、followUpQuestion 默认 null，与老前端兼容。
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
        LocalDateTime createdAt,

        // ===== UF-01 多轮扩展 =====
        String conversationId,
        Integer round,
        Boolean isFinal,
        String followUpQuestion) {

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
