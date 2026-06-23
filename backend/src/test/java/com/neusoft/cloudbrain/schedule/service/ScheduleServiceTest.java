package com.neusoft.cloudbrain.schedule.service;

import com.neusoft.cloudbrain.appointment.entity.Appointment;
import com.neusoft.cloudbrain.appointment.repository.AppointmentRepository;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.neusoft.cloudbrain.department.entity.Department;
import com.neusoft.cloudbrain.department.repository.DepartmentRepository;
import com.neusoft.cloudbrain.doctor.entity.Doctor;
import com.neusoft.cloudbrain.doctor.repository.DoctorRepository;
import com.neusoft.cloudbrain.schedule.dto.ScheduleCancelRequest;
import com.neusoft.cloudbrain.schedule.dto.ScheduleCreateRequest;
import com.neusoft.cloudbrain.schedule.dto.ScheduleResponse;
import com.neusoft.cloudbrain.schedule.dto.ScheduleUpdateRequest;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ScheduleService 单元测试
 *
 * 覆盖文档 11.2 必测场景：
 * - 排班冲突
 * - 号源已满
 * - 取消排班同步取消未开始挂号
 * - 存在进行中或已完成挂号时拒绝取消排班
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduleService - 排班服务测试")
class ScheduleServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @InjectMocks
    private ScheduleService scheduleService;

    private Doctor testDoctor;
    private Department testDepartment;
    private Schedule testSchedule;

    @BeforeEach
    void setUp() {
        testDoctor = Doctor.builder()
                .id(1L)
                .userId(10L)
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
    }

    @Test
    @DisplayName("创建排班 - 正常创建成功")
    void createSchedule_shouldCreateSuccessfully() {
        ScheduleCreateRequest request = new ScheduleCreateRequest(
                1L, 1L, LocalDate.now().plusDays(1),
                LocalDateTime.now().plusDays(1).withHour(8).withMinute(0),
                LocalDateTime.now().plusDays(1).withHour(12).withMinute(0),
                10);

        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));
        when(scheduleRepository.findConflictingSchedules(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(scheduleRepository.save(any(Schedule.class))).thenReturn(testSchedule);

        ScheduleResponse response = scheduleService.createSchedule(request);

        assertThat(response).isNotNull();
        verify(scheduleRepository).save(any(Schedule.class));
    }

    @Test
    @DisplayName("创建排班 - 医生不存在时抛出 BusinessException(404)")
    void createSchedule_shouldThrowWhenDoctorNotFound() {
        ScheduleCreateRequest request = new ScheduleCreateRequest(
                99L, 1L, LocalDate.now().plusDays(1),
                LocalDateTime.now().plusDays(1).withHour(8).withMinute(0),
                LocalDateTime.now().plusDays(1).withHour(12).withMinute(0),
                10);

        when(doctorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.createSchedule(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DOCTOR_NOT_FOUND")
                .hasFieldOrPropertyWithValue("httpStatus", 404);
    }

    @Test
    @DisplayName("创建排班 - 医生已停用时抛出 BusinessException(409)")
    void createSchedule_shouldThrowWhenDoctorDisabled() {
        testDoctor.setStatus("DISABLED");
        ScheduleCreateRequest request = new ScheduleCreateRequest(
                1L, 1L, LocalDate.now().plusDays(1),
                LocalDateTime.now().plusDays(1).withHour(8).withMinute(0),
                LocalDateTime.now().plusDays(1).withHour(12).withMinute(0),
                10);

        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));

        assertThatThrownBy(() -> scheduleService.createSchedule(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DOCTOR_DISABLED")
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    @Test
    @DisplayName("创建排班 - 排班时间冲突时抛出 BusinessException(409)")
    void createSchedule_shouldThrowWhenTimeConflict() {
        ScheduleCreateRequest request = new ScheduleCreateRequest(
                1L, 1L, LocalDate.now().plusDays(1),
                LocalDateTime.now().plusDays(1).withHour(8).withMinute(0),
                LocalDateTime.now().plusDays(1).withHour(12).withMinute(0),
                10);

        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));
        when(scheduleRepository.findConflictingSchedules(any(), any(), any(), any()))
                .thenReturn(List.of(testSchedule));

        assertThatThrownBy(() -> scheduleService.createSchedule(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SCHEDULE_CONFLICT")
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    @Test
    @DisplayName("创建排班 - 开始时间晚于结束时间时抛出 BusinessException(400)")
    void createSchedule_shouldThrowWhenStartTimeAfterEndTime() {
        ScheduleCreateRequest request = new ScheduleCreateRequest(
                1L, 1L, LocalDate.now().plusDays(1),
                LocalDateTime.now().plusDays(1).withHour(14).withMinute(0),
                LocalDateTime.now().plusDays(1).withHour(8).withMinute(0),
                10);

        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));

        assertThatThrownBy(() -> scheduleService.createSchedule(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SCHEDULE_TIME_INVALID")
                .hasFieldOrPropertyWithValue("httpStatus", 400);
    }

    @Test
    @DisplayName("取消排班 - 正常取消并同步取消未开始挂号")
    void cancelSchedule_shouldCancelAndSyncAppointments() {
        Appointment bookedAppointment = Appointment.builder()
                .id(1L)
                .patientId(1L)
                .scheduleId(1L)
                .doctorId(1L)
                .appointmentNumber("APPT001")
                .status("BOOKED")
                .bookedAt(LocalDateTime.now())
                .build();

        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(testSchedule));
        when(appointmentRepository.findByScheduleId(1L))
                .thenReturn(List.of(bookedAppointment));
        when(scheduleRepository.save(any(Schedule.class))).thenReturn(testSchedule);
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));

        ScheduleCancelRequest request = new ScheduleCancelRequest("测试取消");
        ScheduleResponse response = scheduleService.cancelSchedule(1L, request);

        assertThat(response).isNotNull();
        assertThat(bookedAppointment.getStatus()).isEqualTo("CANCELLED");
        assertThat(bookedAppointment.getCancellationSource()).isEqualTo("SCHEDULE");
        verify(appointmentRepository).save(bookedAppointment);
    }

    @Test
    @DisplayName("取消排班 - 存在进行中挂号时拒绝取消")
    void cancelSchedule_shouldRejectWhenHasInProgressAppointment() {
        Appointment inProgressAppointment = Appointment.builder()
                .id(1L)
                .patientId(1L)
                .scheduleId(1L)
                .doctorId(1L)
                .appointmentNumber("APPT001")
                .status("IN_PROGRESS")
                .bookedAt(LocalDateTime.now())
                .build();

        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(testSchedule));
        when(appointmentRepository.findByScheduleId(1L))
                .thenReturn(List.of(inProgressAppointment));

        ScheduleCancelRequest request = new ScheduleCancelRequest("测试取消");

        assertThatThrownBy(() -> scheduleService.cancelSchedule(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SCHEDULE_CANCEL_CONFLICT")
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    @Test
    @DisplayName("取消排班 - 已取消的排班不能再次取消")
    void cancelSchedule_shouldThrowWhenAlreadyCancelled() {
        testSchedule.setStatus("CANCELLED");
        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(testSchedule));

        ScheduleCancelRequest request = new ScheduleCancelRequest("测试取消");

        assertThatThrownBy(() -> scheduleService.cancelSchedule(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SCHEDULE_STATUS_CONFLICT")
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    @Test
    @DisplayName("更新排班 - 已有预约时不能减少容量")
    void updateSchedule_shouldRejectWhenReducingCapacityBelowBooked() {
        testSchedule.setBookedCount(5);
        ScheduleUpdateRequest request = new ScheduleUpdateRequest(
                testSchedule.getStartTime(),
                testSchedule.getEndTime(),
                3);

        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(testSchedule));

        assertThatThrownBy(() -> scheduleService.updateSchedule(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SCHEDULE_CAPACITY_CONFLICT")
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    @Test
    @DisplayName("获取排班详情 - 存在时返回正确信息")
    void getScheduleById_shouldReturnWhenExists() {
        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(testSchedule));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));

        ScheduleResponse response = scheduleService.getScheduleById(1L);

        assertThat(response).isNotNull();
        assertThat(response.doctorName()).isEqualTo("张医生");
        assertThat(response.departmentName()).isEqualTo("内科");
    }

    @Test
    @DisplayName("获取排班详情 - 不存在时抛出 BusinessException(404)")
    void getScheduleById_shouldThrowWhenNotExists() {
        when(scheduleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.getScheduleById(99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SCHEDULE_NOT_FOUND")
                .hasFieldOrPropertyWithValue("httpStatus", 404);
    }

    @Test
    @DisplayName("状态计算 - 号源已满时状态为 FULL")
    void statusCalculation_shouldReturnFullWhenBookedEqualsMax() {
        testSchedule.setBookedCount(10);
        testSchedule.setMaxAppointments(10);

        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(testSchedule));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));

        ScheduleResponse response = scheduleService.getScheduleById(1L);

        assertThat(response.status()).isEqualTo("FULL");
        assertThat(response.remainingCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("按医生查询排班 - 返回分页结果")
    void getSchedulesByDoctor_shouldReturnPagedResult() {
        Pageable pageable = PageRequest.of(0, 10);
        when(scheduleRepository.findByDoctorId(eq(1L), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(testSchedule), pageable, 1));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));

        var responses = scheduleService.getSchedulesByDoctor(1L, pageable);

        assertThat(responses.getContent()).hasSize(1);
        assertThat(responses.getTotalElements()).isEqualTo(1);
    }
}
