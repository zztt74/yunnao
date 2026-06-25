package com.neusoft.cloudbrain.encounter.service;

import com.neusoft.cloudbrain.appointment.entity.Appointment;
import com.neusoft.cloudbrain.appointment.repository.AppointmentRepository;
import com.neusoft.cloudbrain.auth.dto.AuthPrincipal;
import com.neusoft.cloudbrain.auth.security.SecurityUtils;
import com.neusoft.cloudbrain.department.entity.Department;
import com.neusoft.cloudbrain.department.repository.DepartmentRepository;
import com.neusoft.cloudbrain.doctor.entity.Doctor;
import com.neusoft.cloudbrain.doctor.repository.DoctorRepository;
import com.neusoft.cloudbrain.encounter.dto.EncounterCancelRequest;
import com.neusoft.cloudbrain.encounter.dto.EncounterDiagnosisRequest;
import com.neusoft.cloudbrain.encounter.dto.EncounterDiagnosisResponse;
import com.neusoft.cloudbrain.encounter.dto.EncounterResponse;
import com.neusoft.cloudbrain.encounter.dto.EncounterStartRequest;
import com.neusoft.cloudbrain.encounter.entity.Encounter;
import com.neusoft.cloudbrain.encounter.entity.EncounterDiagnosis;
import com.neusoft.cloudbrain.encounter.exception.EncounterErrorCode;
import com.neusoft.cloudbrain.encounter.repository.EncounterDiagnosisRepository;
import com.neusoft.cloudbrain.encounter.repository.EncounterRepository;
import com.neusoft.cloudbrain.examination.service.ExaminationService;
import com.neusoft.cloudbrain.medicalrecord.service.MedicalRecordService;
import com.neusoft.cloudbrain.prescription.service.PrescriptionService;
import com.neusoft.cloudbrain.patient.entity.Patient;
import com.neusoft.cloudbrain.patient.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 就诊 Service
 *
 * 核心职责（来自 11_功能需求.md 第8节 和 12_业务流程与状态机.md 第6节）：
 *
 * 1. 就诊状态机：
 *    CREATED → IN_PROGRESS       开始接诊
 *    IN_PROGRESS → WAITING_EXAM  等待检查结果
 *    WAITING_EXAM → IN_PROGRESS  检查返回继续诊疗
 *    IN_PROGRESS → COMPLETED     就诊完成
 *    CREATED → CANCELLED         取消就诊
 *
 * 2. 状态同步：
 *    Appointment 与 Encounter 的 IN_PROGRESS、WAITING_EXAM、COMPLETED
 *    必须在同一业务用例中同步。
 *
 * 3. 完成就诊前置条件检查：
 *    - 病历确认状态
 *    - 医生最终诊断
 *    - 检查检验完成
 *    - 处方状态
 *
 * 4. 诊断隔离原则：
 *    - AI 只能产生 AI_SUGGESTION，不能创建 FINAL + DOCTOR
 *    - 医生最终诊断必须为 type=FINAL、source=DOCTOR
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EncounterService {

    private final EncounterRepository encounterRepository;
    private final EncounterDiagnosisRepository encounterDiagnosisRepository;
    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final DepartmentRepository departmentRepository;
    private final MedicalRecordService medicalRecordService;
    private final ExaminationService examinationService;
    private final PrescriptionService prescriptionService;

    // ============================================================
    // 状态机：开始接诊 CREATED → IN_PROGRESS
    // ============================================================

    /**
     * 开始接诊
     *
     * 事务保证：
     * 1. 校验挂号存在且状态为 BOOKED 或 CHECKED_IN
     * 2. 校验医生权限（只能接诊本人排班中的患者）
     * 3. 创建 Encounter（一个挂号最多一个就诊）
     * 4. 同步更新 Appointment 状态为 IN_PROGRESS
     */
    @Transactional
    public EncounterResponse startEncounter(EncounterStartRequest request) {
        LocalDateTime now = LocalDateTime.now();

        // 1. 校验挂号存在
        Appointment appointment = appointmentRepository.findById(request.appointmentId())
                .orElseThrow(EncounterErrorCode.APPOINTMENT_NOT_FOUND::toException);

        // 2. 校验挂号状态（BOOKED 或 CHECKED_IN 可开始接诊）
        if (!"BOOKED".equals(appointment.getStatus())
                && !"CHECKED_IN".equals(appointment.getStatus())) {
            throw EncounterErrorCode.ENCOUNTER_STATUS_CONFLICT.toException();
        }

        // 3. 校验医生权限
        Doctor doctor = validateDoctorOwnership(appointment.getDoctorId());

        // 4. 检查是否已存在就诊（一个挂号最多一个就诊）
        if (encounterRepository.findByAppointmentId(request.appointmentId()).isPresent()) {
            throw EncounterErrorCode.ENCOUNTER_DUPLICATE.toException();
        }

        // 5. 创建 Encounter
        Encounter encounter = Encounter.builder()
                .appointmentId(appointment.getId())
                .patientId(appointment.getPatientId())
                .doctorId(appointment.getDoctorId())
                .departmentId(doctor.getDepartmentId())
                .status("IN_PROGRESS")
                .startedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
        encounter = encounterRepository.save(encounter);

        // 6. 同步更新 Appointment 状态为 IN_PROGRESS
        int updated = appointmentRepository.updateStatusIfCurrent(
                appointment.getId(), appointment.getStatus(), "IN_PROGRESS", now);
        if (updated == 0) {
            throw EncounterErrorCode.ENCOUNTER_STATUS_CONFLICT.toException();
        }

        return toResponse(encounter);
    }

    // ============================================================
    // 状态机：等待检查 IN_PROGRESS → WAITING_EXAM
    // ============================================================

    /**
     * 进入等待检查状态
     *
     * 事务保证：同步更新 Appointment 状态为 WAITING_EXAM
     */
    @Transactional
    public EncounterResponse waitForExam(Long encounterId) {
        Encounter encounter = findAndValidateEncounter(encounterId);
        validateEncounterStatus(encounter, "IN_PROGRESS");

        LocalDateTime now = LocalDateTime.now();
        encounter.setStatus("WAITING_EXAM");
        encounter.setWaitingExamAt(now);
        encounter.setUpdatedAt(now);
        encounter = encounterRepository.save(encounter);

        // 同步更新 Appointment 状态
        syncAppointmentStatus(encounter.getAppointmentId(), "IN_PROGRESS", "WAITING_EXAM", now);

        return toResponse(encounter);
    }

    // ============================================================
    // 状态机：继续诊疗 WAITING_EXAM → IN_PROGRESS
    // ============================================================

    /**
     * 检查返回，继续诊疗
     */
    @Transactional
    public EncounterResponse resumeEncounter(Long encounterId) {
        Encounter encounter = findAndValidateEncounter(encounterId);
        validateEncounterStatus(encounter, "WAITING_EXAM");

        LocalDateTime now = LocalDateTime.now();
        encounter.setStatus("IN_PROGRESS");
        encounter.setUpdatedAt(now);
        encounter = encounterRepository.save(encounter);

        // 同步更新 Appointment 状态
        syncAppointmentStatus(encounter.getAppointmentId(), "WAITING_EXAM", "IN_PROGRESS", now);

        return toResponse(encounter);
    }

    // ============================================================
    // 状态机：完成就诊 IN_PROGRESS → COMPLETED
    // ============================================================

    /**
     * 完成就诊
     *
     * 前置条件检查（来自 12_业务流程与状态机.md 第6节）：
     * 1. 检查病历确认状态
     * 2. 检查医生最终诊断
     * 3. 检查所有检查检验已完成
     * 4. 检查处方状态（如有）
     * 5. 更新状态
     * 6. 同步更新挂号状态
     */
    @Transactional
    public EncounterResponse completeEncounter(Long encounterId) {
        Encounter encounter = findAndValidateEncounter(encounterId);
        validateEncounterStatus(encounter, "IN_PROGRESS");

        // 1. 检查病历确认状态
        validateMedicalRecordConfirmed(encounter);

        // 2. 检查医生最终诊断
        validateFinalDiagnosisExists(encounter);

        // 3. 检查所有检查检验已完成
        validateAllExamsReviewed(encounter);

        // 4. 检查处方状态（如有）
        validatePrescriptionStatus(encounter);

        // 5. 更新就诊状态
        LocalDateTime now = LocalDateTime.now();
        encounter.setStatus("COMPLETED");
        encounter.setCompletedAt(now);
        encounter.setUpdatedAt(now);
        encounter = encounterRepository.save(encounter);

        // 6. 同步更新挂号状态
        syncAppointmentStatus(encounter.getAppointmentId(), "IN_PROGRESS", "COMPLETED", now);

        log.info("就诊完成: encounterId={}, doctorId={}, patientId={}",
                encounter.getId(), encounter.getDoctorId(), encounter.getPatientId());

        return toResponse(encounter);
    }

    // ============================================================
    // 状态机：取消就诊 CREATED → CANCELLED
    // ============================================================

    /**
     * 取消就诊
     *
     * 仅 CREATED 状态可取消；IN_PROGRESS、WAITING_EXAM、COMPLETED 不允许取消。
     */
    @Transactional
    public EncounterResponse cancelEncounter(Long encounterId, EncounterCancelRequest request) {
        Encounter encounter = findAndValidateEncounter(encounterId);
        validateEncounterStatus(encounter, "CREATED");

        LocalDateTime now = LocalDateTime.now();
        encounter.setStatus("CANCELLED");
        encounter.setCancelledAt(now);
        encounter.setCancelReason(request.reason());
        encounter.setUpdatedAt(now);
        encounter = encounterRepository.save(encounter);

        return toResponse(encounter);
    }

    // ============================================================
    // 诊断管理（诊断隔离原则）
    // ============================================================

    /**
     * 添加 AI 候选诊断
     *
     * 诊断隔离原则：
     * - AI 只能创建 type=PRELIMINARY, source=AI_SUGGESTION
     * - AI 不能创建 FINAL + DOCTOR 记录
     */
    @Transactional
    public EncounterDiagnosisResponse addAIDiagnosis(Long encounterId, EncounterDiagnosisRequest request) {
        Encounter encounter = findAndValidateEncounter(encounterId);

        // 诊断隔离校验：AI 只能创建 AI_SUGGESTION
        if ("DOCTOR".equals(request.source())) {
            throw EncounterErrorCode.ENCOUNTER_DIAGNOSIS_ISOLATION_VIOLATION.toException();
        }
        if ("FINAL".equals(request.type()) && "AI_SUGGESTION".equals(request.source())) {
            throw EncounterErrorCode.ENCOUNTER_DIAGNOSIS_ISOLATION_VIOLATION.toException();
        }

        LocalDateTime now = LocalDateTime.now();
        EncounterDiagnosis diagnosis = EncounterDiagnosis.builder()
                .encounterId(encounterId)
                .diagnosisCode(request.diagnosisCode())
                .diagnosisName(request.diagnosisName())
                .type(request.type())
                .source(request.source())
                // AI 候选诊断不设置 confirmedAt（仅为建议，非正式确认）
                .notes(request.notes())
                .createdAt(now)
                .updatedAt(now)
                .build();
        diagnosis = encounterDiagnosisRepository.save(diagnosis);

        return toDiagnosisResponse(diagnosis);
    }

    /**
     * 添加医生最终诊断
     *
     * 诊断隔离原则：
     * - 医生诊断必须为 type=FINAL, source=DOCTOR
     * - 医生确认的最终诊断不得被 AI 调用覆盖
     */
    @Transactional
    public EncounterDiagnosisResponse addDoctorDiagnosis(Long encounterId, EncounterDiagnosisRequest request) {
        Encounter encounter = findAndValidateEncounter(encounterId);
        Doctor doctor = validateDoctorOwnership(encounter.getDoctorId());

        // 医生诊断必须为 FINAL + DOCTOR
        if (!"FINAL".equals(request.type()) || !"DOCTOR".equals(request.source())) {
            throw EncounterErrorCode.ENCOUNTER_DIAGNOSIS_ISOLATION_VIOLATION.toException();
        }

        LocalDateTime now = LocalDateTime.now();
        EncounterDiagnosis diagnosis = EncounterDiagnosis.builder()
                .encounterId(encounterId)
                .diagnosisCode(request.diagnosisCode())
                .diagnosisName(request.diagnosisName())
                .type("FINAL")
                .source("DOCTOR")
                .doctorId(doctor.getId())
                .confirmedAt(now)
                .notes(request.notes())
                .createdAt(now)
                .updatedAt(now)
                .build();
        diagnosis = encounterDiagnosisRepository.save(diagnosis);

        return toDiagnosisResponse(diagnosis);
    }

    /**
     * 查询就诊的所有诊断
     */
    @Transactional(readOnly = true)
    public List<EncounterDiagnosisResponse> getEncounterDiagnoses(Long encounterId) {
        findAndValidateEncounter(encounterId);
        return encounterDiagnosisRepository.findByEncounterId(encounterId).stream()
                .map(this::toDiagnosisResponse)
                .collect(Collectors.toList());
    }

    // ============================================================
    // 查询方法
    // ============================================================

    /**
     * 获取就诊详情
     */
    @Transactional(readOnly = true)
    public EncounterResponse getEncounterById(Long id) {
        Encounter encounter = encounterRepository.findById(id)
                .orElseThrow(EncounterErrorCode.ENCOUNTER_NOT_FOUND::toException);
        checkEncounterAccess(encounter);
        return toResponse(encounter);
    }

    /**
     * 按挂号 ID 查询就诊
     */
    @Transactional(readOnly = true)
    public EncounterResponse getEncounterByAppointmentId(Long appointmentId) {
        Encounter encounter = encounterRepository.findByAppointmentId(appointmentId)
                .orElseThrow(EncounterErrorCode.ENCOUNTER_NOT_FOUND::toException);
        checkEncounterAccess(encounter);
        return toResponse(encounter);
    }

    /**
     * 查询医生就诊列表（分页）
     */
    @Transactional(readOnly = true)
    public Page<EncounterResponse> getEncountersByDoctor(Long doctorId, Pageable pageable) {
        return encounterRepository.findByDoctorId(doctorId, pageable)
                .map(this::toResponse);
    }

    /**
     * 查询医生某状态的就诊列表
     */
    @Transactional(readOnly = true)
    public List<EncounterResponse> getEncountersByDoctorAndStatus(Long doctorId, String status) {
        return encounterRepository.findByDoctorIdAndStatus(doctorId, status).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 查询患者就诊列表（分页）
     */
    @Transactional(readOnly = true)
    public Page<EncounterResponse> getEncountersByPatient(Long patientId, Pageable pageable) {
        checkPatientOwnership(patientId);
        return encounterRepository.findByPatientId(patientId, pageable)
                .map(this::toResponse);
    }

    // ============================================================
    // 完成就诊前置条件校验
    // ============================================================

    /**
     * 1. 检查病历确认状态
     *
     * 完成就诊必须存在已确认正式病历。
     */
    private void validateMedicalRecordConfirmed(Encounter encounter) {
        if (!medicalRecordService.hasConfirmedRecord(encounter.getId())) {
            throw EncounterErrorCode.ENCOUNTER_MEDICAL_RECORD_NOT_CONFIRMED.toException();
        }
    }

    /**
     * 2. 检查医生最终诊断
     *
     * 完成就诊必须存在至少一条 type=FINAL, source=DOCTOR 的诊断。
     */
    private void validateFinalDiagnosisExists(Encounter encounter) {
        boolean hasFinalDiagnosis = encounterDiagnosisRepository
                .existsByEncounterIdAndTypeAndSource(encounter.getId(), "FINAL", "DOCTOR");
        if (!hasFinalDiagnosis) {
            throw EncounterErrorCode.ENCOUNTER_FINAL_DIAGNOSIS_REQUIRED.toException();
        }
    }

    /**
     * 3. 检查所有检查检验已完成
     *
     * 任一检查检验处于 ORDERED、IN_PROGRESS 或 RESULT_ENTERED 时不得完成就诊。
     */
    private void validateAllExamsReviewed(Encounter encounter) {
        if (examinationService.hasPendingExaminations(encounter.getId())) {
            throw EncounterErrorCode.ENCOUNTER_EXAMINATION_PENDING.toException();
        }
    }

    /**
     * 4. 检查处方状态（如有）
     *
     * 处方可不存在；存在处方时，其业务状态必须为 CONFIRMED 或 VOIDED。
     * DRAFT 状态处方阻塞就诊完成。
     */
    private void validatePrescriptionStatus(Encounter encounter) {
        if (prescriptionService.hasPendingPrescriptions(encounter.getId())) {
            throw EncounterErrorCode.ENCOUNTER_PRESCRIPTION_PENDING.toException();
        }
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    /**
     * 查找就诊并校验医生权限
     */
    private Encounter findAndValidateEncounter(Long encounterId) {
        Encounter encounter = encounterRepository.findById(encounterId)
                .orElseThrow(EncounterErrorCode.ENCOUNTER_NOT_FOUND::toException);
        validateDoctorOwnership(encounter.getDoctorId());
        return encounter;
    }

    /**
     * 校验就诊状态
     */
    private void validateEncounterStatus(Encounter encounter, String expectedStatus) {
        if (!expectedStatus.equals(encounter.getStatus())) {
            throw EncounterErrorCode.ENCOUNTER_STATUS_CONFLICT.toException();
        }
    }

    /**
     * 校验当前登录医生权限
     *
     * 医生只能处理本人 Encounter。
     */
    private Doctor validateDoctorOwnership(Long encounterDoctorId) {
        if (!SecurityUtils.isAuthenticated()) {
            return doctorRepository.findById(encounterDoctorId)
                    .orElseThrow(EncounterErrorCode.DOCTOR_NOT_FOUND::toException);
        }
        AuthPrincipal currentUser = SecurityUtils.getCurrentUser();
        if (currentUser.roles() != null && currentUser.roles().contains("DOCTOR")) {
            Doctor currentDoctor = doctorRepository.findByUserId(currentUser.userId())
                    .orElseThrow(EncounterErrorCode.DOCTOR_NOT_FOUND::toException);
            if (!currentDoctor.getId().equals(encounterDoctorId)) {
                throw EncounterErrorCode.ENCOUNTER_PERMISSION_DENIED.toException();
            }
            return currentDoctor;
        }
        // 管理员放行
        return doctorRepository.findById(encounterDoctorId)
                .orElseThrow(EncounterErrorCode.DOCTOR_NOT_FOUND::toException);
    }

    /**
     * 校验当前登录患者只能访问自己的数据
     */
    private void checkPatientOwnership(Long patientId) {
        if (!SecurityUtils.isAuthenticated()) {
            return;
        }
        AuthPrincipal currentUser = SecurityUtils.getCurrentUser();
        if (currentUser.roles() != null && currentUser.roles().contains("PATIENT")) {
            Patient currentPatient = patientRepository.findByUserId(currentUser.userId())
                    .orElseThrow(EncounterErrorCode.PATIENT_NOT_FOUND::toException);
            if (!currentPatient.getId().equals(patientId)) {
                throw EncounterErrorCode.ENCOUNTER_PERMISSION_DENIED.toException();
            }
        }
    }

    /**
     * 校验就诊访问权限
     */
    private void checkEncounterAccess(Encounter encounter) {
        if (!SecurityUtils.isAuthenticated()) {
            return;
        }
        AuthPrincipal currentUser = SecurityUtils.getCurrentUser();
        if (currentUser.roles() == null) {
            return;
        }
        if (currentUser.roles().contains("ADMIN")) {
            return;
        }
        if (currentUser.roles().contains("DOCTOR")) {
            Doctor doctor = doctorRepository.findByUserId(currentUser.userId())
                    .orElseThrow(EncounterErrorCode.DOCTOR_NOT_FOUND::toException);
            if (!doctor.getId().equals(encounter.getDoctorId())) {
                throw EncounterErrorCode.ENCOUNTER_PERMISSION_DENIED.toException();
            }
        }
        if (currentUser.roles().contains("PATIENT")) {
            Patient patient = patientRepository.findByUserId(currentUser.userId())
                    .orElseThrow(EncounterErrorCode.PATIENT_NOT_FOUND::toException);
            if (!patient.getId().equals(encounter.getPatientId())) {
                throw EncounterErrorCode.ENCOUNTER_PERMISSION_DENIED.toException();
            }
        }
    }

    /**
     * 同步更新挂号状态（条件更新）
     */
    private void syncAppointmentStatus(Long appointmentId, String expectedStatus,
                                        String newStatus, LocalDateTime now) {
        int updated = appointmentRepository.updateStatusIfCurrent(
                appointmentId, expectedStatus, newStatus, now);
        if (updated == 0) {
            throw EncounterErrorCode.ENCOUNTER_STATUS_CONFLICT.toException();
        }
    }

    /**
     * 转换为就诊响应 DTO
     */
    private EncounterResponse toResponse(Encounter encounter) {
        Patient patient = patientRepository.findById(encounter.getPatientId()).orElse(null);
        Doctor doctor = doctorRepository.findById(encounter.getDoctorId()).orElse(null);
        Department department = departmentRepository.findById(encounter.getDepartmentId()).orElse(null);

        return new EncounterResponse(
                encounter.getId(),
                encounter.getAppointmentId(),
                encounter.getPatientId(),
                patient != null ? patient.getName() : null,
                encounter.getDoctorId(),
                doctor != null ? doctor.getName() : null,
                encounter.getDepartmentId(),
                department != null ? department.getName() : null,
                encounter.getStatus(),
                encounter.getStartedAt(),
                encounter.getWaitingExamAt(),
                encounter.getCompletedAt(),
                encounter.getCancelledAt(),
                encounter.getCancelReason(),
                encounter.getCreatedAt(),
                encounter.getUpdatedAt());
    }

    /**
     * 转换为诊断响应 DTO
     */
    private EncounterDiagnosisResponse toDiagnosisResponse(EncounterDiagnosis diagnosis) {
        return new EncounterDiagnosisResponse(
                diagnosis.getId(),
                diagnosis.getEncounterId(),
                diagnosis.getDiagnosisCode(),
                diagnosis.getDiagnosisName(),
                diagnosis.getType(),
                diagnosis.getSource(),
                diagnosis.getAiInvocationId(),
                diagnosis.getDoctorId(),
                diagnosis.getConfirmedAt(),
                diagnosis.getNotes(),
                diagnosis.getCreatedAt(),
                diagnosis.getUpdatedAt());
    }
}
