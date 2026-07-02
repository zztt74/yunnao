package com.neusoft.cloudbrain.audit.repository;

import com.neusoft.cloudbrain.audit.entity.AIInvocation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/**
 * AI 调用记录 Repository
 *
 * 统计口径（来自 11_功能需求.md 第15.4节）：
 * - AI 成功率：合法结构化业务响应数 / AI 业务调用数
 * - 同一次业务调用内的重试不重复进入分母（按 invocation 统计）
 */
public interface AIInvocationRepository extends JpaRepository<AIInvocation, Long> {

    /**
     * AI 调用统计
     *
     * @return [0]=成功数, [1]=总调用数, [2]=平均耗时(ms)
     */
    @Query("SELECT " +
            "COUNT(CASE WHEN i.status = 'SUCCESS' THEN 1 END), " +
            "COUNT(i), " +
            "COALESCE(AVG(CASE WHEN i.status = 'SUCCESS' THEN i.durationMs END), 0) " +
            "FROM AIInvocation i " +
            "WHERE i.startedAt BETWEEN :start AND :end")
    Object[] getAICallStatistics(@Param("start") LocalDateTime start,
                                 @Param("end") LocalDateTime end);

    /**
     * 按能力分组的 AI 调用统计
     *
     * @return 每行 [capability, successCount, totalCount, avgDurationMs]
     */
    @Query("SELECT i.capability, " +
            "COUNT(CASE WHEN i.status = 'SUCCESS' THEN 1 END), " +
            "COUNT(i), " +
            "COALESCE(AVG(CASE WHEN i.status = 'SUCCESS' THEN i.durationMs END), 0) " +
            "FROM AIInvocation i " +
            "WHERE i.startedAt BETWEEN :start AND :end " +
            "GROUP BY i.capability")
    java.util.List<Object[]> getAICallStatisticsByCapability(@Param("start") LocalDateTime start,
                                                              @Param("end") LocalDateTime end);

    /**
     * 管理端 AI 调用日志分页查询（多条件）
     *
     * @param capability  能力筛选（可空）
     * @param success     成功筛选（可空）：true=仅 SUCCESS，false=非 SUCCESS（含 FAILED/PENDING）
     * @param businessType 业务类型筛选（可空）
     * @param startDate   开始时间（可空，闭区间，按 startedAt）
     * @param endDate     结束时间（可空，开区间，按 startedAt）
     */
    @Query("SELECT i FROM AIInvocation i WHERE " +
            "(:capability IS NULL OR i.capability = :capability) " +
            "AND (:businessType IS NULL OR i.businessType = :businessType) " +
            "AND (:success IS NULL " +
            "     OR (:success = true AND i.status = 'SUCCESS') " +
            "     OR (:success = false AND i.status <> 'SUCCESS')) " +
            "AND (:startDate IS NULL OR i.startedAt >= :startDate) " +
            "AND (:endDate IS NULL OR i.startedAt < :endDate) " +
            "ORDER BY i.startedAt DESC")
    Page<AIInvocation> searchInvocations(
            @Param("capability") String capability,
            @Param("businessType") String businessType,
            @Param("success") Boolean success,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);
}
