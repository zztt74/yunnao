package com.neusoft.cloudbrain.ai.application;

import com.neusoft.cloudbrain.ai.api.AITriageService;
import com.neusoft.cloudbrain.ai.dto.TriageAIRequest;
import com.neusoft.cloudbrain.ai.dto.TriageAIResult;
import com.neusoft.cloudbrain.ai.provider.AIProvider;
import com.neusoft.cloudbrain.ai.provider.AIProviderRequest;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 智能分诊服务 Mock 实现
 *
 * 说明：此为阶段4 的最小可用实现，使用 MockAIProvider。
 * AI 能力集成角色后续将替换为真实 Provider + Prompt + Schema 校验实现。
 *
 * 降级策略（来自 12_业务流程与状态机.md 第14节）：
 * - AI 超时或错误时抛出 BusinessException
 * - 业务模块捕获后进入手动流程
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AITriageServiceImpl implements AITriageService {

    private final AIProvider aiProvider;

    @Override
    public TriageAIResult analyze(TriageAIRequest request) {
        try {
            // 构造最小化输入，不包含患者隐私 ID
            String sanitizedInput = String.format(
                    "主诉: %s; 持续时间: %s; 补充: %s; 年龄区间: %s; 性别: %s",
                    safe(request.chiefComplaint()),
                    safe(request.duration()),
                    safe(request.supplement()),
                    safe(request.ageRange()),
                    safe(request.gender()));

            AIProviderRequest providerRequest = new AIProviderRequest("triage", sanitizedInput);
            var response = aiProvider.generate(providerRequest);

            // Mock 实现：基于关键词返回确定性结果
            // 真实实现由 AI 集成角色通过 Prompt + Schema 校验完成
            return buildMockResult(request.chiefComplaint());
        } catch (Exception e) {
            log.error("AI 分诊调用失败: {}", e.getMessage(), e);
            throw new BusinessException("AI_PROVIDER_UNAVAILABLE", "AI 分诊服务暂时不可用，请转人工选择科室", 504);
        }
    }

    /**
     * 基于主诉关键词的确定性 Mock 结果
     * 真实实现由 AI 集成角色提供
     */
    private TriageAIResult buildMockResult(String chiefComplaint) {
        String complaint = chiefComplaint == null ? "" : chiefComplaint.toLowerCase();

        String departmentCode;
        String priority;
        String reason;
        boolean emergency;
        List<String> keywords;

        if (containsAny(complaint, "胸痛", "胸闷", "心悸", "chest pain")) {
            departmentCode = "DEPT_CARDIOLOGY";
            priority = "HIGH";
            reason = "症状提示可能的心血管问题，建议尽快就诊心血管内科";
            emergency = true;
            keywords = List.of("胸痛", "心血管");
        } else if (containsAny(complaint, "头痛", "头晕", "眩晕", "headache", "dizziness")) {
            departmentCode = "DEPT_NEUROLOGY";
            priority = "MEDIUM";
            reason = "症状提示可能的神经系统问题，建议就诊神经内科";
            emergency = false;
            keywords = List.of("头痛", "神经");
        } else if (containsAny(complaint, "发热", "咳嗽", "咳痰", "fever", "cough")) {
            departmentCode = "DEPT_INTERNAL";
            priority = "MEDIUM";
            reason = "症状提示呼吸道感染可能，建议就诊内科";
            emergency = false;
            keywords = List.of("发热", "咳嗽");
        } else if (containsAny(complaint, "腹痛", "腹泻", "呕吐", "abdominal pain")) {
            departmentCode = "DEPT_INTERNAL";
            priority = "MEDIUM";
            reason = "症状提示消化系统问题，建议就诊内科";
            emergency = false;
            keywords = List.of("腹痛", "消化");
        } else if (containsAny(complaint, "外伤", "骨折", "割伤", "trauma")) {
            departmentCode = "DEPT_GENERAL_SURGERY";
            priority = "MEDIUM";
            reason = "症状提示外科问题，建议就诊普通外科";
            emergency = false;
            keywords = List.of("外伤");
        } else if (containsAny(complaint, "儿童", "婴儿", "患儿", "child")) {
            departmentCode = "DEPT_PEDIATRICS";
            priority = "MEDIUM";
            reason = "患者为儿童，建议就诊儿科";
            emergency = false;
            keywords = List.of("儿童");
        } else if (containsAny(complaint, "急救", "昏迷", "呼吸困难", "emergency")) {
            departmentCode = "DEPT_EMERGENCY";
            priority = "EMERGENCY";
            reason = "症状紧急，建议立即就诊急诊科";
            emergency = true;
            keywords = List.of("急诊");
        } else {
            departmentCode = "DEPT_INTERNAL";
            priority = "LOW";
            reason = "症状不明确，建议先就诊内科进行初步检查";
            emergency = false;
            keywords = List.of("待评估");
        }

        return new TriageAIResult(
                departmentCode,
                priority,
                keywords,
                reason,
                "本结果由 AI 辅助生成，仅供参考，最终诊断请由医生确认",
                emergency);
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
