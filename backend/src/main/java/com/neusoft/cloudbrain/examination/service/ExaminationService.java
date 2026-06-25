package com.neusoft.cloudbrain.examination.service;

import com.neusoft.cloudbrain.ai.api.AIResultInterpretationService;
import com.neusoft.cloudbrain.ai.dto.ResultInterpretationAIRequest;
import com.neusoft.cloudbrain.ai.dto.ResultInterpretationAIResult;
import com.neusoft.cloudbrain.auth.dto.AuthPrincipal;
import com.neusoft.cloudbrain.auth.security.SecurityUtils;
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
import com.neusoft.cloudbrain.examination.entity.ExaminationOrder;
import com.neusoft.cloudbrain.examination.entity.ExaminationResult;
import com.neusoft.cloudbrain.examination.exception.ExaminationErrorCode;
import com.neusoft.cloudbrain.examination.repository.ExaminationOrderRepository;
import com.neusoft.cloudbrain.examination.repository.ExaminationResultRepository;
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
 * 检查检验 Service
 *
 * 核心职责（来自 11_功能需求.md 第10节 和 12_业务流程与状态机.md 第10节）：
 *
 * 1. 状态机：
 *    ORDERED → IN_PROGRESS         执行中
 *    IN_PROGRESS → RESULT_ENTERED   结果录入
 *    RESULT_ENTERED → REVIEWED      医生审核
 *    ORDERED → CANCELLED            取消
 *    IN_PROGRESS → CANCELLED        取消
 *    RESULT_ENTERED → IN_PROGRESS   退回重录（需记录原因）
 *
 * 2. AI 结果解读（异步，不阻塞业务）：
 *    - 保存原始结果后调用 AI 解读
 *    - AI 失败不影响业务，允许医生手工解读
 *    - AI 不得修改原始结果
 *
 * 3. 权限校验：
 *    - 医生只能操作本人接诊就诊的检查检验
 *    - 患者只能查看 REVIEWED 结果
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExaminationService {

    private final ExaminationOrderRepository examinationOrderRepository;
    private final ExaminationResultRepository examinationResultRepository;
    private final EncounterRepository encounterRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final AIResultInterpretationService aiResultInterpretationService;

    // ============================================================
    // 创建检查检验申请
    // ============================================================

    /**
     * 创建检查检验申请
     *
     * 业务规则：
     * - 检查检验关联具体 Encounter
     * - 医生只能为本人接诊的就诊开立申请
     */
    @Transactional
    public ExaminationOrderResponse createOrder(ExaminationOrderCreateRequest request) {
        // 1. 校验申请类型
        if (!"EXAMINATION".equals(request.orderType()) && !"LABORATORY".equals(request.orderType())) {
            throw ExaminationErrorCode.EXAMINATION_ORDER_TYPE_INVALID.toException();
        }

        // 2. 校验就诊存在
        Encounter encounter = encounterRepository.findById(request.encounterId())
                .orElseThrow(ExaminationErrorCode.ENCOUNTER_NOT_FOUND::toException);

        // 3. 校验医生权限（只能为本人接诊的就诊开立申请）
        Doctor doctor = validateDoctorOwnership(encounter.getDoctorId());

        LocalDateTime now = LocalDateTime.now();
        ExaminationOrder order = ExaminationOrder.builder()
                .encounterId(request.encounterId())
                .patientId(encounter.getPatientId())
                .doctorId(doctor.getId())
                .orderType(request.orderType())
                .itemCode(request.itemCode())
                .itemName(request.itemName())
                .status("ORDERED")
                .orderedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
        order = examinationOrderRepository.save(order);

        log.info("创建检查检验申请: orderId={}, encounterId={}, type={}",
                order.getId(), order.getEncounterId(), order.getOrderType());

        return toOrderResponse(order);
    }

    // ============================================================
    // 状态机：执行中 ORDERED → IN_PROGRESS
    // ============================================================

    /**
     * 开始执行检查检验
     */
    @Transactional
    public ExaminationOrderResponse startProgress(Long orderId) {
        ExaminationOrder order = findAndValidateOrder(orderId);
        validateOrderStatus(order, "ORDERED");

        LocalDateTime now = LocalDateTime.now();
        int updated = examinationOrderRepository.updateStatusIfCurrent(
                orderId, "ORDERED", "IN_PROGRESS", now);
        if (updated == 0) {
            throw ExaminationErrorCode.EXAMINATION_STATUS_CONFLICT.toException();
        }

        order.setStatus("IN_PROGRESS");
        order.setInProgressAt(now);
        order.setUpdatedAt(now);
        return toOrderResponse(order);
    }

    // ============================================================
    // 状态机：结果录入 IN_PROGRESS → RESULT_ENTERED
    // ============================================================

    /**
     * 录入检查检验结果
     *
     * 业务编排（来自任务要求）：
     * 1. 保存原始结果
     * 2. 调用 AI 解读（异步，不阻塞业务）
     * 3. AI 失败不抛异常，允许医生手工解读
     */
    @Transactional
    public ExaminationResultResponse recordResult(Long orderId, ExaminationResultRequest request) {
        ExaminationOrder order = findAndValidateOrder(orderId);
        validateOrderStatus(order, "IN_PROGRESS");

        // 1. 检查是否已存在结果（一个申请对应一条结果）
        if (examinationResultRepository.findByOrderId(orderId).isPresent()) {
            throw ExaminationErrorCode.EXAMINATION_RESULT_ALREADY_EXISTS.toException();
        }

        LocalDateTime now = LocalDateTime.now();

        // 2. 保存原始结果
        ExaminationResult result = ExaminationResult.builder()
                .orderId(orderId)
                .resultText(request.resultText())
                .normalRange(request.normalRange())
                .conclusion(request.conclusion())
                .abnormalFlag(request.abnormalFlag())
                .enteredBy(getCurrentUserId())
                .aiStatus("PENDING")
                .createdAt(now)
                .updatedAt(now)
                .build();
        result = examinationResultRepository.save(result);

        // 3. 更新申请状态为 RESULT_ENTERED
        int updated = examinationOrderRepository.updateStatusIfCurrent(
                orderId, "IN_PROGRESS", "RESULT_ENTERED", now);
        if (updated == 0) {
            throw ExaminationErrorCode.EXAMINATION_STATUS_CONFLICT.toException();
        }
        order.setStatus("RESULT_ENTERED");
        order.setResultEnteredAt(now);
        order.setUpdatedAt(now);

        // 4. 调用 AI 解读（异步，不阻塞业务）
        try {
            ResultInterpretationAIRequest aiRequest = new ResultInterpretationAIRequest(
                    order.getItemName(),
                    request.resultText(),
                    request.normalRange(),
                    order.getOrderType());
            ResultInterpretationAIResult interpretation = aiResultInterpretationService.interpret(aiRequest);

            result.setAiInterpretation(interpretation.plainLanguageExplanation());
            result.setAiAbnormalItems(String.join(",", interpretation.abnormalItems()));
            result.setAiFollowUpAdvice(interpretation.followUpAdvice());
            result.setAiStatus("SUCCESS");
            result.setUpdatedAt(LocalDateTime.now());
            result = examinationResultRepository.save(result);
        } catch (Exception e) {
            log.warn("AI 解读失败，不影响业务: orderId={}, error={}", orderId, e.getMessage());
            result.setAiStatus("FAILED");
            result.setAiFailureReason(e.getMessage());
            result.setUpdatedAt(LocalDateTime.now());
            result = examinationResultRepository.save(result);
            // 不抛异常，允许医生手工解读
        }

        log.info("录入检查检验结果: orderId={}, resultId={}, aiStatus={}",
                orderId, result.getId(), result.getAiStatus());

        return toResultResponse(result);
    }

    // ============================================================
    // 状态机：医生审核 RESULT_ENTERED → REVIEWED
    // ============================================================

    /**
     * 医生审核结果
     *
     * REVIEWED 后不得直接覆盖原始结果
     */
    @Transactional
    public ExaminationResultResponse reviewResult(Long orderId) {
        ExaminationOrder order = findAndValidateOrder(orderId);
        validateOrderStatus(order, "RESULT_ENTERED");

        ExaminationResult result = examinationResultRepository.findByOrderId(orderId)
                .orElseThrow(ExaminationErrorCode.EXAMINATION_RESULT_NOT_FOUND::toException);

        LocalDateTime now = LocalDateTime.now();
        result.setReviewedBy(getCurrentUserId());
        result.setUpdatedAt(now);
        result = examinationResultRepository.save(result);

        // 更新申请状态为 REVIEWED
        int updated = examinationOrderRepository.updateStatusIfCurrent(
                orderId, "RESULT_ENTERED", "REVIEWED", now);
        if (updated == 0) {
            throw ExaminationErrorCode.EXAMINATION_STATUS_CONFLICT.toException();
        }
        order.setStatus("REVIEWED");
        order.setReviewedAt(now);
        order.setUpdatedAt(now);

        log.info("审核检查检验结果: orderId={}, reviewerId={}", orderId, result.getReviewedBy());

        return toResultResponse(result);
    }

    // ============================================================
    // 状态机：退回重录 RESULT_ENTERED → IN_PROGRESS
    // ============================================================

    /**
     * 退回重录
     *
     * RESULT_ENTERED → IN_PROGRESS 仅用于审核退回，必须记录原因
     */
    @Transactional
    public ExaminationOrderResponse returnForReentry(Long orderId, ExaminationReturnRequest request) {
        ExaminationOrder order = findAndValidateOrder(orderId);
        validateOrderStatus(order, "RESULT_ENTERED");

        // 退回重录必须记录原因
        if (request.reason() == null || request.reason().isBlank()) {
            throw ExaminationErrorCode.EXAMINATION_RETURN_REASON_REQUIRED.toException();
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = examinationOrderRepository.updateStatusIfCurrent(
                orderId, "RESULT_ENTERED", "IN_PROGRESS", now);
        if (updated == 0) {
            throw ExaminationErrorCode.EXAMINATION_STATUS_CONFLICT.toException();
        }

        order.setStatus("IN_PROGRESS");
        order.setReturnReason(request.reason());
        order.setUpdatedAt(now);

        log.info("退回重录检查检验结果: orderId={}, reason={}", orderId, request.reason());

        return toOrderResponse(order);
    }

    // ============================================================
    // 状态机：取消 ORDERED/IN_PROGRESS → CANCELLED
    // ============================================================

    /**
     * 取消检查检验申请
     *
     * ORDERED 和 IN_PROGRESS 状态可取消
     * RESULT_ENTERED、REVIEWED、CANCELLED 为终态或不可取消
     */
    @Transactional
    public ExaminationOrderResponse cancelOrder(Long orderId, ExaminationCancelRequest request) {
        ExaminationOrder order = findAndValidateOrder(orderId);

        // 只有 ORDERED 和 IN_PROGRESS 状态可取消
        if (!"ORDERED".equals(order.getStatus()) && !"IN_PROGRESS".equals(order.getStatus())) {
            throw ExaminationErrorCode.EXAMINATION_STATUS_CONFLICT.toException();
        }

        String expectedStatus = order.getStatus();
        LocalDateTime now = LocalDateTime.now();
        int updated = examinationOrderRepository.updateStatusIfCurrent(
                orderId, expectedStatus, "CANCELLED", now);
        if (updated == 0) {
            throw ExaminationErrorCode.EXAMINATION_STATUS_CONFLICT.toException();
        }

        order.setStatus("CANCELLED");
        order.setCancelledAt(now);
        order.setCancelReason(request.reason());
        order.setUpdatedAt(now);

        log.info("取消检查检验申请: orderId={}, reason={}", orderId, request.reason());

        return toOrderResponse(order);
    }

    // ============================================================
    // 查询方法
    // ============================================================

    /**
     * 获取申请详情
     */
    @Transactional(readOnly = true)
    public ExaminationOrderResponse getOrderById(Long id) {
        ExaminationOrder order = examinationOrderRepository.findById(id)
                .orElseThrow(ExaminationErrorCode.EXAMINATION_ORDER_NOT_FOUND::toException);
        checkOrderAccess(order);
        return toOrderResponse(order);
    }

    /**
     * 按就诊 ID 查询申请列表
     */
    @Transactional(readOnly = true)
    public List<ExaminationOrderResponse> getOrdersByEncounter(Long encounterId) {
        return examinationOrderRepository.findByEncounterId(encounterId).stream()
                .map(this::toOrderResponse)
                .collect(Collectors.toList());
    }

    /**
     * 按患者 ID 查询申请列表（分页）
     */
    @Transactional(readOnly = true)
    public Page<ExaminationOrderResponse> getOrdersByPatient(Long patientId, Pageable pageable) {
        checkPatientOwnership(patientId);
        return examinationOrderRepository.findByPatientId(patientId, pageable)
                .map(this::toOrderResponse);
    }

    /**
     * 按医生 ID 查询申请列表（分页）
     */
    @Transactional(readOnly = true)
    public Page<ExaminationOrderResponse> getOrdersByDoctor(Long doctorId, Pageable pageable) {
        return examinationOrderRepository.findByDoctorId(doctorId, pageable)
                .map(this::toOrderResponse);
    }

    /**
     * 获取结果详情
     *
     * 患者只能查看 REVIEWED 结果
     */
    @Transactional(readOnly = true)
    public ExaminationResultResponse getResultByOrderId(Long orderId) {
        ExaminationOrder order = examinationOrderRepository.findById(orderId)
                .orElseThrow(ExaminationErrorCode.EXAMINATION_ORDER_NOT_FOUND::toException);

        // 患者只能查看 REVIEWED 结果
        checkResultAccessForPatient(order);

        ExaminationResult result = examinationResultRepository.findByOrderId(orderId)
                .orElseThrow(ExaminationErrorCode.EXAMINATION_RESULT_NOT_FOUND::toException);
        return toResultResponse(result);
    }

    // ============================================================
    // 完成就诊前置条件校验（供 EncounterService 调用）
    // ============================================================

    /**
     * 检查就诊是否存在未完成或未审核的检查检验
     *
     * 任一检查检验处于 ORDERED、IN_PROGRESS 或 RESULT_ENTERED 时不得完成就诊
     */
    @Transactional(readOnly = true)
    public boolean hasPendingExaminations(Long encounterId) {
        return examinationOrderRepository.countPendingByEncounterId(encounterId) > 0;
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private ExaminationOrder findAndValidateOrder(Long orderId) {
        ExaminationOrder order = examinationOrderRepository.findById(orderId)
                .orElseThrow(ExaminationErrorCode.EXAMINATION_ORDER_NOT_FOUND::toException);
        validateDoctorOwnership(order.getDoctorId());
        return order;
    }

    private void validateOrderStatus(ExaminationOrder order, String expectedStatus) {
        if (!expectedStatus.equals(order.getStatus())) {
            throw ExaminationErrorCode.EXAMINATION_STATUS_CONFLICT.toException();
        }
    }

    /**
     * 校验当前登录医生权限
     *
     * 医生只能处理本人接诊就诊的检查检验
     */
    private Doctor validateDoctorOwnership(Long orderDoctorId) {
        if (!SecurityUtils.isAuthenticated()) {
            return doctorRepository.findById(orderDoctorId)
                    .orElseThrow(ExaminationErrorCode.DOCTOR_NOT_FOUND::toException);
        }
        AuthPrincipal currentUser = SecurityUtils.getCurrentUser();
        if (currentUser.roles() != null && currentUser.roles().contains("DOCTOR")) {
            Doctor currentDoctor = doctorRepository.findByUserId(currentUser.userId())
                    .orElseThrow(ExaminationErrorCode.DOCTOR_NOT_FOUND::toException);
            if (!currentDoctor.getId().equals(orderDoctorId)) {
                throw ExaminationErrorCode.EXAMINATION_PERMISSION_DENIED.toException();
            }
            return currentDoctor;
        }
        // 管理员放行
        return doctorRepository.findById(orderDoctorId)
                .orElseThrow(ExaminationErrorCode.DOCTOR_NOT_FOUND::toException);
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
                    .orElseThrow(ExaminationErrorCode.PATIENT_NOT_FOUND::toException);
            if (!currentPatient.getId().equals(patientId)) {
                throw ExaminationErrorCode.EXAMINATION_PERMISSION_DENIED.toException();
            }
        }
    }

    /**
     * 检查申请访问权限
     */
    private void checkOrderAccess(ExaminationOrder order) {
        if (!SecurityUtils.isAuthenticated()) {
            return;
        }
        AuthPrincipal currentUser = SecurityUtils.getCurrentUser();
        if (currentUser.roles() != null && currentUser.roles().contains("PATIENT")) {
            Patient currentPatient = patientRepository.findByUserId(currentUser.userId())
                    .orElseThrow(ExaminationErrorCode.PATIENT_NOT_FOUND::toException);
            if (!currentPatient.getId().equals(order.getPatientId())) {
                throw ExaminationErrorCode.EXAMINATION_PERMISSION_DENIED.toException();
            }
        }
    }

    /**
     * 患者只能查看 REVIEWED 结果
     */
    private void checkResultAccessForPatient(ExaminationOrder order) {
        if (!SecurityUtils.isAuthenticated()) {
            return;
        }
        AuthPrincipal currentUser = SecurityUtils.getCurrentUser();
        if (currentUser.roles() != null && currentUser.roles().contains("PATIENT")) {
            // 患者只能查看 REVIEWED 结果
            if (!"REVIEWED".equals(order.getStatus())) {
                throw ExaminationErrorCode.EXAMINATION_RESULT_NOT_REVIEWED.toException();
            }
            // 校验归属
            Patient currentPatient = patientRepository.findByUserId(currentUser.userId())
                    .orElseThrow(ExaminationErrorCode.PATIENT_NOT_FOUND::toException);
            if (!currentPatient.getId().equals(order.getPatientId())) {
                throw ExaminationErrorCode.EXAMINATION_PERMISSION_DENIED.toException();
            }
        }
    }

    private Long getCurrentUserId() {
        if (SecurityUtils.isAuthenticated()) {
            return SecurityUtils.getCurrentUser().userId();
        }
        return null;
    }

    private ExaminationOrderResponse toOrderResponse(ExaminationOrder order) {
        return new ExaminationOrderResponse(
                order.getId(),
                order.getEncounterId(),
                order.getPatientId(),
                order.getDoctorId(),
                order.getOrderType(),
                order.getItemCode(),
                order.getItemName(),
                order.getStatus(),
                order.getOrderedAt(),
                order.getInProgressAt(),
                order.getResultEnteredAt(),
                order.getReviewedAt(),
                order.getCancelledAt(),
                order.getCancelReason(),
                order.getReturnReason(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    private ExaminationResultResponse toResultResponse(ExaminationResult result) {
        return new ExaminationResultResponse(
                result.getId(),
                result.getOrderId(),
                result.getResultText(),
                result.getNormalRange(),
                result.getConclusion(),
                result.getAbnormalFlag(),
                result.getEnteredBy(),
                result.getReviewedBy(),
                result.getAiInterpretation(),
                result.getAiAbnormalItems(),
                result.getAiFollowUpAdvice(),
                result.getAiStatus(),
                result.getAiFailureReason(),
                result.getCreatedAt(),
                result.getUpdatedAt());
    }
}
