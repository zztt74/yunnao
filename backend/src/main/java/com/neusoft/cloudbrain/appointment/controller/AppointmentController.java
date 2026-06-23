package com.neusoft.cloudbrain.appointment.controller;

import com.neusoft.cloudbrain.appointment.dto.AppointmentCancelRequest;
import com.neusoft.cloudbrain.appointment.dto.AppointmentCreateRequest;
import com.neusoft.cloudbrain.appointment.dto.AppointmentResponse;
import com.neusoft.cloudbrain.appointment.service.AppointmentService;
import com.neusoft.cloudbrain.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 挂号接口
 *
 * - POST   /api/appointments              创建挂号
 * - POST   /api/appointments/{id}/cancel  取消挂号
 * - GET    /api/appointments/{id}         挂号详情
 * - GET    /api/appointments/patient/{patientId}    患者挂号列表
 * - GET    /api/appointments/doctor/{doctorId}      医生挂号列表
 * - GET    /api/appointments/doctor/{doctorId}/pending  医生待诊队列
 * - GET    /api/appointments              全部挂号（管理员）
 */
@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    /**
     * 创建挂号
     */
    @PostMapping
    public ApiResponse<AppointmentResponse> create(
            @Valid @RequestBody AppointmentCreateRequest request,
            HttpServletRequest httpRequest) {
        AppointmentResponse response = appointmentService.createAppointment(request);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 取消挂号
     */
    @PostMapping("/{id}/cancel")
    public ApiResponse<AppointmentResponse> cancel(
            @PathVariable Long id,
            @Valid @RequestBody AppointmentCancelRequest request,
            HttpServletRequest httpRequest) {
        AppointmentResponse response = appointmentService.cancelAppointment(id, request);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 获取挂号详情
     */
    @GetMapping("/{id}")
    public ApiResponse<AppointmentResponse> getById(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        AppointmentResponse response = appointmentService.getAppointmentById(id);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 查询患者挂号列表（分页）
     */
    @GetMapping("/patient/{patientId}")
    public ApiResponse<Page<AppointmentResponse>> getByPatient(
            @PathVariable Long patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AppointmentResponse> response = appointmentService.getAppointmentsByPatient(patientId, pageable);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 查询医生挂号列表（分页）
     */
    @GetMapping("/doctor/{doctorId}")
    public ApiResponse<Page<AppointmentResponse>> getByDoctor(
            @PathVariable Long doctorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AppointmentResponse> response = appointmentService.getAppointmentsByDoctor(doctorId, pageable);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 查询医生待诊队列
     */
    @GetMapping("/doctor/{doctorId}/pending")
    public ApiResponse<List<AppointmentResponse>> getDoctorPending(
            @PathVariable Long doctorId,
            HttpServletRequest httpRequest) {
        List<AppointmentResponse> response = appointmentService.getDoctorPendingAppointments(doctorId);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 管理员查看全部挂号
     */
    @GetMapping
    public ApiResponse<List<AppointmentResponse>> getAll(
            HttpServletRequest httpRequest) {
        List<AppointmentResponse> response = appointmentService.getAllAppointments();
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }
}
