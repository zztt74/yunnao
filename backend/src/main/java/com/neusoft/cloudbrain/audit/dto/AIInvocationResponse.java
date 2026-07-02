package com.neusoft.cloudbrain.audit.dto;

import com.neusoft.cloudbrain.audit.entity.AIInvocation;
import com.neusoft.cloudbrain.audit.entity.AIInvocationAttempt;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * AI 调用记录响应 DTO
 *
 * B-HW-11：新增 provider / model 字段，取最近一次 attempt 的 provider 和 model，
 * 前端可直接在列表和详情中展示供应商和模型信息。
 */
public record AIInvocationResponse(
        Long id,
        String capability,
        String businessType,
        Long businessId,
        String status,
        String provider,
        String model,
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
        return from(entity, null);
    }

    /**
     * B-HW-11：传入最近一次 attempt 以填充 provider / model。
     */
    public static AIInvocationResponse from(AIInvocation entity, AIInvocationAttempt latestAttempt) {
        if (entity == null) {
            return null;
        }
        String provider = latestAttempt != null ? latestAttempt.getProvider() : null;
        String model = latestAttempt != null ? mapModelDisplay(latestAttempt.getModel()) : null;

        return new AIInvocationResponse(
                entity.getId(),
                entity.getCapability(),
                entity.getBusinessType(),
                entity.getBusinessId(),
                entity.getStatus(),
                provider,
                model,
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

    /**
     * B-HW-11：模型名称展示映射。
     * DeepSeek 的实际 model 标识（如 deepseek-chat）映射为 v4 flash 便于展示。
     */
    private static String mapModelDisplay(String model) {
        if (model == null) {
            return null;
        }
        if (model.startsWith("deepseek")) {
            return "v4 flash";
        }
        return model;
    }

    private static OffsetDateTime toOffset(LocalDateTime ldt) {
        if (ldt == null) {
            return null;
        }
        return ldt.atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime();
    }
}
