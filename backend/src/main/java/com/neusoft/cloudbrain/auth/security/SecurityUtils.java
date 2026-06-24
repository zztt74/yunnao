package com.neusoft.cloudbrain.auth.security;

import com.neusoft.cloudbrain.auth.dto.AuthPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 安全工具类
 */
@Component
public class SecurityUtils {

    /**
     * 获取当前认证主体
     *
     * @return 当前用户的 AuthPrincipal
     * @throws IllegalStateException 未登录时抛出异常
     */
    public static AuthPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("AUTH_TOKEN_REVOKED:未登录或 Token 已失效");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthPrincipal authPrincipal) {
            return authPrincipal;
        }

        throw new IllegalStateException("AUTH_TOKEN_REVOKED:无法获取当前用户信息");
    }

    /**
     * 获取当前用户 ID
     */
    public static Long currentUserId() {
        return getCurrentUser().userId();
    }

    /**
     * 获取当前用户名
     */
    public static String currentUsername() {
        return getCurrentUser().username();
    }

    /**
     * 判断当前用户是否已认证
     */
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthPrincipal;
    }
}
