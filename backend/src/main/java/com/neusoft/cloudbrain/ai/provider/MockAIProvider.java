package com.neusoft.cloudbrain.ai.provider;

import com.neusoft.cloudbrain.ai.config.AIProperties;
import com.neusoft.cloudbrain.ai.exception.AIProviderException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mock AI Provider
 *
 * 职责（来自任务 STAGE-AI-1 交付物、32_AI能力契约规范.md 第4节）：
 * - 支持七场景分流：正常 / 高风险 / 空 / 超时 / 非法JSON / 不存在科室 / 异常
 * - 内嵌关键词 Mock 逻辑（从各 ServiceImpl 下沉至此）
 * - 返回符合 Schema 的 JSON 字符串，带 mock=true 标识
 * - 所有响应包含"仅供辅助参考"安全声明
 *
 * 场景触发关键词（可通过 application-ai.yml app.ai.mock.* 配置）：
 * - MOCK_TIMEOUT：模拟超时
 * - MOCK_INVALID_JSON：返回非 JSON 文本
 * - MOCK_NOT_EXIST_DEPT：返回不存在科室编码
 * - MOCK_PROVIDER_ERROR：模拟 Provider 异常
 * - MOCK_EMPTY：返回空结果
 * - MOCK_HIGH_RISK：返回高风险结果
 *
 * 医疗关键词路由（从 ServiceImpl 下沉）：
 * - 胸痛/胸闷/心悸 → 心血管内科 HIGH
 * - 头痛/头晕/眩晕 → 神经内科 MEDIUM
 * - 发热/咳嗽/咳痰 → 内科 MEDIUM
 * - 腹痛/腹泻/呕吐 → 内科 MEDIUM
 * - 外伤/骨折/割伤 → 普通外科 MEDIUM
 * - 儿童/婴儿/患儿 → 儿科 MEDIUM
 * - 急救/昏迷/呼吸困难 → 急诊 EMERGENCY
 */
@Component
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "MOCK", matchIfMissing = true)
@RequiredArgsConstructor
public class MockAIProvider implements AIProvider {

    private final AIProperties aiProperties;

    @Override
    public String name() {
        return "MOCK";
    }

    @Override
    public AIProviderResponse generate(AIProviderRequest request) {
        String input = request.sanitizedInput() == null ? "" : request.sanitizedInput();
        String capability = request.capability();

        // 七场景分流（优先检查 MOCK_ 测试关键词）
        checkScenarioTriggers(input);

        // 非法 JSON 场景：返回非 JSON 文本（供 JsonSchemaParser 触发 AI_INVALID_RESPONSE）
        if (shouldReturnInvalidJson(input)) {
            return generateInvalidJsonResponse();
        }

        // 高风险场景
        if (containsKeyword(input, aiProperties.getMock().getHighRiskKeyword())) {
            return new AIProviderResponse(buildHighRiskResponse(capability, input), true, "mock-high-risk");
        }

        // 空结果场景
        if (containsKeyword(input, aiProperties.getMock().getEmptyKeyword())) {
            return new AIProviderResponse(buildEmptyResponse(capability), true, "mock-empty");
        }

        // 不存在科室场景
        if (containsKeyword(input, aiProperties.getMock().getNotExistDeptKeyword())) {
            return new AIProviderResponse(buildNotExistDeptResponse(capability), true, "mock-not-exist-dept");
        }

        // 正常场景：基于医疗关键词路由
        return new AIProviderResponse(buildNormalResponse(capability, input), true, "mock");
    }

    /**
     * 检查并触发超时和异常场景
     */
    private void checkScenarioTriggers(String input) {
        if (containsKeyword(input, aiProperties.getMock().getTimeoutKeyword())) {
            throw new AIProviderException("Mock 超时", true, null);
        }
        if (containsKeyword(input, aiProperties.getMock().getProviderErrorKeyword())) {
            throw new AIProviderException("Mock Provider 异常", false, 500);
        }
    }

    /**
     * 非法 JSON 场景：返回非 JSON 文本
     */
    private String buildInvalidJsonResponse() {
        return "这不是一个合法的 JSON 响应，仅供测试非法 JSON 场景。";
    }

    /**
     * 判断是否需要返回非法 JSON
     */
    boolean shouldReturnInvalidJson(String input) {
        return containsKeyword(input, aiProperties.getMock().getInvalidJsonKeyword());
    }

    /**
     * 生成非法 JSON 响应（供外部调用检查后使用）
     */
    AIProviderResponse generateInvalidJsonResponse() {
        return new AIProviderResponse(buildInvalidJsonResponse(), true, "mock-invalid-json");
    }

    // ============================================================
    // 正常响应构建（基于医疗关键词路由）
    // ============================================================

