package com.neusoft.cloudbrain.patient.controller;

import com.neusoft.cloudbrain.auth.dto.AuthPrincipal;
import com.neusoft.cloudbrain.auth.security.SecurityUtils;
import com.neusoft.cloudbrain.common.api.ApiResponse;
import com.neusoft.cloudbrain.common.api.PageResponse;
import com.neusoft.cloudbrain.common.api.PageUtils;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.neusoft.cloudbrain.patient.dto.*;
import com.neusoft.cloudbrain.patient.service.PatientService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 患者接口
 *
 * - POST   /api/patients/register      患者注册
 * - GET    /api/patients/me            查询本人信息
 * - GET    /api/patients/{id}          查询患者信息（权限校验）
 * - PUT    /api/patients/{id}          更新患者信息
 * - GET    /api/patients/{id}/profile  获取患者档案
 * - PUT    /api/patients/{id}/profile  更新患者档案
 * - GET    /api/patients/search        管理员搜索患者
 */
@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    /**
     * 患者注册
     */
    @PostMapping("/register")
    public ApiResponse<PatientResponse> register(
            @Valid @RequestBody PatientRegisterRequest request,
            HttpServletRequest httpRequest) {
        PatientResponse response = patientService.register(request);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 获取当前登录患者信息
     */
    @GetMapping("/me")
    public ApiResponse<PatientResponse> getCurrentPatient(HttpServletRequest httpRequest) {
        AuthPrincipal currentUser = SecurityUtils.getCurrentUser();
        PatientResponse response = patientService.getCurrentPatient(currentUser.userId());
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 获取患者详情
     */
    @GetMapping("/{id}")
    public ApiResponse<PatientResponse> getById(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        AuthPrincipal currentUser = SecurityUtils.getCurrentUser();
        PatientResponse response = patientService.getPatientById(id, currentUser.userId(), currentUser.roles());
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 更新患者信息
     */
    @PutMapping("/{id}")
    public ApiResponse<PatientResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody PatientUpdateRequest request,
            HttpServletRequest httpRequest) {
        AuthPrincipal currentUser = SecurityUtils.getCurrentUser();
        PatientResponse response = patientService.updatePatient(id, request, currentUser.userId());
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 获取患者档案
     */
    @GetMapping("/{id}/profile")
    public ApiResponse<PatientProfileResponse> getProfile(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        AuthPrincipal currentUser = SecurityUtils.getCurrentUser();
        PatientProfileResponse response = patientService.getPatientProfile(id, currentUser.userId(), currentUser.roles());
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 更新患者档案
     */
    @PutMapping("/{id}/profile")
    public ApiResponse<PatientProfileResponse> updateProfile(
            @PathVariable Long id,
            @Valid @RequestBody PatientProfileUpdateRequest request,
            HttpServletRequest httpRequest) {
        AuthPrincipal currentUser = SecurityUtils.getCurrentUser();
        PatientProfileResponse response = patientService.updatePatientProfile(id, request, currentUser.userId());
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 管理员搜索患者（按姓名或手机号）
     */
    @GetMapping("/search")
    public ApiResponse<List<PatientResponse>> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String phone,
            HttpServletRequest httpRequest) {
        // 权限校验：仅管理员可搜索患者
        AuthPrincipal currentUser = SecurityUtils.getCurrentUser();
        if (!currentUser.roles().contains("ADMIN")) {
            throw new BusinessException("PERMISSION_DENIED", "无权限搜索患者", 403);
        }

        List<PatientResponse> results;
        if (name != null && !name.isBlank()) {
            results = patientService.searchByName(name);
        } else if (phone != null && !phone.isBlank()) {
            results = patientService.searchByPhone(phone);
        } else {
            results = List.of();
        }
        return ApiResponse.success(results, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 管理员患者分页查询（B7）
     *
     * 分页查看患者列表，支持姓名模糊、手机号精确、状态筛选。
     * 简单关键字搜索仍可用 /search。
     */
    @GetMapping
    public ApiResponse<PageResponse<PatientResponse>> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Integer size,
            HttpServletRequest httpRequest) {
        checkAdminPermission();
        int resolvedSize = PageUtils.resolvePageSize(pageSize, size);
        Pageable pageable = PageUtils.toPageable(page, resolvedSize);
        Page<PatientResponse> response = patientService.listPatients(name, phone, status, pageable);
        return ApiResponse.success(PageResponse.from(response), (String) httpRequest.getAttribute("traceId"));
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
