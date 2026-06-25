package com.neusoft.cloudbrain.statistics.controller;

import com.neusoft.cloudbrain.common.api.ApiResponse;
import com.neusoft.cloudbrain.statistics.dto.*;
import com.neusoft.cloudbrain.statistics.service.StatisticsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 统计接口
 *
 * 接口（来自 11_功能需求.md 第15节）：
 * - GET /api/statistics/dashboard               今日概览
 * - GET /api/statistics/outpatient/daily         每日门诊量趋势
 * - GET /api/statistics/doctor/encounter         医生接诊量排行
 * - GET /api/statistics/department/outpatient    科室门诊量
 * - GET /api/statistics/appointment/rate         挂号完成率/取消率
 * - GET /api/statistics/device/usage             设备使用率
 * - GET /api/statistics/ai/summary               AI 调用汇总
 * - GET /api/statistics/ai/by-capability         AI 按能力分组统计
 *
 * 关键约束：
 * - 统计模块只读
 * - 仅管理员可访问
 * - 所有按日统计使用 Asia/Shanghai 自然日
 * - 空数据返回空集合而非异常
 */
@RestController
@RequestMapping("/api/statistics")
@PreAuthorize("hasRole('ADMIN')")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    /**
     * 今日概览
     */
    @GetMapping("/dashboard")
    public ApiResponse<DashboardSummary> getDashboard(HttpServletRequest httpRequest) {
        return ApiResponse.success(statisticsService.getDashboardSummary(),
                (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 每日门诊量趋势
     *
     * @param days 天数，默认 7
     * @param departmentId 科室 ID（可选）
     */
    @GetMapping("/outpatient/daily")
    public ApiResponse<List<DailyOutpatientStatistics>> getDailyOutpatient(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) Long departmentId,
            HttpServletRequest httpRequest) {
        return ApiResponse.success(
                statisticsService.getDailyOutpatientStatistics(days, departmentId),
                (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 医生接诊量排行
     */
    @GetMapping("/doctor/encounter")
    public ApiResponse<List<DoctorEncounterStatistics>> getDoctorEncounterStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long departmentId,
            HttpServletRequest httpRequest) {
        return ApiResponse.success(
                statisticsService.getDoctorEncounterStatistics(startDate, endDate, departmentId),
                (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 科室门诊量统计
     */
    @GetMapping("/department/outpatient")
    public ApiResponse<List<DepartmentOutpatientStatistics>> getDepartmentOutpatient(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpServletRequest httpRequest) {
        return ApiResponse.success(
                statisticsService.getDepartmentOutpatientStatistics(startDate, endDate),
                (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 挂号完成率/取消率统计
     */
    @GetMapping("/appointment/rate")
    public ApiResponse<AppointmentRateStatistics> getAppointmentRate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long departmentId,
            HttpServletRequest httpRequest) {
        return ApiResponse.success(
                statisticsService.getAppointmentRateStatistics(startDate, endDate, departmentId),
                (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 设备使用率统计
     */
    @GetMapping("/device/usage")
    public ApiResponse<List<DeviceUsageStatistics>> getDeviceUsage(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long departmentId,
            HttpServletRequest httpRequest) {
        return ApiResponse.success(
                statisticsService.getDeviceUsageStatistics(startDate, endDate, departmentId),
                (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * AI 调用汇总
     */
    @GetMapping("/ai/summary")
    public ApiResponse<AIStatistics> getAISummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpServletRequest httpRequest) {
        return ApiResponse.success(
                statisticsService.getAIStatistics(startDate, endDate),
                (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * AI 按能力分组统计
     */
    @GetMapping("/ai/by-capability")
    public ApiResponse<List<AICapabilityStatistics>> getAICapabilityStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpServletRequest httpRequest) {
        return ApiResponse.success(
                statisticsService.getAICapabilityStatistics(startDate, endDate),
                (String) httpRequest.getAttribute("traceId"));
    }
}
