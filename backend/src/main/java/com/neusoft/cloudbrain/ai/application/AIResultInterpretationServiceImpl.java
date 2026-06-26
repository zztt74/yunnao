package com.neusoft.cloudbrain.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.neusoft.cloudbrain.ai.api.AIResultInterpretationService;
import com.neusoft.cloudbrain.ai.dto.ResultInterpretationAIRequest;
import com.neusoft.cloudbrain.ai.dto.ResultInterpretationAIResult;
import com.neusoft.cloudbrain.ai.exception.AIInvalidResponseException;
import com.neusoft.cloudbrain.ai.exception.AIProviderException;
import com.neusoft.cloudbrain.ai.parser.JsonSchemaParser;
import com.neusoft.cloudbrain.ai.prompt.PromptManager;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 结果解读服务实现
 *
 * 重构后（任务 STAGE-AI-3b）：
 * - 通过 AIInvocationRecorder 统一调用 Provider（含重试和调用记录）
 * - 通过 JsonSchemaParser 解析和校验 AI 响应
 * - 通过 PromptManager 获取 system prompt 和版本
 * - 关键词 Mock 逻辑已下沉到 MockAIProvider
 *
 * 输出 Schema（来自 13_AI能力集成AI任务书.md 第3.5节，5 个字段）：
 * abnormalItems、plainLanguageExplanation、possibleAttentionPoints、
 * followUpSuggestion、disclaimer
 *
 * 规则：
 * - 不得修改原始检查数值
 * - 禁止编造输入中不存在的检查结果
 * - 结果仅供辅助参考，医生必须审核确认
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIResultInterpretationServiceImpl implements AIResultInterpretationService {

    private static final String CAPABILITY = "result_interpretation";

    private final AIInvocationRecorder recorder;
    private final JsonSchemaParser jsonSchemaParser;
    private final PromptManager promptManager;

    @Override
    public ResultInterpretationAIResult interpret(ResultInterpretationAIRequest request) {
        String sanitizedInput = String.format(
                "项目名称: %s; 结果文本: %s; 参考范围: %s; 类型: %s",
                safe(request.itemName()),
                safe(request.resultText()),
                safe(request.normalRange()),
                safe(request.orderType()));

        AIInvocationRecorder.InvocationSpec spec = new AIInvocationRecorder.InvocationSpec(
                CAPABILITY, CAPABILITY, null, null,
                sanitizedInput,
                promptManager.getPrompt(CAPABILITY),
                promptManager.getPromptVersion(CAPABILITY));

        try {
            AIInvocationRecorder.InvokeResult<ResultInterpretationAIResult> result =
                    recorder.invoke(spec, this::parseResultInterpretationResponse);
            return result.result();
        } catch (AIProviderException e) {
            throw new BusinessException(
                    "AI_RESULT_INTERPRETATION_FAILED",
                    "AI 结果解读服务暂时不可用，请医生手工解读",
                    504);
        } catch (AIInvalidResponseException e) {
            throw new BusinessException(
                    "AI_RESULT_INTERPRETATION_FAILED",
                    "AI 结果解读响应异常: " + e.getMessage(),
                    500);
        }
    }

    /**
     * 解析结果解读响应（由 AIInvocationRecorder 调用）
     *
     * 校验 3 个必填字符串字段（plainLanguageExplanation、followUpSuggestion、disclaimer）。
     * 数组字段 abnormalItems 和 possibleAttentionPoints 缺失时返回空列表（空输入不编造）。
     */
    private ResultInterpretationAIResult parseResultInterpretationResponse(String content) {
        JsonNode node = jsonSchemaParser.parse(content);
        jsonSchemaParser.validateRequired(node,
                "plainLanguageExplanation", "followUpSuggestion", "disclaimer");

        return new ResultInterpretationAIResult(
                jsonSchemaParser.parseStringArray(node, "abnormalItems"),
                node.get("plainLanguageExplanation").asText(),
                jsonSchemaParser.parseStringArray(node, "possibleAttentionPoints"),
                node.get("followUpSuggestion").asText(),
                node.get("disclaimer").asText());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
