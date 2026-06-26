package com.neusoft.cloudbrain.device.controller;

import com.neusoft.cloudbrain.common.api.ApiResponse;
import com.neusoft.cloudbrain.common.api.PageResponse;
import com.neusoft.cloudbrain.device.dto.DeviceEndUsageRequest;
import com.neusoft.cloudbrain.device.dto.DeviceResponse;
import com.neusoft.cloudbrain.device.dto.DeviceStartUsageRequest;
import com.neusoft.cloudbrain.device.dto.DeviceStatusChangeRequest;
import com.neusoft.cloudbrain.device.dto.DeviceStatusHistoryResponse;
import com.neusoft.cloudbrain.device.dto.DeviceUsageResponse;
import com.neusoft.cloudbrain.device.service.DeviceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 设备接口
 *
 * 状态机接口（来自 12_业务流程与状态机.md 第10节）：
 * - POST /api/devices/{id}/usage/start        开始使用（AVAILABLE → IN_USE）
 * - POST /api/devices/{id}/usage/end           结束使用（IN_USE → AVAILABLE）
 * - POST /api/devices/{id}/status              状态变更（异常上报/送修/修复/停用/启用）
 *
 * 查询接口：
 * - GET  /api/devices/{id}                     设备详情
 * - GET  /api/devices/code/{code}              按编码查询
 * - GET  /api/devices/department/{departmentId}/available  科室可用设备
 * - GET  /api/devices/status/{status}          按状态查询
 * - GET  /api/devices                          搜索设备（关键字+状态+类型+科室）
 * - GET  /api/devices/{id}/usage               设备使用记录
 * - GET  /api/devices/{id}/history             设备状态变更历史
 * - GET  /api/devices/encounter/{encounterId}/usage  就诊的设备使用记录
 * - GET  /api/devices/user/{userId}/usage      操作人的使用记录（分页）
 *
 * 关键规则：
 * - 并发控制：使用 CAS 模式确保设备只能被一个就诊同时占用
 * - IN_USE → ABNORMAL 必须先结束使用记录
 * - 已停用设备不能直接使用，需先重新启用
 */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    // ============================================================
    // 状态机接口
    // ============================================================

    /**
     * 开始使用设备（AVAILABLE → IN_USE）
     *
     * 并发控制：CAS 模式，设备只能被一个就诊同时占用
     */
    @PostMapping("/{id}/usage/start")
    public ApiResponse<DeviceUsageResponse> startUsage(
            @PathVariable Long id,
            @Valid @RequestBody DeviceStartUsageRequest request,
            HttpServletRequest httpRequest) {
        DeviceUsageResponse response = deviceService.startUsage(request);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 结束设备使用（IN_USE → AVAILABLE）
     */
    @PostMapping("/{id}/usage/end")
    public ApiResponse<DeviceUsageResponse> endUsage(
            @PathVariable Long id,
            @RequestBody(required = false) DeviceEndUsageRequest request,
            HttpServletRequest httpRequest) {
        DeviceUsageResponse response = deviceService.endUsage(id,
                request != null ? request : new DeviceEndUsageRequest(null));
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 设备状态变更
     *
     * 用于异常上报、送修、修复完成、停用、重新启用等场景。
     * - IN_USE → ABNORMAL：发现异常（系统自动结束使用记录）
     * - ABNORMAL → MAINTENANCE：送修
     * - ABNORMAL → AVAILABLE：修复完成
     * - AVAILABLE → DISABLED：停用
     * - DISABLED → AVAILABLE：重新启用
     */
    @PostMapping("/{id}/status")
    public ApiResponse<DeviceResponse> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody DeviceStatusChangeRequest request,
            HttpServletRequest httpRequest) {
        DeviceResponse response = deviceService.changeDeviceStatus(id, request);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    // ============================================================
    // 查询接口
    // ============================================================

    /**
     * 获取设备详情
     */
    @GetMapping("/{id}")
    public ApiResponse<DeviceResponse> getById(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        DeviceResponse response = deviceService.getDeviceById(id);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 按编码查询设备
     */
    @GetMapping("/code/{code}")
    public ApiResponse<DeviceResponse> getByCode(
            @PathVariable String code,
            HttpServletRequest httpRequest) {
        DeviceResponse response = deviceService.getDeviceByCode(code);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 查询科室可用设备
     */
    @GetMapping("/department/{departmentId}/available")
    public ApiResponse<List<DeviceResponse>> getAvailableByDepartment(
            @PathVariable Long departmentId,
            HttpServletRequest httpRequest) {
        List<DeviceResponse> response = deviceService.getAvailableDevicesByDepartment(departmentId);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 按状态查询设备
     */
    @GetMapping("/status/{status}")
    public ApiResponse<List<DeviceResponse>> getByStatus(
            @PathVariable String status,
            HttpServletRequest httpRequest) {
        List<DeviceResponse> response = deviceService.getDevicesByStatus(status);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 搜索设备（关键字 + 状态 + 类型 + 科室）
     */
    @GetMapping
    public ApiResponse<PageResponse<DeviceResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") @Max(100) int size,
            HttpServletRequest httpRequest) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(size, 100));
        Page<DeviceResponse> response = deviceService.searchDevices(keyword, status, type, departmentId, pageable);
        return ApiResponse.success(PageResponse.from(response), (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 查询设备使用记录
     */
    @GetMapping("/{id}/usage")
    public ApiResponse<List<DeviceUsageResponse>> getUsageHistory(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        List<DeviceUsageResponse> response = deviceService.getDeviceUsageHistory(id);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 查询设备状态变更历史
     */
    @GetMapping("/{id}/history")
    public ApiResponse<List<DeviceStatusHistoryResponse>> getStatusHistory(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        List<DeviceStatusHistoryResponse> response = deviceService.getDeviceStatusHistory(id);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 查询就诊的设备使用记录
     */
    @GetMapping("/encounter/{encounterId}/usage")
    public ApiResponse<List<DeviceUsageResponse>> getUsageByEncounter(
            @PathVariable Long encounterId,
            HttpServletRequest httpRequest) {
        List<DeviceUsageResponse> response = deviceService.getDeviceUsageByEncounter(encounterId);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 查询操作人的设备使用记录（分页）
     */
    @GetMapping("/user/{userId}/usage")
    public ApiResponse<PageResponse<DeviceUsageResponse>> getUsageByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") @Max(100) int size,
            HttpServletRequest httpRequest) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(size, 100));
        Page<DeviceUsageResponse> response = deviceService.getDeviceUsageByUser(userId, pageable);
        return ApiResponse.success(PageResponse.from(response), (String) httpRequest.getAttribute("traceId"));
    }
}
