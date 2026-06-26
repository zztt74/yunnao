package com.neusoft.cloudbrain.audit.dto;

import com.neusoft.cloudbrain.audit.entity.AIInvocationAttempt;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * AI 调用尝试记录响应 DTO
 *
 * 不暴露实体内部的 JPA 注解，
 * 时间字段统一使用 OffsetDateTime 输出带 +08:00 偏移的 ISO 8601
 * （参见 30_接口数据与错误契约.md 第5节、33_错误码与时间规范.md 第5节）。
 */
public record AIInvocationAttemptResponse(
        Long id,
        Long invocationId,
        String provider,
        String model,
        String promptVersion,
        String status,
        Integer httpStatus,
        String errorType,
        String errorMessage,
        String requestSummary,
        String responseSummary,
        Long durationMs,
        Integer attemptIndex,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt
) {
    public static AIInvocationAttemptResponse from(AIInvocationAttempt entity) {
        if (entity == null) {
            return null;
        }
        return new AIInvocationAttemptResponse(
                entity.getId(),
                entity.getInvocationId(),
                entity.getProvider(),
                entity.getModel(),
                entity.getPromptVersion(),
                entity.getStatus(),
                entity.getHttpStatus(),
                entity.getErrorType(),
                entity.getErrorMessage(),
                entity.getRequestSummary(),
                entity.getResponseSummary(),
                entity.getDurationMs(),
                entity.getAttemptIndex(),
                toOffset(entity.getStartedAt()),
                toOffset(entity.getFinishedAt())
        );
    }

    public static List<AIInvocationAttemptResponse> fromList(
            java.util.List<AIInvocationAttempt> entities) {
        if (entities == null) {
            return List.of();
        }
        return entities.stream()
                .map(AIInvocationAttemptResponse::from)
                .toList();
    }

    private static OffsetDateTime toOffset(LocalDateTime ldt) {
        if (ldt == null) {
            return null;
        }
        return ldt.atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime();
    }
}
