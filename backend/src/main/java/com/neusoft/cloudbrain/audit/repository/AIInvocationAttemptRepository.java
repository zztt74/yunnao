package com.neusoft.cloudbrain.audit.repository;

import com.neusoft.cloudbrain.audit.entity.AIInvocationAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * AI 调用尝试记录 Repository
 */
public interface AIInvocationAttemptRepository extends JpaRepository<AIInvocationAttempt, Long> {

    /**
     * 按调用记录 ID 查询所有尝试
     */
    List<AIInvocationAttempt> findByInvocationIdOrderByAttemptIndexAsc(Long invocationId);

    /**
     * B-HW-11：按调用记录 ID 查询所有尝试（倒序，取最新 attempt）
     */
    List<AIInvocationAttempt> findByInvocationIdOrderByAttemptIndexDesc(Long invocationId);
}
