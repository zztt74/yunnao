package com.neusoft.cloudbrain.statistics.dto;

/**
 * 仪表盘概览统计
 *
 * 来自 11_功能需求.md 第15.3节：今日数据概览
 */
public record DashboardSummary(
        Long todayAppointmentCount,
        Long todayCompletedEncounterCount,
        Long currentOnDutyDoctorCount,
        Long currentAvailableDeviceCount,
        Long highPriorityTriageCount
) {
}
