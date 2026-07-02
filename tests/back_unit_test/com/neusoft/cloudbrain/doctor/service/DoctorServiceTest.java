package com.neusoft.cloudbrain.doctor.service;

import com.neusoft.cloudbrain.auth.entity.Role;
import com.neusoft.cloudbrain.auth.entity.UserAccount;
import com.neusoft.cloudbrain.auth.repository.RoleRepository;
import com.neusoft.cloudbrain.auth.repository.UserAccountRepository;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.neusoft.cloudbrain.department.entity.Department;
import com.neusoft.cloudbrain.department.repository.DepartmentRepository;
import com.neusoft.cloudbrain.doctor.dto.*;
import com.neusoft.cloudbrain.doctor.entity.Doctor;
import com.neusoft.cloudbrain.doctor.entity.DoctorProfile;
import com.neusoft.cloudbrain.doctor.repository.DoctorProfileRepository;
import com.neusoft.cloudbrain.doctor.repository.DoctorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DoctorService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DoctorService - 医生服务测试")
class DoctorServiceTest {

    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private DoctorProfileRepository doctorProfileRepository;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private DoctorService doctorService;

    private Doctor testDoctor;
    private Department testDepartment;
    private Role doctorRole;

    @BeforeEach
    void setUp() {
        testDepartment = Department.builder()
                .id(1L)
                .code("DEPT_INTERNAL")
                .name("内科")
                .status("ENABLED")
                .build();

        testDoctor = Doctor.builder()
                .id(1L)
                .userId(10L)
                .departmentId(1L)
                .name("李医生")
                .title("ATTENDING")
                .specialty("心血管疾病")
                .status("ENABLED")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        doctorRole = Role.builder()
                .id(2L)
                .name("DOCTOR")
                .description("医生")
                .build();
    }

