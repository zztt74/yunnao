package com.neusoft.cloudbrain.statistics.repository;

import com.neusoft.cloudbrain.statistics.dto.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 统计 Repository
 *
 * 核心原则（来自 11_功能需求.md 第15.5节）：
 * - 后端使用数据库聚合
 * - 前端不下载全量数据自行统计
 * - 筛选条件必须进入查询
 * - 空数据需要正确展示
 *
 * 所有查询使用 SQL 聚合函数（COUNT, SUM, AVG），不加载全量到内存。
 */
@Slf4j
@Repository
public class StatisticsRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 仪表盘概览：今日数据
     *
     * - 今日挂号量
     * - 今日完成就诊量
     * - 当前出诊医生数（有进行中就诊的医生）
     * - 当前可用设备数
     * - 高优先级分诊数
     */
    public DashboardSummary getDashboardSummary(LocalDateTime dayStart, LocalDateTime dayEnd) {
        Long todayAppointments = safeCount(
                "SELECT COUNT(*) FROM appointment WHERE booked_at >= ?1 AND booked_at < ?2",
                dayStart, dayEnd);

        Long todayCompletedEncounters = safeCount(
                "SELECT COUNT(*) FROM encounter WHERE status = 'COMPLETED' AND completed_at >= ?1 AND completed_at < ?2",
                dayStart, dayEnd);

        Long onDutyDoctors = safeCount(
                "SELECT COUNT(DISTINCT doctor_id) FROM encounter WHERE status IN ('IN_PROGRESS', 'WAITING_EXAM')");

        Long availableDevices = safeCount(
                "SELECT COUNT(*) FROM device WHERE status = 'AVAILABLE'");

        Long highPriorityTriage = safeCount(
                "SELECT COUNT(*) FROM triage_record WHERE ai_priority = 'HIGH' AND created_at >= ?1 AND created_at < ?2",
                dayStart, dayEnd);

        Long totalPatients = safeCount("SELECT COUNT(*) FROM patient");

        return new DashboardSummary(
                todayAppointments,
                todayCompletedEncounters,
                onDutyDoctors,
                availableDevices,
                highPriorityTriage,
                totalPatients);
    }

    /**
     * 每日门诊量趋势
     *
     * 统计口径：已完成 Encounter 数
     * 取消数按 cancelled_at 统计，解决 cancelled encounter 无 completed_at 问题
     */
    @SuppressWarnings("unchecked")
    public List<DailyOutpatientStatistics> getDailyOutpatientStatistics(
            LocalDateTime start, LocalDateTime end, Long departmentId) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT DATE(coalesce_d) AS d, ");
        sql.append("SUM(completed_flag) AS completed, ");
        sql.append("SUM(cancelled_flag) AS cancelled ");
        sql.append("FROM (");
        sql.append("SELECT e.completed_at AS coalesce_d, ");
        sql.append(" 1 AS completed_flag, 0 AS cancelled_flag ");
        sql.append("FROM encounter e ");
        sql.append("WHERE e.status = 'COMPLETED' AND e.completed_at >= ?1 AND e.completed_at < ?2 ");
        if (departmentId != null) {
            sql.append("AND e.department_id = ?3 ");
        }
        sql.append("UNION ALL ");
        sql.append("SELECT e.cancelled_at AS coalesce_d, ");
        sql.append(" 0 AS completed_flag, 1 AS cancelled_flag ");
        sql.append("FROM encounter e ");
        sql.append("WHERE e.status = 'CANCELLED' AND e.cancelled_at >= ?1 AND e.cancelled_at < ?2 ");
        if (departmentId != null) {
            sql.append("AND e.department_id = ?3 ");
        }
        sql.append(") sub ");
        sql.append("GROUP BY DATE(coalesce_d) ");
        sql.append("ORDER BY d");

        Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter(1, start);
        query.setParameter(2, end);
        if (departmentId != null) {
            query.setParameter(3, departmentId);
        }

        return ((List<Object[]>) query.getResultList()).stream()
                .map(row -> new DailyOutpatientStatistics(
                        ((java.sql.Date) row[0]).toLocalDate(),
                        ((Number) row[1]).longValue(),
                        row[2] == null ? 0L : ((Number) row[2]).longValue()))
                .toList();
    }

    /**
     * 医生接诊量排行
     *
     * 统计口径：医生完成的 Encounter 数
     */
    @SuppressWarnings("unchecked")
    public List<DoctorEncounterStatistics> getDoctorEncounterStatistics(
            LocalDateTime start, LocalDateTime end, Long departmentId) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT d.id, d.name, dept.name AS dept_name, COUNT(e.id) AS cnt ");
        sql.append("FROM doctor d ");
        sql.append("JOIN encounter e ON e.doctor_id = d.id AND e.status = 'COMPLETED' ");
        sql.append("AND e.completed_at >= ?1 AND e.completed_at < ?2 ");
        sql.append("LEFT JOIN department dept ON dept.id = d.department_id ");
        if (departmentId != null) {
            sql.append("WHERE d.department_id = ?3 ");
        }
        sql.append("GROUP BY d.id, d.name, dept.name ");
        sql.append("ORDER BY cnt DESC");

        Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter(1, start);
        query.setParameter(2, end);
        if (departmentId != null) {
            query.setParameter(3, departmentId);
        }

        return ((List<Object[]>) query.getResultList()).stream()
                .map(row -> new DoctorEncounterStatistics(
                        ((Number) row[0]).longValue(),
                        (String) row[1],
                        (String) row[2],
                        ((Number) row[3]).longValue()))
                .toList();
    }

    /**
     * 科室门诊量统计
     */
    @SuppressWarnings("unchecked")
    public List<DepartmentOutpatientStatistics> getDepartmentOutpatientStatistics(
            LocalDateTime start, LocalDateTime end) {
        String sql = "SELECT dept.id, dept.name, COUNT(e.id) AS cnt " +
                "FROM department dept " +
                "LEFT JOIN encounter e ON e.department_id = dept.id " +
                "AND e.status = 'COMPLETED' " +
                "AND e.completed_at >= ?1 AND e.completed_at < ?2 " +
                "GROUP BY dept.id, dept.name " +
                "ORDER BY cnt DESC";

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(1, start);
        query.setParameter(2, end);

        return ((List<Object[]>) query.getResultList()).stream()
                .map(row -> new DepartmentOutpatientStatistics(
                        ((Number) row[0]).longValue(),
                        (String) row[1],
                        row[2] == null ? 0L : ((Number) row[2]).longValue()))
                .toList();
    }

    /**
     * 挂号完成率/取消率统计
     *
     * 统计口径：分母包含已取消挂号，分母为 0 时返回 0%
     */
    public AppointmentRateStatistics getAppointmentRateStatistics(
            LocalDateTime start, LocalDateTime end, Long departmentId) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) AS total, ");
        sql.append("COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) AS completed, ");
        sql.append("COUNT(CASE WHEN status = 'CANCELLED' THEN 1 END) AS cancelled ");
        sql.append("FROM appointment WHERE booked_at >= ?1 AND booked_at < ?2");
        if (departmentId != null) {
            sql.append(" AND doctor_id IN (SELECT id FROM doctor WHERE department_id = ?3)");
        }

        Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter(1, start);
        query.setParameter(2, end);
        if (departmentId != null) {
            query.setParameter(3, departmentId);
        }

        Object[] row = (Object[]) query.getSingleResult();
        long total = row[0] == null ? 0L : ((Number) row[0]).longValue();
        long completed = row[1] == null ? 0L : ((Number) row[1]).longValue();
        long cancelled = row[2] == null ? 0L : ((Number) row[2]).longValue();

        double completionRate = total == 0 ? 0.0 : (double) completed / total;
        double cancellationRate = total == 0 ? 0.0 : (double) cancelled / total;

        return new AppointmentRateStatistics(total, completed, cancelled,
                completionRate, cancellationRate);
    }

    /**
     * 设备使用率统计
     *
     * 统计口径（来自 11_功能需求.md 第15.4节）：
     * - 分子：已完成使用记录的时长总和
     * - 分母：统计周期内设备有效管理时长 = end - MAX(start, 设备创建时间)
     *         设备创建时间来自 device_status_history 首条记录（from_status IS NULL）
     * - 使用率限制在 0%-100%，无可用设备或无使用记录时为 0%
     *
     * 注意：完整实现应基于 DeviceStatusHistory 计算设备处于 AVAILABLE/IN_USE
     * 状态的精确时长，当前版本以创建时间为起点做简化近似。
     */
    @SuppressWarnings("unchecked")
    public List<DeviceUsageStatistics> getDeviceUsageStatistics(
            LocalDateTime start, LocalDateTime end, Long departmentId) {
        try {
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT d.id, d.name, d.type, ");
            sql.append("COUNT(CASE WHEN du.status = 'COMPLETED' THEN 1 END) AS usage_count, ");
            sql.append("COALESCE(SUM(CASE WHEN du.status = 'COMPLETED' THEN ");
            sql.append("TIMESTAMPDIFF(SECOND, du.start_time, COALESCE(du.end_time, ?2)) ELSE 0 END), 0) AS usage_seconds, ");
            sql.append("COALESCE(TIMESTAMPDIFF(SECOND, GREATEST(?1, fs.first_seen), ?2), ");
            sql.append("TIMESTAMPDIFF(SECOND, ?1, ?2)) AS managed_seconds ");
            sql.append("FROM device d ");
            sql.append("LEFT JOIN device_usage du ON du.device_id = d.id ");
            sql.append("AND du.start_time >= ?1 AND du.start_time < ?2 ");
            sql.append("LEFT JOIN (");
            sql.append("SELECT device_id, MIN(changed_at) AS first_seen ");
            sql.append("FROM device_status_history ");
            sql.append("GROUP BY device_id");
            sql.append(") fs ON fs.device_id = d.id ");
            if (departmentId != null) {
                sql.append("WHERE d.department_id = ?3 ");
            }
            sql.append("GROUP BY d.id, d.name, d.type, fs.first_seen ");
            sql.append("ORDER BY usage_count DESC");

            Query query = entityManager.createNativeQuery(sql.toString());
            query.setParameter(1, start);
            query.setParameter(2, end);
            if (departmentId != null) {
                query.setParameter(3, departmentId);
            }

            return ((List<Object[]>) query.getResultList()).stream()
                .map(row -> {
                    long usageCount = row[3] == null ? 0L : ((Number) row[3]).longValue();
                    long usageSeconds = row[4] == null ? 0L : ((Number) row[4]).longValue();
                    long managedSeconds = row[5] == null ? 0L : ((Number) row[5]).longValue();
                    double rate = managedSeconds <= 0 ? 0.0 : Math.min(1.0, (double) usageSeconds / managedSeconds);
                    return new DeviceUsageStatistics(
                            ((Number) row[0]).longValue(),
                            (String) row[1],
                            (String) row[2],
                            usageCount,
                            usageSeconds,
                            rate);
                })
                .toList();
        } catch (Exception e) {
            log.warn("设备使用率统计查询失败，返回空列表: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * AI 调用统计
     *
     * 统计口径：按 invocation 统计，重试不重复进入分母
     */
    public AIStatistics getAIStatistics(LocalDateTime start, LocalDateTime end) {
        String sql = "SELECT " +
                "COUNT(*) AS total, " +
                "COUNT(CASE WHEN status = 'SUCCESS' THEN 1 END) AS success, " +
                "COUNT(CASE WHEN status = 'FAILED' THEN 1 END) AS failed, " +
                "COALESCE(AVG(CASE WHEN status = 'SUCCESS' THEN duration_ms END), 0) AS avg_ms " +
                "FROM ai_invocation WHERE started_at >= ?1 AND started_at < ?2";

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(1, start);
        query.setParameter(2, end);

        Object[] row = (Object[]) query.getSingleResult();
        long total = row[0] == null ? 0L : ((Number) row[0]).longValue();
        long success = row[1] == null ? 0L : ((Number) row[1]).longValue();
        long failed = row[2] == null ? 0L : ((Number) row[2]).longValue();
        double avgMs = row[3] == null ? 0.0 : ((Number) row[3]).doubleValue();
        double successRate = total == 0 ? 0.0 : (double) success / total;

        return new AIStatistics(total, success, failed, successRate, avgMs, avgMs / 1000.0);
    }

    /**
     * 按能力分组的 AI 调用统计
     */
    @SuppressWarnings("unchecked")
    public List<AICapabilityStatistics> getAICapabilityStatistics(
            LocalDateTime start, LocalDateTime end) {
        String sql = "SELECT capability, " +
                "COUNT(*) AS total, " +
                "COUNT(CASE WHEN status = 'SUCCESS' THEN 1 END) AS success, " +
                "COALESCE(AVG(CASE WHEN status = 'SUCCESS' THEN duration_ms END), 0) AS avg_ms " +
                "FROM ai_invocation WHERE started_at >= ?1 AND started_at < ?2 " +
                "GROUP BY capability ORDER BY total DESC";

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(1, start);
        query.setParameter(2, end);

        return ((List<Object[]>) query.getResultList()).stream()
                .map(row -> {
                    long total = ((Number) row[1]).longValue();
                    long success = row[2] == null ? 0L : ((Number) row[2]).longValue();
                    double avgMs = row[3] == null ? 0.0 : ((Number) row[3]).doubleValue();
                    double successRate = total == 0 ? 0.0 : (double) success / total;
                    return new AICapabilityStatistics(
                            (String) row[0], total, success, successRate, avgMs);
                })
                .toList();
    }

    private Long countSingle(String sql, Object... params) {
        Query query = entityManager.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) {
            query.setParameter(i + 1, params[i]);
        }
        Object result = query.getSingleResult();
        return result == null ? 0L : ((Number) result).longValue();
    }

    /**
     * 安全计数：查询异常时返回 0，不抛 500
     */
    private Long safeCount(String sql, Object... params) {
        try {
            return countSingle(sql, params);
        } catch (Exception e) {
            log.warn("统计查询失败，返回 0: sql={}, error={}", sql.substring(0, Math.min(60, sql.length())), e.getMessage());
            return 0L;
        }
    }
}
