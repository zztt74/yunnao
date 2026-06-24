package com.neusoft.cloudbrain.doctor.exception;

import com.neusoft.cloudbrain.common.exception.BusinessException;

/**
 * 医生模块错误码及对应的 HTTP 状态码
 *
 * 状态码规则（参考 30_接口数据与错误契约.md 第7节）：
 * - 400 参数错误
 * - 403 无权限
 * - 404 资源不存在
 * - 409 业务冲突
 */
public enum DoctorErrorCode {

    DOCTOR_NOT_FOUND("医生不存在", 404),
    DOCTOR_PERMISSION_DENIED("无权限执行该操作", 403),
    USER_USERNAME_DUPLICATED("用户名已存在", 409),
    DEPARTMENT_NOT_FOUND("科室不存在", 404),
    DEPARTMENT_DISABLED("科室已停用，不能添加医生", 409),
    SYSTEM_ROLE_NOT_INITIALIZED("系统角色未初始化", 500);

    private final String message;
    private final int httpStatus;

    DoctorErrorCode(String message, int httpStatus) {
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String getMessage() {
        return message;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    /**
     * 转换为业务异常
     */
    public BusinessException toException() {
        return new BusinessException(name(), message, httpStatus);
    }
}