    private String buildNormalResponse(String capability, String input) {
        return switch (capability) {
            case "triage" -> buildTriageResponse(input, false);
            case "diagnosis" -> buildDiagnosisResponse(input);
            case "medical_record" -> buildMedicalRecordResponse(input);
            case "prescription_review" -> buildPrescriptionReviewResponse(input);
            case "result_interpretation" -> buildResultInterpretationResponse(input);
            default -> buildFallbackResponse();
        };
    }

    private String buildHighRiskResponse(String capability, String input) {
        return switch (capability) {
            case "triage" -> buildTriageResponse(input, true);
            case "diagnosis" -> buildHighRiskDiagnosisResponse();
            case "medical_record" -> buildHighRiskMedicalRecordResponse(input);
            case "prescription_review" -> buildHighRiskPrescriptionResponse();
            default -> buildNormalResponse(capability, input);
        };
    }

    private String buildEmptyResponse(String capability) {
        return switch (capability) {
            case "triage" -> """
                    {"departmentCode":"DEPT_INTERNAL","priority":"LOW","symptomKeywords":[],"reason":"信息不足，无法精确分诊，建议先就诊内科进行初步检查","safetyNotice":"本结果由 AI 辅助生成，仅供辅助参考，最终诊断请由医生确认","emergencySuggested":false}""";
            case "diagnosis" -> """
                    {"possibleDiagnoses":[],"evidence":[],"missingInformation":["信息不足，无法生成候选诊断"],"riskFactors":[],"suggestedExaminations":[],"disclaimer":"本结果由 AI 辅助生成，仅供医生参考，不能作为正式诊断依据"}""";
            case "medical_record" -> """
                    {"chiefComplaint":"","presentIllness":"","pastHistory":"","physicalExamination":"","preliminaryDiagnosis":"","treatmentSuggestion":""}""";
            case "prescription_review" -> """
                    {"riskLevel":"SAFE","allergyWarnings":[],"interactionWarnings":[],"dosageWarnings":[],"contraindicationWarnings":[],"suggestions":"处方用药基本合理，请医生结合临床最终确认","disclaimer":"本审核由 AI 辅助生成，仅供医生参考，不能替代医生专业判断"}""";
            case "result_interpretation" -> """
                    {"abnormalItems":[],"plainLanguageExplanation":"暂无明显异常","followUpAdvice":"建议保持健康生活方式，按需复查","disclaimer":"本解读由 AI 辅助生成，仅供医生参考，不能替代医生专业判断"}""";
            default -> "{}";
        };
    }

    private String buildNotExistDeptResponse(String capability) {
        if ("triage".equals(capability)) {
            return """
                    {"departmentCode":"DEPT_NOT_EXIST_999","priority":"MEDIUM","symptomKeywords":["测试"],"reason":"测试不存在科室场景","safetyNotice":"本结果由 AI 辅助生成，仅供参考，最终诊断请由医生确认","emergencySuggested":false}""";
        }
        return buildNormalResponse(capability, "MOCK_NOT_EXIST_DEPT");
    }

    // ============================================================
    // 分诊响应
    // ============================================================

    private String buildTriageResponse(String input, boolean forceHighRisk) {
        String lower = input.toLowerCase();
        String departmentCode;
        String priority;
        String reason;
        boolean emergency;
        String keywords;

        if (forceHighRisk || containsAny(lower, "胸痛", "胸闷", "心悸", "chest pain")) {
            departmentCode = "DEPT_CARDIOLOGY";
            priority = "HIGH";
            reason = "症状提示可能的心血管问题，建议尽快就诊心血管内科";
            emergency = true;
            keywords = "[\"胸痛\",\"心血管\"]";
        } else if (containsAny(lower, "头痛", "头晕", "眩晕", "headache", "dizziness")) {
            departmentCode = "DEPT_NEUROLOGY";
            priority = "MEDIUM";
            reason = "症状提示可能的神经系统问题，建议就诊神经内科";
            emergency = false;
            keywords = "[\"头痛\",\"神经\"]";
        } else if (containsAny(lower, "发热", "咳嗽", "咳痰", "fever", "cough")) {
            departmentCode = "DEPT_INTERNAL";
            priority = "MEDIUM";
            reason = "症状提示呼吸道感染可能，建议就诊内科";
            emergency = false;
            keywords = "[\"发热\",\"咳嗽\"]";
        } else if (containsAny(lower, "腹痛", "腹泻", "呕吐", "abdominal pain")) {
            departmentCode = "DEPT_INTERNAL";
            priority = "MEDIUM";
            reason = "症状提示消化系统问题，建议就诊内科";
            emergency = false;
            keywords = "[\"腹痛\",\"消化\"]";
        } else if (containsAny(lower, "外伤", "骨折", "割伤", "trauma")) {
            departmentCode = "DEPT_GENERAL_SURGERY";
            priority = "MEDIUM";
            reason = "症状提示外科问题，建议就诊普通外科";
            emergency = false;
            keywords = "[\"外伤\"]";
        } else if (containsAny(lower, "儿童", "婴儿", "患儿", "child")) {
            departmentCode = "DEPT_PEDIATRICS";
            priority = "MEDIUM";
            reason = "患者为儿童，建议就诊儿科";
            emergency = false;
            keywords = "[\"儿童\"]";
        } else if (containsAny(lower, "急救", "昏迷", "呼吸困难", "emergency")) {
            departmentCode = "DEPT_EMERGENCY";
            priority = "EMERGENCY";
            reason = "症状紧急，建议立即就诊急诊科";
            emergency = true;
            keywords = "[\"急诊\"]";
        } else {
            departmentCode = "DEPT_INTERNAL";
            priority = "LOW";
            reason = "症状不明确，建议先就诊内科进行初步检查";
            emergency = false;
            keywords = "[\"待评估\"]";
        }

        return String.format(
                "{\"departmentCode\":\"%s\",\"priority\":\"%s\",\"symptomKeywords\":%s,"
                        + "\"reason\":\"%s\",\"safetyNotice\":\"本结果由 AI 辅助生成，仅供参考，最终诊断请由医生确认\","
                        + "\"emergencySuggested\":%s}",
                departmentCode, priority, keywords, reason, emergency);
    }

