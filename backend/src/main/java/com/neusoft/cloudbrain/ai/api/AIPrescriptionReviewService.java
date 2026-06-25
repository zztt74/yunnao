package com.neusoft.cloudbrain.ai.api;

import com.neusoft.cloudbrain.ai.dto.PrescriptionReviewAIRequest;
import com.neusoft.cloudbrain.ai.dto.PrescriptionReviewAIResult;

/**
 * AI 处方审核服务
 *
 * 能力契约（来自 32_AI能力契约规范.md 第3节）：
 * - 输入：药品明细、患者过敏史、确定性规则检查结果
 * - 输出：风险等级、过敏警告、相互作用警告、剂量警告、补充建议
 * - 禁止：覆盖确定性规则或自动确认处方
 *
 * 降级规则（来自 12_业务流程与状态机.md 第14节）：
 * - AI 超时或错误时抛出异常
 * - 业务模块捕获后允许医生手工继续
 * - 不无限重试
 *
 * 关键规则（来自 11_功能需求.md 第12.6节）：
 * - 后端先基于虚构规则表执行过敏、相互作用、剂量和禁忌检查
 * - 确定性规则命中不得被 AI 输出降级或覆盖
 * - AI 负责解释风险和补充建议，不作为确定性用药校验的唯一来源
 */
public interface AIPrescriptionReviewService {

    /**
     * 审核处方
     *
     * @param request 审核请求（含药品明细、过敏史、确定性规则结果，不含患者隐私 ID）
     * @return 审核结果（风险等级、警告、建议，仅供医生参考）
     * @throws com.neusoft.cloudbrain.common.exception.BusinessException
     *         AI 调用失败时抛出（错误码 AI_PRESCRIPTION_REVIEW_FAILED 或 AI_INVALID_RESPONSE）
     */
    PrescriptionReviewAIResult review(PrescriptionReviewAIRequest request);
}
