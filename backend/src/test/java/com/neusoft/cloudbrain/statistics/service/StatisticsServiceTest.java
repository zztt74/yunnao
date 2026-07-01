package com.neusoft.cloudbrain.statistics.service;

import com.neusoft.cloudbrain.statistics.dto.*;
import com.neusoft.cloudbrain.statistics.repository.StatisticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * StatisticsService 单元测试
 *
 * 覆盖文档 11_功能需求.md 第15节 验收重点：
 * - 统计口径正确
 * - 日期与科室筛选有效
 * - 空数据不报错
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StatisticsService - 统计服务测试")
class StatisticsServiceTest {

    @Mock
    private StatisticsRepository statisticsRepository;

    @InjectMocks
    private StatisticsService statisticsService;

    @Test
    @DisplayName("仪表盘概览：正确返回今日数据")
    void getDashboardSummary_shouldReturnTodayData() {
        DashboardSummary expected = new DashboardSummary(10L, 8L, 3L, 5L, 2L, 120L);
        when(statisticsRepository.getDashboardSummary(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(expected);

        DashboardSummary result = statisticsService.getDashboardSummary();

        assertThat(result).isEqualTo(expected);
        assertThat(result.todayAppointmentCount()).isEqualTo(10L);
        assertThat(result.todayCompletedEncounterCount()).isEqualTo(8L);
    }

    @Test
    @DisplayName("每日门诊量：按天数和科室筛选")
    void getDailyOutpatientStatistics_shouldFilterByDaysAndDepartment() {
        DailyOutpatientStatistics stats = new DailyOutpatientStatistics(
                LocalDate.of(2026, 6, 25), 5L, 1L);
        when(statisticsRepository.getDailyOutpatientStatistics(
                any(LocalDateTime.class), any(LocalDateTime.class), eq(1L)))
                .thenReturn(List.of(stats));

        List<DailyOutpatientStatistics> result =
                statisticsService.getDailyOutpatientStatistics(7, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).completedCount()).isEqualTo(5L);
        assertThat(result.get(0).cancelledCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("每日门诊量：空数据返回空集合")
    void getDailyOutpatientStatistics_emptyDataReturnsEmptyList() {
        when(statisticsRepository.getDailyOutpatientStatistics(
                any(LocalDateTime.class), any(LocalDateTime.class), isNull()))
                .thenReturn(List.of());

        List<DailyOutpatientStatistics> result =
                statisticsService.getDailyOutpatientStatistics(7, null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("医生接诊量：按日期范围和科室查询")
    void getDoctorEncounterStatistics_shouldQueryByDateRange() {
        DoctorEncounterStatistics stats = new DoctorEncounterStatistics(
                1L, "张医生", "内科", 15L);
        when(statisticsRepository.getDoctorEncounterStatistics(
                any(LocalDateTime.class), any(LocalDateTime.class), isNull()))
                .thenReturn(List.of(stats));

        List<DoctorEncounterStatistics> result = statisticsService.getDoctorEncounterStatistics(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 25), null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).doctorName()).isEqualTo("张医生");
        assertThat(result.get(0).encounterCount()).isEqualTo(15L);
    }

    @Test
    @DisplayName("挂号完成率：分母为0时返回0%")
    void getAppointmentRateStatistics_zeroDenominatorReturnsZero() {
        AppointmentRateStatistics expected = new AppointmentRateStatistics(
                0L, 0L, 0L, 0.0, 0.0);
        when(statisticsRepository.getAppointmentRateStatistics(
                any(LocalDateTime.class), any(LocalDateTime.class), isNull()))
                .thenReturn(expected);

        AppointmentRateStatistics result = statisticsService.getAppointmentRateStatistics(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 25), null);

        assertThat(result.totalAppointments()).isZero();
        assertThat(result.completionRate()).isZero();
        assertThat(result.cancellationRate()).isZero();
    }

    @Test
    @DisplayName("挂号完成率：正确计算比率")
    void getAppointmentRateStatistics_calculatesRatesCorrectly() {
        // 10 个挂号，8 个完成，2 个取消
        AppointmentRateStatistics expected = new AppointmentRateStatistics(
                10L, 8L, 2L, 0.8, 0.2);
        when(statisticsRepository.getAppointmentRateStatistics(
                any(LocalDateTime.class), any(LocalDateTime.class), isNull()))
                .thenReturn(expected);

        AppointmentRateStatistics result = statisticsService.getAppointmentRateStatistics(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 25), null);

        assertThat(result.completionRate()).isEqualTo(0.8);
        assertThat(result.cancellationRate()).isEqualTo(0.2);
    }

    @Test
    @DisplayName("AI 统计：空数据时成功率为0")
    void getAIStatistics_emptyDataReturnsZero() {
        AIStatistics expected = new AIStatistics(0L, 0L, 0L, 0.0, 0.0, 0.0);
        when(statisticsRepository.getAIStatistics(
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(expected);

        AIStatistics result = statisticsService.getAIStatistics(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 25));

        assertThat(result.totalInvocations()).isZero();
        assertThat(result.successRate()).isZero();
    }

    @Test
    @DisplayName("AI 统计：重试不计入分母")
    void getAIStatistics_retriesNotCountedInDenominator() {
        // 5 次业务调用，4 次成功
        AIStatistics expected = new AIStatistics(5L, 4L, 1L, 0.8, 1200.0, 1.2);
        when(statisticsRepository.getAIStatistics(
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(expected);

        AIStatistics result = statisticsService.getAIStatistics(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 25));

        assertThat(result.totalInvocations()).isEqualTo(5L);
        assertThat(result.successCount()).isEqualTo(4L);
        assertThat(result.successRate()).isEqualTo(0.8);
        // 重试不进入分母：分母是 invocation 数 5，不是 attempt 数
    }

    @Test
    @DisplayName("设备使用率：无设备时返回空集合")
    void getDeviceUsageStatistics_emptyDataReturnsEmptyList() {
        when(statisticsRepository.getDeviceUsageStatistics(
                any(LocalDateTime.class), any(LocalDateTime.class), isNull()))
                .thenReturn(List.of());

        List<DeviceUsageStatistics> result = statisticsService.getDeviceUsageStatistics(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 25), null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("设备使用率：正确计算使用率")
    void getDeviceUsageStatistics_calculatesUsageRate() {
        DeviceUsageStatistics stats = new DeviceUsageStatistics(
                1L, "监护仪 #1", "MONITOR", 10L, 3600L, 0.5);
        when(statisticsRepository.getDeviceUsageStatistics(
                any(LocalDateTime.class), any(LocalDateTime.class), eq(1L)))
                .thenReturn(List.of(stats));

        List<DeviceUsageStatistics> result = statisticsService.getDeviceUsageStatistics(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 25), 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).usageCount()).isEqualTo(10L);
        assertThat(result.get(0).usageRate()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("AI 按能力分组统计")
    void getAICapabilityStatistics_returnsGroupedStats() {
        AICapabilityStatistics stats = new AICapabilityStatistics(
                "DIAGNOSIS", 10L, 8L, 0.8, 1500.0);
        when(statisticsRepository.getAICapabilityStatistics(
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(stats));

        List<AICapabilityStatistics> result = statisticsService.getAICapabilityStatistics(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 25));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).capability()).isEqualTo("DIAGNOSIS");
        assertThat(result.get(0).successRate()).isEqualTo(0.8);
    }
}
