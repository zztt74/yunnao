package com.neusoft.cloudbrain.doctor.service;

import com.neusoft.cloudbrain.auth.entity.Role;
import com.neusoft.cloudbrain.auth.entity.UserAccount;
import com.neusoft.cloudbrain.auth.repository.RoleRepository;
import com.neusoft.cloudbrain.auth.repository.UserAccountRepository;
import com.neusoft.cloudbrain.department.entity.Department;
import com.neusoft.cloudbrain.department.repository.DepartmentRepository;
import com.neusoft.cloudbrain.doctor.dto.*;
import com.neusoft.cloudbrain.doctor.entity.Doctor;
import com.neusoft.cloudbrain.doctor.entity.DoctorProfile;
import com.neusoft.cloudbrain.doctor.exception.DoctorErrorCode;
import com.neusoft.cloudbrain.doctor.repository.DoctorProfileRepository;
import com.neusoft.cloudbrain.doctor.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 医生 Service
 *
 * 功能：
 * - 医生账号创建
 * - 医生档案维护
 * - 按科室、姓名和状态查询
 * - 医生启用和停用
 *
 * 权限规则：
 * - 停用医生不能接受新挂号
 * - 有历史诊疗数据的医生不能物理删除
 * - 医生账号和医生档案一一关联
 */
@Service
@RequiredArgsConstructor
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final UserAccountRepository userAccountRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 创建医生
     */
    @Transactional
    public DoctorResponse createDoctor(DoctorCreateRequest request) {
        // 检查用户名唯一性
        if (userAccountRepository.existsByUsername(request.username())) {
            throw DoctorErrorCode.USER_USERNAME_DUPLICATED.toException();
        }

        // 校验科室存在且启用
        Department department = departmentRepository.findById(request.departmentId())
                .orElseThrow(DoctorErrorCode.DEPARTMENT_NOT_FOUND::toException);
        if (!"ENABLED".equals(department.getStatus())) {
            throw DoctorErrorCode.DEPARTMENT_DISABLED.toException();
        }

        // 创建用户账号
        LocalDateTime now = LocalDateTime.now();
        UserAccount userAccount = UserAccount.builder()
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

        // 分配医生角色
        Role doctorRole = roleRepository.findByName("DOCTOR")
                .orElseThrow(DoctorErrorCode.SYSTEM_ROLE_NOT_INITIALIZED::toException);
        userAccount.setRoles(Set.of(doctorRole));

        userAccount = userAccountRepository.save(userAccount);

        // 创建医生
        Doctor doctor = Doctor.builder()
                .userId(userAccount.getId())
                .departmentId(request.departmentId())
                .name(request.name())
                .title(request.title())
                .specialty(request.specialty())
                .status("ENABLED")
                .createdAt(now)
                .updatedAt(now)
                .build();

        doctor = doctorRepository.save(doctor);

        // 创建档案
        DoctorProfile profile = DoctorProfile.builder()
                .doctorId(doctor.getId())
                .education(request.education())
                .experienceYears(request.experienceYears())
                .introduction(request.introduction())
                .createdAt(now)
                .updatedAt(now)
                .build();
        doctorProfileRepository.save(profile);

        return toResponse(doctor, department.getName(), profile);
    }

    /**
     * 获取医生详情
     */
    @Transactional(readOnly = true)
    public DoctorResponse getDoctorById(Long id) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(DoctorErrorCode.DOCTOR_NOT_FOUND::toException);
        Department department = departmentRepository.findById(doctor.getDepartmentId())
                .orElse(null);
        DoctorProfile profile = doctorProfileRepository.findByDoctorId(id).orElse(null);
        return toResponse(doctor, department != null ? department.getName() : null, profile);
    }

    /**
     * 获取医生列表（分页）
     */
    @Transactional(readOnly = true)
    public Page<DoctorResponse> getDoctorList(int page, int pageSize) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.max(1, pageSize));
        return doctorRepository.findAll(pageable)
                .map(doctor -> {
                    Department department = departmentRepository.findById(doctor.getDepartmentId()).orElse(null);
                    DoctorProfile profile = doctorProfileRepository.findByDoctorId(doctor.getId()).orElse(null);
                    return toResponse(doctor, department != null ? department.getName() : null, profile);
                });
    }

    /**
     * 按科室查询医生
     */
    @Transactional(readOnly = true)
    public List<DoctorResponse> getDoctorsByDepartment(Long departmentId) {
        return doctorRepository.findByDepartmentIdAndStatus(departmentId, "ENABLED").stream()
                .map(doctor -> {
                    Department department = departmentRepository.findById(doctor.getDepartmentId()).orElse(null);
                    DoctorProfile profile = doctorProfileRepository.findByDoctorId(doctor.getId()).orElse(null);
                    return toResponse(doctor, department != null ? department.getName() : null, profile);
                })
                .collect(Collectors.toList());
    }

    /**
     * 按姓名搜索医生
     */
    @Transactional(readOnly = true)
    public Page<DoctorResponse> searchByName(String name, int page, int pageSize) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.max(1, pageSize));
        return doctorRepository.findByNameContaining(name, pageable)
                .map(doctor -> {
                    Department department = departmentRepository.findById(doctor.getDepartmentId()).orElse(null);
                    DoctorProfile profile = doctorProfileRepository.findByDoctorId(doctor.getId()).orElse(null);
                    return toResponse(doctor, department != null ? department.getName() : null, profile);
                });
    }

    /**
     * 更新医生信息
     */
    @Transactional
    public DoctorResponse updateDoctor(Long id, DoctorUpdateRequest request) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(DoctorErrorCode.DOCTOR_NOT_FOUND::toException);

        // 校验科室
        Department department = departmentRepository.findById(request.departmentId())
                .orElseThrow(DoctorErrorCode.DEPARTMENT_NOT_FOUND::toException);

        doctor.setDepartmentId(request.departmentId());
        doctor.setName(request.name());
        doctor.setTitle(request.title());
        doctor.setSpecialty(request.specialty());
        if (request.status() != null) {
            doctor.setStatus(request.status());
        }
        doctor.setUpdatedAt(LocalDateTime.now());
        doctor = doctorRepository.save(doctor);

        // 更新档案
        DoctorProfile profile = doctorProfileRepository.findByDoctorId(id)
                .orElseGet(() -> DoctorProfile.builder()
                        .doctorId(id)
                        .createdAt(LocalDateTime.now())
                        .build());
        profile.setEducation(request.education());
        profile.setExperienceYears(request.experienceYears());
        profile.setIntroduction(request.introduction());
        profile.setUpdatedAt(LocalDateTime.now());
        doctorProfileRepository.save(profile);

        return toResponse(doctor, department.getName(), profile);
    }

    /**
     * 转换为响应 DTO
     */
    private DoctorResponse toResponse(Doctor doctor, String departmentName, DoctorProfile profile) {
        return new DoctorResponse(
                doctor.getId(),
                doctor.getUserId(),
                doctor.getDepartmentId(),
                departmentName,
                doctor.getName(),
                doctor.getTitle(),
                doctor.getSpecialty(),
                doctor.getStatus(),
                profile != null ? profile.getEducation() : null,
                profile != null ? profile.getExperienceYears() : null,
                profile != null ? profile.getIntroduction() : null,
                doctor.getCreatedAt(),
                doctor.getUpdatedAt()
        );
    }
}
