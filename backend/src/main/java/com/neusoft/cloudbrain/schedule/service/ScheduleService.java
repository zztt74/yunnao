package com.neusoft.cloudbrain.schedule.service;

import com.neusoft.cloudbrain.appointment.entity.Appointment;
import com.neusoft.cloudbrain.appointment.repository.AppointmentRepository;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 排班 Service
 *
 * 功能：
 * - 新增排班
 * - 修改未开始排班
 * - 取消排班
 * - 按医生、科室和日期查询
 * - 设置起止时间
 * - 设置最大号源数
 * - 显示已预约和剩余号源
 * - 检测时间冲突
 *
 * 核心规则（来自 12_业务流程与状态机.md 第3节）：
 * - 同一医生有效排班不得重叠
 * - 已预约数不能超过容量
 * - 已有预约不能直接删除
 * - AVAILABLE 或 FULL 可以由管理员取消为 CANCELLED，但必须记录原因并处理已有预约
 * - 取消排班与取消其 BOOKED、CHECKED_IN 挂号必须在同一事务完成
 * - 因排班取消的挂号记录 cancellationSource=SCHEDULE，且不恢复已取消排班的可预约号源
 * - 存在 IN_PROGRESS、WAITING_EXAM 或 COMPLETED 挂号时禁止取消排班
 * - AVAILABLE 与 FULL 随号源占用和恢复自动切换
 * - 到达结束时间后转为 COMPLETED
 * - CANCELLED 和 COMPLETED 不得恢复为可预约状态
 */
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final DoctorRepository doctorRepository;
    private final DepartmentRepository departmentRepository;
    private final AppointmentRepository appointmentRepository;

    /**
     * 创建排班
     */
    @Transactional
    public ScheduleResponse createSchedule(ScheduleCreateRequest request) {
        // 校验医生存在且启用
        Doctor doctor = doctorRepository.findById(request.doctorId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "DOCTOR_NOT_FOUND:医生不存在"));
        if (!"ENABLED".equals(doctor.getStatus())) {
            throw new IllegalArgumentException(
                    "DOCTOR_DISABLED:医生已停用，不能创建排班");
        }

        // 校验科室存在且启用
        Department department = departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "DEPARTMENT_NOT_FOUND:科室不存在"));
        if (!"ENABLED".equals(department.getStatus())) {
            throw new IllegalArgumentException(
                    "DEPARTMENT_DISABLED:科室已停用，不能创建排班");
        }

        // 校验时间合法性
        if (!request.startTime().isBefore(request.endTime())) {
            throw new IllegalArgumentException(
                    "VALIDATION_FAILED:开始时间必须早于结束时间");
        }

        // 校验时间不重叠（同一医生同一天）
        List<Schedule> conflicts = scheduleRepository.findConflictingSchedules(
                request.doctorId(),
                request.scheduleDate(),
                request.startTime(),
                request.endTime());
        if (!conflicts.isEmpty()) {
            throw new IllegalArgumentException(
                    "SCHEDULE_CONFLICT:同一医生排班时间重叠");
        }

        LocalDateTime now = LocalDateTime.now();
        Schedule schedule = Schedule.builder()
                .doctorId(request.doctorId())
                .departmentId(request.departmentId())
                .scheduleDate(request.scheduleDate())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .maxAppointments(request.maxAppointments())
                .bookedCount(0)
                .status("AVAILABLE")
                .createdAt(now)
                .updatedAt(now)
                .build();

        schedule = scheduleRepository.save(schedule);
        return toResponse(schedule, doctor.getName(), department.getName());
    }

    /**
     * 更新排班（仅允许修改未开始排班）
     */
    @Transactional
    public ScheduleResponse updateSchedule(Long id, ScheduleUpdateRequest request) {
        Schedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "SCHEDULE_NOT_FOUND:排班不存在"));

        // 已取消或已完成的排班不能修改
        if ("CANCELLED".equals(schedule.getStatus()) || "COMPLETED".equals(schedule.getStatus())) {
            throw new IllegalArgumentException(
                    "SCHEDULE_STATUS_CONFLICT:已取消或已结束的排班不能修改");
        }

        // 已有预约时不能减少容量
        if (request.maxAppointments() < schedule.getBookedCount()) {
            throw new IllegalArgumentException(
                    "SCHEDULE_CAPACITY_CONFLICT:已有预约数大于新的最大号源数");
        }

        // 校验时间合法性
        if (!request.startTime().isBefore(request.endTime())) {
            throw new IllegalArgumentException(
                    "VALIDATION_FAILED:开始时间必须早于结束时间");
        }

        // 检查时间冲突（排除自身）
        List<Schedule> conflicts = scheduleRepository.findConflictingSchedules(
                schedule.getDoctorId(),
                schedule.getScheduleDate(),
                request.startTime(),
                request.endTime());
        conflicts = conflicts.stream()
                .filter(s -> !s.getId().equals(id))
                .collect(Collectors.toList());
        if (!conflicts.isEmpty()) {
            throw new IllegalArgumentException(
                    "SCHEDULE_CONFLICT:同一医生排班时间重叠");
        }

        schedule.setStartTime(request.startTime());
        schedule.setEndTime(request.endTime());
        schedule.setMaxAppointments(request.maxAppointments());
        schedule.setUpdatedAt(LocalDateTime.now());

        schedule = scheduleRepository.save(schedule);

        Doctor doctor = doctorRepository.findById(schedule.getDoctorId()).orElse(null);
        Department department = departmentRepository.findById(schedule.getDepartmentId()).orElse(null);

        return toResponse(schedule,
                doctor != null ? doctor.getName() : null,
                department != null ? department.getName() : null);
    }

    /**
     * 取消排班
     *
     * 事务联动规则：
     * - 取消排班与取消其 BOOKED、CHECKED_IN 挂号必须在同一事务完成
     * - 因排班取消的挂号记录 cancellationSource=SCHEDULE
     * - 存在 IN_PROGRESS、WAITING_EXAM 或 COMPLETED 挂号时禁止取消排班
     */
    @Transactional
    public ScheduleResponse cancelSchedule(Long id, ScheduleCancelRequest request) {
        Schedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "SCHEDULE_NOT_FOUND:排班不存在"));

        // 已取消或已完成的排班不能再次取消
        if ("CANCELLED".equals(schedule.getStatus())) {
            throw new IllegalArgumentException(
                    "SCHEDULE_STATUS_CONFLICT:排班已取消");
        }
        if ("COMPLETED".equals(schedule.getStatus())) {
            throw new IllegalArgumentException(
                    "SCHEDULE_STATUS_CONFLICT:排班已结束");
        }

        // 检查是否有进行中或已完成的挂号
        List<Appointment> activeAppointments = appointmentRepository.findByScheduleId(id).stream()
                .filter(a -> "IN_PROGRESS".equals(a.getStatus())
                        || "WAITING_EXAM".equals(a.getStatus())
                        || "COMPLETED".equals(a.getStatus()))
                .collect(Collectors.toList());
        if (!activeAppointments.isEmpty()) {
            throw new IllegalArgumentException(
                    "SCHEDULE_CANCEL_CONFLICT:存在进行中或已完成的挂号，不能取消排班");
        }

        LocalDateTime now = LocalDateTime.now();

        // 取消排班
        schedule.setStatus("CANCELLED");
        schedule.setCancelledAt(now);
        schedule.setCancelReason(request.reason());
        schedule.setUpdatedAt(now);

        // 同事务取消所有 BOOKED 和 CHECKED_IN 挂号
        List<Appointment> appointmentsToCancel = appointmentRepository.findByScheduleId(id).stream()
                .filter(a -> "BOOKED".equals(a.getStatus()) || "CHECKED_IN".equals(a.getStatus()))
                .collect(Collectors.toList());

        for (Appointment appointment : appointmentsToCancel) {
            appointment.setStatus("CANCELLED");
            appointment.setCancellationSource("SCHEDULE");
            appointment.setCancellationReason("排班取消：" + request.reason());
            appointment.setCancelledAt(now);
            appointment.setUpdatedAt(now);
            appointmentRepository.save(appointment);
        }

        schedule = scheduleRepository.save(schedule);

        Doctor doctor = doctorRepository.findById(schedule.getDoctorId()).orElse(null);
        Department department = departmentRepository.findById(schedule.getDepartmentId()).orElse(null);

        return toResponse(schedule,
                doctor != null ? doctor.getName() : null,
                department != null ? department.getName() : null);
    }

    /**
     * 获取排班详情
     */
    @Transactional(readOnly = true)
    public ScheduleResponse getScheduleById(Long id) {
        Schedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "SCHEDULE_NOT_FOUND:排班不存在"));

        Doctor doctor = doctorRepository.findById(schedule.getDoctorId()).orElse(null);
        Department department = departmentRepository.findById(schedule.getDepartmentId()).orElse(null);

        return toResponse(schedule,
                doctor != null ? doctor.getName() : null,
                department != null ? department.getName() : null);
    }

    /**
     * 按医生查询排班
     */
    @Transactional(readOnly = true)
    public List<ScheduleResponse> getSchedulesByDoctor(Long doctorId) {
        Doctor doctor = doctorRepository.findById(doctorId).orElse(null);
        String doctorName = doctor != null ? doctor.getName() : null;

        return scheduleRepository.findByDoctorId(doctorId).stream()
                .map(schedule -> {
                    Department department = departmentRepository.findById(schedule.getDepartmentId()).orElse(null);
                    return toResponse(schedule, doctorName,
                            department != null ? department.getName() : null);
                })
                .collect(Collectors.toList());
    }

    /**
     * 按科室查询排班
     */
    @Transactional(readOnly = true)
    public List<ScheduleResponse> getSchedulesByDepartment(Long departmentId) {
        Department department = departmentRepository.findById(departmentId).orElse(null);
        String departmentName = department != null ? department.getName() : null;

        return scheduleRepository.findByDepartmentId(departmentId).stream()
                .map(schedule -> {
                    Doctor doctor = doctorRepository.findById(schedule.getDoctorId()).orElse(null);
                    return toResponse(schedule,
                            doctor != null ? doctor.getName() : null,
                            departmentName);
                })
                .collect(Collectors.toList());
    }

    /**
     * 按日期查询排班
     */
    @Transactional(readOnly = true)
    public List<ScheduleResponse> getSchedulesByDate(LocalDateTime date) {
        return scheduleRepository.findByScheduleDate(date.toLocalDate()).stream()
                .map(schedule -> {
                    Doctor doctor = doctorRepository.findById(schedule.getDoctorId()).orElse(null);
                    Department department = departmentRepository.findById(schedule.getDepartmentId()).orElse(null);
                    return toResponse(schedule,
                            doctor != null ? doctor.getName() : null,
                            department != null ? department.getName() : null);
                })
                .collect(Collectors.toList());
    }

    /**
     * 查询可预约排班（患者端）
     */
    @Transactional(readOnly = true)
    public List<ScheduleResponse> getAvailableSchedules(Long departmentId, LocalDateTime date) {
        List<Schedule> schedules;
        if (departmentId != null && date != null) {
            schedules = scheduleRepository.findByDepartmentIdAndScheduleDateAndStatusNot(
                    departmentId, date.toLocalDate(), "CANCELLED", null).getContent();
        } else if (departmentId != null) {
            schedules = scheduleRepository.findByDepartmentId(departmentId);
        } else if (date != null) {
            schedules = scheduleRepository.findByScheduleDate(date.toLocalDate());
        } else {
            schedules = scheduleRepository.findAll();
        }

        LocalDateTime now = LocalDateTime.now();
        return schedules.stream()
                .filter(s -> !"CANCELLED".equals(s.getStatus())
                        && !"COMPLETED".equals(s.getStatus())
                        && s.getEndTime().isAfter(now))
                .map(schedule -> {
                    Doctor doctor = doctorRepository.findById(schedule.getDoctorId()).orElse(null);
                    Department department = departmentRepository.findById(schedule.getDepartmentId()).orElse(null);
                    return toResponse(schedule,
                            doctor != null ? doctor.getName() : null,
                            department != null ? department.getName() : null);
                })
                .collect(Collectors.toList());
    }

    /**
     * 转换为响应 DTO
     */
    private ScheduleResponse toResponse(Schedule schedule, String doctorName, String departmentName) {
        int remainingCount = schedule.getMaxAppointments() - schedule.getBookedCount();
        if (remainingCount < 0) {
            remainingCount = 0;
        }

        // 动态计算状态：AVAILABLE 和 FULL 由号源计算
        String status = schedule.getStatus();
        if (!"CANCELLED".equals(status) && !"COMPLETED".equals(status)) {
            if (schedule.getBookedCount() >= schedule.getMaxAppointments()) {
                status = "FULL";
            } else {
                status = "AVAILABLE";
            }
            // 检查是否已过期
            if (schedule.getEndTime().isBefore(LocalDateTime.now())) {
                status = "COMPLETED";
            }
        }

        return new ScheduleResponse(
                schedule.getId(),
                schedule.getDoctorId(),
                doctorName,
                schedule.getDepartmentId(),
                departmentName,
                schedule.getScheduleDate(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getMaxAppointments(),
                schedule.getBookedCount(),
                remainingCount,
                status,
                schedule.getCancelledAt(),
                schedule.getCancelReason(),
                schedule.getCreatedAt(),
                schedule.getUpdatedAt()
        );
    }
}
