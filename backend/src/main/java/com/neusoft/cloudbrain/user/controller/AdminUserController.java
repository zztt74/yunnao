package com.neusoft.cloudbrain.user.controller;

import com.neusoft.cloudbrain.auth.dto.AuthPrincipal;
import com.neusoft.cloudbrain.auth.security.SecurityUtils;
import com.neusoft.cloudbrain.common.api.ApiResponse;
import com.neusoft.cloudbrain.common.api.PageResponse;
import com.neusoft.cloudbrain.common.api.PageUtils;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.neusoft.cloudbrain.user.dto.AdminUserCreateRequest;
import com.neusoft.cloudbrain.user.dto.AdminUserResponse;
import com.neusoft.cloudbrain.user.dto.AdminUserUpdateRequest;
import com.neusoft.cloudbrain.user.dto.ResetPasswordRequest;
import com.neusoft.cloudbrain.user.dto.UserStatusChangeRequest;
import com.neusoft.cloudbrain.user.service.AdminUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员用户管理接口（B3）
 *
 * - GET    /api/admin/users                用户分页列表（按角色/状态/关键字筛选）
 * - POST   /api/admin/users                创建用户（ADMIN/DOCTOR）
 * - PUT    /api/admin/users/{id}           更新用户（第一阶段仅角色）
 * - POST   /api/admin/users/{id}/status    启用/禁用/锁定
 * - POST   /api/admin/users/{id}/reset-password  重置密码
 *
 * 权限：仅管理员。
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /**
     * 用户分页列表
     */
    @GetMapping
    public ApiResponse<PageResponse<AdminUserResponse>> list(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Integer size,
            HttpServletRequest httpRequest) {
        checkAdminPermission();
        int resolvedSize = PageUtils.resolvePageSize(pageSize, size);
        Pageable pageable = PageUtils.toPageable(page, resolvedSize);
        Page<AdminUserResponse> response = adminUserService.listUsers(role, enabled, keyword, pageable);
        return ApiResponse.success(PageResponse.from(response), (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 创建用户
     */
    @PostMapping
    public ApiResponse<AdminUserResponse> create(
            @Valid @RequestBody AdminUserCreateRequest request,
            HttpServletRequest httpRequest) {
        checkAdminPermission();
        AdminUserResponse response = adminUserService.createUser(request);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 更新用户（第一阶段仅支持角色）
     */
    @PutMapping("/{id}")
    public ApiResponse<AdminUserResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserUpdateRequest request,
            HttpServletRequest httpRequest) {
        checkAdminPermission();
        AdminUserResponse response = adminUserService.updateUser(id, request);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 变更用户状态（启用/禁用/锁定）
     */
    @PostMapping("/{id}/status")
    public ApiResponse<AdminUserResponse> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody UserStatusChangeRequest request,
            HttpServletRequest httpRequest) {
        checkAdminPermission();
        AdminUserResponse response = adminUserService.changeStatus(id, request);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 重置密码
     */
    @PostMapping("/{id}/reset-password")
    public ApiResponse<Void> resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest) {
        checkAdminPermission();
        adminUserService.resetPassword(id, request);
        return ApiResponse.success(null, (String) httpRequest.getAttribute("traceId"));
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
