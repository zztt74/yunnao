package com.neusoft.cloudbrain.ai.api;

import com.neusoft.cloudbrain.ai.dto.ResultInterpretationAIRequest;
import com.neusoft.cloudbrain.ai.dto.ResultInterpretationAIResult;

/**
 * AI 结果解读服务
 *
 * 能力契约（来自 32_AI能力契约规范.md 第3节）：
 * - 输入：检查检验结果文本、参考范围、项目名称
 * - 输出：异常项、通俗解释和随访建议
 * - 禁止：修改原始检查数值
 *
 * 降级规则（来自 12_业务流程与状态机.md 第14节）：
 * - AI 超时或错误时抛出异常
 * - 业务模块捕获后允许医生手工解读
 * - 不无限重试
 */
public interface AIResultInterpretationService {

    /**
     * 解读检查检验结果
     *
     * @param request 解读请求（不包含患者隐私 ID）
     * @return 解读结果（异常项、通俗解释、随访建议，仅供医生参考）
     * @throws com.neusoft.cloudbrain.common.exception.BusinessException
     *         AI 调用失败时抛出（错误码 AI_RESULT_INTERPRETATION_FAILED 或 AI_INVALID_RESPONSE）
     */
    ResultInterpretationAIResult interpret(ResultInterpretationAIRequest request);
}
