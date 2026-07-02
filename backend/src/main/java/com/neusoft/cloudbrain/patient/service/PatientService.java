package com.neusoft.cloudbrain.patient.service;

import com.neusoft.cloudbrain.auth.entity.Role;
import com.neusoft.cloudbrain.auth.entity.UserAccount;
import com.neusoft.cloudbrain.auth.repository.RoleRepository;
import com.neusoft.cloudbrain.auth.repository.UserAccountRepository;
import com.neusoft.cloudbrain.patient.dto.*;
import com.neusoft.cloudbrain.patient.entity.Patient;
import com.neusoft.cloudbrain.patient.entity.PatientProfile;
import com.neusoft.cloudbrain.patient.exception.PatientErrorCode;
import com.neusoft.cloudbrain.patient.repository.PatientProfileRepository;
import com.neusoft.cloudbrain.patient.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 患者 Service
 *
 * 功能：
 * - 患者注册建档
 * - 查看和修改本人信息
 * - 管理员按编号、姓名、手机号查询
 * - 维护过敏史、既往史等扩展信息
 *
 * 权限规则：
 * - 患者只能访问和修改自己的数据
 * - 医生只能在接诊关系成立时查看患者必要信息
 */
@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patientRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final UserAccountRepository userAccountRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 患者注册
     */
    @Transactional
    public PatientResponse register(PatientRegisterRequest request) {
        // 检查用户名唯一性
        if (userAccountRepository.findByUsername(request.username()).isPresent()) {
            throw PatientErrorCode.USER_USERNAME_DUPLICATED.toException();
        }

        // 创建用户账号
        LocalDateTime now = LocalDateTime.now();
        UserAccount userAccount = UserAccount.builder()
                .username(request.username())
                .realName(request.name())
                .phone(request.phone())
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

        // 分配患者角色
        Role patientRole = roleRepository.findByName("PATIENT")
                .orElseThrow(PatientErrorCode.SYSTEM_ROLE_NOT_INITIALIZED::toException);
        userAccount.setRoles(Set.of(patientRole));

        userAccount = userAccountRepository.save(userAccount);

        // 创建患者
        Patient patient = Patient.builder()
                .userId(userAccount.getId())
                .name(request.name())
                .gender(request.gender())
                .birthDate(request.birthDate())
                .phone(request.phone())
                .status("ACTIVE")
                .createdAt(now)
                .updatedAt(now)
                .build();

        patient = patientRepository.save(patient);

        // 创建空档案
        PatientProfile profile = PatientProfile.builder()
                .patientId(patient.getId())
                .createdAt(now)
                .updatedAt(now)
                .build();
        patientProfileRepository.save(profile);

        return toResponse(patient);
    }

    /**
     * 获取当前登录患者信息
     */
    @Transactional(readOnly = true)
    public PatientResponse getCurrentPatient(Long userId) {
        Patient patient = patientRepository.findByUserId(userId)
                .orElseThrow(PatientErrorCode.PATIENT_NOT_FOUND::toException);
        return toResponse(patient);
    }

    /**
     * 获取患者详情（含权限校验）
     */
    @Transactional(readOnly = true)
    public PatientResponse getPatientById(Long id, Long currentUserId, Set<String> currentRoles) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(PatientErrorCode.PATIENT_NOT_FOUND::toException);

        // 权限校验：患者只能查看自己的信息
        if (currentRoles.contains("PATIENT") && !currentRoles.contains("ADMIN")
                && !currentRoles.contains("DOCTOR")) {
            if (!patient.getUserId().equals(currentUserId)) {
                throw PatientErrorCode.PATIENT_PERMISSION_DENIED.toException();
            }
        }

        return toResponse(patient);
    }

    /**
     * 更新患者信息（患者只能更新自己的信息）
     */
    @Transactional
    public PatientResponse updatePatient(Long id, PatientUpdateRequest request, Long currentUserId) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(PatientErrorCode.PATIENT_NOT_FOUND::toException);

        // 权限校验：患者只能更新自己的信息
        if (!patient.getUserId().equals(currentUserId)) {
            throw PatientErrorCode.PATIENT_PERMISSION_DENIED.toException();
        }

        patient.setName(request.name());
        patient.setGender(request.gender());
        patient.setBirthDate(request.birthDate());
        patient.setPhone(request.phone());
        patient.setUpdatedAt(LocalDateTime.now());

        patient = patientRepository.save(patient);
        return toResponse(patient);
    }

    /**
     * 获取患者档案
     */
    @Transactional(readOnly = true)
    public PatientProfileResponse getPatientProfile(Long patientId, Long currentUserId, Set<String> currentRoles) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(PatientErrorCode.PATIENT_NOT_FOUND::toException);

        // 权限校验：患者只能查看自己的档案
        if (currentRoles.contains("PATIENT") && !currentRoles.contains("ADMIN")
                && !currentRoles.contains("DOCTOR")) {
            if (!patient.getUserId().equals(currentUserId)) {
                throw PatientErrorCode.PATIENT_PERMISSION_DENIED.toException();
            }
        }

        PatientProfile profile = patientProfileRepository.findByPatientId(patientId)
                .orElseThrow(PatientErrorCode.PATIENT_PROFILE_NOT_FOUND::toException);

        return toProfileResponse(profile);
    }

    /**
     * 更新患者档案（患者只能更新自己的档案）
     */
    @Transactional
    public PatientProfileResponse updatePatientProfile(
            Long patientId, PatientProfileUpdateRequest request, Long currentUserId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(PatientErrorCode.PATIENT_NOT_FOUND::toException);

        // 权限校验：患者只能更新自己的档案
        if (!patient.getUserId().equals(currentUserId)) {
            throw PatientErrorCode.PATIENT_PERMISSION_DENIED.toException();
        }

        PatientProfile profile = patientProfileRepository.findByPatientId(patientId)
                .orElseGet(() -> PatientProfile.builder()
                        .patientId(patientId)
                        .createdAt(LocalDateTime.now())
                        .build());

        profile.setAddress(request.address());
        profile.setEmergencyContact(request.emergencyContact());
        profile.setEmergencyPhone(request.emergencyPhone());
        profile.setAllergies(request.allergies());
        profile.setMedicalHistory(request.medicalHistory());
        profile.setUpdatedAt(LocalDateTime.now());

        profile = patientProfileRepository.save(profile);
        return toProfileResponse(profile);
    }

    /**
     * 管理员按姓名查询患者
     */
    @Transactional(readOnly = true)
    public List<PatientResponse> searchByName(String name) {
        return patientRepository.findByName(name).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 管理员按手机号查询患者
     */
    @Transactional(readOnly = true)
    public List<PatientResponse> searchByPhone(String phone) {
        return patientRepository.findByPhone(phone).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 管理员患者分页查询（B7 / B-HW-06）
     *
     * 多条件分页：姓名模糊、手机号精确、状态、账号关键字。
     * 权限由 Controller 层校验管理员。
     */
    @Transactional(readOnly = true)
    public Page<PatientResponse> listPatients(String name, String phone, String status,
                                              String keyword, Pageable pageable) {
        return patientRepository.searchPatients(name, phone, status, keyword, pageable)
                .map(this::toResponse);
    }

    /**
     * 转换为响应 DTO
     *
     * B-HW-06：填充 username，便于管理端展示账号并按账号筛选。
     */
    private PatientResponse toResponse(Patient patient) {
        String username = userAccountRepository.findById(patient.getUserId())
                .map(UserAccount::getUsername).orElse(null);
        return new PatientResponse(
                patient.getId(),
                patient.getUserId(),
                username,
                patient.getName(),
                patient.getGender(),
                patient.getBirthDate(),
                patient.getPhone(),
                patient.getStatus(),
                patient.getCreatedAt(),
                patient.getUpdatedAt()
        );
    }

    /**
     * 转换为档案响应 DTO
     */
    private PatientProfileResponse toProfileResponse(PatientProfile profile) {
        return new PatientProfileResponse(
                profile.getId(),
                profile.getPatientId(),
                profile.getAddress(),
                profile.getEmergencyContact(),
                profile.getEmergencyPhone(),
                profile.getAllergies(),
                profile.getMedicalHistory(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }
}
