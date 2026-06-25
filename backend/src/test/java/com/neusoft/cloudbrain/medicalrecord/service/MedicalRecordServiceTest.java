package com.neusoft.cloudbrain.medicalrecord.service;

import com.neusoft.cloudbrain.ai.api.AIMedicalRecordService;
import com.neusoft.cloudbrain.ai.dto.MedicalRecordAIRequest;
import com.neusoft.cloudbrain.ai.dto.MedicalRecordAIResult;
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
import com.neusoft.cloudbrain.medicalrecord.repository.MedicalRecordRepository;
import com.neusoft.cloudbrain.patient.repository.PatientRepository;
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
 * MedicalRecordService 单元测试
 *
 * 覆盖文档 11.2 必测场景：
 * - 创建医生手工草稿
 * - AI 生成病历草稿
 * - 更新病历（仅 DRAFT/AI_GENERATED）
 * - 确认病历（DRAFT/AI_GENERATED → CONFIRMED）
 * - 关键约束：AI 只能生成 DRAFT/AI_GENERATED
 * - 关键约束：CONFIRMED 必须由医生完成
 * - 关键约束：每个 Encounter 只能有一条 CONFIRMED 记录
 * - 关键约束：CONFIRMED 后不允许修改
 * - 完成就诊前置条件校验
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MedicalRecordService - 病历服务测试")
class MedicalRecordServiceTest {

    @Mock
    private MedicalRecordRepository medicalRecordRepository;

    @Mock
    private EncounterRepository encounterRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private AIMedicalRecordService aiMedicalRecordService;

    @InjectMocks
    private MedicalRecordService medicalRecordService;

    private Encounter testEncounter;
    private Doctor testDoctor;
    private MedicalRecord testDraftRecord;
    private MedicalRecord testAIRecord;
    private MedicalRecord testConfirmedRecord;

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

        testDraftRecord = MedicalRecord.builder()
                .id(1L)
                .encounterId(1L)
                .patientId(1L)
                .doctorId(1L)
                .content("医生手工草稿内容")
                .source("DOCTOR")
                .status("DRAFT")
                .createdBy(20L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        testAIRecord = MedicalRecord.builder()
                .id(2L)
                .encounterId(1L)
                .patientId(1L)
                .doctorId(1L)
                .content("AI 生成草稿内容")
                .source("AI")
                .status("AI_GENERATED")
                .createdBy(20L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        testConfirmedRecord = MedicalRecord.builder()
                .id(3L)
                .encounterId(1L)
                .patientId(1L)
                .doctorId(1L)
                .content("已确认病历内容")
                .source("DOCTOR")
                .status("CONFIRMED")
                .createdBy(20L)
                .confirmedBy(20L)
                .confirmedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ============================================================
    // 创建医生手工草稿
    // ============================================================

    @Test
    @DisplayName("创建草稿 - 正常创建医生手工草稿")
    void createDraft_shouldCreateDraftRecord() {
        MedicalRecordCreateRequest request = new MedicalRecordCreateRequest(1L, "病历内容");

        when(encounterRepository.findById(1L)).thenReturn(Optional.of(testEncounter));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(medicalRecordRepository.save(any(MedicalRecord.class))).thenAnswer(invocation -> {
            MedicalRecord record = invocation.getArgument(0);
            record.setId(1L);
            return record;
        });

        MedicalRecordResponse response = medicalRecordService.createDraft(request);

        assertThat(response).isNotNull();
        assertThat(response.source()).isEqualTo("DOCTOR");
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.content()).isEqualTo("病历内容");
    }

    @Test
    @DisplayName("创建草稿 - 就诊不存在时抛出 BusinessException(404)")
    void createDraft_shouldThrowWhenEncounterNotFound() {
        MedicalRecordCreateRequest request = new MedicalRecordCreateRequest(99L, "病历内容");

        when(encounterRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> medicalRecordService.createDraft(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 404);
    }

    // ============================================================
    // AI 生成病历草稿
    // ============================================================

    @Test
    @DisplayName("AI 生成 - 正常生成 AI 草稿")
    void generateByAI_shouldCreateAIGeneratedRecord() {
        MedicalRecordGenerateRequest request = new MedicalRecordGenerateRequest(
                1L, "头痛 3 天", "持续性胀痛", "无特殊", "神经系统无异常",
                List.of("偏头痛"), "对症治疗");

        when(encounterRepository.findById(1L)).thenReturn(Optional.of(testEncounter));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));

        MedicalRecordAIResult aiResult = new MedicalRecordAIResult(
                "头痛 3 天", "持续性胀痛", "无特殊", "神经系统无异常",
                "偏头痛", "对症治疗", "AI 仅供参考");
        when(aiMedicalRecordService.generate(any(MedicalRecordAIRequest.class))).thenReturn(aiResult);
        when(medicalRecordRepository.save(any(MedicalRecord.class))).thenAnswer(invocation -> {
            MedicalRecord record = invocation.getArgument(0);
            record.setId(2L);
            return record;
        });

        MedicalRecordResponse response = medicalRecordService.generateByAI(request);

        assertThat(response).isNotNull();
        assertThat(response.source()).isEqualTo("AI");
        assertThat(response.status()).isEqualTo("AI_GENERATED");
        assertThat(response.content()).contains("头痛 3 天");
        verify(aiMedicalRecordService).generate(any(MedicalRecordAIRequest.class));
    }