    // ============================================================
    // 诊断响应
    // ============================================================

    private String buildDiagnosisResponse(String input) {
        String lower = input.toLowerCase();
        String diagnoses;
        String exams;

        if (containsAny(lower, "胸痛", "胸闷", "心悸")) {
            diagnoses = "[{\"diagnosisCode\":\"I20.9\",\"diagnosisName\":\"心绞痛\",\"confidence\":\"MEDIUM\",\"explanation\":\"胸痛症状提示心肌缺血可能\"},"
                    + "{\"diagnosisCode\":\"I10\",\"diagnosisName\":\"高血压病\",\"confidence\":\"LOW\",\"explanation\":\"需排查血压相关因素\"}]";
            exams = "[\"心电图\",\"心肌酶谱\",\"血压监测\"]";
        } else if (containsAny(lower, "头痛", "头晕", "眩晕")) {
            diagnoses = "[{\"diagnosisCode\":\"G44.1\",\"diagnosisName\":\"血管性头痛\",\"confidence\":\"MEDIUM\",\"explanation\":\"头痛症状提示血管性可能\"},"
                    + "{\"diagnosisCode\":\"H81.1\",\"diagnosisName\":\"良性阵发性位置性眩晕\",\"confidence\":\"LOW\",\"explanation\":\"眩晕症状提示前庭功能异常\"}]";
            exams = "[\"头颅CT\",\"颈椎X光\",\"前庭功能检查\"]";
        } else if (containsAny(lower, "发热", "咳嗽", "咳痰")) {
            diagnoses = "[{\"diagnosisCode\":\"J06.9\",\"diagnosisName\":\"急性上呼吸道感染\",\"confidence\":\"MEDIUM\",\"explanation\":\"发热咳嗽提示呼吸道感染\"},"
                    + "{\"diagnosisCode\":\"J18.9\",\"diagnosisName\":\"肺炎\",\"confidence\":\"LOW\",\"explanation\":\"需排查肺部感染\"}]";
            exams = "[\"血常规\",\"胸部X光\",\"C反应蛋白\"]";
        } else {
            diagnoses = "[{\"diagnosisCode\":\"R69\",\"diagnosisName\":\"病因未明\",\"confidence\":\"LOW\",\"explanation\":\"信息不足，建议进一步检查\"}]";
            exams = "[\"血常规\",\"尿常规\"]";
        }

        return String.format(
                "{\"possibleDiagnoses\":%s,\"evidence\":[\"基于患者主诉和症状描述分析\"],"
                        + "\"missingInformation\":[\"建议补充既往病史和用药史\"],\"riskFactors\":[\"建议关注症状变化\"],"
                        + "\"suggestedExaminations\":%s,"
                        + "\"disclaimer\":\"本结果由 AI 辅助生成，仅供医生参考，不能作为正式诊断依据\"}",
                diagnoses, exams);
    }

    private String buildHighRiskDiagnosisResponse() {
        return """
                {"possibleDiagnoses":[{"diagnosisCode":"I21.9","diagnosisName":"急性心肌梗死","confidence":"HIGH","explanation":"胸痛症状高度提示心肌梗死，需紧急处理"}],"evidence":["胸痛持续不缓解","可能伴随冷汗和放射痛"],"missingInformation":["需确认疼痛持续时间和放射部位","需心电图和心肌酶谱结果"],"riskFactors":["年龄","高血压史","吸烟史"],"suggestedExaminations":["心电图","心肌酶谱","肌钙蛋白"],"disclaimer":"本结果由 AI 辅助生成，仅供医生参考，不能作为正式诊断依据"}""";
    }

