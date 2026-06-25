package com.neusoft.cloudbrain.ai.application;

import com.neusoft.cloudbrain.ai.api.AIDiagnosisService;
import com.neusoft.cloudbrain.ai.dto.DiagnosisAIRequest;
import com.neusoft.cloudbrain.ai.dto.DiagnosisAIResult;
import com.neusoft.cloudbrain.ai.provider.AIProvider;
import com.neusoft.cloudbrain.ai.provider.AIProviderRequest;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 辅助诊断服务 Mock 实现
 *
 * 说明：此为阶段4 的最小可用实现，使用 MockAIProvider。
 * AI 能力集成角色后续将替换为真实 Provider + Prompt + Schema 校验实现。
 *
 * 诊断隔离原则（来自 12_业务流程与状态机.md 第7节）：
 * - AI 只能产生 AI_SUGGESTION，不得产生正式 FINAL + DOCTOR 记录
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIDiagnosisServiceImpl implements AIDiagnosisService {

    private final AIProvider aiProvider;

    @Override
    public DiagnosisAIResult analyze(DiagnosisAIRequest request) {
        try {
            String sanitizedInput = String.format(
                    "主诉: %s; 现病史: %s; 既往史: %s; 体格检查: %s; 年龄: %s; 性别: %s",
                    safe(request.chiefComplaint()),
                    safe(request.presentIllness()),
                    safe(request.pastHistory()),
                    safe(request.physicalExamination()),
                    safe(request.patientAgeRange()),
                    safe(request.patientGender()));

            AIProviderRequest providerRequest = new AIProviderRequest("diagnosis", sanitizedInput);
            aiProvider.generate(providerRequest);

            return buildMockResult(request.chiefComplaint());
        } catch (Exception e) {
            log.error("AI 辅助诊断调用失败: {}", e.getMessage(), e);
            throw new BusinessException("AI_DIAGNOSIS_FAILED", "AI 辅助诊断服务暂时不可用，请医生手工诊断", 504);
        }
    }

    /**
     * 基于主诉的确定性 Mock 候选诊断
     * 真实实现由 AI 集成角色提供
     */
    private DiagnosisAIResult buildMockResult(String chiefComplaint) {
        String complaint = chiefComplaint == null ? "" : chiefComplaint.toLowerCase();
        List<DiagnosisAIResult.PossibleDiagnosis> diagnoses;
        List<String> suggestedExams;

        if (containsAny(complaint, "胸痛", "胸闷", "心悸")) {
            diagnoses = List.of(
                    new DiagnosisAIResult.PossibleDiagnosis(
                            "I20.9", "心绞痛", "MEDIUM", "胸痛症状提示心肌缺血可能"),
                    new DiagnosisAIResult.PossibleDiagnosis(
                            "I10", "高血压病", "LOW", "需排查血压相关因素"));
            suggestedExams = List.of("心电图", "心肌酶谱", "血压监测");
        } else if (containsAny(complaint, "头痛", "头晕", "眩晕")) {
            diagnoses = List.of(
                    new DiagnosisAIResult.PossibleDiagnosis(
                            "G44.1", "血管性头痛", "MEDIUM", "头痛症状提示血管性可能"),
                    new DiagnosisAIResult.PossibleDiagnosis(
                            "H81.1", "良性阵发性位置性眩晕", "LOW", "眩晕症状提示前庭功能异常"));
            suggestedExams = List.of("头颅CT", "颈椎X光", "前庭功能检查");
        } else if (containsAny(complaint, "发热", "咳嗽", "咳痰")) {
            diagnoses = List.of(
                    new DiagnosisAIResult.PossibleDiagnosis(
                            "J06.9", "急性上呼吸道感染", "MEDIUM", "发热咳嗽提示呼吸道感染"),
                    new DiagnosisAIResult.PossibleDiagnosis(
                            "J18.9", "肺炎", "LOW", "需排查肺部感染"));
            suggestedExams = List.of("血常规", "胸部X光", "C反应蛋白");
        } else {
            diagnoses = List.of(new DiagnosisAIResult.PossibleDiagnosis(
                    "R69", "病因未明", "LOW", "信息不足，建议进一步检查"));
            suggestedExams = List.of("血常规", "尿常规");
        }

        return new DiagnosisAIResult(
                diagnoses,
                List.of("基于患者主诉和症状描述分析"),
                List.of("建议补充既往病史和用药史"),
                List.of("建议关注症状变化"),
                suggestedExams,
                "本结果由 AI 辅助生成，仅供医生参考，不能作为正式诊断依据");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
