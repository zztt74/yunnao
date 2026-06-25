package com.neusoft.cloudbrain.statistics.dto;

/**
 * 科室门诊量统计
 */
public record DepartmentOutpatientStatistics(
        Long departmentId,
        String departmentName,
        Long encounterCount
) {
}
