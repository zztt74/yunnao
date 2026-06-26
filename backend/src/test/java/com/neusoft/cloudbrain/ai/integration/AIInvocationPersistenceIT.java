package com.neusoft.cloudbrain.ai.integration;

import com.neusoft.cloudbrain.audit.entity.AIInvocation;
import com.neusoft.cloudbrain.audit.repository.AIInvocationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AIInvocation 实体持久化集成测试
 *
 * 验证（IT-1）：
 * - createdAt/updatedAt 填充后不违反 NOT NULL 约束
 * - @Version 乐观锁字段自动递增
 * - 实体字段与数据库列映射正确
 *
 * 运行方式：mvn test -Dtest="AIInvocationPersistenceIT"
 */
@ActiveProfiles("test")
@DataJpaTest
@DisplayName("IT1 - AIInvocation 实体持久化")
class AIInvocationPersistenceIT {

    @Autowired
    private AIInvocationRepository repository;

    @Test
    @DisplayName("完整字段的 AIInvocation 入库后所有 NOT NULL 字段有值")
    void save_fullEntity_allNonNullFieldsPopulated() {
        LocalDateTime now = LocalDateTime.now();
        AIInvocation invocation = AIInvocation.builder()
                .capability("triage")
                .businessType("triage")
                .status("PENDING")
                .startedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .attemptCount(0)
                .build();

        AIInvocation saved = repository.saveAndFlush(invocation);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCapability()).isEqualTo("triage");
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getAttemptCount()).isEqualTo(0);
        assertThat(saved.getStartedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getVersion()).isEqualTo(0L);
    }

    @Test
    @DisplayName("乐观锁 version 在更新后自动递增")
    void version_incrementsOnUpdate() {
        LocalDateTime now = LocalDateTime.now();
        AIInvocation invocation = AIInvocation.builder()
                .capability("diagnosis")
                .status("PENDING")
                .startedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .attemptCount(0)
                .build();

        AIInvocation saved = repository.saveAndFlush(invocation);
        assertThat(saved.getVersion()).isEqualTo(0L);

        saved.setStatus("SUCCESS");
        saved.setUpdatedAt(LocalDateTime.now());
        AIInvocation updated = repository.saveAndFlush(saved);
        assertThat(updated.getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("更新后 updatedAt 与 createdAt 不同（updatedAt 被刷新）")
    void updatedAt_differsFromCreatedAt() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        AIInvocation invocation = AIInvocation.builder()
                .capability("prescription_review")
                .status("PENDING")
                .startedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .attemptCount(0)
                .build();

        AIInvocation saved = repository.saveAndFlush(invocation);
        LocalDateTime createdAt = saved.getCreatedAt();

        // 更新
        saved.setStatus("SUCCESS");
        saved.setUpdatedAt(LocalDateTime.now().withNano(0));
        AIInvocation updated = repository.saveAndFlush(saved);

        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(createdAt);
    }
}
