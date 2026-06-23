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
                "ATTENDING", "心血管疾病", "博士", 10, "简介");

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
                "ATTENDING", "心血管疾病", null, null, null);

        when(userAccountRepository.existsByUsername("doctor1")).thenReturn(false);
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));

        assertThatThrownBy(() -> doctorService.createDoctor(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DEPARTMENT_DISABLED");
    }

    @Test
    @DisplayName("创建医生 - 用户名重复时应抛出异常")
    void createDoctor_shouldThrowWhenUsernameDuplicated() {
        DoctorCreateRequest request = new DoctorCreateRequest(
                "existing", "Password123!", 1L, "李医生",
                "ATTENDING", null, null, null, null);

        when(userAccountRepository.existsByUsername("existing")).thenReturn(true);

        assertThatThrownBy(() -> doctorService.createDoctor(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("USER_USERNAME_DUPLICATED");
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
}
