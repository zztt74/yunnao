package com.neusoft.cloudbrain.audit.aspect;

import com.neusoft.cloudbrain.audit.annotation.Auditable;
import com.neusoft.cloudbrain.audit.entity.AuditLog;
import com.neusoft.cloudbrain.audit.repository.AuditLogRepository;
import com.neusoft.cloudbrain.audit.util.ClientInfo;
import com.neusoft.cloudbrain.auth.dto.AuthPrincipal;
import com.neusoft.cloudbrain.auth.dto.LoginRequest;
import com.neusoft.cloudbrain.auth.dto.LoginResponse;
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
import java.util.List;
import java.util.Set;

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
                recordAuditLog(point, auditable, success, result, errorMessage);
            } catch (Exception e) {
                log.warn("审计日志记录失败，不影响业务: action={}, error={}",
                        auditable.action(), e.getMessage());
            }
        }
    }

    private void recordAuditLog(ProceedingJoinPoint point, Auditable auditable,
                                 boolean success, Object result,
                                 String errorMessage) {
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

        // B-HW-09：登录场景从 LoginRequest 提取 username，从 LoginResponse 提取角色
        if ("AUTH_LOGIN".equals(auditable.action())) {
            extractLoginOperatorInfo(args, result, builder, success);
        } else if (SecurityUtils.isAuthenticated()) {
            // B-HW-10：已认证请求 operatorType 为真实角色
            try {
                AuthPrincipal principal = SecurityUtils.getCurrentUser();
                builder.operatorId(principal.userId())
                        .operatorName(principal.username())
                        .operatorType(resolveOperatorType(principal.roles()));
            } catch (Exception ignored) {
                builder.operatorType("SYSTEM");
            }
        } else {
            builder.operatorType("SYSTEM");
        }

        // 目标 ID 提取
        Long targetId = extractTargetId(method, args, auditable);
        builder.targetId(targetId);

        // B-HW-10：提取目标名称/摘要
        String targetName = extractTargetName(method, args, result, auditable);
        builder.targetName(targetName);

        // 操作详情（脱敏）
        if (auditable.recordDetails()) {
            builder.details(buildDetails(method, args));
        }

        auditLogRepository.save(builder.build());
    }

    /**
     * B-HW-09：登录场景提取操作人信息。
     * - 成功：从返回值 LoginResponse 获取 userId、username、roles
     * - 失败：从 LoginRequest 参数获取输入用户名
     */
    private void extractLoginOperatorInfo(Object[] args, Object result,
                                           AuditLog.AuditLogBuilder builder, boolean success) {
        // 尝试从参数中获取 LoginRequest
        LoginRequest loginRequest = null;
        for (Object arg : args) {
            if (arg instanceof LoginRequest req) {
                loginRequest = req;
                break;
            }
        }

        if (success && result instanceof LoginResponse loginResp) {
            builder.operatorId(loginResp.userId())
                    .operatorName(loginResp.username())
                    .operatorType(resolveOperatorType(loginResp.roles()));
        } else if (loginRequest != null) {
            builder.operatorName(loginRequest.username())
                    .operatorType("UNKNOWN");
        } else {
            builder.operatorType("UNKNOWN");
        }
    }

    /**
     * B-HW-10：从角色集合中确定 operatorType。
     * 优先级 ADMIN > DOCTOR > PATIENT
     */
    private String resolveOperatorType(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return "USER";
        }
        if (roles.contains("ADMIN")) return "ADMIN";
        if (roles.contains("DOCTOR")) return "DOCTOR";
        if (roles.contains("PATIENT")) return "PATIENT";
        return "USER";
    }

    /**
     * B-HW-10：从角色列表中确定 operatorType（LoginResponse 用 List<String>）
     */
    private String resolveOperatorType(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return "USER";
        }
        if (roles.contains("ADMIN")) return "ADMIN";
        if (roles.contains("DOCTOR")) return "DOCTOR";
        if (roles.contains("PATIENT")) return "PATIENT";
        return "USER";
    }

    /**
     * B-HW-10：提取目标名称或摘要。
     * 从方法参数或返回值中提取可读的目标名称，如医生姓名、患者姓名等。
     */
    private String extractTargetName(Method method, Object[] args, Object result, Auditable auditable) {
        // 尝试从参数中提取 name 字段
        for (Object arg : args) {
            if (arg == null) continue;
            // 跳过基本类型和 String
            if (arg instanceof String || arg instanceof Number) continue;
            // 尝试通过反射获取 name 字段
            try {
                var nameMethod = arg.getClass().getMethod("name");
                Object nameValue = nameMethod.invoke(arg);
                if (nameValue instanceof String s && !s.isBlank()) {
                    return truncate(s, 128);
                }
            } catch (NoSuchMethodException ignored) {
                // 无 name 方法，跳过
            } catch (Exception e) {
                log.debug("提取 targetName 失败: {}", e.getMessage());
            }
        }

        // 尝试从返回值提取 name 或 id
        if (result != null) {
            try {
                var nameMethod = result.getClass().getMethod("name");
                Object nameValue = nameMethod.invoke(result);
                if (nameValue instanceof String s && !s.isBlank()) {
                    return truncate(s, 128);
                }
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                log.debug("从返回值提取 targetName 失败: {}", e.getMessage());
            }
        }

        return null;
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
