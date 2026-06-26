package com.neusoft.cloudbrain.ai.integration;

import com.neusoft.cloudbrain.ai.application.AIInvocationRecorder;
import com.neusoft.cloudbrain.ai.config.AIProperties;
import com.neusoft.cloudbrain.ai.exception.AIProviderException;
import com.neusoft.cloudbrain.ai.provider.AIProvider;
import com.neusoft.cloudbrain.ai.provider.AIProviderRequest;
import com.neusoft.cloudbrain.ai.provider.AIProviderResponse;
import com.neusoft.cloudbrain.audit.entity.AIInvocation;
import com.neusoft.cloudbrain.audit.entity.AIInvocationAttempt;
import com.neusoft.cloudbrain.audit.repository.AIInvocationAttemptRepository;
import com.neusoft.cloudbrain.audit.repository.AIInvocationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AIInvocationRecorder 持久化集成测试
 *
 * 验证（IT-3）：
 * - 超时重试后 Invocation 状态为 FAILED，attemptCount 正确
 * - 2 条 Attempt 正确写入，含 error_type/error_message
 * - createdAt/updatedAt 非空，updatedAt 在更新后刷新
 * - REQUIRES_NEW 事务传播使 Attempt 独立持久化
 *
 * 运行方式：mvn test -Dtest="AIInvocationRecorderPersistenceIT"
 */
@ActiveProfiles("test")
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("IT3 - AIInvocationRecorder 持久化")
class AIInvocationRecorderPersistenceIT {

    @Autowired
    private AIInvocationRepository invocationRepository;

    @Autowired
    private AIInvocationAttemptRepository attemptRepository;

    @AfterEach
    void cleanUp() {
        attemptRepository.deleteAll();
        invocationRepository.deleteAll();
    }

    @Test
    @DisplayName("超时重试耗尽：1 条 Invocation(FAILED) + 2 条 Attempt，updatedAt 已刷新")
    void retryTimeout_exhausted_persistsCorrectly() {
        // ============================================================
        // Setup
        // ============================================================
        AIProperties properties = new AIProperties();
        properties.setMaxRetries(1); // 1 retry = 2 attempts total

        AIProvider mockProvider = mock(AIProvider.class);
        when(mockProvider.name()).thenReturn("MOCK");
        when(mockProvider.generate(any(AIProviderRequest.class)))
                .thenThrow(new AIProviderException("连接超时", true, null))
                .thenThrow(new AIProviderException("连接超时", true, null));

        AIInvocationRecorder recorder = new AIInvocationRecorder(
                mockProvider, invocationRepository, attemptRepository, properties);

        var spec = new AIInvocationRecorder.InvocationSpec(
                "triage", "triage", null, null,
                "测试输入", "system prompt", "v1");

        // ============================================================
        // Execute
        // ============================================================
        assertThatThrownBy(() -> recorder.invoke(spec, content -> content))
                .isInstanceOf(AIProviderException.class)
                .satisfies(ex -> assertThat(((AIProviderException) ex).getMessage()).contains("连接超时"));

        // ============================================================
        // Verify: Invocation
        // ============================================================
        List<AIInvocation> allInvocations = invocationRepository.findAll();
        assertThat(allInvocations).hasSize(1);
        AIInvocation inv = allInvocations.get(0);

        assertThat(inv.getCapability()).isEqualTo("triage");
        assertThat(inv.getStatus()).isEqualTo("FAILED");
        assertThat(inv.getAttemptCount()).isEqualTo(2);
        assertThat(inv.getErrorType()).isEqualTo("AI_PROVIDER_TIMEOUT");
        assertThat(inv.getErrorMessage()).contains("连接超时");
        assertThat(inv.getDurationMs()).isPositive();

        // 验证时间戳
        assertThat(inv.getCreatedAt()).isNotNull();
        assertThat(inv.getUpdatedAt()).isNotNull();
        assertThat(inv.getStartedAt()).isNotNull();
        assertThat(inv.getFinishedAt()).isNotNull();
        assertThat(inv.getUpdatedAt()).isAfterOrEqualTo(inv.getCreatedAt());

        // ============================================================
        // Verify: Attempts
        // ============================================================
        List<AIInvocationAttempt> attempts =
                attemptRepository.findByInvocationIdOrderByAttemptIndexAsc(inv.getId());
        assertThat(attempts).hasSize(2);

        // Attempt 1
        AIInvocationAttempt attempt1 = attempts.get(0);
        assertThat(attempt1.getAttemptIndex()).isEqualTo(1);
        assertThat(attempt1.getStatus()).isEqualTo("TIMEOUT");
        assertThat(attempt1.getErrorType()).isEqualTo("AI_PROVIDER_TIMEOUT");
        assertThat(attempt1.getErrorMessage()).contains("连接超时");
        assertThat(attempt1.getProvider()).isEqualTo("MOCK");
        assertThat(attempt1.getDurationMs()).isPositive();
        assertThat(attempt1.getStartedAt()).isNotNull();
        assertThat(attempt1.getFinishedAt()).isNotNull();

        // Attempt 2
        AIInvocationAttempt attempt2 = attempts.get(1);
        assertThat(attempt2.getAttemptIndex()).isEqualTo(2);
        assertThat(attempt2.getStatus()).isEqualTo("TIMEOUT");
        assertThat(attempt2.getErrorType()).isEqualTo("AI_PROVIDER_TIMEOUT");
        assertThat(attempt2.getErrorMessage()).contains("连接超时");
        assertThat(attempt2.getProvider()).isEqualTo("MOCK");
        assertThat(attempt2.getDurationMs()).isPositive();

        // Attempt 时间顺序
        assertThat(attempt2.getStartedAt()).isAfterOrEqualTo(attempt1.getStartedAt());
    }

