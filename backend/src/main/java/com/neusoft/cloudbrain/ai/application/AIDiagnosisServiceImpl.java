package com.neusoft.cloudbrain.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.neusoft.cloudbrain.ai.api.AIDiagnosisService;
import com.neusoft.cloudbrain.ai.dto.DiagnosisAIRequest;
import com.neusoft.cloudbrain.ai.dto.DiagnosisAIResult;
import com.neusoft.cloudbrain.ai.exception.AIInvalidResponseException;
import com.neusoft.cloudbrain.ai.exception.AIProviderException;
import com.neusoft.cloudbrain.ai.parser.JsonSchemaParser;
import com.neusoft.cloudbrain.ai.prompt.PromptManager;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * AI 辅助诊断服务实现
 *
 * 重构后（任务 STAGE-AI-1）：
 * - 通过 AIInvocationRecorder 统一调用 Provider（含重试和调用记录）
 * - 通过 JsonSchemaParser 解析和校验 AI 响应
 * - 通过 PromptManager 获取 system prompt 和版本
 * - 关键词 Mock 逻辑已下沉到 MockAIProvider
 *
 * 诊断隔离原则（来自 12_业务流程与状态机.md 第7节）：
 * - AI 只能产生 AI_SUGGESTION，不得产生正式 FINAL + DOCTOR 记录
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIDiagnosisServiceImpl implements AIDiagnosisService {

    private static final String CAPABILITY = "diagnosis";
    private static final Set<String> ALLOWED_CONFIDENCE = Set.of("HIGH", "MEDIUM", "LOW");

    private final AIInvocationRecorder recorder;
    private final JsonSchemaParser jsonSchemaParser;
    private final PromptManager promptManager;

    @Override
    public DiagnosisAIResult analyze(DiagnosisAIRequest request) {
        String sanitizedInput = String.format(
                "主诉: %s; 现病史: %s; 既往史: %s; 体格检查: %s; 年龄: %s; 性别: %s",
                safe(request.chiefComplaint()),
                safe(request.presentIllness()),
                safe(request.pastHistory()),
                safe(request.physicalExamination()),
                safe(request.patientAgeRange()),
                safe(request.patientGender()));

        AIInvocationRecorder.InvocationSpec spec = new AIInvocationRecorder.InvocationSpec(
                CAPABILITY, CAPABILITY, null, null,
                sanitizedInput,
                promptManager.getPrompt(CAPABILITY),
                promptManager.getPromptVersion(CAPABILITY));

        try {
            AIInvocationRecorder.InvokeResult<DiagnosisAIResult> result =
                    recorder.invoke(spec, this::parseDiagnosisResponse);
            return result.result();
        } catch (AIProviderException e) {
            throw new BusinessException("AI_DIAGNOSIS_FAILED", "AI 辅助诊断服务暂时不可用，请医生手工诊断", 504);
        } catch (AIInvalidResponseException e) {
            throw new BusinessException("AI_DIAGNOSIS_FAILED", "AI 诊断响应异常: " + e.getMessage(), 500);
        }
    }

    /**
     * 解析诊断响应（由 AIInvocationRecorder 调用）
     */
    private DiagnosisAIResult parseDiagnosisResponse(String content) {
        JsonNode node = jsonSchemaParser.parse(content);
        jsonSchemaParser.validateRequired(node,
                "possibleDiagnoses", "evidence", "disclaimer");

        List<DiagnosisAIResult.PossibleDiagnosis> diagnoses = new ArrayList<>();
        JsonNode diagnosesNode = node.get("possibleDiagnoses");
        if (diagnosesNode != null && diagnosesNode.isArray()) {
            for (JsonNode d : diagnosesNode) {
                String confidence = d.has("confidence") ? d.get("confidence").asText() : "LOW";
                if (!ALLOWED_CONFIDENCE.contains(confidence)) {
                    throw new AIInvalidResponseException(
                            "AI 响应字段 confidence 枚举值非法: " + confidence);
                }
                diagnoses.add(new DiagnosisAIResult.PossibleDiagnosis(
                        d.has("diagnosisCode") ? d.get("diagnosisCode").asText() : "",
                        d.has("diagnosisName") ? d.get("diagnosisName").asText() : "",
                        confidence,
                        d.has("explanation") ? d.get("explanation").asText() : ""));
            }
        }

        return new DiagnosisAIResult(
                diagnoses,
                jsonSchemaParser.parseStringArray(node, "evidence"),
                jsonSchemaParser.parseStringArray(node, "missingInformation"),
                jsonSchemaParser.parseStringArray(node, "riskFactors"),
                jsonSchemaParser.parseStringArray(node, "suggestedExaminations"),
                node.get("disclaimer").asText());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
