package com.neusoft.cloudbrain.medicalrecord.service;

import com.neusoft.cloudbrain.ai.api.AIMedicalRecordService;
import com.neusoft.cloudbrain.ai.dto.MedicalRecordAIRequest;
import com.neusoft.cloudbrain.ai.dto.MedicalRecordAIResult;
import com.neusoft.cloudbrain.auth.dto.AuthPrincipal;
import com.neusoft.cloudbrain.auth.security.SecurityUtils;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.neusoft.cloudbrain.doctor.entity.Doctor;
import com.neusoft.cloudbrain.doctor.repository.DoctorRepository;
import com.neusoft.cloudbrain.encounter.entity.Encounter;
import com.neusoft.cloudbrain.encounter.repository.EncounterRepository;
import com.neusoft.cloudbrain.medicalrecord.dto.MedicalRecordCreateRequest;
import com.neusoft.cloudbrain.medicalrecord.dto.MedicalRecordGenerateRequest;
import com.neusoft.cloudbrain.medicalrecord.dto.MedicalRecordResponse;
import com.neusoft.cloudbrain.medicalrecord.dto.MedicalRecordUpdateRequest;
import com.neusoft.cloudbrain.medicalrecord.entity.MedicalRecord;
import com.neusoft.cloudbrain.medicalrecord.exception.MedicalRecordErrorCode;
import com.neusoft.cloudbrain.medicalrecord.repository.MedicalRecordRepository;
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
 * 病历 Service
 *
 * 核心职责（来自 11_功能需求.md 第11节 和 12_业务流程与状态机.md 第8节）：
 *
 * 1. 状态机：
 *    DRAFT → CONFIRMED            医生手工草稿确认
 *    AI_GENERATED → CONFIRMED     AI 草稿医生确认
 *    DRAFT ↔ AI_GENERATED         来源切换（非正式确认）
 *    AMENDED 为扩展版本保留状态，基础版本不得进入
 *
 * 2. AI 病历生成：
 *    - 收集就诊数据
 *    - 调用 AI 生成
 *    - 保存为 AI 草稿（AI_GENERATED）
 *    - AI 失败抛出异常，允许医生手工填写
 *
 * 3. 关键约束：
 *    - AI 只能生成 DRAFT 或 AI_GENERATED，CONFIRMED 必须由医生完成
 *    - AI 原始草稿永久保留，不可被覆盖
 *    - CONFIRMED 后基础版本不允许修改（扩展版本支持 AMENDED）
 *    - 基础版本每个 Encounter 只能有一条当前有效的 CONFIRMED 记录
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalRecordService {

    private final MedicalRecordRepository medicalRecordRepository;
    private final EncounterRepository encounterRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final AIMedicalRecordService aiMedicalRecordService;

    // ============================================================
    // 创建医生手工草稿
    // ============================================================

    /**
     * 创建医生手工草稿
     *
     * 医生手工创建的草稿 source=DOCTOR, status=DRAFT
     */
    @Transactional
    public MedicalRecordResponse createDraft(MedicalRecordCreateRequest request) {
        Encounter encounter = encounterRepository.findById(request.encounterId())
                .orElseThrow(MedicalRecordErrorCode.ENCOUNTER_NOT_FOUND::toException);
        Doctor doctor = validateDoctorOwnership(encounter.getDoctorId());

        LocalDateTime now = LocalDateTime.now();
        MedicalRecord record = MedicalRecord.builder()
                .encounterId(request.encounterId())
                .patientId(encounter.getPatientId())
                .doctorId(doctor.getId())
                .content(request.content())
                .source("DOCTOR")
                .status("DRAFT")
                .createdBy(doctor.getUserId())
                .createdAt(now)
                .updatedAt(now)
                .build();
        record = medicalRecordRepository.save(record);

        log.info("创建医生手工草稿: recordId={}, encounterId={}", record.getId(), record.getEncounterId());

        return toResponse(record);
    }

    // ============================================================
    // AI 病历生成
    // ============================================================

    /**
     * AI 生成病历草稿
     *
     * 业务编排（来自任务要求）：
     * 1. 收集就诊数据
     * 2. 调用 AI 生成
     * 3. 保存为 AI 草稿（AI_GENERATED）
     * 4. AI 失败抛出异常，允许医生手工填写
     *
     * 关键约束：
     * - AI 只能生成 AI_GENERATED，不能生成 CONFIRMED
     * - AI 原始草稿永久保留，不可被覆盖
     */
    @Transactional
    public MedicalRecordResponse generateByAI(MedicalRecordGenerateRequest request) {
        // 1. 收集就诊数据
        Encounter encounter = encounterRepository.findById(request.encounterId())
                .orElseThrow(MedicalRecordErrorCode.ENCOUNTER_NOT_FOUND::toException);
        Doctor doctor = validateDoctorOwnership(encounter.getDoctorId());

        // 2. 调用 AI 生成
        MedicalRecordAIRequest aiRequest = new MedicalRecordAIRequest(
                request.chiefComplaint(),
                request.presentIllness(),
                request.pastHistory(),
                request.physicalExamination(),
                request.preliminaryDiagnoses(),
                request.treatmentSuggestion());

        MedicalRecordAIResult aiResult;
        try {
            aiResult = aiMedicalRecordService.generate(aiRequest);
        } catch (BusinessException e) {
            log.warn("AI 病历生成失败: encounterId={}, error={}", encounter.getId(), e.getMessage());
            throw new BusinessException(
                    "AI_MEDICAL_RECORD_FAILED",
                    "AI 生成失败，请手动填写",
                    e.getHttpStatus());
        }

        // 3. 构造病历内容（结构化文本）
        String content = buildMedicalRecordContent(aiResult);

        // 4. 保存为 AI 草稿（AI_GENERATED）
        LocalDateTime now = LocalDateTime.now();
        MedicalRecord record = MedicalRecord.builder()
                .encounterId(request.encounterId())
                .patientId(encounter.getPatientId())
                .doctorId(doctor.getId())
                .content(content)
                .source("AI")
                .status("AI_GENERATED")
                .createdBy(doctor.getUserId())
                .createdAt(now)
                .updatedAt(now)
                .build();
        record = medicalRecordRepository.save(record);

        log.info("AI 生成病历草稿: recordId={}, encounterId={}", record.getId(), record.getEncounterId());

        return toResponse(record);
    }

    // ============================================================
    // 更新病历（仅 DRAFT 和 AI_GENERATED 状态可更新）
    // ============================================================

    /**
     * 更新病历内容
     *
     * 仅 DRAFT 和 AI_GENERATED 状态可更新
     * CONFIRMED 后基础版本不允许修改
     */
    @Transactional
    public MedicalRecordResponse updateRecord(Long recordId, MedicalRecordUpdateRequest request) {
        MedicalRecord record = findAndValidateRecord(recordId);

        // CONFIRMED 后基础版本不允许修改
        if ("CONFIRMED".equals(record.getStatus())) {
            throw MedicalRecordErrorCode.MEDICAL_RECORD_ALREADY_CONFIRMED.toException();
        }

        // 只有 DRAFT 和 AI_GENERATED 状态可更新
        if (!"DRAFT".equals(record.getStatus()) && !"AI_GENERATED".equals(record.getStatus())) {
            throw MedicalRecordErrorCode.MEDICAL_RECORD_STATUS_CONFLICT.toException();
        }

        LocalDateTime now = LocalDateTime.now();
        record.setContent(request.content());
        record.setUpdatedAt(now);
        record = medicalRecordRepository.save(record);

        log.info("更新病历: recordId={}, status={}", record.getId(), record.getStatus());

        return toResponse(record);
    }

    // ============================================================
    // 状态机：确认病历 DRAFT/AI_GENERATED → CONFIRMED
    // ============================================================

    /**
     * 医生确认病历
     *
     * DRAFT → CONFIRMED            医生手工草稿确认
     * AI_GENERATED → CONFIRMED     AI 草稿医生确认
     *
     * 关键约束：
     * - CONFIRMED 必须由医生完成
     * - 基础版本每个 Encounter 只能有一条当前有效的 CONFIRMED 记录
     */
    @Transactional
    public MedicalRecordResponse confirmRecord(Long recordId) {
        MedicalRecord record = findAndValidateRecord(recordId);

        // 只有 DRAFT 和 AI_GENERATED 状态可确认
        if (!"DRAFT".equals(record.getStatus()) && !"AI_GENERATED".equals(record.getStatus())) {
            throw MedicalRecordErrorCode.MEDICAL_RECORD_STATUS_CONFLICT.toException();
        }

        // 检查是否已存在已确认病历（基础版本每个 Encounter 只能有一条 CONFIRMED 记录）
        if (medicalRecordRepository.existsByEncounterIdAndStatus(record.getEncounterId(), "CONFIRMED")) {
            throw MedicalRecordErrorCode.MEDICAL_RECORD_CONFIRMED_EXISTS.toException();
        }

        Doctor doctor = validateDoctorOwnership(record.getDoctorId());

        LocalDateTime now = LocalDateTime.now();
        int updated = medicalRecordRepository.updateStatusIfCurrent(
                recordId,
                record.getStatus(),
                "CONFIRMED",
                doctor.getUserId(),
                now,
                now);
        if (updated == 0) {
            throw MedicalRecordErrorCode.MEDICAL_RECORD_STATUS_CONFLICT.toException();
        }

        record.setStatus("CONFIRMED");
        record.setConfirmedBy(doctor.getUserId());
        record.setConfirmedAt(now);
        record.setUpdatedAt(now);

        log.info("确认病历: recordId={}, encounterId={}, doctorId={}",
                record.getId(), record.getEncounterId(), doctor.getId());

        return toResponse(record);
    }

    // ============================================================
    // 查询方法
    // ============================================================

    /**
     * 获取病历详情
     */
    @Transactional(readOnly = true)
    public MedicalRecordResponse getRecordById(Long id) {
        MedicalRecord record = medicalRecordRepository.findById(id)
                .orElseThrow(MedicalRecordErrorCode.MEDICAL_RECORD_NOT_FOUND::toException);
        checkRecordAccess(record);
        return toResponse(record);
    }

    /**
     * 按就诊 ID 查询病历列表
     */
    @Transactional(readOnly = true)
    public List<MedicalRecordResponse> getRecordsByEncounter(Long encounterId) {
        return medicalRecordRepository.findByEncounterId(encounterId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 按患者 ID 查询病历列表（分页）
     */
    @Transactional(readOnly = true)
    public Page<MedicalRecordResponse> getRecordsByPatient(Long patientId, Pageable pageable) {
        checkPatientOwnership(patientId);
        return medicalRecordRepository.findByPatientId(patientId, pageable)
                .map(this::toResponse);
    }

    /**
     * 查询就诊的已确认病历
     */
    @Transactional(readOnly = true)
    public MedicalRecordResponse getConfirmedRecordByEncounter(Long encounterId) {
        MedicalRecord record = medicalRecordRepository
                .findOneByEncounterIdAndStatus(encounterId, "CONFIRMED")
                .orElseThrow(MedicalRecordErrorCode.MEDICAL_RECORD_NOT_CONFIRMED::toException);
        checkRecordAccess(record);
        return toResponse(record);
    }

    // ============================================================
    // 完成就诊前置条件校验（供 EncounterService 调用）
    // ============================================================

    /**
     * 检查就诊是否存在已确认病历
     *
     * 完成就诊必须存在已确认正式病历
     */
    @Transactional(readOnly = true)
    public boolean hasConfirmedRecord(Long encounterId) {
        return medicalRecordRepository.existsByEncounterIdAndStatus(encounterId, "CONFIRMED");
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private MedicalRecord findAndValidateRecord(Long recordId) {
        MedicalRecord record = medicalRecordRepository.findById(recordId)
                .orElseThrow(MedicalRecordErrorCode.MEDICAL_RECORD_NOT_FOUND::toException);
        validateDoctorOwnership(record.getDoctorId());
        return record;
    }

    /**
     * 校验当前登录医生权限
     *
     * 医生只能处理本人接诊就诊的病历
     */
    private Doctor validateDoctorOwnership(Long recordDoctorId) {
        if (!SecurityUtils.isAuthenticated()) {
            return doctorRepository.findById(recordDoctorId)
                    .orElseThrow(MedicalRecordErrorCode.DOCTOR_NOT_FOUND::toException);
        }
        AuthPrincipal currentUser = SecurityUtils.getCurrentUser();
        if (currentUser.roles() != null && currentUser.roles().contains("DOCTOR")) {
            Doctor currentDoctor = doctorRepository.findByUserId(currentUser.userId())
                    .orElseThrow(MedicalRecordErrorCode.DOCTOR_NOT_FOUND::toException);
            if (!currentDoctor.getId().equals(recordDoctorId)) {
                throw MedicalRecordErrorCode.MEDICAL_RECORD_PERMISSION_DENIED.toException();
            }
            return currentDoctor;
        }
        // 管理员放行
        return doctorRepository.findById(recordDoctorId)
                .orElseThrow(MedicalRecordErrorCode.DOCTOR_NOT_FOUND::toException);
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
                    .orElseThrow(MedicalRecordErrorCode.PATIENT_NOT_FOUND::toException);
            if (!currentPatient.getId().equals(patientId)) {
                throw MedicalRecordErrorCode.MEDICAL_RECORD_PERMISSION_DENIED.toException();
            }
        }
    }

    /**
     * 检查病历访问权限
     */
    private void checkRecordAccess(MedicalRecord record) {
        if (!SecurityUtils.isAuthenticated()) {
            return;
        }
        AuthPrincipal currentUser = SecurityUtils.getCurrentUser();
        if (currentUser.roles() != null && currentUser.roles().contains("PATIENT")) {
            Patient currentPatient = patientRepository.findByUserId(currentUser.userId())
                    .orElseThrow(MedicalRecordErrorCode.PATIENT_NOT_FOUND::toException);
            if (!currentPatient.getId().equals(record.getPatientId())) {
                throw MedicalRecordErrorCode.MEDICAL_RECORD_PERMISSION_DENIED.toException();
            }
        }
    }

    /**
     * 构造病历内容（结构化文本）
     */
    private String buildMedicalRecordContent(MedicalRecordAIResult aiResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("【主诉】").append(aiResult.chiefComplaint() == null ? "" : aiResult.chiefComplaint()).append("\n");
        sb.append("【现病史】").append(aiResult.presentIllness() == null ? "" : aiResult.presentIllness()).append("\n");
        sb.append("【既往史】").append(aiResult.pastHistory() == null ? "" : aiResult.pastHistory()).append("\n");
        sb.append("【体格检查】").append(aiResult.physicalExamination() == null ? "" : aiResult.physicalExamination()).append("\n");
        sb.append("【初步诊断】").append(aiResult.preliminaryDiagnosis() == null ? "" : aiResult.preliminaryDiagnosis()).append("\n");
        sb.append("【治疗建议】").append(aiResult.treatmentSuggestion() == null ? "" : aiResult.treatmentSuggestion()).append("\n");
        if (aiResult.disclaimer() != null) {
            sb.append("【说明】").append(aiResult.disclaimer());
        }
        return sb.toString();
    }

    private MedicalRecordResponse toResponse(MedicalRecord record) {
        return new MedicalRecordResponse(
                record.getId(),
                record.getEncounterId(),
                record.getPatientId(),
                record.getDoctorId(),
                record.getContent(),
                record.getSource(),
                record.getStatus(),
                record.getCreatedBy(),
                record.getConfirmedBy(),
                record.getConfirmedAt(),
                record.getCreatedAt(),
                record.getUpdatedAt());
    }
}
