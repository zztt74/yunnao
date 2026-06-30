package com.neusoft.cloudbrain.user.dto;

/**
 * 管理员更新用户请求
 *
 * 任务书要求「更新姓名、手机号、邮箱、角色」，但 user_account 表当前无
 * realName/phone/email 字段（禁止擅自改表），故第一阶段仅支持角色更新。
 * 姓名/手机/邮箱待联调 AI 确认扩展表结构后再支持。
 */
public record AdminUserUpdateRequest(
        String role
) {
}
