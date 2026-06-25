package com.neusoft.cloudbrain.examination.dto;

import java.time.LocalDateTime;

/**
 * 检查检验结果响应
 */
public record ExaminationResultResponse(
        Long id,
        Long orderId,
        String resultText,
        String normalRange,
        String conclusion,
        String abnormalFlag,
        Long enteredBy,
        Long reviewedBy,
        String aiInterpretation,
        String aiAbnormalItems,
        String aiFollowUpAdvice,
        String aiStatus,
        String aiFailureReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
