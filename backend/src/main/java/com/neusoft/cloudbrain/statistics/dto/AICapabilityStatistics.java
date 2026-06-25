package com.neusoft.cloudbrain.statistics.dto;

/**
 * AI 能力维度统计
 */
public record AICapabilityStatistics(
        String capability,
        Long totalInvocations,
        Long successCount,
        Double successRate,
        Double avgDurationMs
) {
}
