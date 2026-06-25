package com.neusoft.cloudbrain.appointment.service;

import com.neusoft.cloudbrain.appointment.dto.AppointmentCancelRequest;
import com.neusoft.cloudbrain.appointment.dto.AppointmentCreateRequest;
import com.neusoft.cloudbrain.appointment.dto.AppointmentResponse;
import com.neusoft.cloudbrain.appointment.entity.Appointment;
import com.neusoft.cloudbrain.appointment.repository.AppointmentRepository;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.neusoft.cloudbrain.department.entity.Department;
import com.neusoft.cloudbrain.department.repository.DepartmentRepository;
import com.neusoft.cloudbrain.doctor.entity.Doctor;
import com.neusoft.cloudbrain.doctor.repository.DoctorRepository;
import com.neusoft.cloudbrain.patient.entity.Patient;
import com.neusoft.cloudbrain.patient.repository.PatientRepository;
import com.neusoft.cloudbrain.schedule.entity.Schedule;
import com.neusoft.cloudbrain.schedule.repository.ScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
 * AppointmentService 单元测试
 *
 * 覆盖文档 11.2 必测场景：
 * - 重复挂号
 * - 号源已满（并发竞争）
 * - 过期排班不能挂号
 * - 接诊开始后不能取消
 * - 取消挂号恢复号源
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AppointmentService - 挂号服务测试")
class AppointmentServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private AppointmentService appointmentService;

    private Patient testPatient;
    private Doctor testDoctor;
    private Department testDepartment;
    private Schedule testSchedule;
    private Appointment testAppointment;

    @BeforeEach
    void setUp() {
        testPatient = Patient.builder()
                .id(1L)
                .userId(10L)
                .name("测试患者")
                .gender("MALE")
                .phone("13800138000")
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

        testSchedule = Schedule.builder()
                .id(1L)
                .doctorId(1L)
                .departmentId(1L)
                .scheduleDate(LocalDate.now().plusDays(1))
                .startTime(LocalDateTime.now().plusDays(1).withHour(8).withMinute(0))
                .endTime(LocalDateTime.now().plusDays(1).withHour(12).withMinute(0))
                .maxAppointments(10)
                .bookedCount(0)
                .status("AVAILABLE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
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
    }

    @Test
    @DisplayName("创建挂号 - 正常创建成功")
    void createAppointment_shouldCreateSuccessfully() {
        AppointmentCreateRequest request = new AppointmentCreateRequest(1L, 1L);

        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));
        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(testSchedule));
        when(appointmentRepository.existsByPatientIdAndScheduleIdAndStatusNot(
                1L, 1L, "CANCELLED")).thenReturn(false);
        when(scheduleRepository.incrementBookedCount(eq(1L), any()))
                .thenReturn(1);
        when(appointmentRepository.save(any(Appointment.class))).thenReturn(testAppointment);
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));

        AppointmentResponse response = appointmentService.createAppointment(request);

        assertThat(response).isNotNull();
        verify(scheduleRepository).incrementBookedCount(eq(1L), any());
        verify(appointmentRepository).save(any(Appointment.class));
    }

    @Test
    @DisplayName("创建挂号 - 患者不存在时抛出 BusinessException(404)")
    void createAppointment_shouldThrowWhenPatientNotFound() {
        AppointmentCreateRequest request = new AppointmentCreateRequest(99L, 1L);

        when(patientRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.createAppointment(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PATIENT_NOT_FOUND")
                .hasFieldOrPropertyWithValue("httpStatus", 404);
    }

    @Test
    @DisplayName("创建挂号 - 患者已停用时抛出 BusinessException(409)")
    void createAppointment_shouldThrowWhenPatientInactive() {
        testPatient.setStatus("DISABLED");
        AppointmentCreateRequest request = new AppointmentCreateRequest(1L, 1L);

        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));

        assertThatThrownBy(() -> appointmentService.createAppointment(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "APPOINTMENT_STATUS_CONFLICT")
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    @Test
    @DisplayName("创建挂号 - 排班已取消时抛出 BusinessException(409)")
    void createAppointment_shouldThrowWhenScheduleCancelled() {
        testSchedule.setStatus("CANCELLED");
        AppointmentCreateRequest request = new AppointmentCreateRequest(1L, 1L);

        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));
        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(testSchedule));

        assertThatThrownBy(() -> appointmentService.createAppointment(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "APPOINTMENT_SCHEDULE_NOT_AVAILABLE")
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    @Test
    @DisplayName("创建挂号 - 排班已过期时抛出 BusinessException(409)")
    void createAppointment_shouldThrowWhenScheduleExpired() {
        testSchedule.setEndTime(LocalDateTime.now().minusHours(1));
        AppointmentCreateRequest request = new AppointmentCreateRequest(1L, 1L);

        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));
        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(testSchedule));

        assertThatThrownBy(() -> appointmentService.createAppointment(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "APPOINTMENT_SCHEDULE_NOT_AVAILABLE")
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    @Test
    @DisplayName("创建挂号 - 重复挂号时抛出 BusinessException(409)")
    void createAppointment_shouldThrowWhenDuplicate() {
        AppointmentCreateRequest request = new AppointmentCreateRequest(1L, 1L);

        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));
        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(testSchedule));
        when(appointmentRepository.existsByPatientIdAndScheduleIdAndStatusNot(
                1L, 1L, "CANCELLED")).thenReturn(true);

        assertThatThrownBy(() -> appointmentService.createAppointment(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "APPOINTMENT_DUPLICATED")
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    @Test
    @DisplayName("创建挂号 - 号源已满时抛出 BusinessException(409)（并发竞争场景）")
    void createAppointment_shouldThrowWhenScheduleFull() {
        AppointmentCreateRequest request = new AppointmentCreateRequest(1L, 1L);

        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));
        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(testSchedule));
        when(appointmentRepository.existsByPatientIdAndScheduleIdAndStatusNot(
                1L, 1L, "CANCELLED")).thenReturn(false);
        // 模拟并发竞争：条件更新返回 0 行
        when(scheduleRepository.incrementBookedCount(eq(1L), any()))
                .thenReturn(0);

        assertThatThrownBy(() -> appointmentService.createAppointment(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "APPOINTMENT_SCHEDULE_FULL")
                .hasFieldOrPropertyWithValue("httpStatus", 409);

        // 验证未创建挂号记录
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    @DisplayName("取消挂号 - 正常取消并恢复号源")
    void cancelAppointment_shouldCancelAndRestoreCapacity() {
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(testAppointment));
        when(scheduleRepository.decrementBookedCount(eq(1L), any())).thenReturn(1);
        when(appointmentRepository.save(any(Appointment.class))).thenReturn(testAppointment);
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));

        AppointmentCancelRequest request = new AppointmentCancelRequest("测试取消");
        AppointmentResponse response = appointmentService.cancelAppointment(1L, request);

        assertThat(response).isNotNull();
        assertThat(testAppointment.getStatus()).isEqualTo("CANCELLED");
        assertThat(testAppointment.getCancellationSource()).isEqualTo("PATIENT");
        verify(scheduleRepository).decrementBookedCount(eq(1L), any());
    }

    @Test
    @DisplayName("取消挂号 - 接诊开始后不能取消")
    void cancelAppointment_shouldRejectWhenInProgress() {
        testAppointment.setStatus("IN_PROGRESS");
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(testAppointment));

        AppointmentCancelRequest request = new AppointmentCancelRequest("测试取消");

        assertThatThrownBy(() -> appointmentService.cancelAppointment(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "APPOINTMENT_CANNOT_CANCEL")
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    @Test
    @DisplayName("取消挂号 - 已完成的挂号不能取消")
    void cancelAppointment_shouldRejectWhenCompleted() {
        testAppointment.setStatus("COMPLETED");
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(testAppointment));

        AppointmentCancelRequest request = new AppointmentCancelRequest("测试取消");

        assertThatThrownBy(() -> appointmentService.cancelAppointment(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "APPOINTMENT_STATUS_CONFLICT")
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    @Test
    @DisplayName("取消挂号 - 已取消的挂号不能再次取消")
    void cancelAppointment_shouldRejectWhenAlreadyCancelled() {
        testAppointment.setStatus("CANCELLED");
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(testAppointment));

        AppointmentCancelRequest request = new AppointmentCancelRequest("测试取消");

        assertThatThrownBy(() -> appointmentService.cancelAppointment(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "APPOINTMENT_STATUS_CONFLICT")
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    @Test
    @DisplayName("获取挂号详情 - 存在时返回正确信息")
    void getAppointmentById_shouldReturnWhenExists() {
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(testAppointment));
        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));

        AppointmentResponse response = appointmentService.getAppointmentById(1L);

        assertThat(response).isNotNull();
        assertThat(response.patientName()).isEqualTo("测试患者");
        assertThat(response.doctorName()).isEqualTo("张医生");
    }

    @Test
    @DisplayName("获取患者挂号列表 - 返回分页结果")
    void getAppointmentsByPatient_shouldReturnPagedResult() {
        Pageable pageable = PageRequest.of(0, 10);
        when(appointmentRepository.findByPatientId(eq(1L), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(testAppointment), pageable, 1));
        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));

        var responses = appointmentService.getAppointmentsByPatient(1L, pageable);

        assertThat(responses.getContent()).hasSize(1);
        assertThat(responses.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("获取医生待诊队列 - 只返回 BOOKED 状态")
    void getDoctorPendingAppointments_shouldReturnOnlyBooked() {
        when(appointmentRepository.findByDoctorIdAndStatus(1L, "BOOKED"))
                .thenReturn(List.of(testAppointment));
        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));

        List<AppointmentResponse> responses = appointmentService.getDoctorPendingAppointments(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).status()).isEqualTo("BOOKED");
    }

    @Test
    @DisplayName("获取挂号详情 - 不存在时抛出 BusinessException(404)")
    void getAppointmentById_shouldThrowWhenNotFound() {
        when(appointmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.getAppointmentById(99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "APPOINTMENT_NOT_FOUND")
                .hasFieldOrPropertyWithValue("httpStatus", 404);
    }
}
