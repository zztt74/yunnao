package com.neusoft.cloudbrain.triage.service;

import com.neusoft.cloudbrain.ai.api.AITriageService;
import com.neusoft.cloudbrain.ai.dto.TriageAIRequest;
import com.neusoft.cloudbrain.ai.dto.TriageAIResult;
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
import com.neusoft.cloudbrain.triage.dto.TriageRecommendedDoctorResponse;
import com.neusoft.cloudbrain.triage.dto.TriageRecordResponse;
import com.neusoft.cloudbrain.triage.entity.TriageRecord;
import com.neusoft.cloudbrain.triage.repository.TriageRecordRepository;
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
 * TriageService 单元测试
 *
 * 覆盖文档 11.2 必测场景：
 * - AI 分诊成功流程（AI 返回 → 科室映射 → 推荐排班）
 * - AI 分诊失败降级流程（AI 异常 → 记录失败 → 转人工）
 * - AI 推荐科室映射失败（科室不存在 → 转人工）
 * - 患者不存在
 * - 患者不活跃
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TriageService - 分诊服务测试")
class TriageServiceTest {

    @Mock
    private AITriageService aiTriageService;

    @Mock
    private TriageRecordRepository triageRecordRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    @InjectMocks
    private TriageService triageService;

    private Patient testPatient;
    private Department testDepartment;
    private Doctor testDoctor;
    private Schedule testSchedule;

