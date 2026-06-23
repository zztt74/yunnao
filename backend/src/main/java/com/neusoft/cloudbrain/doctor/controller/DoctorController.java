package com.neusoft.cloudbrain.doctor.controller;

import com.neusoft.cloudbrain.common.api.ApiResponse;
import com.neusoft.cloudbrain.doctor.dto.DoctorCreateRequest;
import com.neusoft.cloudbrain.doctor.dto.DoctorResponse;
import com.neusoft.cloudbrain.doctor.dto.DoctorUpdateRequest;
import com.neusoft.cloudbrain.doctor.service.DoctorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
    public ApiResponse<Page<DoctorResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String name,
            HttpServletRequest httpRequest) {
        Page<DoctorResponse> result;
        if (name != null && !name.isBlank()) {
            result = doctorService.searchByName(name, page, pageSize);
        } else {
            result = doctorService.getDoctorList(page, pageSize);
        }
        return ApiResponse.success(result, (String) httpRequest.getAttribute("traceId"));
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
     * 创建医生
     */
    @PostMapping
    public ApiResponse<DoctorResponse> create(
            @Valid @RequestBody DoctorCreateRequest request,
            HttpServletRequest httpRequest) {
        DoctorResponse response = doctorService.createDoctor(request);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 更新医生
     */
    @PutMapping("/{id}")
    public ApiResponse<DoctorResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody DoctorUpdateRequest request,
            HttpServletRequest httpRequest) {
        DoctorResponse response = doctorService.updateDoctor(id, request);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }
}
