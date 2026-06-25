package com.neusoft.cloudbrain.statistics.dto;

/**
 * 挂号完成率/取消率统计
 *
 * 统计口径（来自 11_功能需求.md 第15.4节）：
 * - 挂号取消率：统计周期内取消挂号数 / 同周期内创建的全部非测试挂号数
 * - 分母包含已取消挂号，分母为 0 时返回 0%
 */
public record AppointmentRateStatistics(
        Long totalAppointments,
        Long completedAppointments,
        Long cancelledAppointments,
        Double completionRate,
        Double cancellationRate
) {
}
