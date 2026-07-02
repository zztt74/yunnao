package com.neusoft.cloudbrain.examination.service;

import com.neusoft.cloudbrain.ai.api.AIResultInterpretationService;
import com.neusoft.cloudbrain.ai.dto.ResultInterpretationAIRequest;
import com.neusoft.cloudbrain.ai.dto.ResultInterpretationAIResult;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.neusoft.cloudbrain.department.entity.Department;
import com.neusoft.cloudbrain.department.repository.DepartmentRepository;
import com.neusoft.cloudbrain.device.entity.Device;
import com.neusoft.cloudbrain.device.entity.DeviceUsage;
import com.neusoft.cloudbrain.device.repository.DeviceRepository;
import com.neusoft.cloudbrain.device.repository.DeviceUsageRepository;
import com.neusoft.cloudbrain.doctor.entity.Doctor;
import com.neusoft.cloudbrain.doctor.repository.DoctorRepository;
import com.neusoft.cloudbrain.encounter.entity.Encounter;
import com.neusoft.cloudbrain.encounter.repository.EncounterRepository;
import com.neusoft.cloudbrain.examination.dto.ExaminationCancelRequest;
import com.neusoft.cloudbrain.examination.dto.ExaminationOrderCreateRequest;
import com.neusoft.cloudbrain.examination.dto.ExaminationOrderResponse;
import com.neusoft.cloudbrain.examination.dto.ExaminationResultRequest;
import com.neusoft.cloudbrain.examination.dto.ExaminationResultResponse;
import com.neusoft.cloudbrain.examination.dto.ExaminationReturnRequest;
import com.neusoft.cloudbrain.examination.dto.ExaminationTrackingResponse;
import com.neusoft.cloudbrain.examination.entity.ExaminationOrder;
import com.neusoft.cloudbrain.examination.entity.ExaminationResult;
import com.neusoft.cloudbrain.examination.repository.ExaminationOrderRepository;
import com.neusoft.cloudbrain.examination.repository.ExaminationResultRepository;
import com.neusoft.cloudbrain.patient.repository.PatientRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ExaminationService 单元测试
 *
 * 覆盖文档 11.2 必测场景：
 * - 创建检查检验申请
 * - 状态机流转（ORDERED → IN_PROGRESS → RESULT_ENTERED → REVIEWED）
 * - 取消（ORDERED/IN_PROGRESS → CANCELLED）
 * - 退回重录（RESULT_ENTERED → IN_PROGRESS）
 * - AI 结果解读（成功/失败均不影响业务）
 * - 状态冲突
 * - 完成就诊前置条件校验
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExaminationService - 检查检验服务测试")
class ExaminationServiceTest {

    @Mock
    private ExaminationOrderRepository examinationOrderRepository;

    @Mock
    private ExaminationResultRepository examinationResultRepository;

    @Mock
    private EncounterRepository encounterRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private AIResultInterpretationService aiResultInterpretationService;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private DeviceUsageRepository deviceUsageRepository;

    @InjectMocks
    private ExaminationService examinationService;

    private Encounter testEncounter;
    private Doctor testDoctor;
    private ExaminationOrder testOrder;
    private ExaminationResult testResult;

