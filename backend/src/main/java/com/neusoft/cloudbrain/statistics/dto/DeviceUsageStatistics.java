package com.neusoft.cloudbrain.statistics.dto;

/**
 * 设备使用率统计
 *
 * 统计口径（来自 11_功能需求.md 第15.4节）：
 * - 单设备使用率：统计周期内该设备已完成使用记录的重叠时长总和 / 同期该设备处于 AVAILABLE 或 IN_USE 的有效管理时长
 * - 总体设备使用率：统计周期内全部设备使用时长总和 / 同期全部设备处于 AVAILABLE 或 IN_USE 的有效管理时长总和
 * - 使用率结果限制在 0% 到 100%，无可用设备或无使用记录时为 0%
 */
public record DeviceUsageStatistics(
        Long deviceId,
        String deviceName,
        String deviceType,
        Long usageCount,
        Long totalUsageSeconds,
        Double usageRate
) {
}
