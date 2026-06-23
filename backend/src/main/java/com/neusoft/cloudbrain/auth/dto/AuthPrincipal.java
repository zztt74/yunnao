package com.neusoft.cloudbrain.auth.dto;

import java.util.Set;

/**
 * 认证主体，用于 JWT 和 Security Context
 */
public record AuthPrincipal(
        Long userId,
        String username,
        Set<String> roles,
        Long tokenVersion) {
}
