package com.neusoft.cloudbrain.statistics.service;

import com.neusoft.cloudbrain.statistics.dto.*;
import com.neusoft.cloudbrain.statistics.repository.StatisticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 统计 Service
 *
 * 核心原则（来自 11_功能需求.md 第15节）：
 * - 后端使用数据库聚合，不加载全量到内存
 * - 前端不下载全量数据自行统计
 * - 统计模块只读
 * - 空数据需要正确展示
 * - 所有按日统计使用 Asia/Shanghai 自然日 [00:00:00, 次日 00:00:00)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticsService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final StatisticsRepository statisticsRepository;

    /**
     * 仪表盘概览（今日数据）
     */
    public DashboardSummary getDashboardSummary() {
        LocalDateTime dayStart = todayStart();
        LocalDateTime dayEnd = todayStart().plusDays(1);
        return statisticsRepository.getDashboardSummary(dayStart, dayEnd);
    }

    /**
     * 每日门诊量趋势
     *
     * @param days 天数（7 或 30）
     * @param departmentId 科室 ID（可选）
     */
    public List<DailyOutpatientStatistics> getDailyOutpatientStatistics(int days, Long departmentId) {
        LocalDateTime end = todayStart().plusDays(1);
        LocalDateTime start = end.minusDays(days);
        return statisticsRepository.getDailyOutpatientStatistics(start, end, departmentId);
    }

    /**
     * 医生接诊量排行
     */
    public List<DoctorEncounterStatistics> getDoctorEncounterStatistics(
            LocalDate startDate, LocalDate endDate, Long departmentId) {
        LocalDateTime[] range = toRange(startDate, endDate);
        return statisticsRepository.getDoctorEncounterStatistics(range[0], range[1], departmentId);
    }

    /**
     * 科室门诊量统计
     */
    public List<DepartmentOutpatientStatistics> getDepartmentOutpatientStatistics(
            LocalDate startDate, LocalDate endDate) {
        LocalDateTime[] range = toRange(startDate, endDate);
        return statisticsRepository.getDepartmentOutpatientStatistics(range[0], range[1]);
    }

    /**
     * 挂号完成率/取消率统计
     */
    public AppointmentRateStatistics getAppointmentRateStatistics(
            LocalDate startDate, LocalDate endDate, Long departmentId) {
        LocalDateTime[] range = toRange(startDate, endDate);
        return statisticsRepository.getAppointmentRateStatistics(range[0], range[1], departmentId);
    }

    /**
     * 设备使用率统计
     */
    public List<DeviceUsageStatistics> getDeviceUsageStatistics(
            LocalDate startDate, LocalDate endDate, Long departmentId) {
        LocalDateTime[] range = toRange(startDate, endDate);
        return statisticsRepository.getDeviceUsageStatistics(range[0], range[1], departmentId);
    }

    /**
     * AI 调用统计
     */
    public AIStatistics getAIStatistics(LocalDate startDate, LocalDate endDate) {
        LocalDateTime[] range = toRange(startDate, endDate);
        return statisticsRepository.getAIStatistics(range[0], range[1]);
    }

    /**
     * 按能力分组的 AI 调用统计
     */
    public List<AICapabilityStatistics> getAICapabilityStatistics(
            LocalDate startDate, LocalDate endDate) {
        LocalDateTime[] range = toRange(startDate, endDate);
        return statisticsRepository.getAICapabilityStatistics(range[0], range[1]);
    }

    /**
     * 将日期范围转换为 Asia/Shanghai 自然日边界 [start 00:00:00, end+1 00:00:00)
     */
    private LocalDateTime[] toRange(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();
        return new LocalDateTime[]{start, end};
    }

    /**
     * 获取 Asia/Shanghai 今日 00:00:00
     */
    private LocalDateTime todayStart() {
        return LocalDate.now(SHANGHAI).atStartOfDay();
    }
}
