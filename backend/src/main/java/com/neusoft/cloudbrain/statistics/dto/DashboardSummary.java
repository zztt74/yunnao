package com.neusoft.cloudbrain.statistics.dto;

/**
 * 仪表盘概览统计
 *
 * 来自 11_功能需求.md 第15.3节：今日数据概览
 * totalPatientCount：系统累计患者总数（B6，来自真实患者表）
 */
public record DashboardSummary(
        Long todayAppointmentCount,
        Long todayCompletedEncounterCount,
        Long currentOnDutyDoctorCount,
        Long currentAvailableDeviceCount,
        Long highPriorityTriageCount,
        Long totalPatientCount
) {
}
