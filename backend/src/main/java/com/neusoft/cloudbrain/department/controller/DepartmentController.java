package com.neusoft.cloudbrain.department.controller;

import com.neusoft.cloudbrain.auth.dto.AuthPrincipal;
import com.neusoft.cloudbrain.auth.security.SecurityUtils;
import com.neusoft.cloudbrain.common.api.ApiResponse;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.neusoft.cloudbrain.department.dto.DepartmentCreateRequest;
import com.neusoft.cloudbrain.department.dto.DepartmentResponse;
import com.neusoft.cloudbrain.department.dto.DepartmentUpdateRequest;
import com.neusoft.cloudbrain.department.service.DepartmentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 科室接口
 *
 * - GET    /api/departments/tree    科室树形结构
 * - GET    /api/departments         科室扁平列表
 * - GET    /api/departments/{id}    科室详情
 * - POST   /api/departments         创建科室（管理员）
 * - PUT    /api/departments/{id}    更新科室（管理员）
 */
@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    /**
     * 获取科室树形结构
     */
    @GetMapping("/tree")
    public ApiResponse<List<DepartmentResponse>> getTree(HttpServletRequest httpRequest) {
        List<DepartmentResponse> tree = departmentService.getDepartmentTree();
        return ApiResponse.success(tree, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 获取科室扁平列表
     */
    @GetMapping
    public ApiResponse<List<DepartmentResponse>> list(HttpServletRequest httpRequest) {
        List<DepartmentResponse> list = departmentService.getDepartmentList();
        return ApiResponse.success(list, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 获取科室详情
     */
    @GetMapping("/{id}")
    public ApiResponse<DepartmentResponse> getById(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        DepartmentResponse response = departmentService.getDepartmentById(id);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 创建科室（管理员）
     */
    @PostMapping
    public ApiResponse<DepartmentResponse> create(
            @Valid @RequestBody DepartmentCreateRequest request,
            HttpServletRequest httpRequest) {
        checkAdminPermission();
        DepartmentResponse response = departmentService.createDepartment(request);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 更新科室（管理员）
     */
    @PutMapping("/{id}")
    public ApiResponse<DepartmentResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody DepartmentUpdateRequest request,
            HttpServletRequest httpRequest) {
        checkAdminPermission();
        DepartmentResponse response = departmentService.updateDepartment(id, request);
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
