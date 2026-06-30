package com.neusoft.cloudbrain.user.exception;

import com.neusoft.cloudbrain.common.exception.BusinessException;
import lombok.Getter;

/**
 * 用户管理错误码（管理员用户管理 B3）
 */
@Getter
public enum UserErrorCode {

    USER_PERMISSION_DENIED("无权限执行该操作", 403),
    USER_NOT_FOUND("用户不存在", 404),
    ROLE_NOT_FOUND("角色不存在", 404),
    USER_USERNAME_DUPLICATED("用户名已存在", 409),
    USER_ROLE_NOT_SUPPORTED("不支持的角色，仅支持 ADMIN/DOCTOR", 400),
    PATIENT_CREATE_NOT_ALLOWED("患者账号请走患者自助注册接口", 400),
    DOCTOR_PROFILE_REQUIRED("创建医生账号需提供医生档案字段（departmentId、doctorName）", 400),
    USER_ACTION_NOT_SUPPORTED("不支持的状态操作，仅支持 ENABLE/DISABLE/LOCK", 400);

    private final String message;
    private final int httpStatus;

    UserErrorCode(String message, int httpStatus) {
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public BusinessException toException() {
        return new BusinessException(name(), message, httpStatus);
    }
}
