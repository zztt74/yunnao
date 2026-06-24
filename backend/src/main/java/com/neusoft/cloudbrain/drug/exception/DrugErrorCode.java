package com.neusoft.cloudbrain.drug.exception;

import com.neusoft.cloudbrain.common.exception.BusinessException;

/**
 * 药品模块错误码及对应的 HTTP 状态码
 *
 * 状态码规则（参考 30_接口数据与错误契约.md 第7节）：
 * - 404 资源不存在
 */
public enum DrugErrorCode {

    DRUG_NOT_FOUND("药品不存在", 404);

    private final String message;
    private final int httpStatus;

    DrugErrorCode(String message, int httpStatus) {
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
