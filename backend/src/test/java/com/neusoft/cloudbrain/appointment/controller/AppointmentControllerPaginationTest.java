package com.neusoft.cloudbrain.appointment.controller;

import com.neusoft.cloudbrain.appointment.dto.AppointmentResponse;
import com.neusoft.cloudbrain.appointment.service.AppointmentService;
import com.neusoft.cloudbrain.common.exception.GlobalExceptionHandler;
import com.neusoft.cloudbrain.common.filter.TraceIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AppointmentController 分页参数集成测试
 *
 * 验证修复问题1（分页参数 page 从 1 开始）：
 * - 默认 page 值为 1（非 0）
 * - 传入 page=1 时 Service 收到 0-based offset=0
 * - 传入 page=2 时 Service 收到 0-based offset=1
 *
 * 契约：page 从 1 开始（参见 30_接口数据与错误契约.md §4）
 */
@DisplayName("AppointmentController - 分页参数契约测试（page 从 1 开始）")
class AppointmentControllerPaginationTest {

    private MockMvc mockMvc;
    private AppointmentService appointmentService;

    @BeforeEach
    void setUp() {
        // 直接构造 Service Mock，避免 Spring 上下文加载的依赖链问题
        appointmentService = Mockito.mock(AppointmentService.class);
        Page<AppointmentResponse> emptyPage = new PageImpl<>(
                Collections.emptyList(), PageRequest.of(0, 20), 0);
        when(appointmentService.getAppointmentsByPatient(any(Long.class), any(Pageable.class)))
                .thenReturn(emptyPage);
        when(appointmentService.getAppointmentsByDoctor(any(Long.class), any(Pageable.class)))
                .thenReturn(emptyPage);

        // standaloneSetup 不加载 SecurityConfig，绕过 JWT 过滤器依赖链，
        // 聚焦验证 Controller 的分页参数转换逻辑
        mockMvc = MockMvcBuilders.standaloneSetup(new AppointmentController(appointmentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new TraceIdFilter())
                .build();
    }

    @Test
    @DisplayName("查询患者挂号列表 - 不传 page 应使用默认值 1（0-based offset=0）")
    void shouldUseDefaultPage1WhenPageParamAbsent() throws Exception {
        mockMvc.perform(get("/api/appointments/patient/1"))
                .andExpect(status().isOk());

        Pageable captured = capturePatientPageable();
        org.assertj.core.api.Assertions.assertThat(captured.getPageNumber())
                .as("默认 page=1 应转换为 0-based offset=0")
                .isZero();
        org.assertj.core.api.Assertions.assertThat(captured.getPageSize())
                .as("默认 size 应为 20")
                .isEqualTo(20);
    }

    @Test
    @DisplayName("查询患者挂号列表 - 传入 page=1 应转换为 0-based offset=0")
    void shouldConvertPage1ToOffset0() throws Exception {
        mockMvc.perform(get("/api/appointments/patient/1").param("page", "1"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(capturePatientPageable().getPageNumber())
                .as("page=1 应转换为 0-based offset=0")
                .isZero();
    }

    @Test
    @DisplayName("查询患者挂号列表 - 传入 page=2 应转换为 0-based offset=1")
    void shouldConvertPage2ToOffset1() throws Exception {
        mockMvc.perform(get("/api/appointments/patient/1").param("page", "2"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(capturePatientPageable().getPageNumber())
                .as("page=2 应转换为 0-based offset=1")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("查询医生挂号列表 - 默认 page=1（0-based offset=0）")
    void doctorListShouldUseDefaultPage1() throws Exception {
        mockMvc.perform(get("/api/appointments/doctor/1"))
                .andExpect(status().isOk());

        Pageable captured = captureDoctorPageable();
        org.assertj.core.api.Assertions.assertThat(captured.getPageNumber())
                .as("默认 page=1 应转换为 0-based offset=0")
                .isZero();
    }

    @Test
    @DisplayName("查询医生挂号列表 - page=3 应转换为 0-based offset=2")
    void doctorListShouldConvertPage3ToOffset2() throws Exception {
        mockMvc.perform(get("/api/appointments/doctor/1").param("page", "3"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(captureDoctorPageable().getPageNumber())
                .as("page=3 应转换为 0-based offset=2")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("查询患者挂号列表 - 自定义 size 应正确传递")
    void shouldPassCustomSize() throws Exception {
        mockMvc.perform(get("/api/appointments/patient/1")
                        .param("page", "1")
                        .param("size", "50"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(capturePatientPageable().getPageSize())
                .as("自定义 size=50 应正确传递")
                .isEqualTo(50);
    }

    private Pageable capturePatientPageable() {
        org.mockito.ArgumentCaptor<Pageable> captor =
                org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(appointmentService).getAppointmentsByPatient(any(Long.class), captor.capture());
        return captor.getValue();
    }

    private Pageable captureDoctorPageable() {
        org.mockito.ArgumentCaptor<Pageable> captor =
                org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(appointmentService).getAppointmentsByDoctor(any(Long.class), captor.capture());
        return captor.getValue();
    }
}
