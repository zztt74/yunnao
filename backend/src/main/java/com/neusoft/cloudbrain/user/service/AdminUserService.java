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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserAccountRepository userAccountRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final DoctorService doctorService;

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> listUsers(String role, Boolean enabled, String keyword, Pageable pageable) {
        return userAccountRepository.searchUsers(role, enabled, keyword, pageable)
                .map(AdminUserResponse::from);
    }

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
        }
        if ("DOCTOR".equals(role)) {
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
                .realName(blankToNull(request.realName()))
                .phone(blankToNull(request.phone()))
                .email(blankToNull(request.email()))
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
            throw UserErrorCode.USER_USERNAME_DUPLICATED.toException();
        }
        log.info("Admin created ADMIN account: id={}, username={}", user.getId(), user.getUsername());
        return AdminUserResponse.from(user);
    }

    private AdminUserResponse createDoctor(AdminUserCreateRequest request) {
        String doctorName = firstNonBlank(request.doctorName(), request.realName());
        String doctorTitle = firstNonBlank(request.doctorTitle(), "ATTENDING");
        if (request.departmentId() == null || doctorName == null) {
            throw UserErrorCode.DOCTOR_PROFILE_REQUIRED.toException();
        }
        DoctorCreateRequest doctorReq = new DoctorCreateRequest(
                request.username(),
                request.password(),
                request.departmentId(),
                doctorName,
                doctorTitle,
                request.specialty(),
                request.education(),
                request.experienceYears(),
                request.introduction());
        doctorService.createDoctor(doctorReq);
        UserAccount user = userAccountRepository.findByUsername(request.username())
                .orElseThrow(UserErrorCode.USER_NOT_FOUND::toException);
        user.setRealName(firstNonBlank(request.realName(), doctorName));
        user.setPhone(blankToNull(request.phone()));
        user.setEmail(blankToNull(request.email()));
        user.setUpdatedAt(LocalDateTime.now());
        user = userAccountRepository.save(user);
        log.info("Admin created DOCTOR account: id={}, username={}", user.getId(), user.getUsername());
        return AdminUserResponse.from(user);
    }

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
        }
        if (request.realName() != null) {
            user.setRealName(blankToNull(request.realName()));
        }
        if (request.phone() != null) {
            user.setPhone(blankToNull(request.phone()));
        }
        if (request.email() != null) {
            user.setEmail(blankToNull(request.email()));
        }
        user.setUpdatedAt(LocalDateTime.now());
        user = userAccountRepository.save(user);
        log.info("Admin updated user account: id={}", id);
        return AdminUserResponse.from(user);
    }

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
        log.info("Admin changed user status: id={}, action={}", id, action);
        return AdminUserResponse.from(user);
    }

    @Transactional
    public void resetPassword(Long id, ResetPasswordRequest request) {
        UserAccount user = userAccountRepository.findById(id)
                .orElseThrow(UserErrorCode.USER_NOT_FOUND::toException);
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(true);
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setUpdatedAt(LocalDateTime.now());
        userAccountRepository.save(user);
        log.info("Admin reset user password: id={}", id);
    }

    private String firstNonBlank(String first, String fallback) {
        String normalizedFirst = blankToNull(first);
        return normalizedFirst != null ? normalizedFirst : blankToNull(fallback);
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
