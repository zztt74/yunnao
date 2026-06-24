package com.neusoft.cloudbrain.encounter.service;

import com.neusoft.cloudbrain.appointment.entity.Appointment;
import com.neusoft.cloudbrain.appointment.repository.AppointmentRepository;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.neusoft.cloudbrain.department.entity.Department;
import com.neusoft.cloudbrain.department.repository.DepartmentRepository;
import com.neusoft.cloudbrain.doctor.entity.Doctor;
import com.neusoft.cloudbrain.doctor.repository.DoctorRepository;
import com.neusoft.cloudbrain.encounter.dto.EncounterDiagnosisRequest;
import com.neusoft.cloudbrain.encounter.dto.EncounterDiagnosisResponse;
import com.neusoft.cloudbrain.encounter.dto.EncounterResponse;
import com.neusoft.cloudbrain.encounter.dto.EncounterStartRequest;
import com.neusoft.cloudbrain.encounter.entity.Encounter;
import com.neusoft.cloudbrain.encounter.entity.EncounterDiagnosis;
import com.neusoft.cloudbrain.encounter.repository.EncounterDiagnosisRepository;
import com.neusoft.cloudbrain.encounter.repository.EncounterRepository;
import com.neusoft.cloudbrain.patient.entity.Patient;
import com.neusoft.cloudbrain.patient.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * EncounterService 单元测试
 *
 * 覆盖文档 11.2 必测场景：
 * - 开始接诊（CREATED → IN_PROGRESS，同步 Appointment）
 * - 完成就诊（IN_PROGRESS → COMPLETED，含前置条件校验）
 * - 完成就诊失败（缺少医生最终诊断）
 * - 状态冲突
 * - 诊断隔离违规（AI 尝试创建 FINAL+DOCTOR 被拒绝）
 * - 重复就诊（一个挂号多个就诊）
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EncounterService - 就诊服务测试")
class EncounterServiceTest {

    @Mock
    private EncounterRepository encounterRepository;

    @Mock
    private EncounterDiagnosisRepository encounterDiagnosisRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private EncounterService encounterService;

    private Patient testPatient;
    private Doctor testDoctor;
    private Department testDepartment;
    private Appointment testAppointment;
    private Encounter testEncounter;

