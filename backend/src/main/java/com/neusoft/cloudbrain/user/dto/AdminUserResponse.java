package com.neusoft.cloudbrain.user.dto;

import com.neusoft.cloudbrain.auth.entity.Role;
import com.neusoft.cloudbrain.auth.entity.UserAccount;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理端用户响应 DTO
 *
 * 不返回 passwordHash、tokenVersion 等敏感或内部字段。roles 返回角色名集合。
 * 注意：UserAccount 当前无 realName/phone/email 字段，故不包含这些字段
 * （B3 第一阶段限制，待联调 AI 确认是否扩展 user_account 表）。
 */
public record AdminUserResponse(
        Long id,
        String username,
        Boolean enabled,
        Boolean accountNonLocked,
        Boolean accountNonExpired,
        Boolean credentialsNonExpired,
        Boolean mustChangePassword,
        Set<String> roles,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AdminUserResponse from(UserAccount u) {
        return new AdminUserResponse(
                u.getId(),
                u.getUsername(),
                u.getEnabled(),
                u.getAccountNonLocked(),
                u.getAccountNonExpired(),
                u.getCredentialsNonExpired(),
                u.getMustChangePassword(),
                u.getRoles().stream().map(Role::getName).collect(Collectors.toSet()),
                u.getCreatedAt(),
                u.getUpdatedAt());
    }
}