    @Test
    @DisplayName("成功调用：1 条 Invocation(SUCCESS) + 1 条 Attempt，updatedAt 刷新")
    void success_persistsCorrectly() {
        // ============================================================
        // Setup
        // ============================================================
        AIProperties properties = new AIProperties();
        properties.setMaxRetries(1);

        AIProvider mockProvider = mock(AIProvider.class);
        when(mockProvider.name()).thenReturn("MOCK");
        when(mockProvider.generate(any(AIProviderRequest.class)))
                .thenReturn(new AIProviderResponse(
                        "{\"departmentCode\":\"DEPT_INTERNAL\",\"priority\":\"MEDIUM\"}",
                        true, "mock"));

        AIInvocationRecorder recorder = new AIInvocationRecorder(
                mockProvider, invocationRepository, attemptRepository, properties);

        var spec = new AIInvocationRecorder.InvocationSpec(
                "triage", "triage", null, null,
                "发热咳嗽", "system prompt", "v1");

        // ============================================================
        // Execute
        // ============================================================
        AIInvocationRecorder.InvokeResult<String> result = recorder.invoke(
                spec, content -> content);

        // ============================================================
        // Verify: Invocation
        // ============================================================
        List<AIInvocation> allInvocations = invocationRepository.findAll();
        assertThat(allInvocations).hasSize(1);
        AIInvocation inv = allInvocations.get(0);

        assertThat(inv.getStatus()).isEqualTo("SUCCESS");
        assertThat(inv.getAttemptCount()).isEqualTo(1);
        assertThat(inv.getCreatedAt()).isNotNull();
        assertThat(inv.getUpdatedAt()).isNotNull();
        assertThat(inv.getUpdatedAt()).isAfterOrEqualTo(inv.getCreatedAt());

        // ============================================================
        // Verify: Attempt
        // ============================================================
        List<AIInvocationAttempt> attempts =
                attemptRepository.findByInvocationIdOrderByAttemptIndexAsc(inv.getId());
        assertThat(attempts).hasSize(1);

        AIInvocationAttempt attempt = attempts.get(0);
        assertThat(attempt.getAttemptIndex()).isEqualTo(1);
        assertThat(attempt.getStatus()).isEqualTo("SUCCESS");
        assertThat(attempt.getProvider()).isEqualTo("MOCK");

        // Verify result
        assertThat(result.result()).contains("DEPT_INTERNAL");
        assertThat(result.mock()).isTrue();
        assertThat(result.invocationId()).isEqualTo(inv.getId());
    }
}
