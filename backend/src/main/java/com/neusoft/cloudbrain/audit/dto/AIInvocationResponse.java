package com.neusoft.cloudbrain.audit.dto;

import com.neusoft.cloudbrain.audit.entity.AIInvocation;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * AI 调用记录响应 DTO
 *
 * 不暴露实体内部的 JPA 注解和乐观锁字段（version），
 * 时间字段统一使用 OffsetDateTime 输出带 +08:00 偏移的 ISO 8601
 * （参见 30_接口数据与错误契约.md 第5节、33_错误码与时间规范.md 第5节）。
 */
public record AIInvocationResponse(
        Long id,
        String capability,
        String businessType,
        Long businessId,
        String status,
        String errorType,
        String errorMessage,
        Long durationMs,
        Integer attemptCount,
        Long operatorId,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static AIInvocationResponse from(AIInvocation entity) {
        if (entity == null) {
            return null;
        }
        return new AIInvocationResponse(
                entity.getId(),
                entity.getCapability(),
                entity.getBusinessType(),
                entity.getBusinessId(),
                entity.getStatus(),
                entity.getErrorType(),
                entity.getErrorMessage(),
                entity.getDurationMs(),
                entity.getAttemptCount(),
                entity.getOperatorId(),
                toOffset(entity.getStartedAt()),
                toOffset(entity.getFinishedAt()),
                toOffset(entity.getCreatedAt()),
                toOffset(entity.getUpdatedAt())
        );
    }

    private static OffsetDateTime toOffset(LocalDateTime ldt) {
        if (ldt == null) {
            return null;
        }
        return ldt.atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime();
    }
}