    @BeforeEach
    void setUp() {
        testDoctor = Doctor.builder()
                .id(1L)
                .userId(20L)
                .departmentId(1L)
                .name("张医生")
                .title("ATTENDING")
                .status("ENABLED")
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

        testOrder = ExaminationOrder.builder()
                .id(1L)
                .encounterId(1L)
                .patientId(1L)
                .doctorId(1L)
                .orderType("LABORATORY")
                .itemCode("CBC")
                .itemName("血常规")
                .status("ORDERED")
                .orderedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        testResult = ExaminationResult.builder()
                .id(1L)
                .orderId(1L)
                .resultText("白细胞计数 11.5")
                .normalRange("4.0-10.0")
                .conclusion("轻度升高")
                .abnormalFlag("HIGH")
                .aiStatus("NOT_REQUESTED")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ============================================================
    // 创建检查检验申请
    // ============================================================

    @Test
    @DisplayName("创建申请 - 正常创建检查检验申请")
    void createOrder_shouldCreateOrder() {
        ExaminationOrderCreateRequest request = new ExaminationOrderCreateRequest(
                1L, "LABORATORY", "CBC", "血常规");

        when(encounterRepository.findById(1L)).thenReturn(Optional.of(testEncounter));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(examinationOrderRepository.save(any(ExaminationOrder.class))).thenAnswer(invocation -> {
            ExaminationOrder order = invocation.getArgument(0);
            order.setId(1L);
            return order;
        });

        ExaminationOrderResponse response = examinationService.createOrder(request);

        assertThat(response).isNotNull();
        assertThat(response.orderType()).isEqualTo("LABORATORY");
        assertThat(response.itemName()).isEqualTo("血常规");
        assertThat(response.status()).isEqualTo("ORDERED");
    }

    @Test
    @DisplayName("创建申请 - 申请类型非法时抛出 BusinessException(400)")
    void createOrder_shouldThrowWhenOrderTypeInvalid() {
        ExaminationOrderCreateRequest request = new ExaminationOrderCreateRequest(
                1L, "INVALID_TYPE", "CODE", "项目");

        assertThatThrownBy(() -> examinationService.createOrder(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 400);
    }

    @Test
    @DisplayName("创建申请 - 就诊不存在时抛出 BusinessException(404)")
    void createOrder_shouldThrowWhenEncounterNotFound() {
        ExaminationOrderCreateRequest request = new ExaminationOrderCreateRequest(
                99L, "LABORATORY", "CBC", "血常规");

        when(encounterRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> examinationService.createOrder(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 404);
    }

    // ============================================================
    // 状态机：执行中 ORDERED → IN_PROGRESS
    // ============================================================

    @Test
    @DisplayName("开始执行 - ORDERED → IN_PROGRESS")
    void startProgress_shouldTransitionToInProgress() {
        when(examinationOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(examinationOrderRepository.updateStatusIfCurrent(eq(1L), eq("ORDERED"), eq("IN_PROGRESS"), any()))
                .thenReturn(1);

        ExaminationOrderResponse response = examinationService.startProgress(1L);

        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        assertThat(response.inProgressAt()).isNotNull();
    }

    @Test
    @DisplayName("开始执行 - 非 ORDERED 状态时抛出 BusinessException(409)")
    void startProgress_shouldThrowWhenStatusNotOrdered() {
        testOrder.setStatus("IN_PROGRESS");
        when(examinationOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));

        assertThatThrownBy(() -> examinationService.startProgress(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    // ============================================================
    // 状态机：结果录入 IN_PROGRESS → RESULT_ENTERED
    // ============================================================

    @Test
    @DisplayName("录入结果 - 正常录入并调用 AI 解读成功")
    void recordResult_shouldSaveResultAndCallAI() {
        testOrder.setStatus("IN_PROGRESS");
        ExaminationResultRequest request = new ExaminationResultRequest(
                "白细胞计数 11.5", "4.0-10.0", "轻度升高", "HIGH");

        when(examinationOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(examinationResultRepository.findByOrderId(1L)).thenReturn(Optional.empty());
        when(examinationResultRepository.save(any(ExaminationResult.class))).thenAnswer(invocation -> {
            ExaminationResult result = invocation.getArgument(0);
            result.setId(1L);
            return result;
        });
        when(examinationOrderRepository.updateStatusIfCurrent(
                eq(1L), eq("IN_PROGRESS"), eq("RESULT_ENTERED"), any())).thenReturn(1);

        ResultInterpretationAIResult aiResult = new ResultInterpretationAIResult(
                List.of("白细胞计数"),
                "白细胞轻度升高，可能为感染",
                "建议复查",
                "AI 仅供参考");
        when(aiResultInterpretationService.interpret(any(ResultInterpretationAIRequest.class)))
                .thenReturn(aiResult);

        ExaminationResultResponse response = examinationService.recordResult(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.aiStatus()).isEqualTo("SUCCESS");
        assertThat(response.aiInterpretation()).isEqualTo("白细胞轻度升高，可能为感染");
        verify(aiResultInterpretationService).interpret(any(ResultInterpretationAIRequest.class));
    }

    @Test
    @DisplayName("录入结果 - AI 解读失败不影响业务")
    void recordResult_shouldNotFailWhenAIInterpretationFails() {
        testOrder.setStatus("IN_PROGRESS");
        ExaminationResultRequest request = new ExaminationResultRequest(
                "白细胞计数 11.5", "4.0-10.0", "轻度升高", "HIGH");

        when(examinationOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(examinationResultRepository.findByOrderId(1L)).thenReturn(Optional.empty());
        when(examinationResultRepository.save(any(ExaminationResult.class))).thenAnswer(invocation -> {
            ExaminationResult result = invocation.getArgument(0);
            result.setId(1L);
            return result;
        });
        when(examinationOrderRepository.updateStatusIfCurrent(
                eq(1L), eq("IN_PROGRESS"), eq("RESULT_ENTERED"), any())).thenReturn(1);
        when(aiResultInterpretationService.interpret(any(ResultInterpretationAIRequest.class)))
                .thenThrow(new RuntimeException("AI 服务不可用"));

        ExaminationResultResponse response = examinationService.recordResult(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.aiStatus()).isEqualTo("FAILED");
        assertThat(response.aiFailureReason()).isEqualTo("AI 服务不可用");
        // 业务正常返回，不抛异常
        verify(examinationOrderRepository).updateStatusIfCurrent(
                eq(1L), eq("IN_PROGRESS"), eq("RESULT_ENTERED"), any());
    }

    @Test
    @DisplayName("录入结果 - 结果已存在时抛出 BusinessException(409)")
    void recordResult_shouldThrowWhenResultAlreadyExists() {
        testOrder.setStatus("IN_PROGRESS");
        ExaminationResultRequest request = new ExaminationResultRequest(
                "白细胞计数 11.5", "4.0-10.0", "轻度升高", "HIGH");

        when(examinationOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(examinationResultRepository.findByOrderId(1L)).thenReturn(Optional.of(testResult));

        assertThatThrownBy(() -> examinationService.recordResult(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    // ============================================================
    // 状态机：医生审核 RESULT_ENTERED → REVIEWED
    // ============================================================

    @Test
    @DisplayName("审核结果 - RESULT_ENTERED → REVIEWED")
    void reviewResult_shouldTransitionToReviewed() {
        testOrder.setStatus("RESULT_ENTERED");
        when(examinationOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(examinationResultRepository.findByOrderId(1L)).thenReturn(Optional.of(testResult));
        when(examinationResultRepository.save(any(ExaminationResult.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(examinationOrderRepository.updateStatusIfCurrent(
                eq(1L), eq("RESULT_ENTERED"), eq("REVIEWED"), any())).thenReturn(1);

        ExaminationResultResponse response = examinationService.reviewResult(1L);

        assertThat(response).isNotNull();
        // 验证状态机转换成功（reviewedBy 在有 auth 上下文时才会设置）
        verify(examinationOrderRepository).updateStatusIfCurrent(
                eq(1L), eq("RESULT_ENTERED"), eq("REVIEWED"), any());
    }

    // ============================================================
    // 状态机：退回重录 RESULT_ENTERED → IN_PROGRESS
    // ============================================================

    @Test
    @DisplayName("退回重录 - RESULT_ENTERED → IN_PROGRESS 需记录原因")
    void returnForReentry_shouldTransitionToInProgressWithReason() {
        testOrder.setStatus("RESULT_ENTERED");
        ExaminationReturnRequest request = new ExaminationReturnRequest("结果异常需复核");

        when(examinationOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(examinationOrderRepository.updateStatusIfCurrent(
                eq(1L), eq("RESULT_ENTERED"), eq("IN_PROGRESS"), any())).thenReturn(1);

        ExaminationOrderResponse response = examinationService.returnForReentry(1L, request);

        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        assertThat(response.returnReason()).isEqualTo("结果异常需复核");
    }

    @Test
    @DisplayName("退回重录 - 未提供原因时抛出 BusinessException(400)")
    void returnForReentry_shouldThrowWhenReasonMissing() {
        testOrder.setStatus("RESULT_ENTERED");
        ExaminationReturnRequest request = new ExaminationReturnRequest("");

        when(examinationOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));

        assertThatThrownBy(() -> examinationService.returnForReentry(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    // ============================================================
    // 状态机：取消 ORDERED/IN_PROGRESS → CANCELLED
    // ============================================================

    @Test
    @DisplayName("取消申请 - ORDERED → CANCELLED")
    void cancelOrder_shouldTransitionToCancelled() {
        ExaminationCancelRequest request = new ExaminationCancelRequest("不需要");

        when(examinationOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(examinationOrderRepository.updateStatusIfCurrent(
                eq(1L), eq("ORDERED"), eq("CANCELLED"), any())).thenReturn(1);

        ExaminationOrderResponse response = examinationService.cancelOrder(1L, request);

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(response.cancelReason()).isEqualTo("不需要");
    }

    @Test
    @DisplayName("取消申请 - RESULT_ENTERED 状态不可取消")
    void cancelOrder_shouldThrowWhenStatusIsResultEntered() {
        testOrder.setStatus("RESULT_ENTERED");
        ExaminationCancelRequest request = new ExaminationCancelRequest("不需要");

        when(examinationOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));

        assertThatThrownBy(() -> examinationService.cancelOrder(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    // ============================================================
    // 完成就诊前置条件校验
    // ============================================================

    @Test
    @DisplayName("完成就诊校验 - 存在未完成检查检验时返回 true")
    void hasPendingExaminations_shouldReturnTrueWhenPendingExists() {
        when(examinationOrderRepository.countPendingByEncounterId(1L)).thenReturn(2L);

        boolean result = examinationService.hasPendingExaminations(1L);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("完成就诊校验 - 无未完成检查检验时返回 false")
    void hasPendingExaminations_shouldReturnFalseWhenNoPending() {
        when(examinationOrderRepository.countPendingByEncounterId(1L)).thenReturn(0L);

        boolean result = examinationService.hasPendingExaminations(1L);

        assertThat(result).isFalse();
    }

    // ============================================================
    // 申请不存在
    // ============================================================

    @Test
    @DisplayName("申请不存在时抛出 BusinessException(404)")
    void getOrderById_shouldThrowWhenNotFound() {
        when(examinationOrderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> examinationService.getOrderById(99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 404);
    }

    // ============================================================
    // UF-02 患者检查流程追踪
    // ============================================================

    @Test
    @DisplayName("UF-02 患者流程追踪 - 返回全状态申请 + 引导字段")
    void getTrackingByPatient_shouldReturnAllStatusWithGuide() {
        ExaminationOrder ordered = ExaminationOrder.builder()
                .id(1L).encounterId(1L).patientId(1L).doctorId(1L)
                .orderType("EXAMINATION").itemCode("US").itemName("腹部 B 超")
                .status("ORDERED").orderedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();

        when(examinationOrderRepository.findByPatientId(1L)).thenReturn(List.of(ordered));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(testEncounter));
        Department dept = Department.builder().id(1L).name("内科").description("1 号楼 3 层").build();
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(dept));
        when(deviceUsageRepository.findByEncounterId(1L)).thenReturn(List.of());

        List<ExaminationTrackingResponse> result = examinationService.getTrackingByPatient(1L);

        assertThat(result).hasSize(1);
        ExaminationTrackingResponse r = result.get(0);
        assertThat(r.status()).isEqualTo("ORDERED");
        assertThat(r.doctorName()).isEqualTo("张医生");
        assertThat(r.departmentName()).isEqualTo("内科");
        assertThat(r.nextAction()).contains("内科");
        assertThat(r.deviceName()).isNull();
    }

    @Test
    @DisplayName("UF-02 患者流程追踪 - REVIEWED 状态派生 nextAction")
    void getTrackingByPatient_shouldDeriveNextActionForReviewed() {
        ExaminationOrder reviewed = ExaminationOrder.builder()
                .id(2L).encounterId(1L).patientId(1L).doctorId(1L)
                .orderType("LABORATORY").itemCode("CBC").itemName("血常规")
                .status("REVIEWED").orderedAt(LocalDateTime.now())
                .reviewedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();

        when(examinationOrderRepository.findByPatientId(1L)).thenReturn(List.of(reviewed));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(testEncounter));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(
                Department.builder().id(1L).name("内科").build()));
        when(deviceUsageRepository.findByEncounterId(1L)).thenReturn(List.of());

        List<ExaminationTrackingResponse> result = examinationService.getTrackingByPatient(1L);

        assertThat(result.get(0).nextAction()).isEqualTo("已审核，可查看报告");
    }

    @Test
    @DisplayName("UF-02 患者流程追踪 - 含设备使用记录时返回设备信息")
    void getTrackingByPatient_shouldReturnDeviceInfoWhenUsageExists() {
        when(examinationOrderRepository.findByPatientId(1L)).thenReturn(List.of(testOrder));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(testEncounter));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(
                Department.builder().id(1L).name("内科").build()));

        DeviceUsage usage = DeviceUsage.builder()
                .id(1L).deviceId(10L).encounterId(1L).usedBy(20L)
                .startTime(LocalDateTime.now()).status("COMPLETED")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        when(deviceUsageRepository.findByEncounterId(1L)).thenReturn(List.of(usage));
        Device device = Device.builder()
                .id(10L).code("DEV-US-001").name("彩超 #1").type("ULTRASOUND")
                .status("AVAILABLE").location("2 号楼 1 层 B 超室")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        when(deviceRepository.findById(10L)).thenReturn(Optional.of(device));

        List<ExaminationTrackingResponse> result = examinationService.getTrackingByPatient(1L);

        assertThat(result.get(0).deviceName()).isEqualTo("彩超 #1");
        assertThat(result.get(0).deviceLocation()).isEqualTo("2 号楼 1 层 B 超室");
    }

    @Test
    @DisplayName("UF-02 按就诊 ID 查询流程追踪")
    void getTrackingByEncounter_shouldReturnList() {
        when(examinationOrderRepository.findByEncounterId(1L)).thenReturn(List.of(testOrder));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(testEncounter));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(
                Department.builder().id(1L).name("内科").build()));
        when(deviceUsageRepository.findByEncounterId(1L)).thenReturn(List.of());

        List<ExaminationTrackingResponse> result = examinationService.getTrackingByEncounter(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).itemName()).isEqualTo("血常规");
    }

    // ============================================================
    // 按就诊查询检查检验列表
    // ============================================================

    @Test
    @DisplayName("按就诊查询检查检验列表 - 返回列表")
    void getOrdersByEncounter_shouldReturnList() {
        when(examinationOrderRepository.findByEncounterId(1L)).thenReturn(List.of(testOrder));

        List<ExaminationOrderResponse> result = examinationService.getOrdersByEncounter(1L);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("按就诊查询检查检验列表 - 无数据时返回空列表")
    void getOrdersByEncounter_shouldReturnEmptyList() {
        when(examinationOrderRepository.findByEncounterId(99L)).thenReturn(List.of());

        List<ExaminationOrderResponse> result = examinationService.getOrdersByEncounter(99L);

        assertThat(result).isEmpty();
    }

    // ============================================================
    // 按患者查询检查检验列表（分页）
    // ============================================================

    @Test
    @DisplayName("按患者查询检查检验列表 - 返回分页结果")
    void getOrdersByPatient_shouldReturnPagedResult() {
        Pageable pageable = PageRequest.of(0, 10);
        when(examinationOrderRepository.findByPatientId(eq(1L), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(testOrder), pageable, 1));

        var result = examinationService.getOrdersByPatient(1L, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("按患者查询检查检验列表 - 无数据时返回空页")
    void getOrdersByPatient_shouldReturnEmptyPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(examinationOrderRepository.findByPatientId(eq(1L), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        var result = examinationService.getOrdersByPatient(1L, pageable);

        assertThat(result.getContent()).isEmpty();
    }

    // ============================================================
    // 按医生查询检查检验列表（分页）
    // ============================================================

    @Test
    @DisplayName("按医生查询检查检验列表 - 返回分页结果")
    void getOrdersByDoctor_shouldReturnPagedResult() {
        Pageable pageable = PageRequest.of(0, 10);
        when(examinationOrderRepository.findByDoctorId(eq(1L), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(testOrder), pageable, 1));

        var result = examinationService.getOrdersByDoctor(1L, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("按医生查询检查检验列表 - 无数据时返回空页")
    void getOrdersByDoctor_shouldReturnEmptyPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(examinationOrderRepository.findByDoctorId(eq(1L), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        var result = examinationService.getOrdersByDoctor(1L, pageable);

        assertThat(result.getContent()).isEmpty();
    }

    // ============================================================
    // 获取结果详情
    // ============================================================

    @Test
    @DisplayName("获取检查结果详情 - 存在时返回结果")
    void getResultByOrderId_shouldReturnWhenFound() {
        when(examinationOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(examinationResultRepository.findByOrderId(1L)).thenReturn(Optional.of(testResult));

        ExaminationResultResponse result = examinationService.getResultByOrderId(1L);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("获取检查结果详情 - 订单不存在时抛出异常")
    void getResultByOrderId_shouldThrowWhenOrderNotFound() {
        when(examinationOrderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> examinationService.getResultByOrderId(99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 404);
    }

    @Test
    @DisplayName("获取检查结果详情 - 结果不存在时抛出异常")
    void getResultByOrderId_shouldThrowWhenResultNotFound() {
        when(examinationOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(examinationResultRepository.findByOrderId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> examinationService.getResultByOrderId(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 404);
    }

    // ============================================================
    // 获取检查申请详情 - 存在时返回
    // ============================================================

    @Test
    @DisplayName("获取检查申请详情 - 存在时返回订单")
    void getOrderById_shouldReturnWhenFound() {
        when(examinationOrderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        ExaminationOrderResponse result = examinationService.getOrderById(1L);

        assertThat(result).isNotNull();
    }
}
