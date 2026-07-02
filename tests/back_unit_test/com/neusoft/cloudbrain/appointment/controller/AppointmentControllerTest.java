package com.neusoft.cloudbrain.appointment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neusoft.cloudbrain.appointment.dto.AppointmentCancelRequest;
import com.neusoft.cloudbrain.appointment.dto.AppointmentCreateRequest;
import com.neusoft.cloudbrain.appointment.dto.AppointmentResponse;
import com.neusoft.cloudbrain.appointment.service.AppointmentService;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.neusoft.cloudbrain.common.exception.GlobalExceptionHandler;
import com.neusoft.cloudbrain.common.filter.TraceIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AppointmentController 单元测试
 *
 * 覆盖三类用例：
 * - 正常：创建/取消/详情/按患者查询/按医生查询/待诊队列/全部挂号
 * - 异常：挂号不存在返回 404，取消冲突返回 409，创建冲突返回 409
 * - 边界：空列表、分页参数转换、参数校验（缺少必填字段）、size 超限被拒绝
 * 注意：AppointmentController 所有接口均无权限校验。
 */
@DisplayName("AppointmentController - 挂号接口测试")
class AppointmentControllerTest {

    private MockMvc mockMvc;
    private AppointmentService appointmentService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        appointmentService = Mockito.mock(AppointmentService.class);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(new AppointmentController(appointmentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .addFilters(new TraceIdFilter())
                .build();
    }

    private AppointmentResponse sampleAppointment(Long id, String status) {
        return new AppointmentResponse(
                id, 1L, "张三", 1L, 1L, "张医生", 1L, "内科",
                "APT-20250702-001", status,
                LocalDateTime.of(2025, 7, 2, 9, 0),
                null, null, null, null,
                LocalDateTime.of(2025, 7, 1, 10, 0),
                LocalDateTime.of(2025, 7, 1, 10, 0));
    }

    // ============================================================
    // 创建挂号
    // ============================================================

    @Test
    @DisplayName("create - 创建挂号成功应返回 200")
    void create_shouldReturn200() throws Exception {
        AppointmentResponse response = sampleAppointment(1L, "BOOKED");
        when(appointmentService.createAppointment(any(AppointmentCreateRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":1,\"scheduleId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("BOOKED"));
    }

    @Test
    @DisplayName("create - 缺少 patientId 应返回 400")
    void create_missingPatientId_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduleId\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("create - 缺少 scheduleId 应返回 400")
    void create_missingScheduleId_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("create - 号源已满应返回 409")
    void create_conflict_shouldReturn409() throws Exception {
        when(appointmentService.createAppointment(any(AppointmentCreateRequest.class)))
                .thenThrow(new BusinessException("APPOINTMENT_CONFLICT", "号源已满", 409));

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":1,\"scheduleId\":1}"))
                .andExpect(status().isConflict());
    }

    // ============================================================
    // 取消挂号
    // ============================================================

    @Test
    @DisplayName("cancel - 取消挂号成功应返回 200")
    void cancel_shouldReturn200() throws Exception {
        AppointmentResponse response = sampleAppointment(1L, "CANCELLED");
        response = new AppointmentResponse(
                1L, 1L, "张三", 1L, 1L, "张医生", 1L, "内科",
                "APT-20250702-001", "CANCELLED",
                LocalDateTime.of(2025, 7, 2, 9, 0),
                null, "患者临时有事", "PATIENT",
                LocalDateTime.of(2025, 7, 2, 10, 0),
                LocalDateTime.of(2025, 7, 1, 10, 0),
                LocalDateTime.of(2025, 7, 2, 10, 0));
        when(appointmentService.cancelAppointment(eq(1L), any(AppointmentCancelRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/appointments/1/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"患者临时有事\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("cancel - 缺少取消原因应返回 400")
    void cancel_missingReason_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/appointments/1/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("cancel - 取消原因超过 255 字符应返回 400")
    void cancel_reasonTooLong_shouldReturn400() throws Exception {
        String longReason = "a".repeat(256);
        mockMvc.perform(post("/api/appointments/1/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + longReason + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("cancel - 挂号不存在应返回 404")
    void cancel_notFound_shouldReturn404() throws Exception {
        when(appointmentService.cancelAppointment(eq(99L), any(AppointmentCancelRequest.class)))
                .thenThrow(new BusinessException("APPOINTMENT_NOT_FOUND", "挂号不存在", 404));

        mockMvc.perform(post("/api/appointments/99/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"取消\"}"))
                .andExpect(status().isNotFound());
    }

    // ============================================================
    // 获取挂号详情
    // ============================================================

    @Test
    @DisplayName("getById - 查询挂号详情成功应返回 200")
    void getById_shouldReturn200() throws Exception {
        AppointmentResponse response = sampleAppointment(1L, "BOOKED");
        when(appointmentService.getAppointmentById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/appointments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.patientName").value("张三"));
    }

    @Test
    @DisplayName("getById - 挂号不存在应返回 404")
    void getById_notFound_shouldReturn404() throws Exception {
        when(appointmentService.getAppointmentById(99L))
                .thenThrow(new BusinessException("APPOINTMENT_NOT_FOUND", "挂号不存在", 404));

        mockMvc.perform(get("/api/appointments/99"))
                .andExpect(status().isNotFound());
    }

    // ============================================================
    // 按患者查询挂号列表（分页）
    // ============================================================

    @Test
    @DisplayName("getByPatient - 返回分页结果")
    void getByPatient_shouldReturnPage() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        Page<AppointmentResponse> page = new PageImpl<>(
                List.of(sampleAppointment(1L, "BOOKED")), pageable, 1);
        when(appointmentService.getAppointmentsByPatient(eq(1L), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/appointments/patient/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("getByPatient - 无数据时返回空页")
    void getByPatient_empty_shouldReturnEmptyPage() throws Exception {
        Page<AppointmentResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(appointmentService.getAppointmentsByPatient(eq(99L), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/appointments/patient/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    @DisplayName("getByPatient - page=2 应转换为 0-based offset=1")
    void getByPatient_page2_shouldConvertToOffset1() throws Exception {
        Page<AppointmentResponse> page = new PageImpl<>(
                List.of(), PageRequest.of(1, 20), 0);
        when(appointmentService.getAppointmentsByPatient(eq(1L), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/appointments/patient/1").param("page", "2"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(appointmentService).getAppointmentsByPatient(eq(1L), captor.capture());
        assertEquals(1, captor.getValue().getPageNumber());
        assertEquals(20, captor.getValue().getPageSize());
    }

    @Test
    @DisplayName("getByPatient - size 超过 100 上限被校验拒绝且不调用服务")
    void getByPatient_sizeOverLimit_shouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/appointments/patient/1").param("size", "500"))
                .andExpect(status().is5xxServerError());

        verify(appointmentService, never()).getAppointmentsByPatient(any(), any(Pageable.class));
    }

    // ============================================================
    // 按医生查询挂号列表（分页）
    // ============================================================

    @Test
    @DisplayName("getByDoctor - 返回分页结果")
    void getByDoctor_shouldReturnPage() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        Page<AppointmentResponse> page = new PageImpl<>(
                List.of(sampleAppointment(1L, "BOOKED")), pageable, 1);
        when(appointmentService.getAppointmentsByDoctor(eq(1L), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/appointments/doctor/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("getByDoctor - 无数据时返回空页")
    void getByDoctor_empty_shouldReturnEmptyPage() throws Exception {
        Page<AppointmentResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(appointmentService.getAppointmentsByDoctor(eq(99L), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/appointments/doctor/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    @DisplayName("getByDoctor - page=1 size=10 应正确传递分页参数")
    void getByDoctor_defaultParams_shouldPassCorrectPageable() throws Exception {
        Page<AppointmentResponse> page = new PageImpl<>(
                List.of(), PageRequest.of(0, 10), 0);
        when(appointmentService.getAppointmentsByDoctor(eq(1L), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/appointments/doctor/1")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(appointmentService).getAppointmentsByDoctor(eq(1L), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(10, captor.getValue().getPageSize());
    }

    // ============================================================
    // 医生待诊队列
    // ============================================================

    @Test
    @DisplayName("getDoctorPending - 返回待诊列表")
    void getDoctorPending_shouldReturnList() throws Exception {
        when(appointmentService.getDoctorPendingAppointments(1L))
                .thenReturn(List.of(
                        sampleAppointment(1L, "PENDING"),
                        sampleAppointment(2L, "PENDING")));

        mockMvc.perform(get("/api/appointments/doctor/1/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("getDoctorPending - 无待诊时返回空列表")
    void getDoctorPending_empty_shouldReturnEmptyList() throws Exception {
        when(appointmentService.getDoctorPendingAppointments(99L))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/appointments/doctor/99/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ============================================================
    // 全部挂号列表（管理员，分页）
    // ============================================================

    @Test
    @DisplayName("getAll - 返回分页结果")
    void getAll_shouldReturnPage() throws Exception {
        Page<AppointmentResponse> page = new PageImpl<>(
                List.of(sampleAppointment(1L, "BOOKED")), PageRequest.of(0, 20), 1);
        when(appointmentService.getAllAppointments(any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/appointments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("getAll - 无数据时返回空页")
    void getAll_empty_shouldReturnEmptyPage() throws Exception {
        Page<AppointmentResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(appointmentService.getAllAppointments(any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/appointments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    @DisplayName("getAll - page=3 size=50 应正确传递分页参数")
    void getAll_customParams_shouldPassCorrectPageable() throws Exception {
        Page<AppointmentResponse> page = new PageImpl<>(
                List.of(), PageRequest.of(2, 50), 0);
        when(appointmentService.getAllAppointments(any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/appointments")
                        .param("page", "3")
                        .param("size", "50"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(appointmentService).getAllAppointments(captor.capture());
        assertEquals(2, captor.getValue().getPageNumber());
        assertEquals(50, captor.getValue().getPageSize());
    }

    @Test
    @DisplayName("getAll - size 超过 100 上限被校验拒绝且不调用服务")
    void getAll_sizeOverLimit_shouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/appointments").param("size", "500"))
                .andExpect(status().is5xxServerError());

        verify(appointmentService, never()).getAllAppointments(any(Pageable.class));
    }
}
