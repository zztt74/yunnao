package com.neusoft.cloudbrain.statistics.dto;

/**
 * 医生接诊量统计
 *
 * 统计口径（来自 11_功能需求.md 第15.4节）：
 * - 医生接诊量：医生完成的 Encounter 数
 */
public record DoctorEncounterStatistics(
        Long doctorId,
        String doctorName,
        String departmentName,
        Long encounterCount
) {
}