    @Test
    @DisplayName("AI 生成 - AI 失败时抛出 BusinessException")
    void generateByAI_shouldThrowWhenAIFails() {
        MedicalRecordGenerateRequest request = new MedicalRecordGenerateRequest(
                1L, "头痛 3 天", "持续性胀痛", null, null, null, null);

        when(encounterRepository.findById(1L)).thenReturn(Optional.of(testEncounter));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(aiMedicalRecordService.generate(any(MedicalRecordAIRequest.class)))
                .thenThrow(new BusinessException("AI_GENERATION_FAILED", "AI 生成失败", 500));

        assertThatThrownBy(() -> medicalRecordService.generateByAI(request))
                .isInstanceOf(BusinessException.class);
        verify(medicalRecordRepository, never()).save(any(MedicalRecord.class));
    }

    // ============================================================
    // 更新病历（仅 DRAFT 和 AI_GENERATED 状态可更新）
    // ============================================================

    @Test
    @DisplayName("更新病历 - DRAFT 状态可更新")
    void updateRecord_shouldUpdateWhenDraft() {
        MedicalRecordUpdateRequest request = new MedicalRecordUpdateRequest("更新后内容");

        when(medicalRecordRepository.findById(1L)).thenReturn(Optional.of(testDraftRecord));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(medicalRecordRepository.save(any(MedicalRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MedicalRecordResponse response = medicalRecordService.updateRecord(1L, request);

        assertThat(response.content()).isEqualTo("更新后内容");
    }

    @Test
    @DisplayName("更新病历 - AI_GENERATED 状态可更新")
    void updateRecord_shouldUpdateWhenAIGenerated() {
        MedicalRecordUpdateRequest request = new MedicalRecordUpdateRequest("更新后内容");

        when(medicalRecordRepository.findById(2L)).thenReturn(Optional.of(testAIRecord));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(medicalRecordRepository.save(any(MedicalRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MedicalRecordResponse response = medicalRecordService.updateRecord(2L, request);

        assertThat(response.content()).isEqualTo("更新后内容");
    }

    @Test
    @DisplayName("更新病历 - CONFIRMED 状态不允许修改")
    void updateRecord_shouldThrowWhenConfirmed() {
        MedicalRecordUpdateRequest request = new MedicalRecordUpdateRequest("尝试修改");

        when(medicalRecordRepository.findById(3L)).thenReturn(Optional.of(testConfirmedRecord));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));

        assertThatThrownBy(() -> medicalRecordService.updateRecord(3L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    // ============================================================
    // 确认病历 DRAFT/AI_GENERATED → CONFIRMED
    // ============================================================

    @Test
    @DisplayName("确认病历 - DRAFT → CONFIRMED")
    void confirmRecord_shouldTransitionFromDraftToConfirmed() {
        when(medicalRecordRepository.findById(1L)).thenReturn(Optional.of(testDraftRecord));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(medicalRecordRepository.existsByEncounterIdAndStatus(1L, "CONFIRMED")).thenReturn(false);
        when(medicalRecordRepository.updateStatusIfCurrent(
                eq(1L), eq("DRAFT"), eq("CONFIRMED"), any(), any(), any())).thenReturn(1);

        MedicalRecordResponse response = medicalRecordService.confirmRecord(1L);

        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.confirmedBy()).isNotNull();
        assertThat(response.confirmedAt()).isNotNull();
    }

    @Test
    @DisplayName("确认病历 - AI_GENERATED → CONFIRMED")
    void confirmRecord_shouldTransitionFromAIGeneratedToConfirmed() {
        when(medicalRecordRepository.findById(2L)).thenReturn(Optional.of(testAIRecord));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(medicalRecordRepository.existsByEncounterIdAndStatus(1L, "CONFIRMED")).thenReturn(false);
        when(medicalRecordRepository.updateStatusIfCurrent(
                eq(2L), eq("AI_GENERATED"), eq("CONFIRMED"), any(), any(), any())).thenReturn(1);

        MedicalRecordResponse response = medicalRecordService.confirmRecord(2L);

        assertThat(response.status()).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("确认病历 - 已存在 CONFIRMED 记录时抛出 BusinessException(409)")
    void confirmRecord_shouldThrowWhenConfirmedAlreadyExists() {
        when(medicalRecordRepository.findById(1L)).thenReturn(Optional.of(testDraftRecord));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));
        when(medicalRecordRepository.existsByEncounterIdAndStatus(1L, "CONFIRMED")).thenReturn(true);

        assertThatThrownBy(() -> medicalRecordService.confirmRecord(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    @Test
    @DisplayName("确认病历 - CONFIRMED 状态不可再次确认")
    void confirmRecord_shouldThrowWhenAlreadyConfirmed() {
        when(medicalRecordRepository.findById(3L)).thenReturn(Optional.of(testConfirmedRecord));
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(testDoctor));

        assertThatThrownBy(() -> medicalRecordService.confirmRecord(3L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    // ============================================================
    // 查询方法
    // ============================================================

    @Test
    @DisplayName("查询病历 - 病历不存在时抛出 BusinessException(404)")
    void getRecordById_shouldThrowWhenNotFound() {
        when(medicalRecordRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> medicalRecordService.getRecordById(99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 404);
    }

    @Test
    @DisplayName("查询已确认病历 - 存在时返回")
    void getConfirmedRecordByEncounter_shouldReturnWhenExists() {
        when(medicalRecordRepository.findOneByEncounterIdAndStatus(1L, "CONFIRMED"))
                .thenReturn(Optional.of(testConfirmedRecord));

        MedicalRecordResponse response = medicalRecordService.getConfirmedRecordByEncounter(1L);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("查询已确认病历 - 不存在时抛出 BusinessException(409)")
    void getConfirmedRecordByEncounter_shouldThrowWhenNotConfirmed() {
        when(medicalRecordRepository.findOneByEncounterIdAndStatus(1L, "CONFIRMED"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> medicalRecordService.getConfirmedRecordByEncounter(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    // ============================================================
    // 完成就诊前置条件校验
    // ============================================================

    @Test
    @DisplayName("完成就诊校验 - 存在已确认病历时返回 true")
    void hasConfirmedRecord_shouldReturnTrueWhenConfirmedExists() {
        when(medicalRecordRepository.existsByEncounterIdAndStatus(1L, "CONFIRMED")).thenReturn(true);

        boolean result = medicalRecordService.hasConfirmedRecord(1L);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("完成就诊校验 - 无已确认病历时返回 false")
    void hasConfirmedRecord_shouldReturnFalseWhenNoConfirmed() {
        when(medicalRecordRepository.existsByEncounterIdAndStatus(1L, "CONFIRMED")).thenReturn(false);

        boolean result = medicalRecordService.hasConfirmedRecord(1L);

        assertThat(result).isFalse();
    }
}
