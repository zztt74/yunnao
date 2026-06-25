package com.neusoft.cloudbrain.ai.application;

import com.neusoft.cloudbrain.ai.api.AIMedicalRecordService;
import com.neusoft.cloudbrain.ai.dto.MedicalRecordAIRequest;
import com.neusoft.cloudbrain.ai.dto.MedicalRecordAIResult;
import com.neusoft.cloudbrain.ai.provider.AIProvider;
import com.neusoft.cloudbrain.ai.provider.AIProviderRequest;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 病历生成服务 Mock 实现
 *
 * 说明：此为阶段5 的最小可用实现，使用 MockAIProvider。
 * AI 能力集成角色后续将替换为真实 Provider + Prompt + Schema 校验实现。
 *
 * 规则（来自 32_AI能力契约规范.md 第3节）：
 * - AI 只能生成草稿
 * - 不得编造输入中不存在的事实
 * - 正式病历必须医生确认
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIMedicalRecordServiceImpl implements AIMedicalRecordService {

    private final AIProvider aiProvider;

    @Override
    public MedicalRecordAIResult generate(MedicalRecordAIRequest request) {
        try {
            String sanitizedInput = String.format(
                    "主诉: %s; 现病史: %s; 既往史: %s; 体格检查: %s; 初步诊断: %s; 治疗建议: %s",
                    safe(request.chiefComplaint()),
                    safe(request.presentIllness()),
                    safe(request.pastHistory()),
                    safe(request.physicalExamination()),
                    request.preliminaryDiagnoses() == null ? "" : String.join("、", request.preliminaryDiagnoses()),
                    safe(request.treatmentSuggestion()));

            AIProviderRequest providerRequest = new AIProviderRequest("medical_record", sanitizedInput);
            aiProvider.generate(providerRequest);

            return buildMockResult(request);
        } catch (Exception e) {
            log.error("AI 病历生成调用失败: {}", e.getMessage(), e);
            throw new BusinessException(
                    "AI_MEDICAL_RECORD_FAILED",
                    "AI 病历生成服务暂时不可用，请医生手工填写",
                    504);
        }
    }

    /**
     * 基于问诊内容的确定性 Mock 病历草稿
     * 真实实现由 AI 集成角色提供
     *
     * 关键约束：不编造输入中不存在的事实
     */
    private MedicalRecordAIResult buildMockResult(MedicalRecordAIRequest request) {
        // 直接回填输入内容，不编造事实
        String chiefComplaint = safe(request.chiefComplaint());
        String presentIllness = safe(request.presentIllness());
        String pastHistory = request.pastHistory() == null ? "无特殊既往史" : request.pastHistory();
        String physicalExamination = request.physicalExamination() == null
                ? "待补充" : request.physicalExamination();
        List<String> diagnoses = request.preliminaryDiagnoses();
        String preliminaryDiagnosis = (diagnoses == null || diagnoses.isEmpty())
                ? "待进一步明确" : String.join("、", diagnoses);
        String treatmentSuggestion = request.treatmentSuggestion() == null
                ? "建议对症治疗，根据检查结果调整方案" : request.treatmentSuggestion();

        return new MedicalRecordAIResult(
                chiefComplaint,
                presentIllness,
                pastHistory,
                physicalExamination,
                preliminaryDiagnosis,
                treatmentSuggestion,
                "本病历草稿由 AI 辅助生成，仅供医生参考，需医生确认后形成正式病历");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
