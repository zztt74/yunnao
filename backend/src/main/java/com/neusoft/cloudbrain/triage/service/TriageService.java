package com.neusoft.cloudbrain.triage.service;

import com.neusoft.cloudbrain.ai.api.AITriageService;
import com.neusoft.cloudbrain.ai.dto.TriageAIRequest;
import com.neusoft.cloudbrain.ai.dto.TriageAIResult;
import com.neusoft.cloudbrain.auth.dto.AuthPrincipal;
import com.neusoft.cloudbrain.auth.security.SecurityUtils;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.neusoft.cloudbrain.department.entity.Department;
import com.neusoft.cloudbrain.department.repository.DepartmentRepository;
import com.neusoft.cloudbrain.doctor.entity.Doctor;
import com.neusoft.cloudbrain.doctor.repository.DoctorRepository;
import com.neusoft.cloudbrain.patient.entity.Patient;
import com.neusoft.cloudbrain.patient.repository.PatientRepository;
import com.neusoft.cloudbrain.schedule.entity.Schedule;
import com.neusoft.cloudbrain.schedule.repository.ScheduleRepository;
import com.neusoft.cloudbrain.triage.dto.TriageAnalyzeRequest;
import com.neusoft.cloudbrain.triage.dto.TriageAnalyzeResponse;
import com.neusoft.cloudbrain.triage.dto.TriageRecordResponse;
import com.neusoft.cloudbrain.triage.entity.TriageRecord;
import com.neusoft.cloudbrain.triage.exception.TriageErrorCode;
import com.neusoft.cloudbrain.triage.repository.TriageRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 分诊 Service
 *
 * 核心编排流程（来自 11_功能需求.md 第6节 和 21_模块与依赖边界.md 第7.2节）：
 *
 * Patient 输入症状
 * → AITriageService.analyze(symptoms)  // 调用 AI 服务
 * → TriageResult (priority, suggested_dept, ai_analysis)
 * → Department 映射真实科室
 * → 推荐可预约医生排班
 * → 保存分诊记录
 *
 * 降级处理（来自 12_业务流程与状态机.md 第14节）：
 * AITriageService 超时或失败
 * → 记录失败原因
 * → 返回 TRIAGE_AI_FAILED
 * → 提示转人工选择
 * → 传统业务继续
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TriageService {

    private final AITriageService aiTriageService;
    private final TriageRecordRepository triageRecordRepository;
    private final PatientRepository patientRepository;
    private final DepartmentRepository departmentRepository;
    private final DoctorRepository doctorRepository;
    private final ScheduleRepository scheduleRepository;

    /**
     * AI 分诊分析
     *
     * 事务保证：分诊记录保存与 AI 结果记录在同一事务内完成。
     */
    @Transactional
    public TriageAnalyzeResponse analyze(TriageAnalyzeRequest request) {
        // 1. 校验患者存在且活跃
        Patient patient = patientRepository.findById(request.patientId())
                .orElseThrow(TriageErrorCode.PATIENT_NOT_FOUND::toException);
        if (!"ACTIVE".equals(patient.getStatus())) {
            throw TriageErrorCode.TRIAGE_PERMISSION_DENIED.toException();
        }

        // 患者只能为自己分诊
        checkPatientOwnership(request.patientId());

        LocalDateTime now = LocalDateTime.now();

        // 2. 构建分诊记录（初始状态）
        TriageRecord record = TriageRecord.builder()
                .patientId(request.patientId())
                .symptoms(request.symptoms())
                .duration(request.duration())
                .supplement(request.supplement())
                .aiStatus("PENDING")
                .mappingStatus("PENDING")
                .aiEmergencySuggested(false)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // 3. 构建 AI 请求（最小化输入，不包含患者隐私 ID）
        TriageAIRequest aiRequest = buildAIRequest(patient, request);

        // 4. 调用 AI 分诊服务（含降级处理）
        try {
            TriageAIResult aiResult = aiTriageService.analyze(aiRequest);

            // AI 成功：保存 AI 结果
            record.setAiDepartmentCode(aiResult.departmentCode());
            record.setAiPriority(aiResult.priority());
            record.setAiReason(aiResult.reason());
            record.setAiSafetyNotice(aiResult.safetyNotice());
            record.setAiEmergencySuggested(aiResult.emergencySuggested());
            record.setAiSymptomKeywords(String.join(",", aiResult.symptomKeywords()));
            record.setAiStatus("SUCCESS");

            // 5. 映射真实科室
            mapDepartment(record, aiResult.departmentCode(), now);

        } catch (BusinessException e) {
            // AI 失败：降级处理
            log.warn("AI 分诊失败，进入降级流程: code={}, message={}", e.getCode(), e.getMessage());
            record.setAiStatus("FAILED");
            record.setAiFailureReason(e.getMessage());
            record.setMappingStatus("MANUAL");
        }

        // 6. 保存分诊记录
        record = triageRecordRepository.save(record);

        // 7. 查询推荐排班（映射成功时）
        List<TriageAnalyzeResponse.RecommendedSchedule> recommendedSchedules = Collections.emptyList();
        if (record.getMappedDepartmentId() != null) {
            recommendedSchedules = queryRecommendedSchedules(record.getMappedDepartmentId());
        }

        return toResponse(record, recommendedSchedules);
    }

    /**
     * 获取分诊记录详情
     */
    @Transactional(readOnly = true)
    public TriageRecordResponse getTriageRecordById(Long id) {
        TriageRecord record = triageRecordRepository.findById(id)
                .orElseThrow(TriageErrorCode.TRIAGE_NOT_FOUND::toException);
        checkPatientOwnership(record.getPatientId());
        return toRecordResponse(record);
    }

    /**
     * 查询患者分诊记录（分页）
     */
    @Transactional(readOnly = true)
    public Page<TriageRecordResponse> getTriageRecordsByPatient(Long patientId, Pageable pageable) {
        checkPatientOwnership(patientId);
        return triageRecordRepository.findByPatientId(patientId, pageable)
                .map(this::toRecordResponse);
    }

    /**
     * 构建 AI 请求（最小化输入，不包含患者 ID、姓名、手机号等隐私）
     */
    private TriageAIRequest buildAIRequest(Patient patient, TriageAnalyzeRequest request) {
        String ageRange = calculateAgeRange(patient.getBirthDate());
        return new TriageAIRequest(
                ageRange,
                patient.getGender(),
                request.symptoms(),
                request.duration(),
                request.supplement());
    }

    /**
     * 根据出生日期计算年龄区间
     */
    private String calculateAgeRange(java.time.LocalDate birthDate) {
        if (birthDate == null) {
            return "未知";
        }
        int age = java.time.Period.between(birthDate, java.time.LocalDate.now()).getYears();
        if (age < 14) return "儿童";
        if (age < 35) return "青年";
        if (age < 60) return "中年";
        return "老年";
    }

    /**
     * 映射 AI 推荐科室到真实科室
     *
     * 映射失败时设置 mappingStatus=MANUAL，不抛异常，允许人工选择。
     */
    private void mapDepartment(TriageRecord record, String departmentCode, LocalDateTime now) {
        if (departmentCode == null || departmentCode.isBlank()) {
            record.setMappingStatus("MANUAL");
            return;
        }

        Department department = departmentRepository.findByCode(departmentCode).orElse(null);
        if (department == null || !"ENABLED".equals(department.getStatus())) {
            // 映射失败：转人工选择
            log.warn("AI 推荐科室映射失败: code={}", departmentCode);
            record.setMappingStatus("MANUAL");
            return;
        }

        record.setMappedDepartmentId(department.getId());
        record.setMappingStatus("MAPPED");
    }

    /**
     * 查询推荐可预约排班
     *
     * 查询映射科室今天及以后的可用排班（AVAILABLE 状态）。
     */
    private List<TriageAnalyzeResponse.RecommendedSchedule> queryRecommendedSchedules(Long departmentId) {
        LocalDate today = LocalDate.now();
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "startTime"));
        Page<Schedule> schedulePage = scheduleRepository
                .findByDepartmentIdAndScheduleDateAndStatusNot(departmentId, today, "CANCELLED", pageable);

        return schedulePage.getContent().stream()
                .filter(s -> "AVAILABLE".equals(s.getStatus()))
                .filter(s -> s.getBookedCount() < s.getMaxAppointments())
                .map(this::toRecommendedSchedule)
                .collect(Collectors.toList());
    }

    /**
     * 转换排班为推荐排班 DTO
     */
    private TriageAnalyzeResponse.RecommendedSchedule toRecommendedSchedule(Schedule schedule) {
        Doctor doctor = doctorRepository.findById(schedule.getDoctorId()).orElse(null);
        Department department = departmentRepository.findById(schedule.getDepartmentId()).orElse(null);
        int remaining = schedule.getMaxAppointments() - schedule.getBookedCount();

        return new TriageAnalyzeResponse.RecommendedSchedule(
                schedule.getId(),
                schedule.getDoctorId(),
                doctor != null ? doctor.getName() : null,
                doctor != null ? doctor.getTitle() : null,
                schedule.getDepartmentId(),
                department != null ? department.getName() : null,
                schedule.getScheduleDate() != null ? schedule.getScheduleDate().toString() : null,
                schedule.getStartTime() != null ? schedule.getStartTime().toString() : null,
                schedule.getEndTime() != null ? schedule.getEndTime().toString() : null,
                remaining);
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
                    .orElseThrow(TriageErrorCode.PATIENT_NOT_FOUND::toException);
            if (!currentPatient.getId().equals(patientId)) {
                throw TriageErrorCode.TRIAGE_PERMISSION_DENIED.toException();
            }
        }
    }

    /**
     * 转换为分诊分析响应
     */
    private TriageAnalyzeResponse toResponse(TriageRecord record,
                                              List<TriageAnalyzeResponse.RecommendedSchedule> schedules) {
        Department mappedDept = record.getMappedDepartmentId() != null
                ? departmentRepository.findById(record.getMappedDepartmentId()).orElse(null)
                : null;

        List<String> keywords = record.getAiSymptomKeywords() != null && !record.getAiSymptomKeywords().isEmpty()
                ? List.of(record.getAiSymptomKeywords().split(","))
                : Collections.emptyList();

        return new TriageAnalyzeResponse(
                record.getId(),
                record.getPatientId(),
                record.getSymptoms(),
                record.getDuration(),
                record.getSupplement(),
                record.getAiDepartmentCode(),
                record.getAiPriority(),
                record.getAiReason(),
                record.getAiSafetyNotice(),
                record.getAiEmergencySuggested(),
                keywords,
                record.getAiStatus(),
                record.getAiFailureReason(),
                record.getMappedDepartmentId(),
                mappedDept != null ? mappedDept.getName() : null,
                record.getMappingStatus(),
                schedules,
                record.getCreatedAt());
    }

    /**
     * 管理员全量分诊记录查询（B4）
     *
     * 多条件分页：患者、优先级、映射科室、时间范围。
     * 权限由 Controller 层校验管理员。
     */
    @Transactional(readOnly = true)
    public Page<TriageRecordResponse> listTriageRecords(
            Long patientId, String priority, Long departmentId,
            LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        return triageRecordRepository.searchTriageRecords(
                patientId, priority, departmentId, startDate, endDate, pageable)
                .map(this::toRecordResponse);
    }

    /**
     * 转换为分诊记录响应
     */
    private TriageRecordResponse toRecordResponse(TriageRecord record) {
        return new TriageRecordResponse(
                record.getId(),
                record.getPatientId(),
                record.getSymptoms(),
                record.getDuration(),
                record.getSupplement(),
                record.getAiDepartmentCode(),
                record.getAiPriority(),
                record.getAiReason(),
                record.getAiSafetyNotice(),
                record.getAiEmergencySuggested(),
                record.getAiSymptomKeywords(),
                record.getMappedDepartmentId(),
                record.getMappingStatus(),
                record.getAiStatus(),
                record.getAiFailureReason(),
                record.getCreatedAt(),
                record.getUpdatedAt());
    }
}
