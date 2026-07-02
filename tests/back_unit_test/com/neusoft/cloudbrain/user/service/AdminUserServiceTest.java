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
import org.mockito.ArgumentCaptor;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminUserService")
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
        adminRole = Role.builder().id(1L).name("ADMIN").description("admin").build();
        doctorRole = Role.builder().id(2L).name("DOCTOR").description("doctor").build();
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

    @Test
    void createUser_shouldCreateAdminWithProfileFields() {
        AdminUserCreateRequest request = new AdminUserCreateRequest(
                "admin2", "Password123!", "ADMIN",
                "Admin Two", "13800000001", "admin2@example.com",
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
        assertThat(response.realName()).isEqualTo("Admin Two");
        assertThat(response.phone()).isEqualTo("13800000001");
        assertThat(response.email()).isEqualTo("admin2@example.com");
        assertThat(response.roles()).contains("ADMIN");
        assertThat(response.enabled()).isTrue();
        verify(userAccountRepository).save(any(UserAccount.class));
        verify(doctorService, never()).createDoctor(any());
    }

    @Test
    void createUser_shouldCreateDoctorViaDoctorServiceAndBackfillAccountProfile() {
        AdminUserCreateRequest request = new AdminUserCreateRequest(
                "doctor2", "Password123!", "DOCTOR",
                "Doctor Wang", "13800000002", "doctor2@example.com",
                1L, "Doctor Wang", "ATTENDING", "cardiology", "PhD", 10, "intro");
        UserAccount created = buildUser(20L, "doctor2", doctorRole);

        when(userAccountRepository.existsByUsername("doctor2")).thenReturn(false);
        when(doctorService.createDoctor(any(DoctorCreateRequest.class)))
                .thenReturn(mock(DoctorResponse.class));
        when(userAccountRepository.findByUsername("doctor2")).thenReturn(Optional.of(created));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminUserResponse response = adminUserService.createUser(request);

        assertThat(response.username()).isEqualTo("doctor2");
        assertThat(response.realName()).isEqualTo("Doctor Wang");
        assertThat(response.phone()).isEqualTo("13800000002");
        assertThat(response.email()).isEqualTo("doctor2@example.com");
        assertThat(response.roles()).contains("DOCTOR");
        verify(doctorService).createDoctor(any(DoctorCreateRequest.class));
    }

    @Test
    void createUser_shouldUseRealNameAndDefaultTitleWhenDoctorFieldsArePartial() {
        AdminUserCreateRequest request = new AdminUserCreateRequest(
                "doctor3", "Password123!", "DOCTOR",
                "Doctor Li", "13800000003", "doctor3@example.com",
                1L, null, null, "internal medicine", null, null, null);
        UserAccount created = buildUser(21L, "doctor3", doctorRole);

        when(userAccountRepository.existsByUsername("doctor3")).thenReturn(false);
        when(doctorService.createDoctor(any(DoctorCreateRequest.class)))
                .thenReturn(mock(DoctorResponse.class));
        when(userAccountRepository.findByUsername("doctor3")).thenReturn(Optional.of(created));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminUserResponse response = adminUserService.createUser(request);

        ArgumentCaptor<DoctorCreateRequest> captor = ArgumentCaptor.forClass(DoctorCreateRequest.class);
        verify(doctorService).createDoctor(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("Doctor Li");
        assertThat(captor.getValue().title()).isEqualTo("ATTENDING");
        assertThat(response.realName()).isEqualTo("Doctor Li");
    }

    @Test
    void createUser_shouldRejectDoctorWithoutProfile() {
        AdminUserCreateRequest request = new AdminUserCreateRequest(
                "doctor4", "Password123!", "DOCTOR",
                null, null, null,
                null, null, null, null, null, null, null);

        when(userAccountRepository.existsByUsername("doctor4")).thenReturn(false);

        assertThatThrownBy(() -> adminUserService.createUser(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DOCTOR_PROFILE_REQUIRED")
                .hasFieldOrPropertyWithValue("httpStatus", 400);
    }

    @Test
    void createUser_shouldRejectPatient() {
        AdminUserCreateRequest request = new AdminUserCreateRequest(
                "pat1", "Password123!", "PATIENT",
                null, null, null,
                null, null, null, null, null, null, null);

        assertThatThrownBy(() -> adminUserService.createUser(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PATIENT_CREATE_NOT_ALLOWED")
                .hasFieldOrPropertyWithValue("httpStatus", 400);
    }

    @Test
    void createUser_shouldRejectDuplicatedUsername() {
        AdminUserCreateRequest request = new AdminUserCreateRequest(
                "existing", "Password123!", "ADMIN",
                null, null, null,
                null, null, null, null, null, null, null);

        when(userAccountRepository.existsByUsername("existing")).thenReturn(true);

        assertThatThrownBy(() -> adminUserService.createUser(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "USER_USERNAME_DUPLICATED")
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    @Test
    void updateUser_shouldReplaceRoleAndUpdateProfileFieldsWithoutDoctorProfileLinkage() {
        UserAccount user = buildUser(1L, "u1", doctorRole);
        AdminUserUpdateRequest request = new AdminUserUpdateRequest(
                "ADMIN", "Updated User", "13800000003", "updated@example.com");

        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminUserResponse response = adminUserService.updateUser(1L, request);

        assertThat(response.roles()).containsExactly("ADMIN");
        assertThat(response.roles()).doesNotContain("DOCTOR");
        assertThat(response.realName()).isEqualTo("Updated User");
        assertThat(response.phone()).isEqualTo("13800000003");
        assertThat(response.email()).isEqualTo("updated@example.com");
    }

    @Test
    void updateUser_shouldRejectPatientRole() {
        UserAccount user = buildUser(1L, "u1", doctorRole);
        AdminUserUpdateRequest request = new AdminUserUpdateRequest("PATIENT", null, null, null);

        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> adminUserService.updateUser(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 400);
    }

    @Test
    void changeStatus_shouldDisable() {
        UserAccount user = buildUser(1L, "u1", adminRole);
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminUserResponse response = adminUserService.changeStatus(1L, new UserStatusChangeRequest("DISABLE"));

        assertThat(response.enabled()).isFalse();
    }

    @Test
    void changeStatus_shouldLock() {
        UserAccount user = buildUser(1L, "u1", adminRole);
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminUserResponse response = adminUserService.changeStatus(1L, new UserStatusChangeRequest("LOCK"));

        assertThat(response.accountNonLocked()).isFalse();
    }

    @Test
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
    void changeStatus_shouldRejectUnsupportedAction() {
        UserAccount user = buildUser(1L, "u1", adminRole);
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> adminUserService.changeStatus(1L, new UserStatusChangeRequest("DELETE")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 400);
    }

    @Test
    void resetPassword_shouldUpdateHashAndTokenVersion() {
        UserAccount user = buildUser(1L, "u1", adminRole);
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPassword123!")).thenReturn("newHash");
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        adminUserService.resetPassword(1L, new ResetPasswordRequest("NewPassword123!"));

        assertThat(user.getPasswordHash()).isEqualTo("newHash");
        // B-HW-03：重置密码后不再强制 mustChangePassword
        assertThat(user.getMustChangePassword()).isFalse();
        assertThat(user.getTokenVersion()).isEqualTo(1L);
        verify(userAccountRepository).save(user);
    }

    @Test
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
    void shouldThrowNotFound() {
        when(userAccountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.changeStatus(99L, new UserStatusChangeRequest("DISABLE")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "USER_NOT_FOUND")
                .hasFieldOrPropertyWithValue("httpStatus", 404);
    }
}
