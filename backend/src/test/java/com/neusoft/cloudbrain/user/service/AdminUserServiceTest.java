package com.neusoft.cloudbrain.user.service;

import com.neusoft.cloudbrain.auth.entity.Role;
import com.neusoft.cloudbrain.auth.entity.UserAccount;
import com.neusoft.cloudbrain.auth.repository.RoleRepository;
import com.neusoft.cloudbrain.auth.repository.UserAccountRepository;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.neusoft.cloudbrain.doctor.dto.DoctorCreateRequest;
import com.neusoft.cloudbrain.doctor.dto.DoctorResponse;
import com.neusoft.cloudbrain.doctor.service.DoctorService;
import com.neusoft.cloudbrain.user.dto.AdminUserCreateRequest;
import com.neusoft.cloudbrain.user.dto.AdminUserResponse;
import com.neusoft.cloudbrain.user.dto.AdminUserUpdateRequest;
import com.neusoft.cloudbrain.user.dto.ResetPasswordRequest;
import com.neusoft.cloudbrain.user.dto.UserStatusChangeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * AdminUserService 单元测试（B3）
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminUserService - 管理员用户管理测试")
class AdminUserServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private DoctorService doctorService;

    @InjectMocks
    private AdminUserService adminUserService;

    private Role adminRole;
    private Role doctorRole;

    @BeforeEach
    void setUp() {
        adminRole = Role.builder().id(1L).name("ADMIN").description("管理员").build();
        doctorRole = Role.builder().id(2L).name("DOCTOR").description("医生").build();
    }

    private UserAccount buildUser(Long id, String username, Role role) {
        UserAccount u = UserAccount.builder()
                .id(id)
                .username(username)
                .passwordHash("hashed")
                .enabled(true)
                .accountNonLocked(true)
                .accountNonExpired(true)
                .credentialsNonExpired(true)
                .mustChangePassword(false)
                .failedLoginAttempts(0)
                .tokenVersion(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        u.getRoles().add(role);
        return u;
    }

    // ============================================================
    // 创建
    // ============================================================

    @Test
    @DisplayName("创建 ADMIN - 应只建账号并绑定 ADMIN 角色")
    void createUser_shouldCreateAdmin() {
        AdminUserCreateRequest request = new AdminUserCreateRequest(
                "admin2", "Password123!", "ADMIN",
                null, null, null, null, null, null, null);

        when(userAccountRepository.existsByUsername("admin2")).thenReturn(false);
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(passwordEncoder.encode("Password123!")).thenReturn("hashedPwd");
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> {
            UserAccount u = inv.getArgument(0);
            u.setId(10L);
            return u;
        });

        AdminUserResponse response = adminUserService.createUser(request);

        assertThat(response.username()).isEqualTo("admin2");
        assertThat(response.roles()).contains("ADMIN");
        assertThat(response.enabled()).isTrue();
        verify(userAccountRepository).save(any(UserAccount.class));
        verify(doctorService, never()).createDoctor(any());
    }

    @Test
    @DisplayName("创建 DOCTOR - 应复用 DoctorService 建医生档案")
    void createUser_shouldCreateDoctorViaDoctorService() {
        AdminUserCreateRequest request = new AdminUserCreateRequest(
                "doctor2", "Password123!", "DOCTOR",
                1L, "王医生", "ATTENDING", "心血管", "博士", 10, "简介");
        UserAccount created = buildUser(20L, "doctor2", doctorRole);

        when(userAccountRepository.existsByUsername("doctor2")).thenReturn(false);
        when(doctorService.createDoctor(any(DoctorCreateRequest.class)))
                .thenReturn(mock(DoctorResponse.class));
        when(userAccountRepository.findByUsername("doctor2")).thenReturn(Optional.of(created));

        AdminUserResponse response = adminUserService.createUser(request);

        assertThat(response.username()).isEqualTo("doctor2");
        assertThat(response.roles()).contains("DOCTOR");
        verify(doctorService).createDoctor(any(DoctorCreateRequest.class));
    }

    @Test
    @DisplayName("创建 DOCTOR - 缺少 departmentId/doctorName 应拒绝(400)")
    void createUser_shouldRejectDoctorWithoutProfile() {
        AdminUserCreateRequest request = new AdminUserCreateRequest(
                "doctor3", "Password123!", "DOCTOR",
                null, null, null, null, null, null, null);

        when(userAccountRepository.existsByUsername("doctor3")).thenReturn(false);

        assertThatThrownBy(() -> adminUserService.createUser(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DOCTOR_PROFILE_REQUIRED")
                .hasFieldOrPropertyWithValue("httpStatus", 400);
    }

    @Test
    @DisplayName("创建 PATIENT - 应拒绝并提示走自注册(400)")
    void createUser_shouldRejectPatient() {
        AdminUserCreateRequest request = new AdminUserCreateRequest(
                "pat1", "Password123!", "PATIENT",
                null, null, null, null, null, null, null);

        assertThatThrownBy(() -> adminUserService.createUser(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PATIENT_CREATE_NOT_ALLOWED")
                .hasFieldOrPropertyWithValue("httpStatus", 400);
    }

    @Test
    @DisplayName("创建用户 - 用户名重复应拒绝(409)")
    void createUser_shouldRejectDuplicatedUsername() {
        AdminUserCreateRequest request = new AdminUserCreateRequest(
                "existing", "Password123!", "ADMIN",
                null, null, null, null, null, null, null);

        when(userAccountRepository.existsByUsername("existing")).thenReturn(true);

        assertThatThrownBy(() -> adminUserService.createUser(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "USER_USERNAME_DUPLICATED")
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    // ============================================================
    // 更新角色
    // ============================================================

    @Test
    @DisplayName("更新用户角色 - 应清空旧角色并绑定新角色")
    void updateUser_shouldReplaceRole() {
        UserAccount user = buildUser(1L, "u1", doctorRole);
        AdminUserUpdateRequest request = new AdminUserUpdateRequest("ADMIN");

        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminUserResponse response = adminUserService.updateUser(1L, request);

        assertThat(response.roles()).containsExactly("ADMIN");
        assertThat(response.roles()).doesNotContain("DOCTOR");
    }

    @Test
    @DisplayName("更新用户角色 - 改为 PATIENT 应拒绝(400)")
    void updateUser_shouldRejectPatientRole() {
        UserAccount user = buildUser(1L, "u1", doctorRole);
        AdminUserUpdateRequest request = new AdminUserUpdateRequest("PATIENT");

        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> adminUserService.updateUser(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 400);
    }

    // ============================================================
    // 状态变更
    // ============================================================

    @Test
    @DisplayName("禁用用户 - enabled 应设为 false")
    void changeStatus_shouldDisable() {
        UserAccount user = buildUser(1L, "u1", adminRole);
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminUserResponse response = adminUserService.changeStatus(1L, new UserStatusChangeRequest("DISABLE"));

        assertThat(response.enabled()).isFalse();
    }

    @Test
    @DisplayName("锁定用户 - accountNonLocked 应设为 false")
    void changeStatus_shouldLock() {
        UserAccount user = buildUser(1L, "u1", adminRole);
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminUserResponse response = adminUserService.changeStatus(1L, new UserStatusChangeRequest("LOCK"));

        assertThat(response.accountNonLocked()).isFalse();
    }

    @Test
    @DisplayName("启用用户 - 应清锁定并重置失败计数")
    void changeStatus_shouldEnableAndClearLock() {
        UserAccount user = buildUser(1L, "u1", adminRole);
        user.setEnabled(false);
        user.setAccountNonLocked(false);
        user.setFailedLoginAttempts(5);
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminUserResponse response = adminUserService.changeStatus(1L, new UserStatusChangeRequest("ENABLE"));

        assertThat(response.enabled()).isTrue();
        assertThat(response.accountNonLocked()).isTrue();
    }

    @Test
    @DisplayName("状态变更 - 不支持的操作应拒绝(400)")
    void changeStatus_shouldRejectUnsupportedAction() {
        UserAccount user = buildUser(1L, "u1", adminRole);
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> adminUserService.changeStatus(1L, new UserStatusChangeRequest("DELETE")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 400);
    }

    // ============================================================
    // 重置密码
    // ============================================================

    @Test
    @DisplayName("重置密码 - 应更新 hash、强制改密、递增 tokenVersion")
    void resetPassword_shouldUpdateHashAndTokenVersion() {
        UserAccount user = buildUser(1L, "u1", adminRole);
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPassword123!")).thenReturn("newHash");
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        adminUserService.resetPassword(1L, new ResetPasswordRequest("NewPassword123!"));

        assertThat(user.getPasswordHash()).isEqualTo("newHash");
        assertThat(user.getMustChangePassword()).isTrue();
        assertThat(user.getTokenVersion()).isEqualTo(1L);
        verify(userAccountRepository).save(user);
    }

    // ============================================================
    // 列表
    // ============================================================

    @Test
    @DisplayName("用户列表 - 应分页返回并映射为 AdminUserResponse")
    void listUsers_shouldReturnPagedResponse() {
        UserAccount u1 = buildUser(1L, "admin1", adminRole);
        Page<UserAccount> page = new PageImpl<>(List.of(u1), PageRequest.of(0, 20), 1);
        when(userAccountRepository.searchUsers(eq("ADMIN"), eq(true), eq("admin"), any()))
                .thenReturn(page);

        Page<AdminUserResponse> result = adminUserService.listUsers("ADMIN", true, "admin", PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).username()).isEqualTo("admin1");
        assertThat(result.getContent().get(0).roles()).contains("ADMIN");
    }

    @Test
    @DisplayName("用户/角色不存在 - 应抛 404")
    void shouldThrowNotFound() {
        when(userAccountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.changeStatus(99L, new UserStatusChangeRequest("DISABLE")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "USER_NOT_FOUND")
                .hasFieldOrPropertyWithValue("httpStatus", 404);
    }
}
