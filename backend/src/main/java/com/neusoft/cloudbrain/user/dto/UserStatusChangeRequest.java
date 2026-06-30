package com.neusoft.cloudbrain.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 用户状态变更请求
 *
 * action 取值：
 * - ENABLE：启用（enabled=true，accountNonLocked=true，清锁定）
 * - DISABLE：禁用（enabled=false，禁用后不能登录）
 * - LOCK：锁定（accountNonLocked=false，锁定后不能登录）
 */
public record UserStatusChangeRequest(
        @NotBlank(message = "操作类型不能为空")
        String action
) {
}
