package com.neusoft.cloudbrain.ai.api;

import com.neusoft.cloudbrain.ai.dto.TriageAIRequest;
import com.neusoft.cloudbrain.ai.dto.TriageAIResult;
import com.neusoft.cloudbrain.triage.dto.ChatMessage;

import java.util.List;

/**
 * AI 智能分诊服务
 *
 * 能力契约（来自 32_AI能力契约规范.md 第3节 和 13_AI能力集成AI任务书.md 第3.1节）：
 * - 输入：年龄区间、性别、主诉、症状持续时间、补充描述
 * - 输出：科室编码、优先级、症状关键词、理由和安全提示
 * - 禁止：输出医生 ID 或正式诊断
 *
 * 降级规则（来自 12_业务流程与状态机.md 第14节）：
 * - AI 超时或错误时抛出异常
 * - 业务模块捕获后进入手动流程
 * - 不无限重试
 *
 * UF-01 多轮扩展：新增 analyze(history) 重载，老单轮接口保持兼容。
 */
public interface AITriageService {

    /**
     * 分析患者症状，返回分诊建议（单轮）
     *
     * @param request 分诊请求（不包含患者隐私 ID）
     * @return 分诊结果（科室编码、优先级、理由等）
     * @throws com.neusoft.cloudbrain.common.exception.BusinessException
     *         AI 调用失败时抛出（错误码 AI_TRIAGE_FAILED 或 AI_INVALID_RESPONSE）
     */
    TriageAIResult analyze(TriageAIRequest request);

    /**
     * 多轮分诊：传入历史对话上下文
     *
     * UF-01 扩展。默认实现委托给单轮接口（忽略 history），由具体 provider 重写以支持多轮。
     * history 仅含症状描述（role 仅 USER/ASSISTANT），不含姓名/手机号等隐私。
     *
     * @param request 本轮分诊请求
     * @param history 历史对话（可为空，空时等同单轮）
     * @param round 当前轮次（从 1 开始）
     */
    default TriageAIResult analyze(TriageAIRequest request, List<ChatMessage> history, Integer round) {
        return analyze(request);
    }
}
