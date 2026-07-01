package com.neusoft.cloudbrain.schedule.controller;

import com.neusoft.cloudbrain.common.api.ApiResponse;
import com.neusoft.cloudbrain.common.api.PageResponse;
import com.neusoft.cloudbrain.schedule.dto.ScheduleCancelRequest;
import com.neusoft.cloudbrain.schedule.dto.ScheduleCreateRequest;
import com.neusoft.cloudbrain.schedule.dto.ScheduleResponse;
import com.neusoft.cloudbrain.schedule.dto.ScheduleUpdateRequest;
import com.neusoft.cloudbrain.schedule.service.ScheduleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 排班接口
 *
 * - POST   /api/schedules              创建排班（管理员）
 * - PUT    /api/schedules/{id}         修改排班（管理员）
 * - POST   /api/schedules/{id}/cancel  取消排班（管理员）
 * - GET    /api/schedules/{id}         排班详情
 * - GET    /api/schedules/doctor/{doctorId}      按医生查询排班
 * - GET    /api/schedules/department/{deptId}    按科室查询排班
 * - GET    /api/schedules/available              查询可预约排班（患者）
 */
@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    /**
     * 创建排班（管理员）
     */
    @PostMapping
    public ApiResponse<ScheduleResponse> create(
            @Valid @RequestBody ScheduleCreateRequest request,
            HttpServletRequest httpRequest) {
        ScheduleResponse response = scheduleService.createSchedule(request);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 修改排班（管理员）
     */
    @PutMapping("/{id}")
    public ApiResponse<ScheduleResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ScheduleUpdateRequest request,
            HttpServletRequest httpRequest) {
        ScheduleResponse response = scheduleService.updateSchedule(id, request);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 取消排班（管理员）
     */
    @PostMapping("/{id}/cancel")
    public ApiResponse<ScheduleResponse> cancel(
            @PathVariable Long id,
            @Valid @RequestBody ScheduleCancelRequest request,
            HttpServletRequest httpRequest) {
        ScheduleResponse response = scheduleService.cancelSchedule(id, request);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 获取排班详情
     */
    @GetMapping("/{id}")
    public ApiResponse<ScheduleResponse> getById(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        ScheduleResponse response = scheduleService.getScheduleById(id);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 按医生查询排班（分页）
     */
    @GetMapping("/doctor/{doctorId}")
    public ApiResponse<PageResponse<ScheduleResponse>> getByDoctor(
            @PathVariable Long doctorId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") @Max(100) int size,
            HttpServletRequest httpRequest) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(size, 100));
        Page<ScheduleResponse> response = scheduleService.getSchedulesByDoctor(doctorId, pageable);
        return ApiResponse.success(PageResponse.from(response), (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 按科室查询排班
     */
    @GetMapping("/department/{departmentId}")
    public ApiResponse<List<ScheduleResponse>> getByDepartment(
            @PathVariable Long departmentId,
            HttpServletRequest httpRequest) {
        List<ScheduleResponse> response = scheduleService.getSchedulesByDepartment(departmentId);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 查询可预约排班（患者端）
     */
    @GetMapping("/available")
    public ApiResponse<List<ScheduleResponse>> getAvailable(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String date,
            HttpServletRequest httpRequest) {
        List<ScheduleResponse> response = scheduleService.getAvailableSchedules(departmentId, parseDate(date));
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    private LocalDateTime parseDate(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        String trimmed = date.trim();
        if (trimmed.length() == 10) {
            return LocalDate.parse(trimmed).atStartOfDay();
        }
        return LocalDateTime.parse(trimmed);
    }
}
