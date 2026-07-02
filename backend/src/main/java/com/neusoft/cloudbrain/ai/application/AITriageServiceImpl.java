package com.neusoft.cloudbrain.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.neusoft.cloudbrain.ai.api.AITriageService;
import com.neusoft.cloudbrain.ai.dto.TriageAIRequest;
import com.neusoft.cloudbrain.ai.dto.TriageAIResult;
import com.neusoft.cloudbrain.ai.exception.AIInvalidResponseException;
import com.neusoft.cloudbrain.ai.exception.AIProviderException;
import com.neusoft.cloudbrain.ai.parser.JsonSchemaParser;
import com.neusoft.cloudbrain.ai.prompt.PromptManager;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.neusoft.cloudbrain.triage.dto.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * AI 智能分诊服务实现
 *
 * 重构后（任务 STAGE-AI-1）：
 * - 通过 AIInvocationRecorder 统一调用 Provider（含重试和调用记录）
 * - 通过 JsonSchemaParser 解析和校验 AI 响应
 * - 通过 PromptManager 获取 system prompt 和版本
 * - 关键词 Mock 逻辑已下沉到 MockAIProvider
 *
 * 降级策略（来自 12_业务流程与状态机.md 第14节）：
 * - AI 超时或错误时抛出 BusinessException
 * - 业务模块捕获后进入手动流程
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AITriageServiceImpl implements AITriageService {

    private static final String CAPABILITY = "triage";
    private static final Set<String> ALLOWED_PRIORITIES = Set.of("EMERGENCY", "HIGH", "MEDIUM", "LOW");

    private final AIInvocationRecorder recorder;
    private final JsonSchemaParser jsonSchemaParser;
    private final PromptManager promptManager;

    @Override
    public TriageAIResult analyze(TriageAIRequest request) {
        return analyzeWithHistory(request, null);
    }

    /**
     * 多轮分诊（B-HW-07）
     *
     * history 中的历史主诉会拼接进 sanitizedInput（便于 Mock 关键词路由覆盖前序症状），
     * 同时以原形态透传给 Provider（HttpLLMProvider 拼接为多轮 messages）。
     */
    @Override
    public TriageAIResult analyze(TriageAIRequest request, List<ChatMessage> history, Integer round) {
        return analyzeWithHistory(request, history);
    }

    private TriageAIResult analyzeWithHistory(TriageAIRequest request, List<ChatMessage> history) {
        String currentInput = String.format(
                "主诉: %s; 持续时间: %s; 补充: %s; 年龄区间: %s; 性别: %s",
                safe(request.chiefComplaint()),
                safe(request.duration()),
                safe(request.supplement()),
                safe(request.ageRange()),
                safe(request.gender()));

        // B-HW-07：将历史 USER/ASSISTANT 内容拼入 sanitizedInput，
        // 使 Mock provider 关键词路由也能感知前序症状（如第一轮发烧、第二轮咳嗽胸闷）。
        String sanitizedInput = appendHistory(currentInput, history);

        AIInvocationRecorder.InvocationSpec spec = new AIInvocationRecorder.InvocationSpec(
                CAPABILITY, CAPABILITY, null, null,
                sanitizedInput,
                promptManager.getPrompt(CAPABILITY),
                promptManager.getPromptVersion(CAPABILITY),
                history);

        try {
            AIInvocationRecorder.InvokeResult<TriageAIResult> result =
                    recorder.invoke(spec, this::parseTriageResponse);
            return result.result();
        } catch (AIProviderException e) {
            throw new BusinessException("AI_TRIAGE_FAILED", "AI 分诊服务暂时不可用，请转人工选择科室", 504);
        } catch (AIInvalidResponseException e) {
            throw new BusinessException(AIInvalidResponseException.CODE,
                    "AI 分诊响应异常: " + e.getMessage(), 500);
        }
    }

    /**
     * 将历史对话以“历史主诉/历史建议”形式追加到当前输入，便于 Mock 关键词路由。
     */
    private String appendHistory(String currentInput, List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return currentInput;
        }
        List<String> userParts = new ArrayList<>();
        List<String> assistantParts = new ArrayList<>();
        for (ChatMessage msg : history) {
            if (msg == null || msg.content() == null || msg.content().isBlank()) {
                continue;
            }
            if ("USER".equals(msg.role())) {
                userParts.add(msg.content());
            } else if ("ASSISTANT".equals(msg.role())) {
                assistantParts.add(msg.content());
            }
        }
        StringBuilder sb = new StringBuilder(currentInput);
        if (!userParts.isEmpty()) {
            sb.append("; 历史主诉: ").append(String.join(" | ", userParts));
        }
        if (!assistantParts.isEmpty()) {
            sb.append("; 历史建议: ").append(String.join(" | ", assistantParts));
        }
        return sb.toString();
    }

    /**
     * 解析分诊响应（由 AIInvocationRecorder 调用）
     */
    private TriageAIResult parseTriageResponse(String content) {
        JsonNode node = jsonSchemaParser.parse(content);
        jsonSchemaParser.validateRequired(node,
                "departmentCode", "priority", "symptomKeywords", "reason", "safetyNotice");
        jsonSchemaParser.validateEnum(node, "priority", ALLOWED_PRIORITIES);

        List<String> keywords = jsonSchemaParser.parseStringArray(node, "symptomKeywords");
        boolean emergency = node.has("emergencySuggested") && node.get("emergencySuggested").asBoolean(false);

        return new TriageAIResult(
                node.get("departmentCode").asText(),
                node.get("priority").asText(),
                keywords,
                node.get("reason").asText(),
                node.get("safetyNotice").asText(),
                emergency);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
