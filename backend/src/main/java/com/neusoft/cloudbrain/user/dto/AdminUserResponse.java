package com.neusoft.cloudbrain.user.dto;

import com.neusoft.cloudbrain.auth.entity.Role;
import com.neusoft.cloudbrain.auth.entity.UserAccount;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理端用户响应 DTO。
 *
 * 不返回 passwordHash、tokenVersion 等敏感或内部字段。roles 返回角色名集合。
 */
public record AdminUserResponse(
        Long id,
        String username,
        String realName,
        String phone,
        String email,
        LocalDateTime lastLoginAt,
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
                u.getRealName(),
                u.getPhone(),
                u.getEmail(),
                u.getLastLoginAt(),
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
