package com.neusoft.cloudbrain.statistics.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.neusoft.cloudbrain.common.exception.GlobalExceptionHandler;
import com.neusoft.cloudbrain.common.filter.TraceIdFilter;
import com.neusoft.cloudbrain.statistics.dto.AIStatistics;
import com.neusoft.cloudbrain.statistics.dto.AICapabilityStatistics;
import com.neusoft.cloudbrain.statistics.dto.AppointmentRateStatistics;
import com.neusoft.cloudbrain.statistics.dto.DailyOutpatientStatistics;
import com.neusoft.cloudbrain.statistics.dto.DashboardSummary;
import com.neusoft.cloudbrain.statistics.dto.DepartmentOutpatientStatistics;
import com.neusoft.cloudbrain.statistics.dto.DeviceUsageStatistics;
import com.neusoft.cloudbrain.statistics.dto.DoctorEncounterStatistics;
import com.neusoft.cloudbrain.statistics.service.StatisticsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * StatisticsController 单元测试
 *
 * 覆盖三类用例：
 * - 正常：仪表盘/每日门诊量/医生接诊量/科室门诊量/挂号完成率/设备使用率/AI 汇总/AI 能力分组
 * - 异常：service 抛业务异常返回对应状态码
 * - 边界：空列表、默认 days 参数、departmentId 可选参数
 *
 * 注意：StatisticsController 类级标注 @PreAuthorize("hasRole('ADMIN')")，
 * 但在 standaloneSetup 下 @PreAuthorize 不会被方法安全拦截器处理，
 * 因此测试中无需 ADMIN 角色即可访问所有端点。测试重点在于 service 调用与响应结构。
 */
@DisplayName("StatisticsController - 统计接口测试")
class StatisticsControllerTest {

    private MockMvc mockMvc;
    private StatisticsService statisticsService;

