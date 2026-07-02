package com.neusoft.cloudbrain.prescription.service;

import com.neusoft.cloudbrain.ai.api.AIPrescriptionReviewService;
import com.neusoft.cloudbrain.ai.dto.PrescriptionReviewAIRequest;
import com.neusoft.cloudbrain.ai.dto.PrescriptionReviewAIResult;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.neusoft.cloudbrain.department.repository.DepartmentRepository;
import com.neusoft.cloudbrain.doctor.entity.Doctor;
import com.neusoft.cloudbrain.doctor.repository.DoctorRepository;
import com.neusoft.cloudbrain.drug.entity.DrugContraindication;
import com.neusoft.cloudbrain.drug.entity.DrugDosageRule;
import com.neusoft.cloudbrain.drug.entity.DrugInteractionRule;
import com.neusoft.cloudbrain.drug.repository.DrugContraindicationRepository;
import com.neusoft.cloudbrain.drug.repository.DrugDosageRuleRepository;
import com.neusoft.cloudbrain.drug.repository.DrugInteractionRuleRepository;
import com.neusoft.cloudbrain.drug.repository.DrugRepository;
import com.neusoft.cloudbrain.encounter.entity.Encounter;
import com.neusoft.cloudbrain.encounter.repository.EncounterRepository;
import com.neusoft.cloudbrain.patient.entity.PatientProfile;
import com.neusoft.cloudbrain.patient.repository.PatientProfileRepository;
import com.neusoft.cloudbrain.patient.repository.PatientRepository;
import com.neusoft.cloudbrain.prescription.dto.PrescriptionCreateRequest;
import com.neusoft.cloudbrain.prescription.dto.PrescriptionItemDTO;
import com.neusoft.cloudbrain.prescription.dto.PrescriptionResponse;
import com.neusoft.cloudbrain.prescription.dto.PrescriptionVoidRequest;
import com.neusoft.cloudbrain.prescription.entity.Prescription;
import com.neusoft.cloudbrain.prescription.entity.PrescriptionItem;
import com.neusoft.cloudbrain.prescription.entity.PrescriptionReview;
import com.neusoft.cloudbrain.prescription.repository.PrescriptionItemRepository;
import com.neusoft.cloudbrain.prescription.repository.PrescriptionRepository;
import com.neusoft.cloudbrain.prescription.repository.PrescriptionReviewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PrescriptionService 单元测试
 *
 * 覆盖文档 11.2 必测场景：
 * - 创建处方（确定性规则检查 + AI 审核）
 * - 确定性规则高风险直接拒绝
 * - AI 失败不影响处方创建
 * - 状态机流转（DRAFT → CONFIRMED → VOIDED）
 * - 状态冲突
 * - 完成就诊前置条件校验
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PrescriptionService - 处方服务测试")
class PrescriptionServiceTest {

    @Mock
    private PrescriptionRepository prescriptionRepository;
    @Mock
    private PrescriptionItemRepository prescriptionItemRepository;
    @Mock
    private PrescriptionReviewRepository prescriptionReviewRepository;
    @Mock
    private EncounterRepository encounterRepository;
    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private PatientRepository patientRepository;
    @Mock
    private PatientProfileRepository patientProfileRepository;
    @Mock
    private DrugRepository drugRepository;
    @Mock
    private DrugInteractionRuleRepository drugInteractionRuleRepository;
    @Mock
    private DrugDosageRuleRepository drugDosageRuleRepository;
    @Mock
    private DrugContraindicationRepository drugContraindicationRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private AIPrescriptionReviewService aiPrescriptionReviewService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private PrescriptionService prescriptionService;

    private Encounter testEncounter;
    private Doctor testDoctor;
    private Prescription testPrescription;
    private PrescriptionItemDTO itemDTO;

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

