package com.neusoft.cloudbrain.ai.application;

import com.neusoft.cloudbrain.ai.config.AIProperties;
import com.neusoft.cloudbrain.ai.exception.AIInvalidResponseException;
import com.neusoft.cloudbrain.ai.exception.AIProviderException;
import com.neusoft.cloudbrain.ai.provider.AIProvider;
import com.neusoft.cloudbrain.ai.provider.AIProviderRequest;
import com.neusoft.cloudbrain.ai.provider.AIProviderResponse;
import com.neusoft.cloudbrain.audit.entity.AIInvocation;
import com.neusoft.cloudbrain.audit.entity.AIInvocationAttempt;
import com.neusoft.cloudbrain.audit.repository.AIInvocationAttemptRepository;
import com.neusoft.cloudbrain.audit.repository.AIInvocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.function.Function;

/**
 * AI 调用记录器
 *
 * 职责（来自任务 STAGE-AI-1 交付物、32_AI能力契约规范.md 第5节）：
 * - 1 次业务调用 → 1 条 AIInvocation（PENDING → SUCCESS/FAILED）
 * - 每次 Provider 请求或重试 → 1 条 AIInvocationAttempt
 * - traceId 透传（从 MDC 读取，写入日志）
 * - 不存 API Key
 * - 重试仅对超时/5xx，非法 JSON 不重试
 * - 原始响应与解析结果分开记录
 *
 * 统计口径（来自 11_功能需求.md 第15.4节）：
 * - AI 成功率按 AIInvocation 统计，重试不重复进入分母
 *
 * 事务策略：
 * - 每条记录独立事务（REQUIRES_NEW），确保调用记录持久化不受业务事务回滚影响
 * - invoke 方法本身不持有事务，仅编排事务性子方法
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AIInvocationRecorder {

    private final AIProvider aiProvider;
    private final AIInvocationRepository invocationRepository;
    private final AIInvocationAttemptRepository attemptRepository;
    private final AIProperties aiProperties;

    private static final String MDC_TRACE_ID = "traceId";
    private static final int REQUEST_SUMMARY_MAX = 500;
    private static final int RESPONSE_SUMMARY_MAX = 1000;
    private static final int ERROR_MESSAGE_MAX = 500;

    /**
     * 执行一次 AI 业务调用（含重试编排和调用记录）
     *
     * @param spec   调用规格
     * @param parser 响应解析函数（将原始内容解析为目标类型，可能抛出 AIInvalidResponseException）
     * @param <T>    目标类型
     * @return 调用结果（含解析后的数据和 invocation ID）
     * @throws AIInvalidResponseException 响应非法（不重试）
     * @throws AIProviderException        Provider 调用失败（重试耗尽后抛出）
     */
    public <T> InvokeResult<T> invoke(InvocationSpec spec, Function<String, T> parser) {
        String traceId = MDC.get(MDC_TRACE_ID);
        log.info("AI 调用开始: capability={}, traceId={}", spec.capability(), traceId);

        AIInvocation invocation = startInvocation(spec);
        int maxAttempts = aiProperties.getMaxRetries() + 1;
        long startTime = System.currentTimeMillis();

        AIProviderException lastProviderException = null;

        for (int attemptIndex = 1; attemptIndex <= maxAttempts; attemptIndex++) {
            long attemptStart = System.currentTimeMillis();
            String providerName = aiProvider.name();

            try {
                AIProviderRequest providerRequest = new AIProviderRequest(
                        spec.capability(),
                        spec.sanitizedInput(),
                        spec.systemPrompt(),
                        spec.promptVersion());

                AIProviderResponse response = aiProvider.generate(providerRequest);
                long attemptDuration = System.currentTimeMillis() - attemptStart;

                // 解析响应（可能抛出 AIInvalidResponseException）
                T result = parser.apply(response.content());

                // 记录成功的 attempt（使用简化重载，无错误信息）
                recordAttempt(invocation.getId(), attemptIndex, providerName, response.model(),
                        spec.promptVersion(), "SUCCESS",
                        spec.sanitizedInput(), response.content(), attemptDuration);

                // 完成 invocation
                long totalDuration = System.currentTimeMillis() - startTime;
                updateAttemptCount(invocation.getId(), attemptIndex);
                finishInvocation(invocation.getId(), "SUCCESS", null, null, totalDuration);

                log.info("AI 调用成功: capability={}, invocationId={}, attempts={}, mock={}, traceId={}",
                        spec.capability(), invocation.getId(), attemptIndex, response.mock(), traceId);

                return new InvokeResult<>(result, response.mock(), invocation.getId());

            } catch (AIInvalidResponseException e) {
                long attemptDuration = System.currentTimeMillis() - attemptStart;
                // 非法 JSON 不重试
                recordAttempt(invocation.getId(), attemptIndex, providerName, null,
                        spec.promptVersion(), "FAILED", null,
                        AIInvalidResponseException.CODE, e.getMessage(),
                        spec.sanitizedInput(), null, attemptDuration);
                updateAttemptCount(invocation.getId(), attemptIndex);
                long totalDuration = System.currentTimeMillis() - startTime;
                finishInvocation(invocation.getId(), "FAILED",
                        AIInvalidResponseException.CODE, e.getMessage(), totalDuration);

                log.warn("AI 调用失败（响应非法）: capability={}, invocationId={}, traceId={}, error={}",
                        spec.capability(), invocation.getId(), traceId, e.getMessage());
                throw e;

            } catch (AIProviderException e) {
                long attemptDuration = System.currentTimeMillis() - attemptStart;
                String attemptStatus = e.isRetryable() ? "TIMEOUT" : "FAILED";
                String errorType = resolveProviderErrorType(e);

                recordAttempt(invocation.getId(), attemptIndex, providerName, null,
                        spec.promptVersion(), attemptStatus, e.getHttpStatus(),
                        errorType, e.getMessage(),
                        spec.sanitizedInput(), null, attemptDuration);

                lastProviderException = e;

                if (e.isRetryable() && attemptIndex < maxAttempts) {
                    log.warn("AI 调用失败（可重试）: capability={}, attempt={}/{}, traceId={}, error={}",
                            spec.capability(), attemptIndex, maxAttempts, traceId, e.getMessage());
                    continue;
                }

                // 重试耗尽或不可重试
                updateAttemptCount(invocation.getId(), attemptIndex);
                long totalDuration = System.currentTimeMillis() - startTime;
                finishInvocation(invocation.getId(), "FAILED", errorType, e.getMessage(), totalDuration);

                log.error("AI 调用失败（重试耗尽）: capability={}, invocationId={}, attempts={}, traceId={}",
                        spec.capability(), invocation.getId(), attemptIndex, traceId);
                throw e;

            } catch (Exception e) {
                long attemptDuration = System.currentTimeMillis() - attemptStart;
                recordAttempt(invocation.getId(), attemptIndex, providerName, null,
                        spec.promptVersion(), "FAILED", null,
                        "AI_PROVIDER_ERROR", e.getMessage(),
                        spec.sanitizedInput(), null, attemptDuration);
                updateAttemptCount(invocation.getId(), attemptIndex);
                long totalDuration = System.currentTimeMillis() - startTime;
                finishInvocation(invocation.getId(), "FAILED",
                        "AI_PROVIDER_ERROR", e.getMessage(), totalDuration);

                log.error("AI 调用失败（未知异常）: capability={}, invocationId={}, traceId={}",
                        spec.capability(), invocation.getId(), traceId, e);
                throw new AIProviderException("AI 调用异常: " + e.getMessage(), false, null, e);
            }
        }

        // 理论上不会到达（循环内所有路径都已 return 或 throw）
        long totalDuration = System.currentTimeMillis() - startTime;
        finishInvocation(invocation.getId(), "FAILED", "AI_PROVIDER_ERROR",
                "重试耗尽", totalDuration);
        throw lastProviderException != null
                ? lastProviderException
                : new AIProviderException("AI 调用失败", false, null);
    }

    // ============================================================
    // 事务性子方法（REQUIRES_NEW 确保独立持久化）
    // ============================================================

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AIInvocation startInvocation(InvocationSpec spec) {
        AIInvocation invocation = AIInvocation.builder()
                .capability(spec.capability())
                .businessType(spec.businessType())
                .businessId(spec.businessId())
                .status("PENDING")
                .operatorId(spec.operatorId())
                .startedAt(LocalDateTime.now())
                .attemptCount(0)
                .build();
        return invocationRepository.save(invocation);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAttempt(Long invocationId, int attemptIndex, String provider,
                              String model, String promptVersion, String status,
                              Integer httpStatus, String errorType, String errorMessage,
                              String requestSummary, String responseSummary, long durationMs) {
        AIInvocationAttempt attempt = AIInvocationAttempt.builder()
                .invocationId(invocationId)
                .provider(provider)
                .model(model)
                .promptVersion(promptVersion)
                .status(status)
                .httpStatus(httpStatus)
                .errorType(errorType)
                .errorMessage(truncate(errorMessage, ERROR_MESSAGE_MAX))
                .requestSummary(truncate(requestSummary, REQUEST_SUMMARY_MAX))
                .responseSummary(truncate(responseSummary, RESPONSE_SUMMARY_MAX))
                .durationMs(durationMs)
                .attemptIndex(attemptIndex)
                .startedAt(LocalDateTime.now().minusNanos(durationMs * 1_000_000))
                .finishedAt(LocalDateTime.now())
                .build();
        attemptRepository.save(attempt);
    }

    /**
     * 成功 attempt 的简化重载（无错误信息）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAttempt(Long invocationId, int attemptIndex, String provider,
                              String model, String promptVersion, String status,
                              String requestSummary, String responseSummary, long durationMs) {
        recordAttempt(invocationId, attemptIndex, provider, model, promptVersion,
                status, null, null, null, requestSummary, responseSummary, durationMs);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateAttemptCount(Long invocationId, int attemptCount) {
        invocationRepository.findById(invocationId).ifPresent(inv -> {
            inv.setAttemptCount(attemptCount);
            invocationRepository.save(inv);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finishInvocation(Long invocationId, String status,
                                 String errorType, String errorMessage, long durationMs) {
        invocationRepository.findById(invocationId).ifPresent(inv -> {
            inv.setStatus(status);
            inv.setErrorType(errorType);
            inv.setErrorMessage(truncate(errorMessage, ERROR_MESSAGE_MAX));
            inv.setDurationMs(durationMs);
            inv.setFinishedAt(LocalDateTime.now());
            invocationRepository.save(inv);
        });
    }

    // ============================================================
    // 工具方法
    // ============================================================

    private String resolveProviderErrorType(AIProviderException e) {
        if (e.getHttpStatus() == null) {
            return "AI_PROVIDER_TIMEOUT";
        }
        if (e.getHttpStatus() >= 500) {
            return "AI_PROVIDER_HTTP_5XX";
        }
        if (e.getHttpStatus() >= 400) {
            return "AI_PROVIDER_HTTP_4XX";
        }
        return "AI_PROVIDER_ERROR";
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    // ============================================================
    // 数据结构
    // ============================================================

    /**
     * 调用规格
     */
    public record InvocationSpec(
            String capability,
            String businessType,
            Long businessId,
            Long operatorId,
            String sanitizedInput,
            String systemPrompt,
            String promptVersion) {
    }

    /**
     * 调用结果
     */
    public record InvokeResult<T>(
            T result,
            boolean mock,
            Long invocationId) {
    }
}
