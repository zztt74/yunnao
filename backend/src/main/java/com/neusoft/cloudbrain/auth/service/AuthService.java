package com.neusoft.cloudbrain.auth.service;

import com.neusoft.cloudbrain.audit.annotation.Auditable;
import com.neusoft.cloudbrain.auth.dto.AuthPrincipal;
import com.neusoft.cloudbrain.auth.dto.ChangePasswordRequest;
import com.neusoft.cloudbrain.auth.dto.LoginRequest;
import com.neusoft.cloudbrain.auth.dto.LoginResponse;
import com.neusoft.cloudbrain.auth.entity.UserAccount;
import com.neusoft.cloudbrain.auth.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 认证服务
 *
 * 核心功能：
 * - 登录认证（密码校验、账号状态检查、Token 签发）
 * - 退出登录（递增 tokenVersion）
 * - 修改密码（原密码校验、新密码编码、清除强制改密标记）
 *
 * 安全机制：
 * - BCrypt cost=12 密码哈希
 * - 连续 5 次失败锁定 15 分钟
 * - IP + 用户名维度限流
 * - mustChangePassword 强制初始改密
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final JwtService jwtService;
    private final LoginRateLimiter loginRateLimiter;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);

    /** 连续失败锁定阈值 */
    private static final int MAX_FAILED_ATTEMPTS = 5;

    /** 锁定时长（分钟） */
    private static final int LOCKOUT_MINUTES = 15;

    /**
     * 用户登录
     */
    @Transactional
    @Auditable(action = "AUTH_LOGIN", targetType = "USER", targetIdParam = "")
    public LoginResponse login(LoginRequest request, String clientIp) {
        // 检查限流
        if (!loginRateLimiter.isAllowed(clientIp, request.username())) {
            throw new SecurityException("AUTH_LOGIN_RATE_LIMITED:登录尝试过于频繁");
        }

        // 查找用户
        UserAccount user = userAccountRepository.findByUsername(request.username())
                .orElseThrow(() -> new SecurityException("AUTH_INVALID_CREDENTIALS:用户名或密码错误"));

        // 检查账号状态
        checkAccountStatus(user);

        // 校验密码
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            handleFailedLogin(user);
            loginRateLimiter.recordFailure(clientIp, request.username());
            throw new SecurityException("AUTH_INVALID_CREDENTIALS:用户名或密码错误");
        }

        // 登录成功，重置失败计数
        user.setFailedLoginAttempts(0);
        loginRateLimiter.reset(clientIp, request.username());

        // 记录最近登录时间（B-HW-04）
        user.setLastLoginAt(LocalDateTime.now());
        userAccountRepository.save(user);

        // 生成 Token
        AuthPrincipal principal = buildPrincipal(user);
        String token = jwtService.generateToken(principal);

        return new LoginResponse(
                token,
                "Bearer",
                user.getId(),
                user.getUsername(),
                List.copyOf(principal.roles()),
                user.getMustChangePassword(),
                jwtService.getExpirationInSeconds()
        );
    }

    /**
     * 退出登录
     */
    @Transactional
    public void logout(Long userId) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new SecurityException("AUTH_TOKEN_REVOKED:用户不存在"));

        // 递增 tokenVersion，使旧 Token 失效
        user.setTokenVersion(user.getTokenVersion() + 1);
        userAccountRepository.save(user);
    }

    /**
     * 修改密码
     */
    @Transactional
    @Auditable(action = "AUTH_CHANGE_PASSWORD", targetType = "USER")
    public void changePassword(Long userId, ChangePasswordRequest request) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new SecurityException("AUTH_INVALID_CREDENTIALS:用户不存在"));

        // 校验原密码
        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new SecurityException("AUTH_INVALID_CREDENTIALS:原密码错误");
        }

        // 新密码不能与原密码相同
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("VALIDATION_FAILED:新密码不能与原密码相同");
        }

        // 更新密码
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);

        // 递增 tokenVersion，使旧 Token 失效（需重新登录）
        user.setTokenVersion(user.getTokenVersion() + 1);

        userAccountRepository.save(user);
    }

    /**
     * 检查账号状态
     */
    private void checkAccountStatus(UserAccount user) {
        if (!user.getEnabled()) {
            throw new SecurityException("AUTH_ACCOUNT_DISABLED:账号已禁用");
        }

        if (!user.getAccountNonLocked()) {
            // 检查是否已过锁定时间
            if (user.getLockoutUntil() != null && LocalDateTime.now().isBefore(user.getLockoutUntil())) {
                long minutesRemaining = java.time.Duration.between(LocalDateTime.now(), user.getLockoutUntil()).toMinutes();
                throw new SecurityException("AUTH_ACCOUNT_LOCKED:账号已锁定，" + minutesRemaining + " 分钟后重试");
            } else {
                // 锁定已过期，自动解锁
                user.setAccountNonLocked(true);
                user.setLockoutUntil(null);
                user.setFailedLoginAttempts(0);
                userAccountRepository.save(user);
            }
        }
    }

    /**
     * 处理登录失败
     */
    private void handleFailedLogin(UserAccount user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setAccountNonLocked(false);
            user.setLockoutUntil(LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES));
        }

        userAccountRepository.save(user);
    }

    /**
     * 构建认证主体
     */
    private AuthPrincipal buildPrincipal(UserAccount user) {
        Set<String> roles = user.getRoles().stream()
                .map(role -> role.getName())
                .collect(Collectors.toSet());

        return new AuthPrincipal(
                user.getId(),
                user.getUsername(),
                roles,
                user.getTokenVersion()
        );
    }
}
