package com.neusoft.cloudbrain.patient.controller;

import com.neusoft.cloudbrain.auth.dto.AuthPrincipal;
import com.neusoft.cloudbrain.auth.security.SecurityUtils;
import com.neusoft.cloudbrain.common.api.ApiResponse;
import com.neusoft.cloudbrain.patient.dto.*;
import com.neusoft.cloudbrain.patient.service.PatientService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

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
}
