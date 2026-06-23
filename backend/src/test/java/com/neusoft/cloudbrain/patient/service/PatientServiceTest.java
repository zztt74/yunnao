package com.neusoft.cloudbrain.patient.service;

import com.neusoft.cloudbrain.auth.entity.Role;
import com.neusoft.cloudbrain.auth.entity.UserAccount;
import com.neusoft.cloudbrain.auth.repository.RoleRepository;
import com.neusoft.cloudbrain.auth.repository.UserAccountRepository;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.neusoft.cloudbrain.patient.dto.*;
import com.neusoft.cloudbrain.patient.entity.Patient;
import com.neusoft.cloudbrain.patient.entity.PatientProfile;
import com.neusoft.cloudbrain.patient.repository.PatientProfileRepository;
import com.neusoft.cloudbrain.patient.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * PatientService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PatientService - 患者服务测试")
class PatientServiceTest {

    @Mock
    private PatientRepository patientRepository;
    @Mock
    private PatientProfileRepository patientProfileRepository;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PatientService patientService;

    private Patient testPatient;
    private Role patientRole;

    @BeforeEach
    void setUp() {
        testPatient = Patient.builder()
                .id(1L)
                .userId(10L)
                .name("张三")
                .gender("MALE")
                .birthDate(LocalDate.of(1990, 1, 1))
                .phone("13800138000")
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        patientRole = Role.builder()
                .id(1L)
                .name("PATIENT")
                .description("患者")
                .build();
    }

    @Test
    @DisplayName("患者注册 - 用户名唯一时应注册成功")
    void register_shouldSucceedWhenUsernameUnique() {
        PatientRegisterRequest request = new PatientRegisterRequest(
                "testuser", "Password123!", "张三", "MALE",
                LocalDate.of(1990, 1, 1), "13800138000");

        when(userAccountRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        when(roleRepository.findByName("PATIENT")).thenReturn(Optional.of(patientRole));
        when(passwordEncoder.encode("Password123!")).thenReturn("hashedPassword");
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> {
            UserAccount ua = inv.getArgument(0);
            ua.setId(10L);
            return ua;
        });
        when(patientRepository.save(any(Patient.class))).thenAnswer(inv -> {
            Patient p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });
        when(patientProfileRepository.save(any(PatientProfile.class))).thenReturn(null);

        PatientResponse response = patientService.register(request);

        assertThat(response.name()).isEqualTo("张三");
        assertThat(response.gender()).isEqualTo("MALE");
        verify(userAccountRepository).save(any(UserAccount.class));
        verify(patientRepository).save(any(Patient.class));
    }

    @Test
    @DisplayName("患者注册 - 用户名重复时应抛出异常")
    void register_shouldThrowWhenUsernameDuplicated() {
        PatientRegisterRequest request = new PatientRegisterRequest(
                "existinguser", "Password123!", "张三", "MALE",
                LocalDate.of(1990, 1, 1), "13800138000");

        when(userAccountRepository.findByUsername("existinguser"))
                .thenReturn(Optional.of(UserAccount.builder().build()));

        assertThatThrownBy(() -> patientService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "USER_USERNAME_DUPLICATED")
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    @Test
    @DisplayName("获取当前患者信息 - 存在时应返回")
    void getCurrentPatient_shouldReturnWhenExists() {
        when(patientRepository.findByUserId(10L)).thenReturn(Optional.of(testPatient));

        PatientResponse response = patientService.getCurrentPatient(10L);

        assertThat(response.name()).isEqualTo("张三");
    }

    @Test
    @DisplayName("获取患者详情 - 患者访问他人数据应被拒绝")
    void getPatientById_shouldDenyWhenPatientAccessOthers() {
        when(patientRepository.findById(2L)).thenReturn(Optional.of(
                Patient.builder().id(2L).userId(20L).build()));

        assertThatThrownBy(() -> patientService.getPatientById(2L, 10L, Set.of("PATIENT")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PATIENT_PERMISSION_DENIED")
                .hasFieldOrPropertyWithValue("httpStatus", 403);
    }

    @Test
    @DisplayName("获取患者详情 - 管理员可以访问任何患者")
    void getPatientById_shouldAllowAdminAccessAny() {
        when(patientRepository.findById(2L)).thenReturn(Optional.of(
                Patient.builder().id(2L).userId(20L).name("李四").build()));

        PatientResponse response = patientService.getPatientById(2L, 10L, Set.of("ADMIN"));

        assertThat(response.name()).isEqualTo("李四");
    }

    @Test
    @DisplayName("更新患者信息 - 非本人应被拒绝")
    void updatePatient_shouldDenyWhenNotOwner() {
        PatientUpdateRequest request = new PatientUpdateRequest(
                "新名字", "FEMALE", LocalDate.of(1990, 1, 1), "13900139000");

        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));

        // userId=20L 不是该患者的 userId(10L)
        assertThatThrownBy(() -> patientService.updatePatient(1L, request, 20L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PATIENT_PERMISSION_DENIED")
                .hasFieldOrPropertyWithValue("httpStatus", 403);
    }

    @Test
    @DisplayName("更新患者信息 - 本人应允许更新")
    void updatePatient_shouldAllowOwnerUpdate() {
        PatientUpdateRequest request = new PatientUpdateRequest(
                "新名字", "FEMALE", LocalDate.of(1990, 1, 1), "13900139000");

        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));
        when(patientRepository.save(any(Patient.class))).thenReturn(testPatient);

        PatientResponse response = patientService.updatePatient(1L, request, 10L);

        assertThat(response).isNotNull();
        verify(patientRepository).save(any(Patient.class));
    }
}