    // ============================================================
    // 病历生成响应
    // ============================================================

    private String buildMedicalRecordResponse(String input) {
        // 病历生成回填输入信息，不编造事实；缺失字段留空或标记
        return """
                {"chiefComplaint":"基于问诊内容整理的主诉","presentIllness":"基于问诊内容整理的现病史","pastHistory":"不详","physicalExamination":"待查","preliminaryDiagnosis":"待进一步明确","treatmentSuggestion":"建议对症治疗，根据检查结果调整方案"}""";
    }

    /**
     * 高风险病历场景：症状严重（如胸痛、呼吸困难），提示紧急处理但不自动确诊。
     */
    private String buildHighRiskMedicalRecordResponse(String input) {
        return """
                {"chiefComplaint":"胸痛伴大汗，持续不缓解","presentIllness":"患者突发胸痛，呈压榨样，伴大汗及放射痛，症状持续不缓解，疑似急性心血管事件","pastHistory":"不详","physicalExamination":"待查（建议立即测量血压、心电图）","preliminaryDiagnosis":"胸痛待查，警惕急性心肌梗死","treatmentSuggestion":"建议立即启动急诊流程，完善心电图、心肌酶谱、肌钙蛋白检查，暂禁食水，监护生命体征"}""";
    }

    // ============================================================
    // 处方审核响应
    // ============================================================

    private String buildPrescriptionReviewResponse(String input) {
        return """
                {"riskLevel":"SAFE","allergyWarnings":[],"interactionWarnings":[],"dosageWarnings":[],"contraindicationWarnings":[],"suggestions":"处方用药基本合理，请医生结合临床最终确认","disclaimer":"本审核由 AI 辅助生成，仅供医生参考，不能替代医生专业判断"}""";
    }

    private String buildHighRiskPrescriptionResponse() {
        return """
                {"riskLevel":"HIGH","allergyWarnings":["患者对青霉素过敏，处方包含青霉素类药物"],"interactionWarnings":["两种药物存在相互作用"],"dosageWarnings":[],"contraindicationWarnings":["存在用药禁忌"],"suggestions":"存在过敏禁忌和药物相互作用，建议更换药品并重新评估用药方案。风险等级：HIGH。","disclaimer":"本审核由 AI 辅助生成，仅供医生参考，不能替代医生专业判断"}""";
    }

    // ============================================================
    // 结果解读响应
    // ============================================================

    private String buildResultInterpretationResponse(String input) {
        String lower = input.toLowerCase();
        String abnormalItems;
        String explanation;
        String followUp;

        if (containsAny(lower, "偏高", "升高", "增高", "high", "elevated")) {
            abnormalItems = "[\"指标偏高\"]";
            explanation = "结果偏高，超出参考范围，建议结合临床进一步评估。";
            followUp = "建议复查并关注相关指标变化，必要时就诊。";
        } else if (containsAny(lower, "偏低", "降低", "减低", "low", "decreased")) {
            abnormalItems = "[\"指标偏低\"]";
            explanation = "结果偏低，低于参考范围，建议结合临床进一步评估。";
            followUp = "建议复查并关注相关指标变化，必要时就诊。";
        } else if (containsAny(lower, "阳性", "positive")) {
            abnormalItems = "[\"指标阳性\"]";
            explanation = "结果为阳性，提示可能存在异常，需医生结合症状判断。";
            followUp = "建议尽快就诊，由医生结合临床表现进一步诊断。";
        } else if (containsAny(lower, "危急", "critical")) {
            abnormalItems = "[\"危急值\"]";
            explanation = "结果为危急值，需立即就医处理。";
            followUp = "建议立即就诊，及时处理危急值。";
        } else {
            abnormalItems = "[]";
            explanation = "结果在正常范围内，暂无明显异常。";
            followUp = "建议保持健康生活方式，按需复查。";
        }

        return String.format(
                "{\"abnormalItems\":%s,\"plainLanguageExplanation\":\"%s\","
                        + "\"followUpAdvice\":\"%s\","
                        + "\"disclaimer\":\"本解读由 AI 辅助生成，仅供医生参考，不能替代医生专业判断\"}",
                abnormalItems, explanation, followUp);
    }

    // ============================================================
    // 工具方法
    // ============================================================

    private String buildFallbackResponse() {
        return """
                {"message":"Mock AI 基础能力已启用，结果仅供辅助参考，请由授权人员确认。","disclaimer":"本结果由 AI 辅助生成，仅供参考"}""";
    }

    private boolean containsKeyword(String input, String keyword) {
        return keyword != null && !keyword.isEmpty() && input.contains(keyword);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
