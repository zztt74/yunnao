package com.neusoft.cloudbrain.ai.provider;

import com.neusoft.cloudbrain.ai.config.AIProperties;
import com.neusoft.cloudbrain.ai.exception.AIProviderException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * HTTP LLM Provider（真实 AI 调用）
 *
 * 职责（来自任务 STAGE-AI-1 交付物、13_AI能力集成AI任务书.md 第8节）：
 * - 使用 RestClient 调用外部 AI API（OpenAI 兼容格式）
 * - 超时 8 秒（由 AIConfig 中 RestClient Bean 控制）
 * - 单次调用，不内部重试（重试由 AIInvocationRecorder 统一编排）
 * - 超时/5xx 抛出可重试的 AIProviderException
 * - 4xx 抛出不可重试的 AIProviderException
 * - 不记录 API Key 到日志
 *
 * 说明：
 * - 本 Provider 不创建对外的 HTTP 契约，仅为出站调用
 * - API Key 来自环境变量，通过 Authorization 头传递，不写入日志或调用记录
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "HTTP")
@RequiredArgsConstructor
public class HttpLLMProvider implements AIProvider {

    private final RestClient aiRestClient;
    private final AIProperties aiProperties;

    @Override
    public String name() {
        return "HTTP";
    }

    @Override
    public AIProviderResponse generate(AIProviderRequest request) {
        String apiUrl = aiProperties.getHttp().getApiUrl();
        if (apiUrl == null || apiUrl.isBlank()) {
            throw new AIProviderException("AI API URL 未配置", false, null);
        }

        try {
            Map<String, Object> requestBody = buildRequestBody(request);

            Map<String, Object> response = aiRestClient.post()
                    .uri(apiUrl)
                    .header("Authorization", "Bearer " + aiProperties.getHttp().getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new AIProviderException("AI API 返回空响应", false, null);
            }

            String content = extractContent(response);
            String model = aiProperties.getHttp().getModel();

            return new AIProviderResponse(content, false, model);

        } catch (ResourceAccessException e) {
            // 网络超时或连接异常 → 可重试
            log.warn("AI Provider 请求超时或网络异常: {}", e.getMessage());
            throw new AIProviderException("AI 请求超时或网络异常", true, null, e);
        } catch (HttpClientErrorException e) {
            // 4xx → 不可重试
            log.warn("AI Provider 客户端错误: HTTP {}", e.getStatusCode().value());
            throw new AIProviderException(
                    "AI 客户端错误: HTTP " + e.getStatusCode().value(),
                    false, e.getStatusCode().value(), e);
        } catch (HttpServerErrorException e) {
            // 5xx → 可重试
            log.warn("AI Provider 服务端错误: HTTP {}", e.getStatusCode().value());
            throw new AIProviderException(
                    "AI 服务端错误: HTTP " + e.getStatusCode().value(),
                    true, e.getStatusCode().value(), e);
        } catch (AIProviderException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI Provider 调用异常: {}", e.getMessage(), e);
            throw new AIProviderException("AI 调用异常: " + e.getMessage(), false, null, e);
        }
    }

    /**
     * 构建请求体（OpenAI 兼容 Chat Completion 格式）
     */
    private Map<String, Object> buildRequestBody(AIProviderRequest request) {
        return Map.of(
                "model", aiProperties.getHttp().getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content",
                                request.systemPrompt() != null ? request.systemPrompt() : ""),
                        Map.of("role", "user", "content", request.sanitizedInput())),
                "temperature", 0.3);
    }

    /**
     * 从响应中提取内容（兼容 OpenAI 格式）
     */
    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> response) {
        Object choices = response.get("choices");
        if (choices instanceof List<?> choiceList && !choiceList.isEmpty()) {
            Object firstChoice = choiceList.get(0);
            if (firstChoice instanceof Map<?, ?> choiceMap) {
                Object message = choiceMap.get("message");
                if (message instanceof Map<?, ?> messageMap) {
                    Object content = messageMap.get("content");
                    if (content instanceof String s) {
                        return s;
                    }
                }
            }
        }
        throw new AIProviderException("AI 响应格式不符合预期", false, null);
    }
}
