package com.neusoft.cloudbrain.statistics.dto;

import java.time.LocalDate;

/**
 * 每日门诊量统计
 *
 * 统计口径（来自 11_功能需求.md 第15.4节）：
 * - 门诊量：已完成 Encounter 数
 */
public record DailyOutpatientStatistics(
        LocalDate date,
        Long completedCount,
        Long cancelledCount
) {
}
