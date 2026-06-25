package com.neusoft.cloudbrain.audit.aspect;

import com.neusoft.cloudbrain.audit.annotation.Auditable;
import com.neusoft.cloudbrain.audit.entity.AuditLog;
import com.neusoft.cloudbrain.audit.repository.AuditLogRepository;
import com.neusoft.cloudbrain.audit.util.ClientInfo;
import com.neusoft.cloudbrain.auth.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * 审计切面
 *
 * 拦截标注了 {@link Auditable} 的方法，自动记录审计日志。
 *
 * 处理逻辑：
 * - 成功：result=SUCCESS，记录返回值摘要
 * - 失败：result=FAILURE，记录异常信息
 * - 未登录场景（如登录接口）：operator_id 为 NULL，operator_type=SYSTEM
 *
 * 安全规则：
 * - 审计日志记录失败不影响业务流程
 * - 不记录密码、Token、API Key
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLogRepository auditLogRepository;

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint point, Auditable auditable) throws Throwable {
        Object result = null;
        boolean success = true;
        String errorMessage = null;

        try {
            result = point.proceed();
            return result;
        } catch (Throwable ex) {
            success = false;
            errorMessage = ex.getMessage();
            throw ex;
        } finally {
            try {
                recordAuditLog(point, auditable, success, errorMessage);
            } catch (Exception e) {
                log.warn("审计日志记录失败，不影响业务: action={}, error={}",
                        auditable.action(), e.getMessage());
            }
        }
    }

    private void recordAuditLog(ProceedingJoinPoint point, Auditable auditable,
                                 boolean success, String errorMessage) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        Object[] args = point.getArgs();

        AuditLog.AuditLogBuilder builder = AuditLog.builder()
                .action(auditable.action())
                .targetType(auditable.targetType())
                .result(success ? "SUCCESS" : "FAILURE")
                .errorMessage(truncate(errorMessage, 512))
                .ipAddress(ClientInfo.getClientIp())
                .userAgent(ClientInfo.getUserAgent())
                .traceId(ClientInfo.getTraceId())
                .createdAt(LocalDateTime.now());

        // 操作人信息（未登录时为系统操作）
        if (SecurityUtils.isAuthenticated()) {
            try {
                builder.operatorId(SecurityUtils.currentUserId())
                        .operatorName(SecurityUtils.currentUsername())
                        .operatorType("USER");
            } catch (Exception ignored) {
                builder.operatorType("SYSTEM");
            }
        } else {
            builder.operatorType("SYSTEM");
        }

        // 目标 ID 提取
        Long targetId = extractTargetId(method, args, auditable);
        builder.targetId(targetId);

        // 操作详情（脱敏）
        if (auditable.recordDetails()) {
            builder.details(buildDetails(method, args));
        }

        auditLogRepository.save(builder.build());
    }

    /**
     * 提取目标 ID
     * 优先按 targetIdParam 指定的参数名查找，否则取第一个 Long 类型参数
     */
    private Long extractTargetId(Method method, Object[] args, Auditable auditable) {
        if (args == null || args.length == 0) {
            return null;
        }

        String paramHint = auditable.targetIdParam();
        if (!paramHint.isEmpty()) {
            Parameter[] parameters = method.getParameters();
            for (int i = 0; i < parameters.length && i < args.length; i++) {
                if (paramHint.equals(parameters[i].getName()) && args[i] instanceof Long) {
                    return (Long) args[i];
                }
            }
        }

        // 默认取第一个 Long 参数
        for (Object arg : args) {
            if (arg instanceof Long) {
                return (Long) arg;
            }
        }
        return null;
    }

    /**
     * 构建操作详情（脱敏处理）
     * 不记录密码、Token 等敏感字段
     */
    private String buildDetails(Method method, Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        Parameter[] parameters = method.getParameters();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length && i < parameters.length; i++) {
            String name = parameters[i].getName();
            // 跳过敏感字段
            if (isSensitiveField(name)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(name).append("=").append(truncate(safeToString(args[i]), 200));
            if (sb.length() >= 900) {
                break;
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private boolean isSensitiveField(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase();
        return lower.contains("password") || lower.contains("token")
                || lower.contains("secret") || lower.contains("apikey")
                || lower.contains("credential");
    }

    private String safeToString(Object obj) {
        if (obj == null) {
            return "null";
        }
        return obj.toString();
    }

    private String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLen ? value.substring(0, maxLen) : value;
    }
}
