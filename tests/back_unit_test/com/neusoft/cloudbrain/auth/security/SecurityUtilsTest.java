package com.neusoft.cloudbrain.auth.security;

import com.neusoft.cloudbrain.auth.dto.AuthPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core .context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SecurityUtils 单元测试
 *
 * 测试场景（来自 41_质量测试与完成定义.md 11.1）：
 * - 获取当前用户信息
 * - 未登录状态处理
 */
@DisplayName("SecurityUtils - 安全工具类测试")
class SecurityUtilsTest {

    @BeforeEach
    void setUp() {
        // 每次测试前清理 SecurityContext
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("已认证用户应能获取当前用户信息")
    void getCurrentUser_authenticated_shouldReturnPrincipal() {
        // 准备
        AuthPrincipal principal = new AuthPrincipal(1L, "testuser", Set.of("ADMIN"), 0L);
        var auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        // 执行 & 验证
        AuthPrincipal result = SecurityUtils.getCurrentUser();
        assertEquals(1L, result.userId());
        assertEquals("testuser", result.username());
        assertEquals(Set.of("ADMIN"), result.roles());
        assertEquals(0L, result.tokenVersion());
    }

    @Test
    @DisplayName("未登录应抛出 IllegalStateException")
    void getCurrentUser_notAuthenticated_shouldThrowException() {
        assertThrows(IllegalStateException.class, SecurityUtils::getCurrentUser);
    }

    @Test
    @DisplayName("isAuthenticated - 已认证应返回 true")
    void isAuthenticated_authenticated_shouldReturnTrue() {
        AuthPrincipal principal = new AuthPrincipal(1L, "testuser", Set.of("ADMIN"), 0L);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertTrue(SecurityUtils.isAuthenticated());
    }

    @Test
    @DisplayName("isAuthenticated - 未认证应返回 false")
    void isAuthenticated_notAuthenticated_shouldReturnFalse() {
        assertFalse(SecurityUtils.isAuthenticated());
    }

    @Test
    @DisplayName("currentUserId - 应返回当前用户 ID")
    void currentUserId_authenticated_shouldReturnId() {
        AuthPrincipal principal = new AuthPrincipal(42L, "user", Set.of("DOCTOR"), 0L);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertEquals(42L, SecurityUtils.currentUserId());
    }

    @Test
    @DisplayName("currentUsername - 应返回当前用户名")
    void currentUsername_authenticated_shouldReturnUsername() {
        AuthPrincipal principal = new AuthPrincipal(1L, "admin", Set.of("ADMIN"), 0L);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertEquals("admin", SecurityUtils.currentUsername());
    }
}
