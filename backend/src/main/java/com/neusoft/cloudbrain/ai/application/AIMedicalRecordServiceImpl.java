package com.neusoft.cloudbrain.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.neusoft.cloudbrain.ai.api.AIMedicalRecordService;
import com.neusoft.cloudbrain.ai.dto.MedicalRecordAIRequest;
import com.neusoft.cloudbrain.ai.dto.MedicalRecordAIResult;
import com.neusoft.cloudbrain.ai.exception.AIInvalidResponseException;
import com.neusoft.cloudbrain.ai.exception.AIProviderException;
import com.neusoft.cloudbrain.ai.parser.JsonSchemaParser;
import com.neusoft.cloudbrain.ai.prompt.PromptManager;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI 病历生成服务实现
 *
 * 重构后（任务 STAGE-AI-3a）：
 * - 通过 AIInvocationRecorder 统一调用 Provider（含重试和调用记录）
 * - 通过 JsonSchemaParser 解析和校验 AI 响应
 * - 通过 PromptManager 获取 system prompt 和版本
 * - 关键词 Mock 逻辑已下沉到 MockAIProvider
 *
 * 输出 Schema（来自 13_AI能力集成AI任务书.md 第3.3节，6 个字段）：
 * chiefComplaint、presentIllness、pastHistory、physicalExamination、
 * preliminaryDiagnosis、treatmentSuggestion
 *
 * 规则：
 * - AI 只能生成草稿
 * - 不得编造输入中不存在的事实
 * - 缺失信息留空或明确标记
 * - 正式病历必须医生确认，不得自动确认
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIMedicalRecordServiceImpl implements AIMedicalRecordService {

    private static final String CAPABILITY = "medical_record";

    private final AIInvocationRecorder recorder;
    private final JsonSchemaParser jsonSchemaParser;
    private final PromptManager promptManager;

    @Override
    public MedicalRecordAIResult generate(MedicalRecordAIRequest request) {
        String sanitizedInput = String.format(
                "主诉: %s; 现病史: %s; 既往史: %s; 体格检查: %s; 初步诊断: %s; 治疗建议: %s",
                safe(request.chiefComplaint()),
                safe(request.presentIllness()),
                safe(request.pastHistory()),
                safe(request.physicalExamination()),
                request.preliminaryDiagnoses() == null ? "" : String.join("、", request.preliminaryDiagnoses()),
                safe(request.treatmentSuggestion()));

        AIInvocationRecorder.InvocationSpec spec = new AIInvocationRecorder.InvocationSpec(
                CAPABILITY, CAPABILITY, null, null,
                sanitizedInput,
                promptManager.getPrompt(CAPABILITY),
                promptManager.getPromptVersion(CAPABILITY));

        try {
            AIInvocationRecorder.InvokeResult<MedicalRecordAIResult> result =
                    recorder.invoke(spec, this::parseMedicalRecordResponse);
            return result.result();
        } catch (AIProviderException e) {
            throw new BusinessException(
                    "AI_MEDICAL_RECORD_FAILED",
                    "AI 病历生成服务暂时不可用，请医生手工填写",
                    504);
        } catch (AIInvalidResponseException e) {
            throw new BusinessException(
                    "AI_MEDICAL_RECORD_FAILED",
                    "AI 病历生成响应异常: " + e.getMessage(),
                    500);
        }
    }

    /**
     * 解析病历生成响应（由 AIInvocationRecorder 调用）
     *
     * 校验 6 个必填字段均存在（值可为空字符串或标记，由 Prompt 约束 AI 不编造）。
     */
    private MedicalRecordAIResult parseMedicalRecordResponse(String content) {
        JsonNode node = jsonSchemaParser.parse(content);
        jsonSchemaParser.validateRequired(node,
                "chiefComplaint", "presentIllness", "pastHistory",
                "physicalExamination", "preliminaryDiagnosis", "treatmentSuggestion");

        return new MedicalRecordAIResult(
                node.get("chiefComplaint").asText(),
                node.get("presentIllness").asText(),
                node.get("pastHistory").asText(),
                node.get("physicalExamination").asText(),
                node.get("preliminaryDiagnosis").asText(),
                node.get("treatmentSuggestion").asText());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
