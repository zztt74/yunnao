package com.neusoft.cloudbrain.prescription.service;

import com.neusoft.cloudbrain.audit.annotation.Auditable;
import com.neusoft.cloudbrain.ai.api.AIPrescriptionReviewService;
import com.neusoft.cloudbrain.ai.dto.PrescriptionReviewAIRequest;
import com.neusoft.cloudbrain.ai.dto.PrescriptionReviewAIRequest.DeterministicRuleResult;
import com.neusoft.cloudbrain.ai.dto.PrescriptionReviewAIRequest.PrescriptionItemInfo;
import com.neusoft.cloudbrain.ai.dto.PrescriptionReviewAIResult;
import com.neusoft.cloudbrain.auth.dto.AuthPrincipal;
import com.neusoft.cloudbrain.auth.security.SecurityUtils;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.neusoft.cloudbrain.department.entity.Department;
import com.neusoft.cloudbrain.department.repository.DepartmentRepository;
import com.neusoft.cloudbrain.doctor.entity.Doctor;
import com.neusoft.cloudbrain.doctor.repository.DoctorRepository;
import com.neusoft.cloudbrain.drug.entity.Drug;
import com.neusoft.cloudbrain.drug.entity.DrugContraindication;
import com.neusoft.cloudbrain.drug.entity.DrugDosageRule;
import com.neusoft.cloudbrain.drug.entity.DrugInteractionRule;
import com.neusoft.cloudbrain.drug.repository.DrugContraindicationRepository;
import com.neusoft.cloudbrain.drug.repository.DrugDosageRuleRepository;
import com.neusoft.cloudbrain.drug.repository.DrugInteractionRuleRepository;
import com.neusoft.cloudbrain.drug.repository.DrugRepository;
import com.neusoft.cloudbrain.encounter.entity.Encounter;
import com.neusoft.cloudbrain.encounter.repository.EncounterRepository;
import com.neusoft.cloudbrain.patient.entity.Patient;
import com.neusoft.cloudbrain.patient.entity.PatientProfile;
import com.neusoft.cloudbrain.patient.repository.PatientProfileRepository;
import com.neusoft.cloudbrain.patient.repository.PatientRepository;
import com.neusoft.cloudbrain.prescription.dto.PrescriptionCreateRequest;
import com.neusoft.cloudbrain.prescription.dto.PrescriptionItemDTO;
import com.neusoft.cloudbrain.prescription.dto.PrescriptionItemResponse;
import com.neusoft.cloudbrain.prescription.dto.PrescriptionResponse;
import com.neusoft.cloudbrain.prescription.dto.PrescriptionReviewResponse;
import com.neusoft.cloudbrain.prescription.dto.PrescriptionVoidRequest;
import com.neusoft.cloudbrain.prescription.entity.Prescription;
import com.neusoft.cloudbrain.prescription.entity.PrescriptionItem;
import com.neusoft.cloudbrain.prescription.entity.PrescriptionReview;
import com.neusoft.cloudbrain.prescription.exception.PrescriptionErrorCode;
import com.neusoft.cloudbrain.prescription.repository.PrescriptionItemRepository;
import com.neusoft.cloudbrain.prescription.repository.PrescriptionRepository;
import com.neusoft.cloudbrain.prescription.repository.PrescriptionReviewRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 处方 Service
 *
 * 核心职责（来自 11_功能需求.md 第12节 和 12_业务流程与状态机.md 第9节）：
 *
 * 1. 确定性规则优先执行：
 *    - 过敏检查（DrugContraindication ALLERGY）
 *    - 相互作用检查（DrugInteractionRule）
 *    - 剂量检查（DrugDosageRule）
 *    - 禁忌检查（DrugContraindication）
 *
 * 2. AI 审核补充（不降低确定性规则风险）：
 *    - 异步调用 AI 解释风险和补充建议
 *    - AI 失败不影响处方创建，医生可手工确认
 *
 * 3. 处方状态机：
 *    DRAFT → CONFIRMED       医生确认
 *    CONFIRMED → VOIDED      作废（需记录原因）
 *
 * 4. AI 审核状态机（独立）：
 *    NOT_REQUESTED → PENDING       提交审核
 *    PENDING → REVIEWED/FAILED
 *    FAILED → PENDING              重新提交
 *    REVIEWED → PENDING            修改后重新审核
 *
 * 关键规则：
 * - 确定性规则命中不得被 AI 输出降级或覆盖
 * - 处方可不存在，不得为了完成就诊创建空处方
 * - 已确认处方只能作废，不得物理删除
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrescriptionService {

    /**
     * B-HW-02：患者端可见的处方业务状态。
     * 仅返回 CONFIRMED、VOIDED，DRAFT 不对 Patient 暴露。
     */
    private static final List<String> PATIENT_VISIBLE_STATUSES = List.of("CONFIRMED", "VOIDED");

    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;
    private final PrescriptionReviewRepository prescriptionReviewRepository;
    private final EncounterRepository encounterRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final DepartmentRepository departmentRepository;
    private final DrugRepository drugRepository;
    private final DrugInteractionRuleRepository drugInteractionRuleRepository;
    private final DrugDosageRuleRepository drugDosageRuleRepository;
    private final DrugContraindicationRepository drugContraindicationRepository;
    private final AIPrescriptionReviewService aiPrescriptionReviewService;
    private final ObjectMapper objectMapper;

    // ============================================================
    // 创建处方草稿（含确定性规则检查 + AI 审核）
    // ============================================================

    /**
     * 创建处方
     *
     * 业务流程（来自 11_功能需求.md 第12.4节）：
     * 1. 先执行确定性规则检查
     * 2. 高风险直接拒绝或警告
     * 3. 保存处方
     * 4. 异步调用 AI 审核（解释规则结果并补充建议）
     *
     * 关键规则：
     * - 确定性规则先执行，AI 只解释和补充
     * - AI 不得降低或覆盖确定性高风险
     * - AI 失败不影响处方创建，医生可手工确认
     */
    @Transactional
    public PrescriptionResponse createPrescription(PrescriptionCreateRequest request) {
        // 1. 校验就诊存在
        Encounter encounter = encounterRepository.findById(request.encounterId())
                .orElseThrow(PrescriptionErrorCode.ENCOUNTER_NOT_FOUND::toException);

        // 2. 校验医生权限（只能为本人接诊的就诊开立处方）
        Doctor doctor = validateDoctorOwnership(encounter.getDoctorId());

        // 3. 校验药品必须来自系统内固定虚构字典
        validateDrugsExist(request.items());

        // 4. 先执行确定性规则检查
        DeterministicCheckResult ruleResult = checkDeterministicRules(request.items(), encounter.getPatientId());

        // 5. 确定性高风险直接拒绝（过敏禁忌、严重相互作用）
        if ("CONTRAINDICATED".equals(ruleResult.riskLevel())) {
            log.warn("处方确定性规则校验未通过（禁忌）: encounterId={}, risk={}",
                    request.encounterId(), ruleResult.riskLevel());
            throw PrescriptionErrorCode.PRESCRIPTION_RULE_VIOLATION.toException();
        }

        // 6. 保存处方
        LocalDateTime now = LocalDateTime.now();
        Prescription prescription = Prescription.builder()
                .encounterId(request.encounterId())
                .patientId(encounter.getPatientId())
                .doctorId(doctor.getId())
                .status("DRAFT")
                .aiReviewStatus("NOT_REQUESTED")
                .createdAt(now)
                .updatedAt(now)
                .build();
        prescription = prescriptionRepository.save(prescription);

        // 7. 保存处方明细
        List<PrescriptionItem> items = new ArrayList<>();
        for (PrescriptionItemDTO itemDTO : request.items()) {
            PrescriptionItem item = PrescriptionItem.builder()
                    .prescriptionId(prescription.getId())
                    .drugCode(itemDTO.drugCode())
                    .drugName(itemDTO.drugName())
                    .dosage(itemDTO.dosage())
                    .dosageValue(itemDTO.dosageValue())
                    .frequency(itemDTO.frequency())
                    .duration(itemDTO.duration())
                    .quantity(itemDTO.quantity())
                    .instructions(itemDTO.instructions())
                    .createdAt(now)
                    .build();
            items.add(prescriptionItemRepository.save(item));
        }

        // 8. 调用 AI 审核（解释规则结果并补充建议）
        PrescriptionReview review = executeAIReview(prescription.getId(), request.items(),
                encounter.getPatientId(), ruleResult);

        log.info("创建处方: prescriptionId={}, encounterId={}, riskLevel={}",
                prescription.getId(), request.encounterId(), ruleResult.riskLevel());

        return toResponse(prescription, items, review);
    }

    // ============================================================
    // 确定性规则检查
    // ============================================================

    /**
     * 确定性规则检查
     *
     * 规则（来自 11_功能需求.md 第12.6节）：
     * - 过敏检查：药品禁忌表 condition_type=ALLERGY 与患者过敏史匹配
     * - 相互作用检查：药品相互作用规则表
     * - 剂量检查：药品剂量规则表
     * - 禁忌检查：药品禁忌表（疾病、孕期、年龄等）
     *
     * 风险等级：SAFE < LOW < MEDIUM < HIGH < CONTRAINDICATED
     */
    private DeterministicCheckResult checkDeterministicRules(List<PrescriptionItemDTO> items, Long patientId) {
        List<String> allergyWarnings = new ArrayList<>();
        List<String> interactionWarnings = new ArrayList<>();
        List<String> dosageWarnings = new ArrayList<>();
        List<String> contraindicationWarnings = new ArrayList<>();

        // 获取患者过敏史
        String patientAllergies = getPatientAllergies(patientId);

        // 收集所有药品编码
        Set<String> drugCodes = items.stream()
                .map(PrescriptionItemDTO::drugCode)
                .collect(Collectors.toSet());

        // 1. 过敏检查 + 禁忌检查
        for (PrescriptionItemDTO item : items) {
            List<DrugContraindication> contraindications =
                    drugContraindicationRepository.findByDrugCode(item.drugCode());

            for (DrugContraindication c : contraindications) {
                if ("ALLERGY".equals(c.getConditionType())) {
                    // 检查患者是否对该过敏原敏感
                    if (isAllergenMatched(c.getConditionValue(), patientAllergies)) {
                        allergyWarnings.add("药物过敏禁忌：" + item.drugName()
                                + "（" + c.getDescription() + "）");
                    }
                } else {
                    // 其他禁忌（疾病、孕期、年龄）- 基础版本记录警告
                    contraindicationWarnings.add(item.drugName() + "：" + c.getDescription());
                }
            }

            // 2. 剂量检查
            DrugDosageRule dosageRule = drugDosageRuleRepository.findByDrugCode(item.drugCode())
                    .orElse(null);
            if (dosageRule != null && item.dosageValue() != null) {
                if (item.dosageValue().compareTo(dosageRule.getMaxSingleDose()) > 0) {
                    dosageWarnings.add(item.drugName() + " 单次剂量 "
                            + item.dosageValue() + " 超过最大单次剂量 " + dosageRule.getMaxSingleDose());
                }
                if (item.dosageValue().compareTo(dosageRule.getMinDose()) < 0) {
                    dosageWarnings.add(item.drugName() + " 单次剂量 "
                            + item.dosageValue() + " 低于最小剂量 " + dosageRule.getMinDose());
                }
            }
        }

        // 3. 相互作用检查（两两检查）
        List<String> codeList = new ArrayList<>(drugCodes);
        for (int i = 0; i < codeList.size(); i++) {
            for (int j = i + 1; j < codeList.size(); j++) {
                List<DrugInteractionRule> interactions =
                        drugInteractionRuleRepository.findByDrugACodeAndDrugBCode(
                                codeList.get(i), codeList.get(j));
                for (DrugInteractionRule rule : interactions) {
                    String severity = rule.getSeverity();
                    if ("CONTRAINDICATED".equals(severity) || "HIGH".equals(severity)) {
                        interactionWarnings.add("严重药物相互作用：" + rule.getDescription());
                    } else if ("MEDIUM".equals(severity)) {
                        interactionWarnings.add("药物相互作用提示：" + rule.getDescription());
                    }
                }
                // 反向检查（drug_b → drug_a）
                List<DrugInteractionRule> reverseInteractions =
                        drugInteractionRuleRepository.findByDrugACodeAndDrugBCode(
                                codeList.get(j), codeList.get(i));
                for (DrugInteractionRule rule : reverseInteractions) {
                    String severity = rule.getSeverity();
                    if ("CONTRAINDICATED".equals(severity) || "HIGH".equals(severity)) {
                        interactionWarnings.add("严重药物相互作用：" + rule.getDescription());
                    } else if ("MEDIUM".equals(severity)) {
                        interactionWarnings.add("药物相互作用提示：" + rule.getDescription());
                    }
                }
            }
        }

        // 确定风险等级
        String riskLevel = determineRiskLevel(allergyWarnings, interactionWarnings,
                dosageWarnings, contraindicationWarnings);

        return new DeterministicCheckResult(
                riskLevel, allergyWarnings, interactionWarnings,
                dosageWarnings, contraindicationWarnings);
    }

    /**
     * 确定确定性规则风险等级
     *
     * 等级：SAFE < LOW < MEDIUM < HIGH < CONTRAINDICATED
     * - 过敏禁忌 = CONTRAINDICATED
     * - 严重相互作用 = HIGH
     * - 剂量异常 = MEDIUM
     * - 其他禁忌 = MEDIUM
     * - 无警告 = SAFE
     */
    private String determineRiskLevel(List<String> allergyWarnings, List<String> interactionWarnings,
                                      List<String> dosageWarnings, List<String> contraindicationWarnings) {
        if (!allergyWarnings.isEmpty()) {
            return "CONTRAINDICATED";
        }
        if (!interactionWarnings.isEmpty()) {
            // 检查是否有严重相互作用
            boolean hasSevere = interactionWarnings.stream()
                    .anyMatch(w -> w.contains("严重"));
            return hasSevere ? "HIGH" : "MEDIUM";
        }
        if (!dosageWarnings.isEmpty() || !contraindicationWarnings.isEmpty()) {
            return "MEDIUM";
        }
        return "SAFE";
    }

    /**
     * 检查患者过敏原是否匹配
     */
    private boolean isAllergenMatched(String conditionValue, String patientAllergies) {
        if (patientAllergies == null || patientAllergies.isBlank()) {
            return false;
        }
        // 简单匹配：患者过敏史包含禁忌条件值
        return patientAllergies.contains(conditionValue);
    }

    /**
     * 获取患者过敏史
     */
    private String getPatientAllergies(Long patientId) {
        return patientProfileRepository.findByPatientId(patientId)
                .map(PatientProfile::getAllergies)
                .orElse(null);
    }

    // ============================================================
    // AI 审核
    // ============================================================

    /**
     * 执行 AI 审核
     *
     * 规则：
     * - AI 审核状态独立于处方业务状态
     * - 确定性规则命中不得被 AI 输出降级或覆盖
     * - AI 失败不影响处方创建，医生可手工确认
     */
    private PrescriptionReview executeAIReview(Long prescriptionId, List<PrescriptionItemDTO> items,
                                               Long patientId, DeterministicCheckResult ruleResult) {
        LocalDateTime now = LocalDateTime.now();

        // 更新 AI 审核状态为 PENDING
        prescriptionRepository.updateAIReviewStatusIfCurrent(
                prescriptionId, "NOT_REQUESTED", "PENDING", now);

        try {
            // 构建 AI 请求（最小化上下文，不含患者隐私 ID）
            List<PrescriptionItemInfo> itemInfos = items.stream()
                    .map(i -> new PrescriptionItemInfo(
                            i.drugCode(), i.drugName(), i.dosage(),
                            i.frequency(), i.duration()))
                    .collect(Collectors.toList());

            String patientAllergies = getPatientAllergies(patientId);
            DeterministicRuleResult ruleDto = new DeterministicRuleResult(
                    ruleResult.riskLevel(),
                    ruleResult.allergyWarnings(),
                    ruleResult.interactionWarnings(),
                    ruleResult.dosageWarnings(),
                    ruleResult.contraindicationWarnings(),
                    "确定性规则风险等级：" + ruleResult.riskLevel());

            PrescriptionReviewAIRequest aiRequest = new PrescriptionReviewAIRequest(
                    itemInfos, patientAllergies, ruleDto);

            // 调用 AI 审核
            PrescriptionReviewAIResult aiResult = aiPrescriptionReviewService.review(aiRequest);

            // 确定性规则风险等级作为下限，AI 不得降级
            String finalRiskLevel = maxRiskLevel(ruleResult.riskLevel(), aiResult.riskLevel());

            // 确定审核状态：高风险为 FAILED，其他为 REVIEWED
            String reviewStatus = "CONTRAINDICATED".equals(finalRiskLevel)
                    || "HIGH".equals(finalRiskLevel) ? "FAILED" : "REVIEWED";

            PrescriptionReview review = PrescriptionReview.builder()
                    .prescriptionId(prescriptionId)
                    .reviewStatus(reviewStatus)
                    .riskLevel(finalRiskLevel)
                    .allergyWarnings(toJson(aiResult.allergyWarnings()))
                    .interactionWarnings(toJson(aiResult.interactionWarnings()))
                    .dosageWarnings(toJson(aiResult.dosageWarnings()))
                    .contraindicationWarnings(toJson(aiResult.contraindicationWarnings()))
                    .suggestions(aiResult.suggestions())
                    .summary(aiResult.summary())
                    .ruleCheckSummary(ruleDto.summary())
                    .reviewedAt(LocalDateTime.now())
                    .createdAt(now)
                    .updatedAt(LocalDateTime.now())
                    .build();
            review = prescriptionReviewRepository.save(review);

            // 更新处方 AI 审核状态
            prescriptionRepository.updateAIReviewStatusIfCurrent(
                    prescriptionId, "PENDING", reviewStatus, LocalDateTime.now());

            log.info("AI 处方审核完成: prescriptionId={}, reviewStatus={}, riskLevel={}",
                    prescriptionId, reviewStatus, finalRiskLevel);

            return review;
        } catch (Exception e) {
            log.error("AI 处方审核失败，不影响处方创建: prescriptionId={}", prescriptionId, e);

            // AI 失败记录，允许医生手工确认
            PrescriptionReview review = PrescriptionReview.builder()
                    .prescriptionId(prescriptionId)
                    .reviewStatus("FAILED")
                    .riskLevel(ruleResult.riskLevel())
                    .allergyWarnings(toJson(ruleResult.allergyWarnings()))
                    .interactionWarnings(toJson(ruleResult.interactionWarnings()))
                    .dosageWarnings(toJson(ruleResult.dosageWarnings()))
                    .contraindicationWarnings(toJson(ruleResult.contraindicationWarnings()))
                    .suggestions("AI 审核失败，请医生手工确认。" + e.getMessage())
                    .summary("AI 审核失败：" + e.getMessage())
                    .ruleCheckSummary("确定性规则风险等级：" + ruleResult.riskLevel()
                            + "（AI 审核失败，风险等级以确定性规则为准）")
                    .reviewedAt(LocalDateTime.now())
                    .createdAt(now)
                    .updatedAt(LocalDateTime.now())
                    .build();
            review = prescriptionReviewRepository.save(review);

            // 更新处方 AI 审核状态为 FAILED
            prescriptionRepository.updateAIReviewStatusIfCurrent(
                    prescriptionId, "PENDING", "FAILED", LocalDateTime.now());

            return review;
        }
    }

    /**
     * 取两个风险等级中较高的一个（AI 不得降级确定性规则风险）
     */
    private String maxRiskLevel(String ruleLevel, String aiLevel) {
        int ruleOrder = riskOrder(ruleLevel);
        int aiOrder = riskOrder(aiLevel);
        return ruleOrder >= aiOrder ? ruleLevel : aiLevel;
    }

    private int riskOrder(String level) {
        return switch (level == null ? "SAFE" : level) {
            case "SAFE" -> 0;
            case "LOW" -> 1;
            case "MEDIUM" -> 2;
            case "HIGH" -> 3;
            case "CONTRAINDICATED" -> 4;
            default -> 0;
        };
    }

    // ============================================================
    // 处方状态机：确认 DRAFT → CONFIRMED
    // ============================================================

    /**
     * 医生确认处方
     *
     * 规则：
     * - 仅 DRAFT 状态可确认
     * - 高风险需要二次确认（前端展示风险提示，后端不阻止确认但记录）
     */
    @Transactional
    @Auditable(action = "PRESCRIPTION_CONFIRM", targetType = "PRESCRIPTION")
    public PrescriptionResponse confirmPrescription(Long prescriptionId) {
        Prescription prescription = findAndValidatePrescription(prescriptionId);
        validatePrescriptionStatus(prescription, "DRAFT");

        LocalDateTime now = LocalDateTime.now();
        int updated = prescriptionRepository.updateStatusIfCurrent(
                prescriptionId, "DRAFT", "CONFIRMED", now);
        if (updated == 0) {
            throw PrescriptionErrorCode.PRESCRIPTION_STATUS_CONFLICT.toException();
        }

        prescription.setStatus("CONFIRMED");
        prescription.setConfirmedAt(now);
        prescription.setConfirmedBy(getCurrentUserId());
        prescription.setUpdatedAt(now);

        log.info("处方确认: prescriptionId={}, doctorId={}", prescriptionId, prescription.getConfirmedBy());

        return toResponse(prescription,
                prescriptionItemRepository.findByPrescriptionId(prescriptionId),
                getLatestReview(prescriptionId));
    }

    // ============================================================
    // 处方状态机：作废 CONFIRMED → VOIDED
    // ============================================================

    /**
     * 作废处方
     *
     * 规则：
     * - 仅 CONFIRMED 状态可作废
     * - 作废必须记录原因
     * - 已确认处方只能作废，不能物理删除
     */
    @Transactional
    @Auditable(action = "PRESCRIPTION_VOID", targetType = "PRESCRIPTION")
    public PrescriptionResponse voidPrescription(Long prescriptionId, PrescriptionVoidRequest request) {
        Prescription prescription = findAndValidatePrescription(prescriptionId);
        validatePrescriptionStatus(prescription, "CONFIRMED");

        LocalDateTime now = LocalDateTime.now();
        int updated = prescriptionRepository.updateStatusIfCurrent(
                prescriptionId, "CONFIRMED", "VOIDED", now);
        if (updated == 0) {
            throw PrescriptionErrorCode.PRESCRIPTION_STATUS_CONFLICT.toException();
        }

        prescription.setStatus("VOIDED");
        prescription.setVoidedAt(now);
        prescription.setVoidedBy(getCurrentUserId());
        prescription.setVoidedReason(request.reason());
        prescription.setUpdatedAt(now);

        log.info("处方作废: prescriptionId={}, reason={}", prescriptionId, request.reason());

        return toResponse(prescription,
                prescriptionItemRepository.findByPrescriptionId(prescriptionId),
                getLatestReview(prescriptionId));
    }

    // ============================================================
    // 查询方法
    // ============================================================

    /**
     * 获取处方详情
     */
    @Transactional(readOnly = true)
    public PrescriptionResponse getPrescriptionById(Long id) {
        Prescription prescription = prescriptionRepository.findById(id)
                .orElseThrow(PrescriptionErrorCode.PRESCRIPTION_NOT_FOUND::toException);
        checkPrescriptionAccess(prescription);
        return toResponse(prescription,
                prescriptionItemRepository.findByPrescriptionId(id),
                getLatestReview(id));
    }

    /**
     * 按就诊 ID 查询处方列表
     */
    @Transactional(readOnly = true)
    public List<PrescriptionResponse> getPrescriptionsByEncounter(Long encounterId) {
        return prescriptionRepository.findByEncounterId(encounterId).stream()
                .map(p -> toResponse(p,
                        prescriptionItemRepository.findByPrescriptionId(p.getId()),
                        getLatestReview(p.getId())))
                .collect(Collectors.toList());
    }

    /**
     * 按患者 ID 查询处方列表（分页）
     *
     * B-HW-02：患者端仅返回 CONFIRMED、VOIDED 处方，DRAFT 不对 Patients 暴露，
     * 避免草稿被误认为同步失败。医生/管理员调用时通过身份判断不过滤。
     */
    @Transactional(readOnly = true)
    public Page<PrescriptionResponse> getPrescriptionsByPatient(Long patientId, Pageable pageable) {
        checkPatientOwnership(patientId);
        boolean isPatient = SecurityUtils.isAuthenticated()
                && SecurityUtils.getCurrentUser().roles() != null
                && SecurityUtils.getCurrentUser().roles().contains("PATIENT");
        Page<Prescription> page = isPatient
                ? prescriptionRepository.findByPatientIdAndStatusIn(
                        patientId, PATIENT_VISIBLE_STATUSES, pageable)
                : prescriptionRepository.findByPatientId(patientId, pageable);
        return page.map(p -> toResponse(p,
                prescriptionItemRepository.findByPrescriptionId(p.getId()),
                getLatestReview(p.getId())));
    }

    /**
     * 按医生 ID 查询处方列表（分页）
     */
    @Transactional(readOnly = true)
    public Page<PrescriptionResponse> getPrescriptionsByDoctor(Long doctorId, Pageable pageable) {
        return prescriptionRepository.findByDoctorId(doctorId, pageable)
                .map(p -> toResponse(p,
                        prescriptionItemRepository.findByPrescriptionId(p.getId()),
                        getLatestReview(p.getId())));
    }

    // ============================================================
    // 完成就诊前置条件校验（供 EncounterService 调用）
    // ============================================================

    /**
     * 检查就诊是否存在未确认且未作废的处方
     *
     * 处方可不存在；存在处方时，其业务状态必须为 CONFIRMED 或 VOIDED。
     * DRAFT 状态处方阻塞就诊完成。
     */
    @Transactional(readOnly = true)
    public boolean hasPendingPrescriptions(Long encounterId) {
        return prescriptionRepository.countDraftByEncounterId(encounterId) > 0;
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private Prescription findAndValidatePrescription(Long prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(PrescriptionErrorCode.PRESCRIPTION_NOT_FOUND::toException);
        validateDoctorOwnership(prescription.getDoctorId());
        return prescription;
    }

    private void validatePrescriptionStatus(Prescription prescription, String expectedStatus) {
        if (!expectedStatus.equals(prescription.getStatus())) {
            throw PrescriptionErrorCode.PRESCRIPTION_STATUS_CONFLICT.toException();
        }
    }

    /**
     * 校验当前登录医生权限
     *
     * 医生只能处理本人接诊就诊的处方
     */
    private Doctor validateDoctorOwnership(Long prescriptionDoctorId) {
        if (!SecurityUtils.isAuthenticated()) {
            return doctorRepository.findById(prescriptionDoctorId)
                    .orElseThrow(PrescriptionErrorCode.DOCTOR_NOT_FOUND::toException);
        }
        AuthPrincipal currentUser = SecurityUtils.getCurrentUser();
        if (currentUser.roles() != null && currentUser.roles().contains("DOCTOR")) {
            Doctor currentDoctor = doctorRepository.findByUserId(currentUser.userId())
                    .orElseThrow(PrescriptionErrorCode.DOCTOR_NOT_FOUND::toException);
            if (!currentDoctor.getId().equals(prescriptionDoctorId)) {
                throw PrescriptionErrorCode.PRESCRIPTION_PERMISSION_DENIED.toException();
            }
            return currentDoctor;
        }
        // 管理员放行
        return doctorRepository.findById(prescriptionDoctorId)
                .orElseThrow(PrescriptionErrorCode.DOCTOR_NOT_FOUND::toException);
    }

    /**
     * 校验药品必须来自系统内固定虚构字典
     */
    private void validateDrugsExist(List<PrescriptionItemDTO> items) {
        for (PrescriptionItemDTO item : items) {
            if (!drugRepository.existsByCode(item.drugCode())) {
                throw PrescriptionErrorCode.DRUG_NOT_FOUND.toException();
            }
        }
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
                    .orElseThrow(PrescriptionErrorCode.PATIENT_NOT_FOUND::toException);
            if (!currentPatient.getId().equals(patientId)) {
                throw PrescriptionErrorCode.PRESCRIPTION_PERMISSION_DENIED.toException();
            }
        }
    }

    /**
     * 检查处方访问权限
     *
     * B-HW-02：PATIENT 角色仅可访问 CONFIRMED/VOIDED 处方，DRAFT 对患者不可见。
     */
    private void checkPrescriptionAccess(Prescription prescription) {
        if (!SecurityUtils.isAuthenticated()) {
            return;
        }
        AuthPrincipal currentUser = SecurityUtils.getCurrentUser();
        if (currentUser.roles() != null && currentUser.roles().contains("PATIENT")) {
            Patient currentPatient = patientRepository.findByUserId(currentUser.userId())
                    .orElseThrow(PrescriptionErrorCode.PATIENT_NOT_FOUND::toException);
            if (!currentPatient.getId().equals(prescription.getPatientId())) {
                throw PrescriptionErrorCode.PRESCRIPTION_PERMISSION_DENIED.toException();
            }
            // B-HW-02：患者不可访问 DRAFT 处方
            if (!PATIENT_VISIBLE_STATUSES.contains(prescription.getStatus())) {
                throw PrescriptionErrorCode.PRESCRIPTION_PERMISSION_DENIED.toException();
            }
        }
    }

    private Long getCurrentUserId() {
        if (!SecurityUtils.isAuthenticated()) {
            return null;
        }
        return SecurityUtils.getCurrentUser().userId();
    }

    private PrescriptionReview getLatestReview(Long prescriptionId) {
        return prescriptionReviewRepository
                .findFirstByPrescriptionIdOrderByCreatedAtDesc(prescriptionId)
                .orElse(null);
    }

    // ============================================================
    // 响应转换
    // ============================================================

    private PrescriptionResponse toResponse(Prescription prescription,
                                            List<PrescriptionItem> items,
                                            PrescriptionReview review) {
        List<PrescriptionItemResponse> itemResponses = items.stream()
                .map(i -> new PrescriptionItemResponse(
                        i.getId(), i.getDrugCode(), i.getDrugName(),
                        i.getDosage(), i.getDosageValue(), i.getFrequency(),
                        i.getDuration(), i.getQuantity(), i.getInstructions()))
                .collect(Collectors.toList());

        // B-HW-02：补齐患者端展示字段，避免前端依赖 encounter 上下文补字段
        Doctor doctor = doctorRepository.findById(prescription.getDoctorId()).orElse(null);
        String doctorName = doctor != null ? doctor.getName() : null;
        String departmentName = doctor != null
                ? departmentRepository.findById(doctor.getDepartmentId())
                        .map(Department::getName).orElse(null)
                : null;
        String patientName = patientRepository.findById(prescription.getPatientId())
                .map(Patient::getName).orElse(null);

        return new PrescriptionResponse(
                prescription.getId(),
                prescription.getEncounterId(),
                prescription.getPatientId(),
                prescription.getDoctorId(),
                doctorName,
                departmentName,
                patientName,
                prescription.getStatus(),
                prescription.getAiReviewStatus(),
                prescription.getCreatedAt(),
                prescription.getConfirmedAt(),
                prescription.getConfirmedBy(),
                prescription.getVoidedAt(),
                prescription.getVoidedBy(),
                prescription.getVoidedReason(),
                prescription.getUpdatedAt(),
                itemResponses,
                toReviewResponse(review));
    }

    private PrescriptionReviewResponse toReviewResponse(PrescriptionReview review) {
        if (review == null) {
            return null;
        }
        return new PrescriptionReviewResponse(
                review.getId(),
                review.getPrescriptionId(),
                review.getReviewStatus(),
                review.getRiskLevel(),
                fromJson(review.getAllergyWarnings()),
                fromJson(review.getInteractionWarnings()),
                fromJson(review.getDosageWarnings()),
                fromJson(review.getContraindicationWarnings()),
                review.getSuggestions(),
                review.getSummary(),
                review.getRuleCheckSummary(),
                review.getReviewedAt(),
                review.getCreatedAt());
    }

    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    /**
     * 确定性规则检查内部结果
     */
    private record DeterministicCheckResult(
            String riskLevel,
            List<String> allergyWarnings,
            List<String> interactionWarnings,
            List<String> dosageWarnings,
            List<String> contraindicationWarnings) {
    }
}
