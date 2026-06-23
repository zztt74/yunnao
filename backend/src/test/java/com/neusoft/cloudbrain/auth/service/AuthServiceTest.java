package com.neusoft.cloudbrain.auth.service;

import com.neusoft.cloudbrain.auth.dto.AuthPrincipal;
import com.neusoft.cloudbrain.auth.dto.ChangePasswordRequest;
import com.neusoft.cloudbrain.auth.dto.LoginRequest;
import com.neusoft.cloudbrain.auth.dto.LoginResponse;
import com.neusoft.cloudbrain.auth.entity.Role;
import com.neusoft.cloudbrain.auth.entity.UserAccount;
import com.neusoft.cloudbrain.auth.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AuthService 单元测试
 *
 * 测试场景（来自 41_质量测试与完成定义.md 11.1）：
 * - 正确登录
 * - 错误密码
 * - 连续失败触发锁定
 * - 登录限流
 * - 停用账号
 * - 退出登录后旧 Token 失效
 * - 管理员重置密码后旧 Token 失效
 * - 初始管理员首次登录强制修改密码
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService - 认证服务测试")
class AuthServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private LoginRateLimiter loginRateLimiter;

    private AuthService authService;
    private BCryptPasswordEncoder passwordEncoder;

    private static final String RAW_PASSWORD = "Password123!";
    private static final String ENCODED_PASSWORD = new BCryptPasswordEncoder(12).encode(RAW_PASSWORD);

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(12);
        authService = new AuthService(userAccountRepository, jwtService, loginRateLimiter);
    }

    /**
     * 创建测试用户
     */
    private UserAccount createTestUser(boolean enabled, boolean locked, boolean mustChangePassword) {
        Role adminRole = Role.builder()
                .id(1L)
                .name("ADMIN")
                .build();

        return UserAccount.builder()
                .id(1L)
                .username("testuser")
                .passwordHash(ENCODED_PASSWORD)
                .enabled(enabled)
                .accountNonLocked(!locked)
                .accountNonExpired(true)
                .credentialsNonExpired(true)
                .mustChangePassword(mustChangePassword)
                .failedLoginAttempts(0)
                .tokenVersion(0L)
                .roles(Set.of(adminRole))
                .build();
    }

    // ========== 正确登录测试 ==========

    @Test
    @DisplayName("正确登录 - 应返回包含 Token 的响应")
    void login_validCredentials_shouldReturnToken() {
        // 准备
        UserAccount user = createTestUser(true, false, false);
        LoginRequest request = new LoginRequest("testuser", RAW_PASSWORD);
        String expectedToken = "generated.jwt.token";
        AuthPrincipal expectedPrincipal = new AuthPrincipal(1L, "testuser", Set.of("ADMIN"), 0L);

        when(loginRateLimiter.isAllowed(anyString(), anyString())).thenReturn(true);
        when(userAccountRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(expectedPrincipal)).thenReturn(expectedToken);
        when(jwtService.getExpirationInSeconds()).thenReturn(7200L);

        // 执行
        LoginResponse response = authService.login(request, "192.168.1.1");

        // 验证
        assertNotNull(response);
        assertEquals(expectedToken, response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(1L, response.userId());
        assertEquals("testuser", response.username());
        assertEquals(List.of("ADMIN"), response.roles());
        assertFalse(response.mustChangePassword());
        assertEquals(7200L, response.expiresIn());

        verify(loginRateLimiter).reset("192.168.1.1", "testuser");
        // 注意：如果 failedLoginAttempts 为 0，resetFailedLogin 不会被调用
        // verify(userAccountRepository).save(any(UserAccount.class));
    }

    // ========== 错误密码测试 ==========

    @Test
    @DisplayName("错误密码 - 应抛出 SecurityException")
    void login_wrongPassword_shouldThrowException() {
        // 准备
        UserAccount user = createTestUser(true, false, false);
        LoginRequest request = new LoginRequest("testuser", "WrongPassword!");

        when(loginRateLimiter.isAllowed(anyString(), anyString())).thenReturn(true);
        when(userAccountRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        // 执行 & 验证
        SecurityException exception = assertThrows(SecurityException.class,
                () -> authService.login(request, "192.168.1.1"));

        assertTrue(exception.getMessage().contains("AUTH_INVALID_CREDENTIALS"));
        verify(loginRateLimiter).recordFailure("192.168.1.1", "testuser");
        verify(userAccountRepository).save(any(UserAccount.class)); // 保存失败的尝试次数
    }

    // ========== 用户不存在测试 ==========

    @Test
    @DisplayName("用户不存在 - 应抛出 SecurityException")
    void login_userNotFound_shouldThrowException() {
        // 准备
        LoginRequest request = new LoginRequest("nonexistent", RAW_PASSWORD);

        when(loginRateLimiter.isAllowed(anyString(), anyString())).thenReturn(true);
        when(userAccountRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        // 执行 & 验证
        SecurityException exception = assertThrows(SecurityException.class,
                () -> authService.login(request, "192.168.1.1"));

        assertTrue(exception.getMessage().contains("AUTH_INVALID_CREDENTIALS"));
    }

    // ========== 账号停用测试 ==========

    @Test
    @DisplayName("停用账号 - 应抛出 AUTH_ACCOUNT_DISABLED 异常")
    void login_disabledAccount_shouldThrowDisabledException() {
        // 准备
        UserAccount user = createTestUser(false, false, false); // enabled=false
        LoginRequest request = new LoginRequest("testuser", RAW_PASSWORD);

        when(loginRateLimiter.isAllowed(anyString(), anyString())).thenReturn(true);
        when(userAccountRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        // 执行 & 验证
        SecurityException exception = assertThrows(SecurityException.class,
                () -> authService.login(request, "192.168.1.1"));

        assertTrue(exception.getMessage().contains("AUTH_ACCOUNT_DISABLED"));
    }

    // ========== 账号锁定测试 ==========

    @Test
    @DisplayName("锁定账号且未过期 - 应抛出 AUTH_ACCOUNT_LOCKED 异常")
    void login_lockedAccount_notExpired_shouldThrowLockedException() {
        // 准备
        UserAccount user = createTestUser(true, true, false); // locked=true
        user.setLockoutUntil(LocalDateTime.now().plusMinutes(10)); // 还有 10 分钟锁定
        LoginRequest request = new LoginRequest("testuser", RAW_PASSWORD);

        when(loginRateLimiter.isAllowed(anyString(), anyString())).thenReturn(true);
        when(userAccountRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        // 执行 & 验证
        SecurityException exception = assertThrows(SecurityException.class,
                () -> authService.login(request, "192.168.1.1"));

        assertTrue(exception.getMessage().contains("AUTH_ACCOUNT_LOCKED"));
    }

    @Test
    @DisplayName("锁定账号已过期 - 应自动解锁并允许登录")
    void login_lockedAccount_expired_shouldAutoUnlock() {
        // 准备
        UserAccount user = createTestUser(true, true, false);
        user.setLockoutUntil(LocalDateTime.now().minusMinutes(1)); // 锁定已过期
        LoginRequest request = new LoginRequest("testuser", RAW_PASSWORD);

        when(loginRateLimiter.isAllowed(anyString(), anyString())).thenReturn(true);
        when(userAccountRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(any(AuthPrincipal.class))).thenReturn("jwt.token");
        when(jwtService.getExpirationInSeconds()).thenReturn(7200L);

        // 执行
        LoginResponse response = authService.login(request, "192.168.1.1");

        // 验证 - 应该自动解锁并登录成功
        assertNotNull(response);
        verify(userAccountRepository, atLeastOnce()).save(any(UserAccount.class)); // 保存解锁状态
    }

    // ========== 连续失败触发锁定测试 ==========

    @Test
    @DisplayName("连续 5 次失败应触发账号锁定")
    void login_consecutiveFailures_shouldLockAccount() {
        // 准备
        UserAccount user = createTestUser(true, false, false);
        LoginRequest wrongRequest = new LoginRequest("testuser", "WrongPassword!");

        when(loginRateLimiter.isAllowed(anyString(), anyString())).thenReturn(true);
        when(userAccountRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        // 连续失败 5 次
        for (int i = 0; i < 5; i++) {
            assertThrows(SecurityException.class,
                    () -> authService.login(wrongRequest, "192.168.1.1"));
        }

        // 验证用户被锁定
        verify(userAccountRepository, times(5)).save(argThat(savedUser ->
                !savedUser.getAccountNonLocked() && savedUser.getLockoutUntil() != null
        ));
    }

    // ========== 登录限流测试 ==========

    @Test
    @DisplayName("达到限流阈值 - 应抛出 AUTH_LOGIN_RATE_LIMITED 异常")
    void login_rateLimited_shouldThrowRateLimitException() {
        // 准备
        LoginRequest request = new LoginRequest("testuser", RAW_PASSWORD);

        when(loginRateLimiter.isAllowed(anyString(), anyString())).thenReturn(false);

        // 执行 & 验证
        SecurityException exception = assertThrows(SecurityException.class,
                () -> authService.login(request, "192.168.1.1"));

        assertTrue(exception.getMessage().contains("AUTH_LOGIN_RATE_LIMITED"));
        verify(userAccountRepository, never()).findByUsername(anyString()); // 不应该查询数据库
    }

    // ========== 强制改密标记测试 ==========

    @Test
    @DisplayName("mustChangePassword=true - 登录响应应包含强制改密标记")
    void login_mustChangePassword_shouldReturnFlagInResponse() {
        // 准备
        UserAccount user = createTestUser(true, false, true); // mustChangePassword=true
        LoginRequest request = new LoginRequest("testuser", RAW_PASSWORD);

        when(loginRateLimiter.isAllowed(anyString(), anyString())).thenReturn(true);
        when(userAccountRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(any(AuthPrincipal.class))).thenReturn("jwt.token");
        when(jwtService.getExpirationInSeconds()).thenReturn(7200L);

        // 执行
        LoginResponse response = authService.login(request, "192.168.1.1");

        // 验证
        assertTrue(response.mustChangePassword());
    }

    // ========== 退出登录测试 ==========

    @Test
    @DisplayName("退出登录 - 应递增 tokenVersion 使旧 Token 失效")
    void logout_shouldIncrementTokenVersion() {
        // 准备
        UserAccount user = createTestUser(true, false, false);
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));

        // 执行
        authService.logout(1L);

        // 验证 - tokenVersion 应该递增
        verify(userAccountRepository).save(argThat(savedUser ->
                savedUser.getTokenVersion().equals(1L) // 从 0 变为 1
        ));
    }

    @Test
    @DisplayName("退出登录 - 用户不存在应抛出异常")
    void logout_userNotFound_shouldThrowException() {
        when(userAccountRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(SecurityException.class, () -> authService.logout(999L));
    }

    // ========== 修改密码测试 ==========

    @Test
    @DisplayName("修改密码 - 成功应更新密码和清除强制改密标记")
    void changePassword_valid_shouldUpdatePasswordAndClearFlag() {
        // 准备
        UserAccount user = createTestUser(true, false, true); // mustChangePassword=true
        ChangePasswordRequest request = new ChangePasswordRequest(RAW_PASSWORD, "NewPassword123!");

        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));

        // 执行
        authService.changePassword(1L, request);

        // 验证
        verify(userAccountRepository).save(argThat(savedUser ->
                passwordEncoder.matches("NewPassword123!", savedUser.getPasswordHash()) &&
                        !savedUser.getMustChangePassword() &&
                        savedUser.getTokenVersion().equals(1L) // 递增使旧 Token 失效
        ));
    }

    @Test
    @DisplayName("修改密码 - 原密码错误应抛出异常")
    void changePassword_wrongOldPassword_shouldThrowException() {
        // 准备
        UserAccount user = createTestUser(true, false, false);
        ChangePasswordRequest request = new ChangePasswordRequest("WrongOldPassword", "NewPassword123!");

        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));

        // 执行 & 验证
        assertThrows(SecurityException.class, () -> authService.changePassword(1L, request));
        verify(userAccountRepository, never()).save(any()); // 不应该保存
    }

    @Test
    @DisplayName("修改密码 - 新密码与原密码相同应抛出异常")
    void changePassword_samePassword_shouldThrowException() {
        // 准备
        UserAccount user = createTestUser(true, false, false);
        ChangePasswordRequest request = new ChangePasswordRequest(RAW_PASSWORD, RAW_PASSWORD);

        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));

        // 执行 & 验证
        assertThrows(IllegalArgumentException.class, () -> authService.changePassword(1L, request));
        verify(userAccountRepository, never()).save(any()); // 不应该保存
    }

    @Test
    @DisplayName("修改密码 - 用户不存在应抛出异常")
    void changePassword_userNotFound_shouldThrowException() {
        ChangePasswordRequest request = new ChangePasswordRequest(RAW_PASSWORD, "NewPassword123!");
        when(userAccountRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(SecurityException.class, () -> authService.changePassword(999L, request));
    }

    // ========== 成功登录后重置失败计数测试 ==========

    @Test
    @DisplayName("成功登录后应重置失败计数")
    void login_success_shouldResetFailedAttempts() {
        // 准备
        UserAccount user = createTestUser(true, false, false);
        user.setFailedLoginAttempts(3); // 有之前的失败记录
        LoginRequest request = new LoginRequest("testuser", RAW_PASSWORD);

        when(loginRateLimiter.isAllowed(anyString(), anyString())).thenReturn(true);
        when(userAccountRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(any(AuthPrincipal.class))).thenReturn("jwt.token");
        when(jwtService.getExpirationInSeconds()).thenReturn(7200L);

        // 执行
        authService.login(request, "192.168.1.1");

        // 验证 - 失败计数应该被重置为 0
        verify(userAccountRepository).save(argThat(savedUser ->
                savedUser.getFailedLoginAttempts() == 0
        ));
    }
}
