package com.neusoft.cloudbrain.department.exception;

import com.neusoft.cloudbrain.common.exception.BusinessException;

/**
 * 科室模块错误码及对应的 HTTP 状态码
 *
 * 状态码规则（参考 30_接口数据与错误契约.md 第7节）：
 * - 400 参数错误
 * - 403 无权限
 * - 404 资源不存在
 * - 409 业务冲突
 */
public enum DepartmentErrorCode {

    DEPARTMENT_NOT_FOUND("科室不存在", 404),
    DEPARTMENT_PARENT_NOT_FOUND("父科室不存在", 404),
    DEPARTMENT_CODE_DUPLICATED("科室编码已存在", 409),
    DEPARTMENT_DISABLED("科室已停用", 409),
    DEPARTMENT_PERMISSION_DENIED("无权限执行该操作", 403);

    private final String message;
    private final int httpStatus;

    DepartmentErrorCode(String message, int httpStatus) {
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
