package com.neusoft.cloudbrain.auth.dto;

import java.util.List;

/**
 * 登录响应 DTO
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        Long userId,
        String username,
        List<String> roles,
        Boolean mustChangePassword,
        Long expiresIn) {
}
