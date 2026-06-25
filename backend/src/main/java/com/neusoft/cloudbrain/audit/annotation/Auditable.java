package com.neusoft.cloudbrain.audit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计注解
 *
 * 标注在需要审计的业务方法上，由 {@link com.neusoft.cloudbrain.audit.aspect.AuditAspect} 拦截并记录审计日志。
 *
 * 审计范围（来自 11_功能需求.md 第16节）：
 * - 关键业务操作（挂号、接诊、确诊、处方确认、完成就诊）
 * - 数据变更操作（修改、作废、取消）
 * - 安全相关操作（登录、退出、密码修改、锁定/解锁）
 * - 管理操作（科室配置、排班调整、设备停用）
 *
 * 使用示例：
 * <pre>
 * &#64;Auditable(action = "PRESCRIPTION_CONFIRM", targetType = "PRESCRIPTION")
 * public void confirmPrescription(Long prescriptionId) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /**
     * 操作动作，如 PRESCRIPTION_CONFIRM
     */
    String action();

    /**
     * 目标类型，如 PRESCRIPTION
     */
    String targetType();

    /**
     * 目标 ID 参数名，默认取第一个参数。
     * 当方法有多个参数时，通过参数名指定哪个是目标 ID。
     */
    String targetIdParam() default "";

    /**
     * 是否记录方法参数作为详情，默认 true。
     * 敏感参数会被 AuditAspect 脱敏。
     */
    boolean recordDetails() default true;
}
