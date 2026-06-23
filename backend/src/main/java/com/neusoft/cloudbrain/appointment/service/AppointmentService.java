package com.neusoft.cloudbrain.appointment.service;

import com.neusoft.cloudbrain.appointment.dto.AppointmentCancelRequest;
import com.neusoft.cloudbrain.appointment.dto.AppointmentCreateRequest;
import com.neusoft.cloudbrain.appointment.dto.AppointmentResponse;
import com.neusoft.cloudbrain.appointment.entity.Appointment;
import com.neusoft.cloudbrain.appointment.exception.AppointmentErrorCode;
import com.neusoft.cloudbrain.appointment.repository.AppointmentRepository;
import com.neusoft.cloudbrain.auth.dto.AuthPrincipal;
import com.neusoft.cloudbrain.auth.security.SecurityUtils;
import com.neusoft.cloudbrain.department.entity.Department;
import com.neusoft.cloudbrain.department.repository.DepartmentRepository;
import com.neusoft.cloudbrain.doctor.entity.Doctor;
import com.neusoft.cloudbrain.doctor.repository.DoctorRepository;
import com.neusoft.cloudbrain.patient.entity.Patient;
import com.neusoft.cloudbrain.patient.repository.PatientRepository;
import com.neusoft.cloudbrain.schedule.entity.Schedule;
import com.neusoft.cloudbrain.schedule.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 挂号 Service
 *
 * 功能：
 * - 创建挂号
 * - 查看本人挂号
 * - 取消挂号
 * - 管理员查看全部挂号
 * - 医生查看本人待诊患者
 *
 * 核心规则（来自 12_业务流程与状态机.md 第4节 和 11_功能需求.md 第7节）：
 * - 创建挂号必须使用事务
 * - 并发下不能超卖（使用数据库条件更新）
 * - 取消成功后恢复号源
 * - 接诊开始后不能由患者取消
 * - 挂号记录不能物理删除
 * - 同一患者同一排班不能重复挂号
 * - 过期或取消排班继续挂号
 */
