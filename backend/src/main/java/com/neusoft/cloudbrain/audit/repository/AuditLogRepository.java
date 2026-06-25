package com.neusoft.cloudbrain.audit.repository;

import com.neusoft.cloudbrain.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志 Repository
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * 按操作人查询审计日志
     */
    Page<AuditLog> findByOperatorIdOrderByCreatedAtDesc(Long operatorId, Pageable pageable);

    /**
     * 按目标查询审计日志
     */
    Page<AuditLog> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, Long targetId, Pageable pageable);

    /**
     * 按操作动作和时间范围查询
     */
    Page<AuditLog> findByActionAndCreatedAtBetweenOrderByCreatedAtDesc(
            String action, LocalDateTime start, LocalDateTime end, Pageable pageable);

    /**
     * 按时间范围查询
     */
    Page<AuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime start, LocalDateTime end, Pageable pageable);

    /**
     * 按时间范围和操作动作列表统计（用于审计趋势）
     */
    @Query("SELECT a.action, COUNT(a) FROM AuditLog a " +
            "WHERE a.createdAt BETWEEN :start AND :end " +
            "GROUP BY a.action ORDER BY COUNT(a) DESC")
    List<Object[]> countByActionBetween(@Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);
}
