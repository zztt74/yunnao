package com.neusoft.cloudbrain.ai.dto;

import java.util.List;

/**
 * AI 结果解读请求
 *
 * 输入字段来自 32_AI能力契约规范.md 第3节（结果解读能力）：
 * 检查检验结果文本、参考范围、项目名称。
 *
 * 不包含患者 ID、姓名、手机号等隐私信息（最小化原则）。
 */
public record ResultInterpretationAIRequest(
        String itemName,
        String resultText,
        String normalRange,
        String orderType) {
}