        testPrescription = Prescription.builder()
                .id(1L)
                .encounterId(1L)
                .patientId(1L)
                .doctorId(1L)
                .status("DRAFT")
                .aiReviewStatus("NOT_REQUESTED")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        itemDTO = new PrescriptionItemDTO(
                "DRG-001", "阿莫西林胶囊", "0.5g", new BigDecimal("0.5"),
                "每日三次", 7, new BigDecimal("21"), "饭后服用");
    }

    // ============================================================
    // 创建处方
    // ============================================================

    @Test
    @DisplayName("创建处方 - 正常创建并调用 AI 审核")
    void createPrescription_shouldCreateAndCallAI() {
        PrescriptionCreateRequest request = new PrescriptionCreateRequest(1L, List.of(itemDTO));

        when(encounterRepository.findById(1L)).thenReturn(Optional.of(testEncounter));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(drugRepository.existsByCode("DRG-001")).thenReturn(true);
        when(patientProfileRepository.findByPatientId(1L)).thenReturn(Optional.empty());
        when(drugContraindicationRepository.findByDrugCode("DRG-001"))
                .thenReturn(Collections.emptyList());
        when(drugDosageRuleRepository.findByDrugCode("DRG-001")).thenReturn(Optional.empty());
        when(prescriptionRepository.save(any(Prescription.class))).thenAnswer(invocation -> {
            Prescription p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });
        when(prescriptionItemRepository.save(any(PrescriptionItem.class))).thenAnswer(invocation -> {
            PrescriptionItem i = invocation.getArgument(0);
            i.setId(1L);
            return i;
        });
        when(prescriptionRepository.updateAIReviewStatusIfCurrent(anyLong(), any(), any(), any()))
                .thenReturn(1);
        PrescriptionReviewAIResult aiResult = new PrescriptionReviewAIResult(
                "SAFE", Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(),
                "处方用药基本合理", "AI 仅供参考");
        when(aiPrescriptionReviewService.review(any(PrescriptionReviewAIRequest.class)))
                .thenReturn(aiResult);
        when(prescriptionReviewRepository.save(any(PrescriptionReview.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PrescriptionResponse response = prescriptionService.createPrescription(request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.items()).hasSize(1);
        verify(aiPrescriptionReviewService).review(any(PrescriptionReviewAIRequest.class));
    }

    @Test
    @DisplayName("创建处方 - AI 失败不影响处方创建")
    void createPrescription_shouldNotFailWhenAIFails() {
        PrescriptionCreateRequest request = new PrescriptionCreateRequest(1L, List.of(itemDTO));

        when(encounterRepository.findById(1L)).thenReturn(Optional.of(testEncounter));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(drugRepository.existsByCode("DRG-001")).thenReturn(true);
        when(patientProfileRepository.findByPatientId(1L)).thenReturn(Optional.empty());
        when(drugContraindicationRepository.findByDrugCode("DRG-001"))
                .thenReturn(Collections.emptyList());
        when(drugDosageRuleRepository.findByDrugCode("DRG-001")).thenReturn(Optional.empty());
        when(prescriptionRepository.save(any(Prescription.class))).thenAnswer(invocation -> {
            Prescription p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });
        when(prescriptionItemRepository.save(any(PrescriptionItem.class))).thenAnswer(invocation -> {
            PrescriptionItem i = invocation.getArgument(0);
            i.setId(1L);
            return i;
        });
        when(prescriptionRepository.updateAIReviewStatusIfCurrent(anyLong(), any(), any(), any()))
                .thenReturn(1);
        when(aiPrescriptionReviewService.review(any(PrescriptionReviewAIRequest.class)))
                .thenThrow(new BusinessException("AI_FAILED", "AI 不可用", 504));
        when(prescriptionReviewRepository.save(any(PrescriptionReview.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PrescriptionResponse response = prescriptionService.createPrescription(request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("DRAFT");
        // 业务正常返回，不抛异常
        verify(prescriptionRepository).save(any(Prescription.class));
    }

    @Test
    @DisplayName("创建处方 - 过敏禁忌直接拒绝")
    void createPrescription_shouldRejectWhenAllergyContraindication() {
        PrescriptionCreateRequest request = new PrescriptionCreateRequest(1L, List.of(itemDTO));

        PatientProfile profile = new PatientProfile();
        profile.setAllergies("青霉素");
        DrugContraindication allergy = DrugContraindication.builder()
                .drugCode("DRG-001")
                .conditionType("ALLERGY")
                .conditionValue("青霉素")
                .description("青霉素过敏")
                .build();

        when(encounterRepository.findById(1L)).thenReturn(Optional.of(testEncounter));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(drugRepository.existsByCode("DRG-001")).thenReturn(true);
        when(patientProfileRepository.findByPatientId(1L)).thenReturn(Optional.of(profile));
        when(drugContraindicationRepository.findByDrugCode("DRG-001"))
                .thenReturn(List.of(allergy));

        assertThatThrownBy(() -> prescriptionService.createPrescription(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    @Test
    @DisplayName("创建处方 - 药品不存在于字典时抛出 BusinessException(404)")
    void createPrescription_shouldThrowWhenDrugNotFound() {
        PrescriptionCreateRequest request = new PrescriptionCreateRequest(1L, List.of(itemDTO));

        when(encounterRepository.findById(1L)).thenReturn(Optional.of(testEncounter));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(drugRepository.existsByCode("DRG-001")).thenReturn(false);

        assertThatThrownBy(() -> prescriptionService.createPrescription(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 404);
    }

    @Test
    @DisplayName("创建处方 - 就诊不存在时抛出 BusinessException(404)")
    void createPrescription_shouldThrowWhenEncounterNotFound() {
        PrescriptionCreateRequest request = new PrescriptionCreateRequest(99L, List.of(itemDTO));

        when(encounterRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> prescriptionService.createPrescription(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 404);
    }

    // ============================================================
    // 状态机：确认 DRAFT → CONFIRMED
    // ============================================================

    @Test
    @DisplayName("确认处方 - DRAFT → CONFIRMED")
    void confirmPrescription_shouldTransitionToConfirmed() {
        when(prescriptionRepository.findById(1L)).thenReturn(Optional.of(testPrescription));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(prescriptionRepository.updateStatusIfCurrent(
                eq(1L), eq("DRAFT"), eq("CONFIRMED"), any())).thenReturn(1);
        when(prescriptionItemRepository.findByPrescriptionId(1L))
                .thenReturn(Collections.emptyList());
        when(prescriptionReviewRepository.findFirstByPrescriptionIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());

        PrescriptionResponse response = prescriptionService.confirmPrescription(1L);

        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.confirmedAt()).isNotNull();
    }

    @Test
    @DisplayName("确认处方 - 非 DRAFT 状态时抛出 BusinessException(409)")
    void confirmPrescription_shouldThrowWhenStatusNotDraft() {
        testPrescription.setStatus("CONFIRMED");
        when(prescriptionRepository.findById(1L)).thenReturn(Optional.of(testPrescription));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));

        assertThatThrownBy(() -> prescriptionService.confirmPrescription(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    @Test
    @DisplayName("确认处方 - 并发冲突时抛出 BusinessException(409)")
    void confirmPrescription_shouldThrowWhenConcurrentConflict() {
        when(prescriptionRepository.findById(1L)).thenReturn(Optional.of(testPrescription));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(prescriptionRepository.updateStatusIfCurrent(
                eq(1L), eq("DRAFT"), eq("CONFIRMED"), any())).thenReturn(0);

        assertThatThrownBy(() -> prescriptionService.confirmPrescription(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    // ============================================================
    // 状态机：作废 CONFIRMED → VOIDED
    // ============================================================

    @Test
    @DisplayName("作废处方 - CONFIRMED → VOIDED 需记录原因")
    void voidPrescription_shouldTransitionToVoidedWithReason() {
        testPrescription.setStatus("CONFIRMED");
        PrescriptionVoidRequest request = new PrescriptionVoidRequest("开错药品");

        when(prescriptionRepository.findById(1L)).thenReturn(Optional.of(testPrescription));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(prescriptionRepository.updateStatusIfCurrent(
                eq(1L), eq("CONFIRMED"), eq("VOIDED"), any())).thenReturn(1);
        when(prescriptionItemRepository.findByPrescriptionId(1L))
                .thenReturn(Collections.emptyList());
        when(prescriptionReviewRepository.findFirstByPrescriptionIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());

        PrescriptionResponse response = prescriptionService.voidPrescription(1L, request);

        assertThat(response.status()).isEqualTo("VOIDED");
        assertThat(response.voidedReason()).isEqualTo("开错药品");
        assertThat(response.voidedAt()).isNotNull();
    }

    @Test
    @DisplayName("作废处方 - DRAFT 状态不能作废")
    void voidPrescription_shouldThrowWhenStatusIsDraft() {
        PrescriptionVoidRequest request = new PrescriptionVoidRequest("作废");

        when(prescriptionRepository.findById(1L)).thenReturn(Optional.of(testPrescription));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));

        assertThatThrownBy(() -> prescriptionService.voidPrescription(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    // ============================================================
    // 完成就诊前置条件校验
    // ============================================================

    @Test
    @DisplayName("完成就诊校验 - 存在 DRAFT 处方时返回 true")
    void hasPendingPrescriptions_shouldReturnTrueWhenDraftExists() {
        when(prescriptionRepository.countDraftByEncounterId(1L)).thenReturn(1L);

        boolean result = prescriptionService.hasPendingPrescriptions(1L);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("完成就诊校验 - 无 DRAFT 处方时返回 false")
    void hasPendingPrescriptions_shouldReturnFalseWhenNoDraft() {
        when(prescriptionRepository.countDraftByEncounterId(1L)).thenReturn(0L);

        boolean result = prescriptionService.hasPendingPrescriptions(1L);

        assertThat(result).isFalse();
    }

    // ============================================================
    // 处方不存在
    // ============================================================

    @Test
    @DisplayName("处方不存在时抛出 BusinessException(404)")
    void getPrescriptionById_shouldThrowWhenNotFound() {
        when(prescriptionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> prescriptionService.getPrescriptionById(99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 404);
    }

    // ============================================================
    // 获取处方详情 - 正常返回
    // ============================================================

    @Test
    @DisplayName("获取处方详情 - 存在时返回处方及明细")
    void getPrescriptionById_shouldReturnWhenFound() {
        when(prescriptionRepository.findById(1L)).thenReturn(Optional.of(testPrescription));
        when(prescriptionItemRepository.findByPrescriptionId(1L))
                .thenReturn(Collections.emptyList());
        when(prescriptionReviewRepository.findFirstByPrescriptionIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());

        PrescriptionResponse response = prescriptionService.getPrescriptionById(1L);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo("DRAFT");
    }

    // ============================================================
    // 按就诊 ID 查询处方列表
    // ============================================================

    @Test
    @DisplayName("按就诊查询处方列表 - 返回处方列表")
    void getPrescriptionsByEncounter_shouldReturnList() {
        when(prescriptionRepository.findByEncounterId(1L))
                .thenReturn(List.of(testPrescription));
        when(prescriptionItemRepository.findByPrescriptionId(1L))
                .thenReturn(Collections.emptyList());
        when(prescriptionReviewRepository.findFirstByPrescriptionIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());

        List<PrescriptionResponse> result = prescriptionService.getPrescriptionsByEncounter(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("按就诊查询处方列表 - 无数据时返回空列表")
    void getPrescriptionsByEncounter_shouldReturnEmptyList() {
        when(prescriptionRepository.findByEncounterId(99L))
                .thenReturn(Collections.emptyList());

        List<PrescriptionResponse> result = prescriptionService.getPrescriptionsByEncounter(99L);

        assertThat(result).isEmpty();
    }

    // ============================================================
    // 按患者 ID 查询处方列表（分页）
    // ============================================================

    @Test
    @DisplayName("按患者查询处方列表 - 返回分页结果")
    void getPrescriptionsByPatient_shouldReturnPagedResult() {
        PageImpl<Prescription> page = new PageImpl<>(List.of(testPrescription), PageRequest.of(0, 20), 1);
        when(prescriptionRepository.findByPatientId(eq(1L), any()))
                .thenReturn(page);
        when(prescriptionItemRepository.findByPrescriptionId(1L))
                .thenReturn(Collections.emptyList());
        when(prescriptionReviewRepository.findFirstByPrescriptionIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());

        Page<PrescriptionResponse> result =
                prescriptionService.getPrescriptionsByPatient(1L, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("按患者查询处方列表 - 无数据时返回空页")
    void getPrescriptionsByPatient_shouldReturnEmptyPage() {
        PageImpl<Prescription> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);
        when(prescriptionRepository.findByPatientId(eq(1L), any())).thenReturn(emptyPage);

        Page<PrescriptionResponse> result =
                prescriptionService.getPrescriptionsByPatient(1L, PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    // ============================================================
    // 按医生 ID 查询处方列表（分页）
    // ============================================================

    @Test
    @DisplayName("按医生查询处方列表 - 返回分页结果")
    void getPrescriptionsByDoctor_shouldReturnPagedResult() {
        PageImpl<Prescription> page = new PageImpl<>(List.of(testPrescription), PageRequest.of(0, 20), 1);
        when(prescriptionRepository.findByDoctorId(eq(1L), any()))
                .thenReturn(page);
        when(prescriptionItemRepository.findByPrescriptionId(1L))
                .thenReturn(Collections.emptyList());
        when(prescriptionReviewRepository.findFirstByPrescriptionIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());

        Page<PrescriptionResponse> result =
                prescriptionService.getPrescriptionsByDoctor(1L, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("按医生查询处方列表 - 无数据时返回空页")
    void getPrescriptionsByDoctor_shouldReturnEmptyPage() {
        PageImpl<Prescription> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);
        when(prescriptionRepository.findByDoctorId(eq(1L), any())).thenReturn(emptyPage);

        Page<PrescriptionResponse> result =
                prescriptionService.getPrescriptionsByDoctor(1L, PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
    }
}
