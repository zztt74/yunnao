package com.neusoft.cloudbrain.user.dto;

/**
 * 管理员更新用户请求。
 *
 * 支持更新账号资料字段与角色；角色变更不联动医生档案。
 */
public record AdminUserUpdateRequest(
        String role,
        String realName,
        String phone,
        String email
) {
}
