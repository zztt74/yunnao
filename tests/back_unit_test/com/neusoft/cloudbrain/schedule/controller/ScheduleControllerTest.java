package com.neusoft.cloudbrain.schedule.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.neusoft.cloudbrain.common.exception.GlobalExceptionHandler;
import com.neusoft.cloudbrain.common.filter.TraceIdFilter;
import com.neusoft.cloudbrain.schedule.dto.ScheduleResponse;
import com.neusoft.cloudbrain.schedule.service.ScheduleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ScheduleController 单元测试
 *
 * 覆盖三类用例：
 * - 正常：排班详情/创建/更新/取消/按医生查询/按科室查询/可预约查询
 * - 异常：排班不存在返回 404，时间冲突返回 409
 * - 边界：空列表、分页参数、getAvailable 的 parseDate（null/10 位日期/完整日期时间）
 *
 * 注意：ScheduleController 所有接口均无权限校验。
 */
@DisplayName("ScheduleController - 排班接口测试")
class ScheduleControllerTest {

    private MockMvc mockMvc;
    private ScheduleService scheduleService;

    @BeforeEach
    void setUp() {
        scheduleService = Mockito.mock(ScheduleService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(
                com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(new ScheduleController(scheduleService))
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

    private ScheduleResponse sampleSchedule(Long id, String status) {
        return new ScheduleResponse(
                id, 1L, "张医生", 1L, "内科",
                LocalDate.of(2025, 7, 2),
                LocalDateTime.of(2025, 7, 2, 9, 0),
                LocalDateTime.of(2025, 7, 2, 12, 0),
                20, 5, 15, status,
                null, null,
                LocalDateTime.of(2025, 6, 1, 0, 0),
                LocalDateTime.of(2025, 6, 1, 0, 0));
    }

    // ========== 正常情况测试 ==========

    @Test
    @DisplayName("getById - 返回排班详情")
    void getById_shouldReturnDetail() throws Exception {
        when(scheduleService.getScheduleById(1L)).thenReturn(sampleSchedule(1L, "AVAILABLE"));

        mockMvc.perform(get("/api/schedules/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.doctorName").value("张医生"))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("create - 创建排班成功")
    void create_shouldReturnSchedule() throws Exception {
        when(scheduleService.createSchedule(any())).thenReturn(sampleSchedule(10L, "AVAILABLE"));

        mockMvc.perform(post("/api/schedules")
                        .contentType("application/json")
                        .content("{\"doctorId\":1,\"departmentId\":1,\"scheduleDate\":\"2025-07-02\","
                                + "\"startTime\":\"2025-07-02T09:00:00\",\"endTime\":\"2025-07-02T12:00:00\","
                                + "\"maxAppointments\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("update - 修改排班成功")
    void update_shouldReturnSchedule() throws Exception {
        when(scheduleService.updateSchedule(eq(1L), any())).thenReturn(sampleSchedule(1L, "AVAILABLE"));

        mockMvc.perform(put("/api/schedules/1")
                        .contentType("application/json")
                        .content("{\"startTime\":\"2025-07-02T09:00:00\","
                                + "\"endTime\":\"2025-07-02T11:00:00\",\"maxAppointments\":15}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("cancel - 取消排班成功")
    void cancel_shouldReturnSchedule() throws Exception {
        ScheduleResponse cancelled = new ScheduleResponse(
                1L, 1L, "张医生", 1L, "内科",
                LocalDate.of(2025, 7, 2),
                LocalDateTime.of(2025, 7, 2, 9, 0),
                LocalDateTime.of(2025, 7, 2, 12, 0),
                20, 5, 15, "CANCELLED",
                LocalDateTime.of(2025, 7, 1, 10, 0), "医生请假",
                LocalDateTime.of(2025, 6, 1, 0, 0),
                LocalDateTime.of(2025, 7, 1, 10, 0));
        when(scheduleService.cancelSchedule(eq(1L), any())).thenReturn(cancelled);

        mockMvc.perform(post("/api/schedules/1/cancel")
                        .contentType("application/json")
                        .content("{\"reason\":\"医生请假\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelReason").value("医生请假"));
    }

    @Test
    @DisplayName("getByDoctor - 按医生分页查询排班，page=1 转换为 offset=0")
    void getByDoctor_page1_shouldConvertToOffset0() throws Exception {
        Page<ScheduleResponse> page = new PageImpl<>(
                List.of(sampleSchedule(1L, "AVAILABLE")),
                PageRequest.of(0, 20), 1);

        when(scheduleService.getSchedulesByDoctor(eq(1L), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/schedules/doctor/1").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.total").value(1));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(scheduleService).getSchedulesByDoctor(eq(1L), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber(), "page=1 应转换为 0-based offset 0");
    }

    @Test
    @DisplayName("getByDepartment - 按科室查询排班列表")
    void getByDepartment_shouldReturnList() throws Exception {
        when(scheduleService.getSchedulesByDepartment(1L))
                .thenReturn(List.of(sampleSchedule(1L, "AVAILABLE"),
                        sampleSchedule(2L, "AVAILABLE")));

        mockMvc.perform(get("/api/schedules/department/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("getAvailable - 查询可预约排班")
    void getAvailable_shouldReturnList() throws Exception {
        when(scheduleService.getAvailableSchedules(eq(1L), any()))
                .thenReturn(List.of(sampleSchedule(1L, "AVAILABLE")));

        mockMvc.perform(get("/api/schedules/available").param("departmentId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("AVAILABLE"));
    }

    // ========== 异常情况测试 ==========

    @Test
    @DisplayName("getById - 排班不存在返回 404")
    void getById_notExist_shouldReturn404() throws Exception {
        when(scheduleService.getScheduleById(999L))
                .thenThrow(new BusinessException("SCHEDULE_NOT_FOUND", "排班不存在", 404));

        mockMvc.perform(get("/api/schedules/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCHEDULE_NOT_FOUND"));
    }

    @Test
    @DisplayName("update - 排班已取消抛业务异常返回 409")
    void update_statusConflict_shouldReturn409() throws Exception {
        when(scheduleService.updateSchedule(eq(1L), any()))
                .thenThrow(new BusinessException("SCHEDULE_STATUS_CONFLICT", "排班状态冲突", 409));

        mockMvc.perform(put("/api/schedules/1")
                        .contentType("application/json")
                        .content("{\"startTime\":\"2025-07-02T09:00:00\","
                                + "\"endTime\":\"2025-07-02T11:00:00\",\"maxAppointments\":15}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_STATUS_CONFLICT"));
    }

    @Test
    @DisplayName("cancel - 存在进行中挂号禁止取消返回 409")
    void cancel_activeAppointment_shouldReturn409() throws Exception {
        when(scheduleService.cancelSchedule(eq(1L), any()))
                .thenThrow(new BusinessException("SCHEDULE_CANCEL_CONFLICT",
                        "存在进行中的挂号，禁止取消排班", 409));

        mockMvc.perform(post("/api/schedules/1/cancel")
                        .contentType("application/json")
                        .content("{\"reason\":\"医生请假\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_CANCEL_CONFLICT"));
    }

    @Test
    @DisplayName("create - 时间冲突返回 409")
    void create_timeConflict_shouldReturn409() throws Exception {
        when(scheduleService.createSchedule(any()))
                .thenThrow(new BusinessException("SCHEDULE_CONFLICT", "排班时间冲突", 409));

        mockMvc.perform(post("/api/schedules")
                        .contentType("application/json")
                        .content("{\"doctorId\":1,\"departmentId\":1,\"scheduleDate\":\"2025-07-02\","
                                + "\"startTime\":\"2025-07-02T09:00:00\",\"endTime\":\"2025-07-02T12:00:00\","
                                + "\"maxAppointments\":20}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_CONFLICT"));
    }

    // ========== 边界条件测试 ==========

    @Test
    @DisplayName("getAvailable - date 参数为 null 时传 null 给 service")
    void getAvailable_nullDate_shouldPassNull() throws Exception {
        when(scheduleService.getAvailableSchedules(any(), isNull()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/schedules/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(scheduleService).getAvailableSchedules(any(), captor.capture());
        assertNull(captor.getValue(), "date 参数为空时 service 应收到 null");
    }

    @Test
    @DisplayName("getAvailable - 10 位日期字符串解析为当天 00:00:00")
    void getAvailable_dateOnly_shouldParseToStartOfDay() throws Exception {
        when(scheduleService.getAvailableSchedules(any(), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/schedules/available").param("date", "2025-07-02"))
                .andExpect(status().isOk());

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(scheduleService).getAvailableSchedules(any(), captor.capture());
        assertEquals(LocalDateTime.of(2025, 7, 2, 0, 0, 0), captor.getValue(),
                "10 位日期应解析为当天 00:00:00");
    }

    @Test
    @DisplayName("getAvailable - 完整日期时间字符串按 ISO 解析")
    void getAvailable_fullDateTime_shouldParseDirectly() throws Exception {
        when(scheduleService.getAvailableSchedules(any(), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/schedules/available").param("date", "2025-07-02T10:30:00"))
                .andExpect(status().isOk());

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(scheduleService).getAvailableSchedules(any(), captor.capture());
        assertEquals(LocalDateTime.of(2025, 7, 2, 10, 30, 0), captor.getValue(),
                "完整日期时间字符串应按 ISO_LOCAL_DATE_TIME 解析");
    }

    @Test
    @DisplayName("getByDepartment - 空列表返回空数组而非 null")
    void getByDepartment_empty_shouldReturnEmptyArray() throws Exception {
        when(scheduleService.getSchedulesByDepartment(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/schedules/department/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("getByDoctor - 空结果返回空 items 数组")
    void getByDoctor_empty_shouldReturnEmptyItems() throws Exception {
        Page<ScheduleResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(scheduleService.getSchedulesByDoctor(eq(1L), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/schedules/doctor/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("getByDoctor - 默认分页参数 page=1 size=20")
    void getByDoctor_defaultParams_shouldUseDefaults() throws Exception {
        Page<ScheduleResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(scheduleService.getSchedulesByDoctor(eq(1L), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/schedules/doctor/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(scheduleService).getSchedulesByDoctor(eq(1L), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(20, captor.getValue().getPageSize());
    }

    @Test
    @DisplayName("getByDoctor - size 超过 100 上限被校验拒绝且不调用服务")
    void getByDoctor_sizeOverLimit_shouldBeCapped() throws Exception {
        mockMvc.perform(get("/api/schedules/doctor/1").param("size", "500"))
                .andExpect(status().is5xxServerError());

        verify(scheduleService, never()).getSchedulesByDoctor(any(), any(Pageable.class));
    }
}