    @BeforeEach
    void setUp() {
        testPatient = Patient.builder()
                .id(1L)
                .userId(10L)
                .name("测试患者")
                .gender("MALE")
                .birthDate(LocalDate.of(1990, 1, 1))
                .status("ACTIVE")
                .build();

        testDepartment = Department.builder()
                .id(1L)
                .code("DEPT_INTERNAL")
                .name("内科")
                .status("ENABLED")
                .build();

        testDoctor = Doctor.builder()
                .id(1L)
                .userId(20L)
                .departmentId(1L)
                .name("张医生")
                .title("ATTENDING")
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
                .bookedCount(3)
                .status("AVAILABLE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("AI 分诊成功 - 返回 AI 结果、科室映射和推荐排班")
    void analyze_shouldReturnSuccessResult() {
        TriageAnalyzeRequest request = new TriageAnalyzeRequest(
                1L, "头痛、发热三天", "三天", "无补充");

        TriageAIResult aiResult = new TriageAIResult(
                "DEPT_INTERNAL", "MEDIUM",
                List.of("头痛", "发热"),
                "症状符合内科范围",
                "建议尽快就诊",
                false);

        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));
        when(aiTriageService.analyze(any(TriageAIRequest.class))).thenReturn(aiResult);
        when(departmentRepository.findByCode("DEPT_INTERNAL")).thenReturn(Optional.of(testDepartment));
        when(scheduleRepository.findByDepartmentIdAndScheduleDateAndStatusNot(
                eq(1L), any(LocalDate.class), eq("CANCELLED"), any()))
                .thenReturn(new PageImpl<>(List.of(testSchedule)));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));
        when(triageRecordRepository.save(any(TriageRecord.class))).thenAnswer(invocation -> {
            TriageRecord record = invocation.getArgument(0);
            record.setId(1L);
            return record;
        });

        TriageAnalyzeResponse response = triageService.analyze(request);

        assertThat(response).isNotNull();
        assertThat(response.aiStatus()).isEqualTo("SUCCESS");
        assertThat(response.aiDepartmentCode()).isEqualTo("DEPT_INTERNAL");
        assertThat(response.aiPriority()).isEqualTo("MEDIUM");
        assertThat(response.mappingStatus()).isEqualTo("MAPPED");
        assertThat(response.mappedDepartmentId()).isEqualTo(1L);
        assertThat(response.recommendedSchedules()).hasSize(1);
        assertThat(response.recommendedSchedules().get(0).doctorName()).isEqualTo("张医生");
        assertThat(response.aiSymptomKeywords()).containsExactly("头痛", "发热");

        verify(aiTriageService).analyze(any(TriageAIRequest.class));
        verify(triageRecordRepository).save(any(TriageRecord.class));
    }

    @Test
    @DisplayName("AI 分诊失败降级 - AI 异常时记录失败原因并转人工选择")
    void analyze_shouldDegradeWhenAIFails() {
        TriageAnalyzeRequest request = new TriageAnalyzeRequest(
                1L, "头痛、发热三天", "三天", "无补充");

        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));
        when(aiTriageService.analyze(any(TriageAIRequest.class)))
                .thenThrow(new BusinessException("AI_TRIAGE_FAILED", "AI 分诊服务暂时不可用", 504));
        when(triageRecordRepository.save(any(TriageRecord.class))).thenAnswer(invocation -> {
            TriageRecord record = invocation.getArgument(0);
            record.setId(1L);
            return record;
        });

        TriageAnalyzeResponse response = triageService.analyze(request);

        assertThat(response).isNotNull();
        assertThat(response.aiStatus()).isEqualTo("FAILED");
        assertThat(response.aiFailureReason()).contains("AI 分诊服务暂时不可用");
        assertThat(response.mappingStatus()).isEqualTo("MANUAL");
        assertThat(response.mappedDepartmentId()).isNull();
        assertThat(response.recommendedSchedules()).isEmpty();

        verify(triageRecordRepository).save(any(TriageRecord.class));
    }

    @Test
    @DisplayName("AI 推荐科室映射失败 - 科室不存在时转人工选择")
    void analyze_shouldFallbackToManualWhenDepartmentNotFound() {
        TriageAnalyzeRequest request = new TriageAnalyzeRequest(
                1L, "头痛、发热三天", "三天", "无补充");

        TriageAIResult aiResult = new TriageAIResult(
                "DEPT_UNKNOWN", "MEDIUM",
                List.of("头痛"),
                "症状符合未知科室",
                "建议就诊",
                false);

        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));
        when(aiTriageService.analyze(any(TriageAIRequest.class))).thenReturn(aiResult);
        when(departmentRepository.findByCode("DEPT_UNKNOWN")).thenReturn(Optional.empty());
        when(triageRecordRepository.save(any(TriageRecord.class))).thenAnswer(invocation -> {
            TriageRecord record = invocation.getArgument(0);
            record.setId(1L);
            return record;
        });

        TriageAnalyzeResponse response = triageService.analyze(request);

        assertThat(response).isNotNull();
        assertThat(response.aiStatus()).isEqualTo("SUCCESS");
        assertThat(response.mappingStatus()).isEqualTo("MANUAL");
        assertThat(response.mappedDepartmentId()).isNull();
        assertThat(response.recommendedSchedules()).isEmpty();
    }

    @Test
    @DisplayName("患者不存在时抛出 BusinessException(404)")
    void analyze_shouldThrowWhenPatientNotFound() {
        TriageAnalyzeRequest request = new TriageAnalyzeRequest(
                99L, "头痛、发热三天", "三天", "无补充");

        when(patientRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> triageService.analyze(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PATIENT_NOT_FOUND")
                .hasFieldOrPropertyWithValue("httpStatus", 404);
    }

    @Test
    @DisplayName("患者不活跃时抛出 BusinessException(403)")
    void analyze_shouldThrowWhenPatientInactive() {
        testPatient.setStatus("DISABLED");
        TriageAnalyzeRequest request = new TriageAnalyzeRequest(
                1L, "头痛、发热三天", "三天", "无补充");

        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));

        assertThatThrownBy(() -> triageService.analyze(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "TRIAGE_PERMISSION_DENIED")
                .hasFieldOrPropertyWithValue("httpStatus", 403);
    }

    @Test
    @DisplayName("AI 超时降级 - AI_TIMEOUT 时转人工选择")
    void analyze_shouldDegradeWhenAITimeout() {
        TriageAnalyzeRequest request = new TriageAnalyzeRequest(
                1L, "头痛、发热三天", "三天", "无补充");

        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));
        when(aiTriageService.analyze(any(TriageAIRequest.class)))
                .thenThrow(new BusinessException("AI_TIMEOUT", "AI 分诊超时", 504));
        when(triageRecordRepository.save(any(TriageRecord.class))).thenAnswer(invocation -> {
            TriageRecord record = invocation.getArgument(0);
            record.setId(1L);
            return record;
        });

        TriageAnalyzeResponse response = triageService.analyze(request);

        assertThat(response).isNotNull();
        assertThat(response.aiStatus()).isEqualTo("FAILED");
        assertThat(response.mappingStatus()).isEqualTo("MANUAL");
        assertThat(response.recommendedSchedules()).isEmpty();
    }

    @Test
    @DisplayName("AI 非法响应降级 - AI_INVALID_RESPONSE 时转人工选择")
    void analyze_shouldDegradeWhenAIInvalidResponse() {
        TriageAnalyzeRequest request = new TriageAnalyzeRequest(
                1L, "头痛、发热三天", "三天", "无补充");

        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));
        when(aiTriageService.analyze(any(TriageAIRequest.class)))
                .thenThrow(new BusinessException("AI_INVALID_RESPONSE", "AI 返回数据格式异常", 504));
        when(triageRecordRepository.save(any(TriageRecord.class))).thenAnswer(invocation -> {
            TriageRecord record = invocation.getArgument(0);
            record.setId(1L);
            return record;
        });

        TriageAnalyzeResponse response = triageService.analyze(request);

        assertThat(response).isNotNull();
        assertThat(response.aiStatus()).isEqualTo("FAILED");
        assertThat(response.mappingStatus()).isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("AI 紧急情况标记 - 返回 EMERGENCY 优先级和安全提示")
    void analyze_shouldReturnEmergencyWhenSevere() {
        TriageAnalyzeRequest request = new TriageAnalyzeRequest(
                1L, "胸痛、呼吸困难", "1小时", "无补充");

        TriageAIResult aiResult = new TriageAIResult(
                "DEPT_EMERGENCY", "EMERGENCY",
                List.of("胸痛", "呼吸困难"),
                "症状疑似心血管急症",
                "请立即前往急诊就诊",
                true);

        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));
        when(aiTriageService.analyze(any(TriageAIRequest.class))).thenReturn(aiResult);
        when(departmentRepository.findByCode("DEPT_EMERGENCY")).thenReturn(Optional.empty());
        when(triageRecordRepository.save(any(TriageRecord.class))).thenAnswer(invocation -> {
            TriageRecord record = invocation.getArgument(0);
            record.setId(1L);
            return record;
        });

        TriageAnalyzeResponse response = triageService.analyze(request);

        assertThat(response).isNotNull();
        assertThat(response.aiPriority()).isEqualTo("EMERGENCY");
        assertThat(response.aiEmergencySuggested()).isTrue();
        assertThat(response.aiSafetyNotice()).isEqualTo("请立即前往急诊就诊");
    }

    @Test
    @DisplayName("AI 请求不包含患者隐私 ID（最小化原则）")
    void analyze_shouldNotIncludePatientPrivacyInAIRequest() {
        TriageAnalyzeRequest request = new TriageAnalyzeRequest(
                1L, "头痛、发热三天", "三天", "无补充");

        TriageAIResult aiResult = new TriageAIResult(
                "DEPT_INTERNAL", "MEDIUM",
                List.of("头痛"),
                "理由",
                "提示",
                false);

        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));
        when(aiTriageService.analyze(any(TriageAIRequest.class))).thenReturn(aiResult);
        when(departmentRepository.findByCode("DEPT_INTERNAL")).thenReturn(Optional.of(testDepartment));
        when(scheduleRepository.findByDepartmentIdAndScheduleDateAndStatusNot(
                any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));
        when(triageRecordRepository.save(any(TriageRecord.class))).thenAnswer(invocation -> {
            TriageRecord record = invocation.getArgument(0);
            record.setId(1L);
            return record;
        });

        triageService.analyze(request);

        // 验证 AI 请求参数不包含患者 ID、姓名、手机号
        org.mockito.ArgumentCaptor<TriageAIRequest> captor =
                org.mockito.ArgumentCaptor.forClass(TriageAIRequest.class);
        verify(aiTriageService).analyze(captor.capture());
        TriageAIRequest aiRequest = captor.getValue();

        assertThat(aiRequest.chiefComplaint()).isEqualTo("头痛、发热三天");
        assertThat(aiRequest.duration()).isEqualTo("三天");
        // AI 请求中不应包含患者 ID、姓名、手机号等隐私字段
        // TriageAIRequest 只有 ageRange, gender, chiefComplaint, duration, supplement
        assertThat(aiRequest.ageRange()).isNotNull();
        assertThat(aiRequest.gender()).isEqualTo("MALE");
    }

    // ============================================================
    // UF-01 多轮分诊
    // ============================================================

    @Test
    @DisplayName("UF-01 多轮分诊 - 带 history 时调用 AI 多轮重载并回显 conversationId/round")
    void analyze_multiRound_shouldCallAIWithHistoryAndEchoContext() {
        com.neusoft.cloudbrain.triage.dto.ChatMessage msg =
                new com.neusoft.cloudbrain.triage.dto.ChatMessage("USER", "之前说头痛");
        TriageAnalyzeRequest request = new TriageAnalyzeRequest(
                1L, "头痛、发热三天", "三天", "无补充",
                "conv-abc-123", java.util.List.of(msg), 2);

        TriageAIResult aiResult = new TriageAIResult(
                "DEPT_INTERNAL", "MEDIUM",
                List.of("头痛", "发热"),
                "结合前述，建议内科",
                "提示",
                false);

        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));
        when(aiTriageService.analyze(any(TriageAIRequest.class), any(), eq(2))).thenReturn(aiResult);
        when(departmentRepository.findByCode("DEPT_INTERNAL")).thenReturn(Optional.of(testDepartment));
        when(scheduleRepository.findByDepartmentIdAndScheduleDateAndStatusNot(
                any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));
        when(triageRecordRepository.save(any(TriageRecord.class))).thenAnswer(invocation -> {
            TriageRecord record = invocation.getArgument(0);
            record.setId(1L);
            return record;
        });

        TriageAnalyzeResponse response = triageService.analyze(request);

        assertThat(response.conversationId()).isEqualTo("conv-abc-123");
        assertThat(response.round()).isEqualTo(2);
        assertThat(response.isFinal()).isTrue();
        assertThat(response.followUpQuestion()).isNull();
        verify(aiTriageService).analyze(any(TriageAIRequest.class), any(), eq(2));
    }

    @Test
    @DisplayName("UF-01 单轮兼容 - 不带 conversationId/history 时回显 round=1、isFinal=true")
    void analyze_singleRound_shouldDefaultToRound1AndFinal() {
        TriageAnalyzeRequest request = new TriageAnalyzeRequest(
                1L, "头痛、发热三天", "三天", "无补充");

        TriageAIResult aiResult = new TriageAIResult(
                "DEPT_INTERNAL", "MEDIUM",
                List.of("头痛"),
                "建议内科",
                "提示",
                false);

        when(patientRepository.findById(1L)).thenReturn(Optional.of(testPatient));
        when(aiTriageService.analyze(any(TriageAIRequest.class))).thenReturn(aiResult);
        when(departmentRepository.findByCode("DEPT_INTERNAL")).thenReturn(Optional.of(testDepartment));
        when(scheduleRepository.findByDepartmentIdAndScheduleDateAndStatusNot(
                any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));
        when(triageRecordRepository.save(any(TriageRecord.class))).thenAnswer(invocation -> {
            TriageRecord record = invocation.getArgument(0);
            record.setId(1L);
            return record;
        });

        TriageAnalyzeResponse response = triageService.analyze(request);

        assertThat(response.conversationId()).isNull();
        assertThat(response.round()).isEqualTo(1);
        assertThat(response.isFinal()).isTrue();
        // 单轮不走多轮重载
        verify(aiTriageService).analyze(any(TriageAIRequest.class));
    }
    // ============================================================
    // 分诊记录详情查询测试
    // ============================================================

    @Test
    @DisplayName("获取分诊记录详情 - 存在时应返回记录")
    void getTriageRecordById_shouldReturnWhenExists() {
        TriageRecord record = TriageRecord.builder()
                .id(1L).patientId(1L).symptoms("头痛")
                .aiStatus("SUCCESS").mappingStatus("MAPPED")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        when(triageRecordRepository.findById(1L)).thenReturn(Optional.of(record));

        TriageRecordResponse response = triageService.getTriageRecordById(1L);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.symptoms()).isEqualTo("头痛");
    }

    @Test
    @DisplayName("获取分诊记录详情 - 不存在时抛出 TRIAGE_NOT_FOUND(404)")
    void getTriageRecordById_shouldThrowWhenNotFound() {
        when(triageRecordRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> triageService.getTriageRecordById(99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "TRIAGE_NOT_FOUND")
                .hasFieldOrPropertyWithValue("httpStatus", 404);
    }

    // ============================================================
    // 患者分诊记录分页查询测试
    // ============================================================

    @Test
    @DisplayName("患者分诊记录分页查询 - 返回分页结果")
    void getTriageRecordsByPatient_shouldReturnPagedResult() {
        TriageRecord record = TriageRecord.builder()
                .id(1L).patientId(1L).symptoms("头痛")
                .aiStatus("SUCCESS").mappingStatus("MAPPED")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        PageImpl<TriageRecord> page = new PageImpl<>(List.of(record), PageRequest.of(0, 20), 1);
        when(triageRecordRepository.findByPatientId(eq(1L), any())).thenReturn(page);

        Page<TriageRecordResponse> result =
                triageService.getTriageRecordsByPatient(1L, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).symptoms()).isEqualTo("头痛");
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("患者分诊记录分页查询 - 无数据时返回空页")
    void getTriageRecordsByPatient_shouldReturnEmptyPageWhenNoData() {
        PageImpl<TriageRecord> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(triageRecordRepository.findByPatientId(eq(1L), any())).thenReturn(emptyPage);

        Page<TriageRecordResponse> result =
                triageService.getTriageRecordsByPatient(1L, PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    // ============================================================
    // 推荐医生查询测试
    // ============================================================

    @Test
    @DisplayName("查询推荐医生 - 返回医生列表及可预约排班")
    void getRecommendedDoctors_shouldReturnDoctorsWithSchedules() {
        when(doctorRepository.findByDepartmentIdAndStatus(1L, "ENABLED"))
                .thenReturn(List.of(testDoctor));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));
        when(scheduleRepository.findByDoctorId(1L)).thenReturn(List.of(testSchedule));

        List<TriageRecommendedDoctorResponse> result = triageService.getRecommendedDoctors(1L, 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).doctorName()).isEqualTo("张医生");
        assertThat(result.get(0).departmentName()).isEqualTo("内科");
        assertThat(result.get(0).availableSchedules()).hasSize(1);
        assertThat(result.get(0).availableSchedules().get(0).remainingCount()).isEqualTo(7);
    }

    @Test
    @DisplayName("查询推荐医生 - 科室无医生时返回空列表")
    void getRecommendedDoctors_shouldReturnEmptyWhenNoDoctors() {
        when(doctorRepository.findByDepartmentIdAndStatus(99L, "ENABLED"))
                .thenReturn(List.of());

        List<TriageRecommendedDoctorResponse> result = triageService.getRecommendedDoctors(99L, 5);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("查询推荐医生 - 医生无可用排班时返回空排班列表")
    void getRecommendedDoctors_shouldReturnEmptySchedulesWhenNoneAvailable() {
        when(doctorRepository.findByDepartmentIdAndStatus(1L, "ENABLED"))
                .thenReturn(List.of(testDoctor));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(testDepartment));
        when(scheduleRepository.findByDoctorId(1L)).thenReturn(List.of());

        List<TriageRecommendedDoctorResponse> result = triageService.getRecommendedDoctors(1L, 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).availableSchedules()).isEmpty();
    }

    // ============================================================
    // 管理员全量分诊记录查询测试
    // ============================================================

    @Test
    @DisplayName("管理员分诊记录查询 - 返回分页结果")
    void listTriageRecords_shouldReturnPagedResult() {
        TriageRecord record = TriageRecord.builder()
                .id(1L).patientId(1L).symptoms("头痛")
                .aiPriority("MEDIUM").aiStatus("SUCCESS")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        PageImpl<TriageRecord> page = new PageImpl<>(List.of(record), PageRequest.of(0, 20), 1);
        when(triageRecordRepository.searchTriageRecords(any(), any(), any(), any(), any(), any()))
                .thenReturn(page);

        Page<TriageRecordResponse> result = triageService.listTriageRecords(
                null, null, null, null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("管理员分诊记录查询 - 无数据时返回空页")
    void listTriageRecords_shouldReturnEmptyPageWhenNoData() {
        PageImpl<TriageRecord> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(triageRecordRepository.searchTriageRecords(any(), any(), any(), any(), any(), any()))
                .thenReturn(emptyPage);

        Page<TriageRecordResponse> result = triageService.listTriageRecords(
                1L, "HIGH", 1L, null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
    }
}
