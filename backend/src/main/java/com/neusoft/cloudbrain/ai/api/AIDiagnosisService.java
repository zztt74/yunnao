package com.neusoft.cloudbrain.ai.api;

import com.neusoft.cloudbrain.ai.dto.DiagnosisAIRequest;
import com.neusoft.cloudbrain.ai.dto.DiagnosisAIResult;

/**
 * AI 辅助诊断服务
 *
 * 能力契约（来自 32_AI能力契约规范.md 第3节 和 13_AI能力集成AI任务书.md 第3.2节）：
 * - 输出：候选诊断、证据、缺失信息和检查建议
 * - 禁止：写入医生正式诊断
 *
 * 诊断隔离原则（来自 12_业务流程与状态机.md 第7节）：
 * - AI 只能产生 AI_SUGGESTION，不得产生正式 FINAL + DOCTOR 记录
 * - AI 原始结果保存在 AI 调用记录，结构化候选诊断保存为 source=AI_SUGGESTION
 */
public interface AIDiagnosisService {

    /**
     * 分析患者问诊信息，返回候选诊断建议
     *
     * @param request 诊断请求（最小化必要上下文）
     * @return 候选诊断结果（仅供医生参考）
     * @throws com.neusoft.cloudbrain.common.exception.BusinessException
     *         AI 调用失败时抛出（错误码 AI_DIAGNOSIS_FAILED 或 AI_INVALID_RESPONSE）
     */
    DiagnosisAIResult analyze(DiagnosisAIRequest request);
}