    @Test
    @DisplayName("创建医生 - 科室启用时应创建成功")
    void createDoctor_shouldSucceedWhenDepartmentEnabled() {
        DoctorCreateRequest request = new DoctorCreateRequest(
                "doctor1", "Password123!", 1L, "李医生",
                "ATTENDING", "心血管疾病", null, null, "博士", 10, "简介");

        when(userAccountRepository.existsByUsername("doctor1")).thenReturn(false);
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));
        when(roleRepository.findByName("DOCTOR")).thenReturn(Optional.of(doctorRole));
        when(passwordEncoder.encode("Password123!")).thenReturn("hashedPassword");
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> {
            UserAccount ua = inv.getArgument(0);
            ua.setId(10L);
            return ua;
        });
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(inv -> {
            Doctor d = inv.getArgument(0);
            d.setId(1L);
            return d;
        });
        when(doctorProfileRepository.save(any(DoctorProfile.class))).thenReturn(null);

        DoctorResponse response = doctorService.createDoctor(request);

        assertThat(response.name()).isEqualTo("李医生");
        verify(userAccountRepository).save(any(UserAccount.class));
        verify(doctorRepository).save(any(Doctor.class));
    }

    @Test
    @DisplayName("创建医生 - 科室停用时应抛出异常")
    void createDoctor_shouldThrowWhenDepartmentDisabled() {
        testDepartment.setStatus("DISABLED");
        DoctorCreateRequest request = new DoctorCreateRequest(
                "doctor1", "Password123!", 1L, "李医生",
                "ATTENDING", "心血管疾病", null, null, null, null, null);

        when(userAccountRepository.existsByUsername("doctor1")).thenReturn(false);
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));

        assertThatThrownBy(() -> doctorService.createDoctor(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DEPARTMENT_DISABLED")
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    @Test
    @DisplayName("创建医生 - 用户名重复时应抛出异常")
    void createDoctor_shouldThrowWhenUsernameDuplicated() {
        DoctorCreateRequest request = new DoctorCreateRequest(
                "existing", "Password123!", 1L, "李医生",
                "ATTENDING", null, null, null, null, null, null);

        when(userAccountRepository.existsByUsername("existing")).thenReturn(true);

        assertThatThrownBy(() -> doctorService.createDoctor(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "USER_USERNAME_DUPLICATED")
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    @Test
    @DisplayName("获取医生详情 - 存在时应返回")
    void getDoctorById_shouldReturnWhenExists() {
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));
        when(doctorProfileRepository.findByDoctorId(1L)).thenReturn(Optional.empty());

        DoctorResponse response = doctorService.getDoctorById(1L);

        assertThat(response.name()).isEqualTo("李医生");
        assertThat(response.departmentName()).isEqualTo("内科");
    }

    @Test
    @DisplayName("按科室查询医生 - 应返回启用状态的医生")
    void getDoctorsByDepartment_shouldReturnEnabledDoctors() {
        when(doctorRepository.findByDepartmentIdAndStatus(1L, "ENABLED"))
                .thenReturn(List.of(testDoctor));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));
        when(doctorProfileRepository.findByDoctorId(1L)).thenReturn(Optional.empty());

        List<DoctorResponse> response = doctorService.getDoctorsByDepartment(1L);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).name()).isEqualTo("李医生");
    }

    @Test
    @DisplayName("更新医生信息 - 存在时应更新成功")
    void updateDoctor_shouldUpdateWhenExists() {
        DoctorUpdateRequest request = new DoctorUpdateRequest(
                1L, "李医生更新", "CHIEF", "更新擅长",
                "ENABLED", "博士", 15, "更新简介");

        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));
        when(doctorRepository.save(any(Doctor.class))).thenReturn(testDoctor);
        when(doctorProfileRepository.findByDoctorId(1L)).thenReturn(Optional.empty());
        when(doctorProfileRepository.save(any(DoctorProfile.class))).thenReturn(null);

        DoctorResponse response = doctorService.updateDoctor(1L, request);

        assertThat(response).isNotNull();
        verify(doctorRepository).save(any(Doctor.class));
    }

    // ============================================================
    // B1：医生本人资料更新
    // ============================================================

    @Test
    @DisplayName("更新本人资料 - 存在档案时应更新专长/学历/年限/简介")
    void updateMyProfile_shouldUpdateWhenDoctorExists() {
        DoctorProfileUpdateRequest request = new DoctorProfileUpdateRequest(
                "心血管与高血压", "博士", 12, "擅长冠心病介入治疗");
        DoctorProfile existingProfile = DoctorProfile.builder()
                .id(1L)
                .doctorId(1L)
                .education("硕士")
                .experienceYears(10)
                .introduction("原简介")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(doctorRepository.findByUserId(10L)).thenReturn(Optional.of(testDoctor));
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(inv -> inv.getArgument(0));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));
        when(doctorProfileRepository.findByDoctorId(1L)).thenReturn(Optional.of(existingProfile));
        when(doctorProfileRepository.save(any(DoctorProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        DoctorResponse response = doctorService.updateMyProfile(10L, request);

        assertThat(response).isNotNull();
        assertThat(response.specialty()).isEqualTo("心血管与高血压");
        assertThat(response.education()).isEqualTo("博士");
        assertThat(response.experienceYears()).isEqualTo(12);
        assertThat(response.introduction()).isEqualTo("擅长冠心病介入治疗");
        // 不应触碰职称/科室/状态字段
        assertThat(response.title()).isEqualTo("ATTENDING");
        assertThat(response.departmentId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo("ENABLED");
        verify(doctorRepository).save(any(Doctor.class));
        verify(doctorProfileRepository).save(any(DoctorProfile.class));
    }

    @Test
    @DisplayName("更新本人资料 - 无档案时自动创建档案并更新")
    void updateMyProfile_shouldCreateProfileWhenAbsent() {
        DoctorProfileUpdateRequest request = new DoctorProfileUpdateRequest(
                "内分泌", "本科", 5, "糖尿病管理");

        when(doctorRepository.findByUserId(10L)).thenReturn(Optional.of(testDoctor));
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(inv -> inv.getArgument(0));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));
        when(doctorProfileRepository.findByDoctorId(1L)).thenReturn(Optional.empty());
        when(doctorProfileRepository.save(any(DoctorProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        DoctorResponse response = doctorService.updateMyProfile(10L, request);

        assertThat(response.education()).isEqualTo("本科");
        assertThat(response.experienceYears()).isEqualTo(5);
        verify(doctorProfileRepository).save(any(DoctorProfile.class));
    }

    @Test
    @DisplayName("更新本人资料 - 非医生账号（user_id 未关联医生）抛权限错误 403")
    void updateMyProfile_shouldThrowWhenNotDoctor() {
        DoctorProfileUpdateRequest request = new DoctorProfileUpdateRequest(
                "心血管", null, null, null);

        when(doctorRepository.findByUserId(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> doctorService.updateMyProfile(999L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DOCTOR_PERMISSION_DENIED")
                .hasFieldOrPropertyWithValue("httpStatus", 403);
    }
}