    @BeforeEach
    void setUp() {
        testPatient = Patient.builder()
                .id(1L)
                .userId(10L)
                .name("测试患者")
                .gender("MALE")
                .status("ACTIVE")
                .build();

        testDoctor = Doctor.builder()
                .id(1L)
                .userId(20L)
                .departmentId(1L)
                .name("张医生")
                .title("ATTENDING")
                .status("ENABLED")
                .build();

        testDepartment = Department.builder()
                .id(1L)
                .code("DEPT_INTERNAL")
                .name("内科")
                .status("ENABLED")
                .build();

        testAppointment = Appointment.builder()
                .id(1L)
                .patientId(1L)
                .scheduleId(1L)
                .doctorId(1L)
                .appointmentNumber("APPT001")
                .status("BOOKED")
                .bookedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        testEncounter = Encounter.builder()
                .id(1L)
                .appointmentId(1L)
                .patientId(1L)
                .doctorId(1L)
                .departmentId(1L)
                .status("IN_PROGRESS")
                .startedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("开始接诊 - 正常创建 Encounter 并同步 Appointment 状态")
    void startEncounter_shouldCreateEncounterAndSyncAppointment() {
        EncounterStartRequest request = new EncounterStartRequest(1L);

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(testAppointment));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(encounterRepository.findByAppointmentId(1L)).thenReturn(Optional.empty());
        when(encounterRepository.save(any(Encounter.class))).thenAnswer(invocation -> {
            Encounter encounter = invocation.getArgument(0);
            encounter.setId(1L);
            return encounter;
        });
        when(appointmentRepository.updateStatusIfCurrent(eq(1L), eq("BOOKED"), eq("IN_PROGRESS"), any()))
                .thenReturn(1);
        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));

        EncounterResponse response = encounterService.startEncounter(request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        assertThat(response.appointmentId()).isEqualTo(1L);
        assertThat(response.startedAt()).isNotNull();

        // 验证同步更新了 Appointment 状态
        verify(appointmentRepository).updateStatusIfCurrent(eq(1L), eq("BOOKED"), eq("IN_PROGRESS"), any());
    }

    @Test
    @DisplayName("开始接诊 - 挂号状态非 BOOKED/CHECKED_IN 时抛出 BusinessException(409)")
    void startEncounter_shouldThrowWhenAppointmentStatusInvalid() {
        testAppointment.setStatus("COMPLETED");
        EncounterStartRequest request = new EncounterStartRequest(1L);

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(testAppointment));

        assertThatThrownBy(() -> encounterService.startEncounter(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ENCOUNTER_STATUS_CONFLICT")
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    @Test
    @DisplayName("开始接诊 - 重复就诊时抛出 BusinessException(409)")
    void startEncounter_shouldThrowWhenEncounterDuplicate() {
        EncounterStartRequest request = new EncounterStartRequest(1L);

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(testAppointment));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(encounterRepository.findByAppointmentId(1L)).thenReturn(Optional.of(testEncounter));

        assertThatThrownBy(() -> encounterService.startEncounter(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ENCOUNTER_DUPLICATE")
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    @Test
    @DisplayName("完成就诊 - 满足前置条件时成功完成")
    void completeEncounter_shouldSucceedWhenPreconditionsMet() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(testEncounter));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        // 医生最终诊断存在
        when(encounterDiagnosisRepository.existsByEncounterIdAndTypeAndSource(1L, "FINAL", "DOCTOR"))
                .thenReturn(true);
        when(encounterRepository.save(any(Encounter.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentRepository.updateStatusIfCurrent(eq(1L), eq("IN_PROGRESS"), eq("COMPLETED"), any()))
                .thenReturn(1);
        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));

        EncounterResponse response = encounterService.completeEncounter(1L);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.completedAt()).isNotNull();

        // 验证同步更新了 Appointment 状态
        verify(appointmentRepository).updateStatusIfCurrent(eq(1L), eq("IN_PROGRESS"), eq("COMPLETED"), any());
    }

    @Test
    @DisplayName("完成就诊 - 缺少医生最终诊断时抛出 BusinessException(409)")
    void completeEncounter_shouldThrowWhenFinalDiagnosisMissing() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(testEncounter));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        // 无医生最终诊断
        when(encounterDiagnosisRepository.existsByEncounterIdAndTypeAndSource(1L, "FINAL", "DOCTOR"))
                .thenReturn(false);

        assertThatThrownBy(() -> encounterService.completeEncounter(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ENCOUNTER_FINAL_DIAGNOSIS_REQUIRED")
                .hasFieldOrPropertyWithValue("httpStatus", 409);

        // 验证未更新就诊状态
        verify(encounterRepository, never()).save(any(Encounter.class));
        verify(appointmentRepository, never()).updateStatusIfCurrent(any(), any(), any(), any());
    }

    @Test
    @DisplayName("完成就诊 - 就诊状态非 IN_PROGRESS 时抛出 BusinessException(409)")
    void completeEncounter_shouldThrowWhenStatusNotInProgress() {
        testEncounter.setStatus("WAITING_EXAM");

        when(encounterRepository.findById(1L)).thenReturn(Optional.of(testEncounter));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));

        assertThatThrownBy(() -> encounterService.completeEncounter(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ENCOUNTER_STATUS_CONFLICT")
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    @Test
    @DisplayName("等待检查 - IN_PROGRESS → WAITING_EXAM 并同步 Appointment")
    void waitForExam_shouldTransitionToWaitingExam() {
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(testEncounter));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(encounterRepository.save(any(Encounter.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentRepository.updateStatusIfCurrent(eq(1L), eq("IN_PROGRESS"), eq("WAITING_EXAM"), any()))
                .thenReturn(1);
        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));

        EncounterResponse response = encounterService.waitForExam(1L);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("WAITING_EXAM");
        assertThat(response.waitingExamAt()).isNotNull();
        verify(appointmentRepository).updateStatusIfCurrent(eq(1L), eq("IN_PROGRESS"), eq("WAITING_EXAM"), any());
    }

    @Test
    @DisplayName("继续诊疗 - WAITING_EXAM → IN_PROGRESS 并同步 Appointment")
    void resumeEncounter_shouldTransitionToInProgress() {
        testEncounter.setStatus("WAITING_EXAM");

        when(encounterRepository.findById(1L)).thenReturn(Optional.of(testEncounter));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(encounterRepository.save(any(Encounter.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentRepository.updateStatusIfCurrent(eq(1L), eq("WAITING_EXAM"), eq("IN_PROGRESS"), any()))
                .thenReturn(1);
        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));

        EncounterResponse response = encounterService.resumeEncounter(1L);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        verify(appointmentRepository).updateStatusIfCurrent(eq(1L), eq("WAITING_EXAM"), eq("IN_PROGRESS"), any());
    }

    @Test
    @DisplayName("取消就诊 - CREATED → CANCELLED")
    void cancelEncounter_shouldTransitionToCancelled() {
        testEncounter.setStatus("CREATED");

        when(encounterRepository.findById(1L)).thenReturn(Optional.of(testEncounter));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(encounterRepository.save(any(Encounter.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));

        EncounterResponse response = encounterService.cancelEncounter(
                1L, new com.neusoft.cloudbrain.encounter.dto.EncounterCancelRequest("患者取消"));

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(response.cancelReason()).isEqualTo("患者取消");
        assertThat(response.cancelledAt()).isNotNull();
    }

    @Test
    @DisplayName("取消就诊 - 非 CREATED 状态时抛出 BusinessException(409)")
    void cancelEncounter_shouldThrowWhenStatusNotCreated() {
        // IN_PROGRESS 状态不允许取消
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(testEncounter));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));

        assertThatThrownBy(() -> encounterService.cancelEncounter(
                1L, new com.neusoft.cloudbrain.encounter.dto.EncounterCancelRequest("取消")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ENCOUNTER_STATUS_CONFLICT")
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    @Test
    @DisplayName("诊断隔离 - AI 尝试创建 FINAL+DOCTOR 诊断时被拒绝")
    void addAIDiagnosis_shouldRejectFinalDoctorCombination() {
        EncounterDiagnosisRequest request = new EncounterDiagnosisRequest(
                "J00", "急性上呼吸道感染",
                "FINAL", "DOCTOR", "AI 尝试创建最终诊断");

        when(encounterRepository.findById(1L)).thenReturn(Optional.of(testEncounter));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));

        assertThatThrownBy(() -> encounterService.addAIDiagnosis(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ENCOUNTER_DIAGNOSIS_ISOLATION_VIOLATION")
                .hasFieldOrPropertyWithValue("httpStatus", 409);

        verify(encounterDiagnosisRepository, never()).save(any(EncounterDiagnosis.class));
    }

    @Test
    @DisplayName("诊断隔离 - AI 创建 PRELIMINARY+AI_SUGGESTION 诊断成功")
    void addAIDiagnosis_shouldAcceptPreliminaryAISuggestion() {
        EncounterDiagnosisRequest request = new EncounterDiagnosisRequest(
                "J00", "急性上呼吸道感染",
                "PRELIMINARY", "AI_SUGGESTION", "AI 候选诊断");

        when(encounterRepository.findById(1L)).thenReturn(Optional.of(testEncounter));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(encounterDiagnosisRepository.save(any(EncounterDiagnosis.class))).thenAnswer(invocation -> {
            EncounterDiagnosis diagnosis = invocation.getArgument(0);
            diagnosis.setId(1L);
            return diagnosis;
        });

        EncounterDiagnosisResponse response = encounterService.addAIDiagnosis(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.type()).isEqualTo("PRELIMINARY");
        assertThat(response.source()).isEqualTo("AI_SUGGESTION");
        assertThat(response.doctorId()).isNull();
    }

    @Test
    @DisplayName("诊断隔离 - 医生创建 FINAL+DOCTOR 诊断成功")
    void addDoctorDiagnosis_shouldAcceptFinalDoctor() {
        EncounterDiagnosisRequest request = new EncounterDiagnosisRequest(
                "J00", "急性上呼吸道感染",
                "FINAL", "DOCTOR", "医生最终诊断");

        when(encounterRepository.findById(1L)).thenReturn(Optional.of(testEncounter));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(encounterDiagnosisRepository.save(any(EncounterDiagnosis.class))).thenAnswer(invocation -> {
            EncounterDiagnosis diagnosis = invocation.getArgument(0);
            diagnosis.setId(1L);
            return diagnosis;
        });

        EncounterDiagnosisResponse response = encounterService.addDoctorDiagnosis(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.type()).isEqualTo("FINAL");
        assertThat(response.source()).isEqualTo("DOCTOR");
        assertThat(response.doctorId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("诊断隔离 - 医生创建非 FINAL+DOCTOR 诊断时被拒绝")
    void addDoctorDiagnosis_shouldRejectNonFinalDoctor() {
        EncounterDiagnosisRequest request = new EncounterDiagnosisRequest(
                "J00", "急性上呼吸道感染",
                "PRELIMINARY", "DOCTOR", "医生候选诊断");

        when(encounterRepository.findById(1L)).thenReturn(Optional.of(testEncounter));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));

        assertThatThrownBy(() -> encounterService.addDoctorDiagnosis(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ENCOUNTER_DIAGNOSIS_ISOLATION_VIOLATION")
                .hasFieldOrPropertyWithValue("httpStatus", 409);

        verify(encounterDiagnosisRepository, never()).save(any(EncounterDiagnosis.class));
    }

    @Test
    @DisplayName("就诊不存在时抛出 BusinessException(404)")
    void getEncounterById_shouldThrowWhenNotFound() {
        when(encounterRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> encounterService.getEncounterById(99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ENCOUNTER_NOT_FOUND")
                .hasFieldOrPropertyWithValue("httpStatus", 404);
    }
}
