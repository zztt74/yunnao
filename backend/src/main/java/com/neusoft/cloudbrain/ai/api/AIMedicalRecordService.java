package com.neusoft.cloudbrain.ai.api;

import com.neusoft.cloudbrain.ai.dto.MedicalRecordAIRequest;
import com.neusoft.cloudbrain.ai.dto.MedicalRecordAIResult;

/**
 * AI 病历生成服务
 *
 * 能力契约（来自 32_AI能力契约规范.md 第3节）：
 * - 输入：主诉、现病史、既往史、体格检查、初步诊断和治疗建议
 * - 输出：结构化病历草稿
 * - 禁止：编造缺失信息或自动确认
 *
 * 降级规则（来自 12_业务流程与状态机.md 第14节）：
 * - AI 超时或错误时抛出异常
 * - 业务模块捕获后允许医生手工填写
 * - 不无限重试
 */
public interface AIMedicalRecordService {

    /**
     * 根据问诊内容生成病历草稿
     *
     * @param request 病历生成请求（不包含患者隐私 ID）
     * @return 病历草稿（主诉、现病史、既往史等，仅供医生参考）
     * @throws com.neusoft.cloudbrain.common.exception.BusinessException
     *         AI 调用失败时抛出（错误码 AI_MEDICAL_RECORD_FAILED 或 AI_INVALID_RESPONSE）
     */
    MedicalRecordAIResult generate(MedicalRecordAIRequest request);
}
