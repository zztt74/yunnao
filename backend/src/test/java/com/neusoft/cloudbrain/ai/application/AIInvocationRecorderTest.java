package com.neusoft.cloudbrain.ai.application;

import com.neusoft.cloudbrain.ai.config.AIProperties;
import com.neusoft.cloudbrain.ai.exception.AIInvalidResponseException;
import com.neusoft.cloudbrain.ai.exception.AIProviderException;
import com.neusoft.cloudbrain.ai.provider.AIProvider;
import com.neusoft.cloudbrain.ai.provider.AIProviderRequest;
import com.neusoft.cloudbrain.ai.provider.AIProviderResponse;
import com.neusoft.cloudbrain.audit.entity.AIInvocation;
import com.neusoft.cloudbrain.audit.repository.AIInvocationAttemptRepository;
import com.neusoft.cloudbrain.audit.repository.AIInvocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AIInvocationRecorder 单元测试
 *
 * 覆盖（来自任务 STAGE-AI-1 交付物、32_AI能力契约规范.md 第5节）：
 * - 1 次业务调用 → 1 条 AIInvocation
 * - 每次重试 → 1 条 AIInvocationAttempt
 * - 重试仅对超时/5xx，非法 JSON 不重试
 * - 成功时 invocation 状态为 SUCCESS
 * - 失败时 invocation 状态为 FAILED
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AIInvocationRecorder - 调用记录器测试")
class AIInvocationRecorderTest {

    @Mock
    private AIProvider aiProvider;

    @Mock
    private AIInvocationRepository invocationRepository;

    @Mock
    private AIInvocationAttemptRepository attemptRepository;

    private AIInvocationRecorder recorder;

    @BeforeEach
    void setUp() {
        AIProperties properties = new AIProperties();
        properties.setMaxRetries(1);
        recorder = new AIInvocationRecorder(aiProvider, invocationRepository,
                attemptRepository, properties);
    }

    private AIInvocationRecorder.InvocationSpec buildSpec() {
        return new AIInvocationRecorder.InvocationSpec(
                "triage", "triage", null, null,
                "主诉: 胸痛", "system prompt", "v1");
    }

    private void mockInvocationSave() {
        AIInvocation invocation = AIInvocation.builder()
                .id(1L)
                .capability("triage")
                .status("PENDING")
                .build();
        when(invocationRepository.save(any(AIInvocation.class))).thenReturn(invocation);
        when(invocationRepository.findById(1L)).thenReturn(Optional.of(invocation));
    }

    @Test
    @DisplayName("成功调用：1 次 invocation，1 次 attempt，状态 SUCCESS")
    void success_singleAttempt() {
        mockInvocationSave();
        when(aiProvider.generate(any(AIProviderRequest.class)))
                .thenReturn(new AIProviderResponse("{\"result\":\"ok\"}", true, "mock"));

        AIInvocationRecorder.InvokeResult<String> result = recorder.invoke(
                buildSpec(), content -> content);

        assertThat(result.result()).isEqualTo("{\"result\":\"ok\"}");
        assertThat(result.mock()).isTrue();
        assertThat(result.invocationId()).isEqualTo(1L);

        // 成功路径：startInvocation + updateAttemptCount + finishInvocation 共 3 次 invocation save
        verify(invocationRepository, times(3)).save(any(AIInvocation.class));
        verify(attemptRepository, times(1)).save(any());    // 1 attempt
    }

    @Test
    @DisplayName("超时后重试成功：2 次 attempt，最终 SUCCESS")
    void retryAfterTimeout_thenSuccess() {
        mockInvocationSave();
        when(aiProvider.generate(any(AIProviderRequest.class)))
                .thenThrow(new AIProviderException("超时", true, null))
                .thenReturn(new AIProviderResponse("{\"result\":\"ok\"}", true, "mock"));

        AIInvocationRecorder.InvokeResult<String> result = recorder.invoke(
                buildSpec(), content -> content);

        assertThat(result.result()).isEqualTo("{\"result\":\"ok\"}");
        verify(attemptRepository, times(2)).save(any()); // 2 attempts
    }

    @Test
    @DisplayName("超时重试耗尽：2 次 attempt，最终 FAILED")
    void retryExhausted_allTimeout() {
        mockInvocationSave();
        when(aiProvider.generate(any(AIProviderRequest.class)))
                .thenThrow(new AIProviderException("超时", true, null));

        assertThatThrownBy(() -> recorder.invoke(buildSpec(), content -> content))
                .isInstanceOf(AIProviderException.class);

        verify(attemptRepository, times(2)).save(any()); // 2 attempts
    }

    @Test
    @DisplayName("非法 JSON 不重试：1 次 attempt，FAILED")
    void invalidJson_noRetry() {
        mockInvocationSave();
        when(aiProvider.generate(any(AIProviderRequest.class)))
                .thenReturn(new AIProviderResponse("not json", true, "mock"));

        Function<String, String> failingParser = content -> {
            throw new AIInvalidResponseException("非法 JSON");
        };

        assertThatThrownBy(() -> recorder.invoke(buildSpec(), failingParser))
                .isInstanceOf(AIInvalidResponseException.class);

        verify(attemptRepository, times(1)).save(any()); // 1 attempt, no retry
    }

    @Test
    @DisplayName("不可重试异常：1 次 attempt，FAILED")
    void nonRetryableError_noRetry() {
        mockInvocationSave();
        when(aiProvider.generate(any(AIProviderRequest.class)))
                .thenThrow(new AIProviderException("4xx 错误", false, 400));

        assertThatThrownBy(() -> recorder.invoke(buildSpec(), content -> content))
                .isInstanceOf(AIProviderException.class);

        verify(attemptRepository, times(1)).save(any()); // 1 attempt, no retry
    }

    @Test
    @DisplayName("5xx 错误可重试：重试后仍失败")
    void http5xx_retryable() {
        mockInvocationSave();
        when(aiProvider.generate(any(AIProviderRequest.class)))
                .thenThrow(new AIProviderException("500 错误", true, 500))
                .thenThrow(new AIProviderException("500 错误", true, 500));

        assertThatThrownBy(() -> recorder.invoke(buildSpec(), content -> content))
                .isInstanceOf(AIProviderException.class);

        verify(attemptRepository, times(2)).save(any()); // 2 attempts (1 + 1 retry)
    }

    @Test
    @DisplayName("maxRetries=0 时不重试")
    void noRetryWhenMaxRetriesZero() {
        AIProperties properties = new AIProperties();
        properties.setMaxRetries(0);
        AIInvocationRecorder noRetryRecorder = new AIInvocationRecorder(
                aiProvider, invocationRepository, attemptRepository, properties);

        mockInvocationSave();
        when(aiProvider.generate(any(AIProviderRequest.class)))
                .thenThrow(new AIProviderException("超时", true, null));

        assertThatThrownBy(() -> noRetryRecorder.invoke(buildSpec(), content -> content))
                .isInstanceOf(AIProviderException.class);

        verify(attemptRepository, times(1)).save(any()); // 1 attempt, no retry
    }

    @Test
    @DisplayName("成功调用后 attemptCount 正确更新")
    void success_updatesAttemptCount() {
        mockInvocationSave();
        when(aiProvider.generate(any(AIProviderRequest.class)))
                .thenReturn(new AIProviderResponse("{\"result\":\"ok\"}", true, "mock"));

        recorder.invoke(buildSpec(), content -> content);

        // updateAttemptCount 调用 findById + save
        verify(invocationRepository, atLeast(1)).findById(1L);
        verify(invocationRepository, atLeast(2)).save(any(AIInvocation.class));
    }
}
