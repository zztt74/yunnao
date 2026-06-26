package com.neusoft.cloudbrain.audit.dto;

import com.neusoft.cloudbrain.audit.entity.AuditLog;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 审计日志响应 DTO
 *
 * 不暴露实体内部的 JPA 注解和乐观锁字段（version），
 * 时间字段统一使用 OffsetDateTime 输出带 +08:00 偏移的 ISO 8601
 * （参见 30_接口数据与错误契约.md 第5节、33_错误码与时间规范.md 第5节）。
 */
public record AuditLogResponse(
        Long id,
        Long operatorId,
        String operatorType,
        String operatorName,
        String action,
        String targetType,
        Long targetId,
        String details,
        String result,
        String errorMessage,
        String ipAddress,
        String userAgent,
        String traceId,
        OffsetDateTime createdAt
) {
    public static AuditLogResponse from(AuditLog entity) {
        if (entity == null) {
            return null;
        }
        return new AuditLogResponse(
                entity.getId(),
                entity.getOperatorId(),
                entity.getOperatorType(),
                entity.getOperatorName(),
                entity.getAction(),
                entity.getTargetType(),
                entity.getTargetId(),
                entity.getDetails(),
                entity.getResult(),
                entity.getErrorMessage(),
                entity.getIpAddress(),
                entity.getUserAgent(),
                entity.getTraceId(),
                toOffset(entity.getCreatedAt())
        );
    }

    /**
     * 将 LocalDateTime 按 Asia/Shanghai 转换为带偏移的 OffsetDateTime
     */
    private static OffsetDateTime toOffset(LocalDateTime ldt) {
        if (ldt == null) {
            return null;
        }
        return ldt.atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime();
    }
}
