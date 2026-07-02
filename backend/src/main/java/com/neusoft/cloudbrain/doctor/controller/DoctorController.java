package com.neusoft.cloudbrain.doctor.controller;

import com.neusoft.cloudbrain.auth.dto.AuthPrincipal;
import com.neusoft.cloudbrain.auth.security.SecurityUtils;
import com.neusoft.cloudbrain.common.api.ApiResponse;
import com.neusoft.cloudbrain.common.api.PageResponse;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.neusoft.cloudbrain.doctor.dto.DoctorCreateRequest;
import com.neusoft.cloudbrain.doctor.dto.DoctorProfileUpdateRequest;
import com.neusoft.cloudbrain.doctor.dto.DoctorResponse;
import com.neusoft.cloudbrain.doctor.dto.DoctorUpdateRequest;
import com.neusoft.cloudbrain.doctor.service.DoctorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 医生接口
 *
 * - GET    /api/doctors             医生列表（分页）
 * - GET    /api/doctors/{id}        医生详情
 * - GET    /api/doctors/by-department/{deptId}  科室下医生
 * - POST   /api/doctors             创建医生（管理员）
 * - PUT    /api/doctors/{id}        更新医生（管理员）
 */
@RestController
@RequestMapping("/api/doctors")
public class DoctorController {

    private final DoctorService doctorService;

    public DoctorController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    /**
     * 获取医生列表（分页）
     */
    @GetMapping
    public ApiResponse<PageResponse<DoctorResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") @Max(100) int pageSize,
            @RequestParam(required = false) String name,
            HttpServletRequest httpRequest) {
        int cappedSize = Math.min(pageSize, 100);
        Page<DoctorResponse> result;
        if (name != null && !name.isBlank()) {
            result = doctorService.searchByName(name, page, cappedSize);
        } else {
            result = doctorService.getDoctorList(page, cappedSize);
        }
        return ApiResponse.success(PageResponse.from(result), (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 获取医生详情
     */
    @GetMapping("/{id}")
    public ApiResponse<DoctorResponse> getById(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        DoctorResponse response = doctorService.getDoctorById(id);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 按科室查询医生
     */
    @GetMapping("/by-department/{deptId}")
    public ApiResponse<List<DoctorResponse>> getByDepartment(
            @PathVariable Long deptId,
            HttpServletRequest httpRequest) {
        List<DoctorResponse> response = doctorService.getDoctorsByDepartment(deptId);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 医生更新本人资料
     *
     * 仅医生角色可调用，允许更新专长/学历/从业年限/简介，
     * 不允许自行修改科室、职称、状态。
     */
    @PutMapping("/me")
    public ApiResponse<DoctorResponse> updateMyProfile(
            @Valid @RequestBody DoctorProfileUpdateRequest request,
            HttpServletRequest httpRequest) {
        AuthPrincipal currentUser = SecurityUtils.getCurrentUser();
        if (!currentUser.roles().contains("DOCTOR")) {
            throw new BusinessException("PERMISSION_DENIED", "仅医生可更新本人资料", 403);
        }
        DoctorResponse response = doctorService.updateMyProfile(currentUser.userId(), request);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 创建医生（管理员）
     */
    @PostMapping
    public ApiResponse<DoctorResponse> create(
            @Valid @RequestBody DoctorCreateRequest request,
            HttpServletRequest httpRequest) {
        checkAdminPermission();
        DoctorResponse response = doctorService.createDoctor(request);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 更新医生（管理员）
     */
    @PutMapping("/{id}")
    public ApiResponse<DoctorResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody DoctorUpdateRequest request,
            HttpServletRequest httpRequest) {
        checkAdminPermission();
        DoctorResponse response = doctorService.updateDoctor(id, request);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 校验管理员权限
     */
    private void checkAdminPermission() {
        AuthPrincipal currentUser = SecurityUtils.getCurrentUser();
        if (!currentUser.roles().contains("ADMIN")) {
            throw new BusinessException("PERMISSION_DENIED", "无权限执行该操作", 403);
        }
    }
}
