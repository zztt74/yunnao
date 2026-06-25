package com.neusoft.cloudbrain.ai.application;

import com.neusoft.cloudbrain.ai.api.AIResultInterpretationService;
import com.neusoft.cloudbrain.ai.dto.ResultInterpretationAIRequest;
import com.neusoft.cloudbrain.ai.dto.ResultInterpretationAIResult;
import com.neusoft.cloudbrain.ai.provider.AIProvider;
import com.neusoft.cloudbrain.ai.provider.AIProviderRequest;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 结果解读服务 Mock 实现
 *
 * 说明：此为阶段5 的最小可用实现，使用 MockAIProvider。
 * AI 能力集成角色后续将替换为真实 Provider + Prompt + Schema 校验实现。
 *
 * 规则（来自 32_AI能力契约规范.md 第3节）：
 * - 不得修改原始检查数值
 * - 结果仅供辅助参考
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIResultInterpretationServiceImpl implements AIResultInterpretationService {

    private final AIProvider aiProvider;

    @Override
    public ResultInterpretationAIResult interpret(ResultInterpretationAIRequest request) {
        try {
            String sanitizedInput = String.format(
                    "项目名称: %s; 结果文本: %s; 参考范围: %s; 类型: %s",
                    safe(request.itemName()),
                    safe(request.resultText()),
                    safe(request.normalRange()),
                    safe(request.orderType()));

            AIProviderRequest providerRequest = new AIProviderRequest("result_interpretation", sanitizedInput);
            aiProvider.generate(providerRequest);

            return buildMockResult(request);
        } catch (Exception e) {
            log.error("AI 结果解读调用失败: {}", e.getMessage(), e);
            throw new BusinessException(
                    "AI_RESULT_INTERPRETATION_FAILED",
                    "AI 结果解读服务暂时不可用，请医生手工解读",
                    504);
        }
    }

    /**
     * 基于结果文本的确定性 Mock 解读
     * 真实实现由 AI 集成角色提供
     */
    private ResultInterpretationAIResult buildMockResult(ResultInterpretationAIRequest request) {
        String resultText = request.resultText() == null ? "" : request.resultText().toLowerCase();
        String normalRange = request.normalRange() == null ? "" : request.normalRange();
        List<String> abnormalItems = new ArrayList<>();
        String plainExplanation;
        String followUp;

        // 简单确定性规则：识别常见异常标记
        if (resultText.contains("偏高") || resultText.contains("升高") || resultText.contains("增高")
                || resultText.contains("high") || resultText.contains("elevated")) {
            abnormalItems.add(request.itemName() + " 偏高");
            plainExplanation = String.format("%s 结果偏高，超出参考范围 %s，建议结合临床进一步评估。", request.itemName(), normalRange);
            followUp = "建议复查并关注相关指标变化，必要时就诊。";
        } else if (resultText.contains("偏低") || resultText.contains("降低") || resultText.contains("减低")
                || resultText.contains("low") || resultText.contains("decreased")) {
            abnormalItems.add(request.itemName() + " 偏低");
            plainExplanation = String.format("%s 结果偏低，低于参考范围 %s，建议结合临床进一步评估。", request.itemName(), normalRange);
            followUp = "建议复查并关注相关指标变化，必要时就诊。";
        } else if (resultText.contains("阳性") || resultText.contains("positive")) {
            abnormalItems.add(request.itemName() + " 阳性");
            plainExplanation = String.format("%s 结果为阳性，提示可能存在异常，需医生结合症状判断。", request.itemName());
            followUp = "建议尽快就诊，由医生结合临床表现进一步诊断。";
        } else if (resultText.contains("危急") || resultText.contains("critical")) {
            abnormalItems.add(request.itemName() + " 危急值");
            plainExplanation = String.format("%s 结果为危急值，需立即就医处理。", request.itemName());
            followUp = "建议立即就诊，及时处理危急值。";
        } else if (resultText.contains("正常") || resultText.contains("阴性")
                || resultText.contains("normal") || resultText.contains("negative")) {
            plainExplanation = String.format("%s 结果在正常范围内，暂无明显异常。", request.itemName());
            followUp = "建议保持健康生活方式，按需复查。";
        } else {
            plainExplanation = String.format("%s 结果需由医生结合临床综合判断。", request.itemName());
            followUp = "建议就诊由医生解读结果。";
        }

        return new ResultInterpretationAIResult(
                abnormalItems,
                plainExplanation,
                followUp,
                "本解读由 AI 辅助生成，仅供医生参考，不能替代医生专业判断");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