@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final ScheduleRepository scheduleRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final DepartmentRepository departmentRepository;

    /**
     * 创建挂号
     *
     * 事务保证：
     * 1. 检查患者权限
     * 2. 检查排班可用性（条件更新防止超卖）
     * 3. 创建挂号记录
     */
    @Transactional
    public AppointmentResponse createAppointment(AppointmentCreateRequest request) {
        // 1. 检查患者存在且活跃
        Patient patient = patientRepository.findById(request.patientId())
                .orElseThrow(AppointmentErrorCode.PATIENT_NOT_FOUND::toException);
        if (!"ACTIVE".equals(patient.getStatus())) {
            throw AppointmentErrorCode.APPOINTMENT_STATUS_CONFLICT.toException();
        }

        // 患者只能为自己挂号
        checkPatientOwnership(request.patientId());

        // 2. 检查排班存在
        Schedule schedule = scheduleRepository.findById(request.scheduleId())
                .orElseThrow(AppointmentErrorCode.SCHEDULE_NOT_FOUND::toException);

        // 3. 检查排班是否已取消
        if ("CANCELLED".equals(schedule.getStatus())) {
            throw AppointmentErrorCode.APPOINTMENT_SCHEDULE_NOT_AVAILABLE.toException();
        }

        // 4. 检查排班是否已过期
        LocalDateTime now = LocalDateTime.now();
        if (schedule.getEndTime().isBefore(now)) {
            throw AppointmentErrorCode.APPOINTMENT_SCHEDULE_NOT_AVAILABLE.toException();
        }

        // 5. 检查重复预约（同一患者同一排班不能重复挂号）
        if (appointmentRepository.existsByPatientIdAndScheduleIdAndStatusNot(
                request.patientId(), request.scheduleId(), "CANCELLED")) {
            throw AppointmentErrorCode.APPOINTMENT_DUPLICATED.toException();
        }

        // 6. 条件更新扣减号源（防止超卖）
        int updated = scheduleRepository.incrementBookedCount(request.scheduleId(), now);
        if (updated == 0) {
            throw AppointmentErrorCode.APPOINTMENT_SCHEDULE_FULL.toException();
        }

        // 7. 创建挂号记录
        Appointment appointment = Appointment.builder()
                .patientId(request.patientId())
                .scheduleId(request.scheduleId())
                .doctorId(schedule.getDoctorId())
                .appointmentNumber(generateAppointmentNumber())
                .status("BOOKED")
                .bookedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        appointment = appointmentRepository.save(appointment);

        return toResponse(appointment);
    }

    /**
     * 取消挂号
     *
     * 事务保证：
     * 1. 检查挂号状态
     * 2. 接诊开始后不得取消
     * 3. 取消挂号并在事务内同步恢复号源
     */
    @Transactional
    public AppointmentResponse cancelAppointment(Long id, AppointmentCancelRequest request) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(AppointmentErrorCode.APPOINTMENT_NOT_FOUND::toException);

        // 权限校验：患者只能取消自己的挂号
        checkAppointmentAccess(appointment);

        // 检查挂号状态
        if ("CANCELLED".equals(appointment.getStatus())) {
            throw AppointmentErrorCode.APPOINTMENT_STATUS_CONFLICT.toException();
        }
        if ("COMPLETED".equals(appointment.getStatus())) {
            throw AppointmentErrorCode.APPOINTMENT_STATUS_CONFLICT.toException();
        }
        // 接诊开始后不得取消
        if ("IN_PROGRESS".equals(appointment.getStatus())
                || "WAITING_EXAM".equals(appointment.getStatus())) {
            throw AppointmentErrorCode.APPOINTMENT_CANNOT_CANCEL.toException();
        }

        LocalDateTime now = LocalDateTime.now();

        // 取消挂号
        appointment.setStatus("CANCELLED");
        appointment.setCancellationSource("PATIENT");
        appointment.setCancellationReason(request.reason());
        appointment.setCancelledAt(now);
        appointment.setUpdatedAt(now);

        // 恢复号源（条件更新，排班未取消时才恢复）
        scheduleRepository.decrementBookedCount(appointment.getScheduleId(), now);

        appointment = appointmentRepository.save(appointment);

        return toResponse(appointment);
    }

    /**
     * 获取挂号详情
     */
    @Transactional(readOnly = true)
    public AppointmentResponse getAppointmentById(Long id) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(AppointmentErrorCode.APPOINTMENT_NOT_FOUND::toException);
        // 权限校验：患者只能查看自己的挂号
        checkAppointmentAccess(appointment);
        return toResponse(appointment);
    }

    /**
     * 查询患者挂号（患者只能查看本人挂号，分页）
     */
    @Transactional(readOnly = true)
    public Page<AppointmentResponse> getAppointmentsByPatient(Long patientId, Pageable pageable) {
        checkPatientOwnership(patientId);
        return appointmentRepository.findByPatientId(patientId, pageable)
                .map(this::toResponse);
    }

    /**
     * 查询医生待诊患者（医生查看本人待诊队列）
     */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> getDoctorPendingAppointments(Long doctorId) {
        return appointmentRepository.findByDoctorIdAndStatus(doctorId, "BOOKED").stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 查询医生所有挂号（分页）
     */
    @Transactional(readOnly = true)
    public Page<AppointmentResponse> getAppointmentsByDoctor(Long doctorId, Pageable pageable) {
        return appointmentRepository.findByDoctorId(doctorId, pageable)
                .map(this::toResponse);
    }

    /**
     * 管理员查看全部挂号
     */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAllAppointments() {
        return appointmentRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 校验当前登录患者只能操作自己的数据
     */
    private void checkPatientOwnership(Long patientId) {
        if (!SecurityUtils.isAuthenticated()) {
            return;
        }
        AuthPrincipal currentUser = SecurityUtils.getCurrentUser();
        if (currentUser.roles() != null && currentUser.roles().contains("PATIENT")) {
            Patient currentPatient = patientRepository.findByUserId(currentUser.userId())
                    .orElseThrow(AppointmentErrorCode.PATIENT_NOT_FOUND::toException);
            if (!currentPatient.getId().equals(patientId)) {
                throw AppointmentErrorCode.APPOINTMENT_PERMISSION_DENIED.toException();
            }
        }
    }

    /**
     * 校验当前登录用户对挂号的访问权限
     * - 患者：只能访问自己的挂号
     * - 医生：可访问自己作为接诊医生的挂号
     * - 管理员：可访问所有挂号
     */
    private void checkAppointmentAccess(Appointment appointment) {
        if (!SecurityUtils.isAuthenticated()) {
            return;
        }
        AuthPrincipal currentUser = SecurityUtils.getCurrentUser();
        if (currentUser.roles() == null) {
            return;
        }
        // 管理员放行
        if (currentUser.roles().contains("ADMIN")) {
            return;
        }
        // 患者：只能访问自己的挂号
        if (currentUser.roles().contains("PATIENT")) {
            Patient currentPatient = patientRepository.findByUserId(currentUser.userId())
                    .orElseThrow(AppointmentErrorCode.PATIENT_NOT_FOUND::toException);
            if (!currentPatient.getId().equals(appointment.getPatientId())) {
                throw AppointmentErrorCode.APPOINTMENT_PERMISSION_DENIED.toException();
            }
        }
        // 医生：可访问自己作为接诊医生的挂号
        if (currentUser.roles().contains("DOCTOR")) {
            if (!appointment.getDoctorId().equals(currentUser.userId())) {
                throw AppointmentErrorCode.APPOINTMENT_PERMISSION_DENIED.toException();
            }
        }
    }

    /**
     * 生成挂号号
     */
    private String generateAppointmentNumber() {
        return "APPT" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    /**
     * 转换为响应 DTO
     */
    private AppointmentResponse toResponse(Appointment appointment) {
        Patient patient = patientRepository.findById(appointment.getPatientId()).orElse(null);
        Doctor doctor = doctorRepository.findById(appointment.getDoctorId()).orElse(null);
        Department department = doctor != null
                ? departmentRepository.findById(doctor.getDepartmentId()).orElse(null)
                : null;

        return new AppointmentResponse(
                appointment.getId(),
                appointment.getPatientId(),
                patient != null ? patient.getName() : null,
                appointment.getScheduleId(),
                appointment.getDoctorId(),
                doctor != null ? doctor.getName() : null,
                doctor != null ? doctor.getDepartmentId() : null,
                department != null ? department.getName() : null,
                appointment.getAppointmentNumber(),
                appointment.getStatus(),
                appointment.getBookedAt(),
                appointment.getCheckInTime(),
                appointment.getCancellationReason(),
                appointment.getCancellationSource(),
                appointment.getCancelledAt(),
                appointment.getCreatedAt(),
                appointment.getUpdatedAt()
        );
    }
}
