package com.neusoft.cloudbrain.statistics.dto;

/**
 * AI 调用统计
 *
 * 统计口径（来自 11_功能需求.md 第15.4节）：
 * - AI 成功率：合法结构化业务响应数 / AI 业务调用数
 * - 同一次业务调用内的重试不重复进入分母（按 invocation 统计）
 */
public record AIStatistics(
        Long totalInvocations,
        Long successCount,
        Long failedCount,
        Double successRate,
        Double avgDurationMs,
        Double avgDurationSeconds
) {
}
