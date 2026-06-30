package com.neusoft.cloudbrain.user.service;

import com.neusoft.cloudbrain.auth.entity.Role;
import com.neusoft.cloudbrain.auth.entity.UserAccount;
import com.neusoft.cloudbrain.auth.repository.RoleRepository;
import com.neusoft.cloudbrain.auth.repository.UserAccountRepository;
import com.neusoft.cloudbrain.doctor.dto.DoctorCreateRequest;
import com.neusoft.cloudbrain.doctor.service.DoctorService;
import com.neusoft.cloudbrain.user.dto.AdminUserCreateRequest;
import com.neusoft.cloudbrain.user.dto.AdminUserResponse;
import com.neusoft.cloudbrain.user.dto.AdminUserUpdateRequest;
import com.neusoft.cloudbrain.user.dto.ResetPasswordRequest;
import com.neusoft.cloudbrain.user.dto.UserStatusChangeRequest;
import com.neusoft.cloudbrain.user.exception.UserErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 管理员用户管理 Service（B3）
 *
 * - 列表：按角色/状态/关键字分页
 * - 创建：ADMIN 只建账号；DOCTOR 同步建医生档案；PATIENT 拒绝（走自注册）
 * - 更新：第一阶段仅支持角色（user_account 无姓名/手机/邮箱字段，待联调 AI 扩展）
 * - 状态：启用/禁用/锁定（禁用、锁定后不能登录，由 AuthService.checkAccountStatus 保证）
 * - 重置密码：设新 hash + mustChangePassword=true + tokenVersion++（使旧 Token 失效）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserAccountRepository userAccountRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final DoctorService doctorService;

    /**
     * 管理端用户分页查询
     */
    @Transactional(readOnly = true)
    public Page<AdminUserResponse> listUsers(String role, Boolean enabled, String keyword, Pageable pageable) {
        return userAccountRepository.searchUsers(role, enabled, keyword, pageable)
                .map(AdminUserResponse::from);
    }

    /**
     * 创建用户
     */
    @Transactional
    public AdminUserResponse createUser(AdminUserCreateRequest request) {
        String role = request.role().toUpperCase();
        if ("PATIENT".equals(role)) {
            throw UserErrorCode.PATIENT_CREATE_NOT_ALLOWED.toException();
        }
        if (userAccountRepository.existsByUsername(request.username())) {
            throw UserErrorCode.USER_USERNAME_DUPLICATED.toException();
        }
        if ("ADMIN".equals(role)) {
            return createAdmin(request);
        } else if ("DOCTOR".equals(role)) {
            return createDoctor(request);
        }
        throw UserErrorCode.USER_ROLE_NOT_SUPPORTED.toException();
    }

    private AdminUserResponse createAdmin(AdminUserCreateRequest request) {
        LocalDateTime now = LocalDateTime.now();
        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(UserErrorCode.ROLE_NOT_FOUND::toException);
        UserAccount user = UserAccount.builder()
                .username(request.username())
                .passwordHash(passwordEncoder.encode(request.password()))
                .enabled(true)
                .accountNonLocked(true)
                .accountNonExpired(true)
                .credentialsNonExpired(true)
                .mustChangePassword(false)
                .failedLoginAttempts(0)
                .tokenVersion(0L)
                .createdAt(now)
                .updatedAt(now)
                .build();
        user.getRoles().add(adminRole);
        try {
            user = userAccountRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // 并发下 username 唯一约束兜底（existsByUsername 预检查与 save 之间存在竞态）
            throw UserErrorCode.USER_USERNAME_DUPLICATED.toException();
        }
        log.info("管理员创建 ADMIN 账号: id={}, username={}", user.getId(), user.getUsername());
        return AdminUserResponse.from(user);
    }

    private AdminUserResponse createDoctor(AdminUserCreateRequest request) {
        if (request.departmentId() == null
                || request.doctorName() == null || request.doctorName().isBlank()) {
            throw UserErrorCode.DOCTOR_PROFILE_REQUIRED.toException();
        }
        DoctorCreateRequest doctorReq = new DoctorCreateRequest(
                request.username(),
                request.password(),
                request.departmentId(),
                request.doctorName(),
                request.doctorTitle(),
                request.specialty(),
                request.education(),
                request.experienceYears(),
                request.introduction());
        doctorService.createDoctor(doctorReq);
        UserAccount user = userAccountRepository.findByUsername(request.username())
                .orElseThrow(UserErrorCode.USER_NOT_FOUND::toException);
        log.info("管理员创建 DOCTOR 账号: id={}, username={}", user.getId(), user.getUsername());
        return AdminUserResponse.from(user);
    }

    /**
     * 更新用户（第一阶段仅支持角色变更）
     */
    @Transactional
    public AdminUserResponse updateUser(Long id, AdminUserUpdateRequest request) {
        UserAccount user = userAccountRepository.findById(id)
                .orElseThrow(UserErrorCode.USER_NOT_FOUND::toException);
        if (request.role() != null && !request.role().isBlank()) {
            String newRole = request.role().toUpperCase();
            if ("PATIENT".equals(newRole)) {
                throw UserErrorCode.PATIENT_CREATE_NOT_ALLOWED.toException();
            }
            if (!"ADMIN".equals(newRole) && !"DOCTOR".equals(newRole)) {
                throw UserErrorCode.USER_ROLE_NOT_SUPPORTED.toException();
            }
            Role role = roleRepository.findByName(newRole)
                    .orElseThrow(UserErrorCode.ROLE_NOT_FOUND::toException);
            user.getRoles().clear();
            user.getRoles().add(role);
            user.setUpdatedAt(LocalDateTime.now());
            user = userAccountRepository.save(user);
            log.info("管理员更新用户角色: id={}, newRole={}", id, newRole);
        }
        return AdminUserResponse.from(user);
    }

    /**
     * 变更用户状态：启用/禁用/锁定
     */
    @Transactional
    public AdminUserResponse changeStatus(Long id, UserStatusChangeRequest request) {
        UserAccount user = userAccountRepository.findById(id)
                .orElseThrow(UserErrorCode.USER_NOT_FOUND::toException);
        String action = request.action().toUpperCase();
        switch (action) {
            case "ENABLE" -> {
                user.setEnabled(true);
                user.setAccountNonLocked(true);
                user.setLockoutUntil(null);
                user.setFailedLoginAttempts(0);
            }
            case "DISABLE" -> user.setEnabled(false);
            case "LOCK" -> user.setAccountNonLocked(false);
            default -> throw UserErrorCode.USER_ACTION_NOT_SUPPORTED.toException();
        }
        user.setUpdatedAt(LocalDateTime.now());
        user = userAccountRepository.save(user);
        log.info("管理员变更用户状态: id={}, action={}", id, action);
        return AdminUserResponse.from(user);
    }

    /**
     * 重置密码
     *
     * 设置新密码哈希、强制下次改密、递增 tokenVersion 使旧 Token 失效。
     */
    @Transactional
    public void resetPassword(Long id, ResetPasswordRequest request) {
        UserAccount user = userAccountRepository.findById(id)
                .orElseThrow(UserErrorCode.USER_NOT_FOUND::toException);
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(true);
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setUpdatedAt(LocalDateTime.now());
        userAccountRepository.save(user);
        log.info("管理员重置用户密码: id={}", id);
    }
}