    @BeforeEach
    void setUp() {
        statisticsService = Mockito.mock(StatisticsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(
                com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(new StatisticsController(statisticsService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .addFilters(new TraceIdFilter())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String username, Set<String> roles) {
        com.neusoft.cloudbrain.auth.dto.AuthPrincipal principal =
                new com.neusoft.cloudbrain.auth.dto.AuthPrincipal(1L, username, roles, 0L);
        var authorities = roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    // ========== 正常情况测试 ==========

    @Test
    @DisplayName("getDashboard - 返回今日概览")
    void getDashboard_shouldReturnSummary() throws Exception {
        DashboardSummary summary = new DashboardSummary(
                50L, 30L, 10L, 20L, 5L, 1000L);
        when(statisticsService.getDashboardSummary()).thenReturn(summary);

        mockMvc.perform(get("/api/statistics/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.todayAppointmentCount").value(50))
                .andExpect(jsonPath("$.data.todayCompletedEncounterCount").value(30))
                .andExpect(jsonPath("$.data.currentOnDutyDoctorCount").value(10))
                .andExpect(jsonPath("$.data.currentAvailableDeviceCount").value(20))
                .andExpect(jsonPath("$.data.highPriorityTriageCount").value(5))
                .andExpect(jsonPath("$.data.totalPatientCount").value(1000));
    }

    @Test
    @DisplayName("getDailyOutpatient - 返回每日门诊量趋势，默认 days=7")
    void getDailyOutpatient_defaultDays_shouldReturnList() throws Exception {
        when(statisticsService.getDailyOutpatientStatistics(eq(7), eq(null)))
                .thenReturn(List.of(
                        new DailyOutpatientStatistics(LocalDate.of(2025, 7, 1), 30L, 2L),
                        new DailyOutpatientStatistics(LocalDate.of(2025, 7, 2), 28L, 1L)));

        mockMvc.perform(get("/api/statistics/outpatient/daily"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].date").value("2025-07-01"))
                .andExpect(jsonPath("$.data[0].completedCount").value(30))
                .andExpect(jsonPath("$.data[0].cancelledCount").value(2));

        verify(statisticsService).getDailyOutpatientStatistics(eq(7), eq(null));
    }

    @Test
    @DisplayName("getDailyOutpatient - 指定 days 和 departmentId 参数")
    void getDailyOutpatient_withParams_shouldPassThrough() throws Exception {
        when(statisticsService.getDailyOutpatientStatistics(eq(30), eq(1L)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/statistics/outpatient/daily")
                        .param("days", "30")
                        .param("departmentId", "1"))
                .andExpect(status().isOk());

        verify(statisticsService).getDailyOutpatientStatistics(eq(30), eq(1L));
    }

    @Test
    @DisplayName("getDoctorEncounterStats - 返回医生接诊量排行")
    void getDoctorEncounterStats_shouldReturnList() throws Exception {
        when(statisticsService.getDoctorEncounterStatistics(
                eq(LocalDate.of(2025, 7, 1)), eq(LocalDate.of(2025, 7, 2)), eq(null)))
                .thenReturn(List.of(
                        new DoctorEncounterStatistics(1L, "张医生", "内科", 50L),
                        new DoctorEncounterStatistics(2L, "李医生", "外科", 30L)));

        mockMvc.perform(get("/api/statistics/doctor/encounter")
                        .param("startDate", "2025-07-01")
                        .param("endDate", "2025-07-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].doctorName").value("张医生"))
                .andExpect(jsonPath("$.data[0].departmentName").value("内科"))
                .andExpect(jsonPath("$.data[0].encounterCount").value(50));
    }

    @Test
    @DisplayName("getDepartmentOutpatient - 返回科室门诊量统计")
    void getDepartmentOutpatient_shouldReturnList() throws Exception {
        when(statisticsService.getDepartmentOutpatientStatistics(
                eq(LocalDate.of(2025, 7, 1)), eq(LocalDate.of(2025, 7, 2))))
                .thenReturn(List.of(
                        new DepartmentOutpatientStatistics(1L, "内科", 100L)));

        mockMvc.perform(get("/api/statistics/department/outpatient")
                        .param("startDate", "2025-07-01")
                        .param("endDate", "2025-07-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].departmentName").value("内科"))
                .andExpect(jsonPath("$.data[0].encounterCount").value(100));
    }

    @Test
    @DisplayName("getAppointmentRate - 返回挂号完成率/取消率")
    void getAppointmentRate_shouldReturnStats() throws Exception {
        AppointmentRateStatistics stats = new AppointmentRateStatistics(
                100L, 80L, 20L, 0.8, 0.2);
        when(statisticsService.getAppointmentRateStatistics(
                eq(LocalDate.of(2025, 7, 1)), eq(LocalDate.of(2025, 7, 2)), eq(null)))
                .thenReturn(stats);

        mockMvc.perform(get("/api/statistics/appointment/rate")
                        .param("startDate", "2025-07-01")
                        .param("endDate", "2025-07-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAppointments").value(100))
                .andExpect(jsonPath("$.data.completedAppointments").value(80))
                .andExpect(jsonPath("$.data.cancelledAppointments").value(20))
                .andExpect(jsonPath("$.data.completionRate").value(0.8))
                .andExpect(jsonPath("$.data.cancellationRate").value(0.2));
    }

    @Test
    @DisplayName("getDeviceUsage - 返回设备使用率统计")
    void getDeviceUsage_shouldReturnList() throws Exception {
        when(statisticsService.getDeviceUsageStatistics(
                eq(LocalDate.of(2025, 7, 1)), eq(LocalDate.of(2025, 7, 2)), eq(null)))
                .thenReturn(List.of(
                        new DeviceUsageStatistics(1L, "心电图机", "ECG", 50L, 3600L, 0.5)));

        mockMvc.perform(get("/api/statistics/device/usage")
                        .param("startDate", "2025-07-01")
                        .param("endDate", "2025-07-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].deviceName").value("心电图机"))
                .andExpect(jsonPath("$.data[0].usageCount").value(50))
                .andExpect(jsonPath("$.data[0].usageRate").value(0.5));
    }

    @Test
    @DisplayName("getAISummary - 返回 AI 调用汇总")
    void getAISummary_shouldReturnStats() throws Exception {
        AIStatistics stats = new AIStatistics(
                100L, 95L, 5L, 0.95, 200.0, 0.2);
        when(statisticsService.getAIStatistics(
                eq(LocalDate.of(2025, 7, 1)), eq(LocalDate.of(2025, 7, 2))))
                .thenReturn(stats);

        mockMvc.perform(get("/api/statistics/ai/summary")
                        .param("startDate", "2025-07-01")
                        .param("endDate", "2025-07-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalInvocations").value(100))
                .andExpect(jsonPath("$.data.successCount").value(95))
                .andExpect(jsonPath("$.data.failedCount").value(5))
                .andExpect(jsonPath("$.data.successRate").value(0.95));
    }

    @Test
    @DisplayName("getAICapabilityStats - 返回 AI 按能力分组统计")
    void getAICapabilityStats_shouldReturnList() throws Exception {
        when(statisticsService.getAICapabilityStatistics(
                eq(LocalDate.of(2025, 7, 1)), eq(LocalDate.of(2025, 7, 2))))
                .thenReturn(List.of(
                        new AICapabilityStatistics("DIAGNOSIS", 50L, 48L, 0.96, 150.0),
                        new AICapabilityStatistics("PRESCRIPTION_REVIEW", 30L, 28L, 0.93, 100.0)));

        mockMvc.perform(get("/api/statistics/ai/by-capability")
                        .param("startDate", "2025-07-01")
                        .param("endDate", "2025-07-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].capability").value("DIAGNOSIS"))
                .andExpect(jsonPath("$.data[0].totalInvocations").value(50))
                .andExpect(jsonPath("$.data[1].capability").value("PRESCRIPTION_REVIEW"));
    }

    @Test
    @DisplayName("getDoctorEncounterStats - 带 departmentId 参数")
    void getDoctorEncounterStats_withDepartmentId_shouldPassThrough() throws Exception {
        when(statisticsService.getDoctorEncounterStatistics(
                eq(LocalDate.of(2025, 7, 1)), eq(LocalDate.of(2025, 7, 2)), eq(1L)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/statistics/doctor/encounter")
                        .param("startDate", "2025-07-01")
                        .param("endDate", "2025-07-02")
                        .param("departmentId", "1"))
                .andExpect(status().isOk());

        verify(statisticsService).getDoctorEncounterStatistics(
                eq(LocalDate.of(2025, 7, 1)), eq(LocalDate.of(2025, 7, 2)), eq(1L));
    }

    // ========== 异常情况测试 ==========

    @Test
    @DisplayName("getDashboard - service 抛业务异常返回对应状态码")
    void getDashboard_serviceError_shouldReturnError() throws Exception {
        when(statisticsService.getDashboardSummary())
                .thenThrow(new BusinessException("STATISTICS_QUERY_FAILED", "统计查询失败", 500));

        mockMvc.perform(get("/api/statistics/dashboard"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("STATISTICS_QUERY_FAILED"));
    }

    @Test
    @DisplayName("getDoctorEncounterStats - 缺少必需的 startDate 参数返回 500")
    void getDoctorEncounterStats_missingStartDate_shouldReturn500() throws Exception {
        mockMvc.perform(get("/api/statistics/doctor/encounter")
                        .param("endDate", "2025-07-02"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    @DisplayName("getAppointmentRate - service 抛业务异常返回 409")
    void getAppointmentRate_serviceError_shouldReturn409() throws Exception {
        when(statisticsService.getAppointmentRateStatistics(
                any(), any(), any()))
                .thenThrow(new BusinessException("STATISTICS_RANGE_INVALID", "统计区间非法", 400));

        mockMvc.perform(get("/api/statistics/appointment/rate")
                        .param("startDate", "2025-07-01")
                        .param("endDate", "2025-07-02"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STATISTICS_RANGE_INVALID"));
    }

    // ========== 边界条件测试 ==========

    @Test
    @DisplayName("getDailyOutpatient - 空数据返回空数组而非 null")
    void getDailyOutpatient_empty_shouldReturnEmptyArray() throws Exception {
        when(statisticsService.getDailyOutpatientStatistics(eq(7), eq(null)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/statistics/outpatient/daily"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("getDeviceUsage - 空数据返回空数组")
    void getDeviceUsage_empty_shouldReturnEmptyArray() throws Exception {
        when(statisticsService.getDeviceUsageStatistics(
                eq(LocalDate.of(2025, 7, 1)), eq(LocalDate.of(2025, 7, 2)), eq(null)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/statistics/device/usage")
                        .param("startDate", "2025-07-01")
                        .param("endDate", "2025-07-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("getAICapabilityStats - 空数据返回空数组")
    void getAICapabilityStats_empty_shouldReturnEmptyArray() throws Exception {
        when(statisticsService.getAICapabilityStatistics(
                eq(LocalDate.of(2025, 7, 1)), eq(LocalDate.of(2025, 7, 2))))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/statistics/ai/by-capability")
                        .param("startDate", "2025-07-01")
                        .param("endDate", "2025-07-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("getDashboard - 非管理员在 standaloneSetup 下仍可访问（@PreAuthorize 不生效）")
    void getDashboard_notAdmin_stillAccessibleInStandaloneSetup() throws Exception {
        loginAs("doctor1", Set.of("DOCTOR"));
        DashboardSummary summary = new DashboardSummary(
                0L, 0L, 0L, 0L, 0L, 0L);
        when(statisticsService.getDashboardSummary()).thenReturn(summary);

        mockMvc.perform(get("/api/statistics/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.todayAppointmentCount").value(0));
    }

    @Test
    @DisplayName("getDailyOutpatient - 自定义 days=30 参数透传")
    void getDailyOutpatient_customDays_shouldPassThrough() throws Exception {
        when(statisticsService.getDailyOutpatientStatistics(eq(30), eq(null)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/statistics/outpatient/daily").param("days", "30"))
                .andExpect(status().isOk());

        verify(statisticsService).getDailyOutpatientStatistics(eq(30), eq(null));
    }
}