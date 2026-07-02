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
import java.util.List;
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

    // ========== 患者档案测试 ==========

    @Test
    @DisplayName("获取患者档案 - 本人应返回档案")
    void getPatientProfile_shouldReturnWhenOwner() {
        PatientProfile profile = PatientProfile.builder()
                .id(1L).patientId(1L).address("北京市").build();
        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));
        when(patientProfileRepository.findByPatientId(1L)).thenReturn(Optional.of(profile));

        PatientProfileResponse response = patientService.getPatientProfile(1L, 10L, Set.of("PATIENT"));

        assertThat(response.address()).isEqualTo("北京市");
    }

    @Test
    @DisplayName("获取患者档案 - 管理员可查看任意患者档案")
    void getPatientProfile_shouldAllowAdminAccessAny() {
        PatientProfile profile = PatientProfile.builder()
                .id(1L).patientId(1L).address("上海市").build();
        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));
        when(patientProfileRepository.findByPatientId(1L)).thenReturn(Optional.of(profile));

        PatientProfileResponse response = patientService.getPatientProfile(1L, 999L, Set.of("ADMIN"));

        assertThat(response.address()).isEqualTo("上海市");
    }

    @Test
    @DisplayName("获取患者档案 - 患者访问他人档案应被拒绝")
    void getPatientProfile_shouldDenyWhenPatientAccessOthers() {
        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));

        assertThatThrownBy(() -> patientService.getPatientProfile(1L, 999L, Set.of("PATIENT")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PATIENT_PERMISSION_DENIED")
                .hasFieldOrPropertyWithValue("httpStatus", 403);
    }

    @Test
    @DisplayName("获取患者档案 - 患者不存在应抛出 PATIENT_NOT_FOUND")
    void getPatientProfile_shouldThrowWhenPatientNotFound() {
        when(patientRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.getPatientProfile(999L, 10L, Set.of("ADMIN")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PATIENT_NOT_FOUND");
    }

    @Test
    @DisplayName("获取患者档案 - 档案不存在应抛出 PATIENT_PROFILE_NOT_FOUND")
    void getPatientProfile_shouldThrowWhenProfileNotFound() {
        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));
        when(patientProfileRepository.findByPatientId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.getPatientProfile(1L, 10L, Set.of("PATIENT")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PATIENT_PROFILE_NOT_FOUND");
    }

    @Test
    @DisplayName("更新患者档案 - 本人应允许更新")
    void updatePatientProfile_shouldAllowOwnerUpdate() {
        PatientProfileUpdateRequest request = new PatientProfileUpdateRequest(
                "新地址", "紧急联系人", "13800138000", "无", "既往病史");
        PatientProfile existingProfile = PatientProfile.builder()
                .id(1L).patientId(1L).address("旧地址").build();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));
        when(patientProfileRepository.findByPatientId(1L)).thenReturn(Optional.of(existingProfile));
        when(patientProfileRepository.save(any(PatientProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        PatientProfileResponse response = patientService.updatePatientProfile(1L, request, 10L);

        assertThat(response.address()).isEqualTo("新地址");
        assertThat(response.emergencyContact()).isEqualTo("紧急联系人");
        verify(patientProfileRepository).save(any(PatientProfile.class));
    }

    @Test
    @DisplayName("更新患者档案 - 非本人应被拒绝")
    void updatePatientProfile_shouldDenyWhenNotOwner() {
        PatientProfileUpdateRequest request = new PatientProfileUpdateRequest(
                "新地址", null, null, null, null);

        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));

        assertThatThrownBy(() -> patientService.updatePatientProfile(1L, request, 999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PATIENT_PERMISSION_DENIED")
                .hasFieldOrPropertyWithValue("httpStatus", 403);
    }

    @Test
    @DisplayName("更新患者档案 - 档案不存在时应自动创建")
    void updatePatientProfile_shouldCreateWhenProfileNotExists() {
        PatientProfileUpdateRequest request = new PatientProfileUpdateRequest(
                "新地址", null, null, null, null);

        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));
        when(patientProfileRepository.findByPatientId(1L)).thenReturn(Optional.empty());
        when(patientProfileRepository.save(any(PatientProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        PatientProfileResponse response = patientService.updatePatientProfile(1L, request, 10L);

        assertThat(response.address()).isEqualTo("新地址");
        verify(patientProfileRepository).save(any(PatientProfile.class));
    }

    // ========== 搜索测试 ==========

    @Test
    @DisplayName("按姓名搜索 - 返回匹配的患者列表")
    void searchByName_shouldReturnMatchingList() {
        when(patientRepository.findByName("张三")).thenReturn(List.of(testPatient));

        List<PatientResponse> results = patientService.searchByName("张三");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).name()).isEqualTo("张三");
    }

    @Test
    @DisplayName("按姓名搜索 - 无匹配时返回空列表")
    void searchByName_shouldReturnEmptyWhenNoMatch() {
        when(patientRepository.findByName("不存在")).thenReturn(List.of());

        List<PatientResponse> results = patientService.searchByName("不存在");

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("按手机号搜索 - 返回匹配的患者列表")
    void searchByPhone_shouldReturnMatchingList() {
        when(patientRepository.findByPhone("13800138000")).thenReturn(List.of(testPatient));

        List<PatientResponse> results = patientService.searchByPhone("13800138000");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).phone()).isEqualTo("13800138000");
    }

    @Test
    @DisplayName("按手机号搜索 - 无匹配时返回空列表")
    void searchByPhone_shouldReturnEmptyWhenNoMatch() {
        when(patientRepository.findByPhone("00000000000")).thenReturn(List.of());

        List<PatientResponse> results = patientService.searchByPhone("00000000000");

        assertThat(results).isEmpty();
    }

    // ========== 分页查询测试 ==========

    @Test
    @DisplayName("分页查询 - 返回分页结果")
    void listPatients_shouldReturnPagedResult() {
        org.springframework.data.domain.Page<Patient> page = new org.springframework.data.domain.PageImpl<>(
                List.of(testPatient),
                org.springframework.data.domain.PageRequest.of(0, 20),
                1);
        when(patientRepository.searchPatients(eq(null), eq(null), eq(null), any()))
                .thenReturn(page);

        org.springframework.data.domain.Page<PatientResponse> result =
                patientService.listPatients(null, null, null,
                        org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("张三");
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("分页查询 - 无数据时返回空页")
    void listPatients_shouldReturnEmptyPageWhenNoData() {
        org.springframework.data.domain.Page<Patient> emptyPage = new org.springframework.data.domain.PageImpl<>(
                List.of(),
                org.springframework.data.domain.PageRequest.of(0, 20),
                0);
        when(patientRepository.searchPatients(any(), any(), any(), any())).thenReturn(emptyPage);

        org.springframework.data.domain.Page<PatientResponse> result =
                patientService.listPatients("不存在", null, null,
                        org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    // ========== 异常情况补充测试 ==========

    @Test
    @DisplayName("获取当前患者信息 - 不存在时应抛出异常")
    void getCurrentPatient_shouldThrowWhenNotFound() {
        when(patientRepository.findByUserId(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.getCurrentPatient(999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PATIENT_NOT_FOUND");
    }

    @Test
    @DisplayName("更新患者信息 - 患者不存在应抛出异常")
    void updatePatient_shouldThrowWhenPatientNotFound() {
        PatientUpdateRequest request = new PatientUpdateRequest(
                "新名字", "FEMALE", LocalDate.of(1990, 1, 1), "13900139000");

        when(patientRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.updatePatient(999L, request, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PATIENT_NOT_FOUND");
    }
}
