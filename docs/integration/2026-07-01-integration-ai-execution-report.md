# 联调 AI 执行报告

日期：2026-07-01  
分支：`codex/integration`  
基线提交：`89ba8d3 feat(backend): deliver B1-B8 admin/doctor/device/triage/audit/patient APIs`
最新收尾提交：`ed3801e feat: complete admin user profile integration`

## 1. 当前结论

本轮报告中的 mock 阻塞点已转为真实接口闭环，并完成动态复跑：

- 管理端用户管理：列表、创建、更新、状态变更、重置密码。
- 管理端用户资料字段：`realName`、`phone`、`email` 已通过 `user_account` 增量迁移持久化。
- 管理端设备管理：设备新增、编辑。
- 管理端分诊记录：管理员全量分页查询。
- 管理端 AI 调用日志：审计表分页查询。
- 医生端个人资料：医生本人资料更新。
- 权限脚本：已验证禁用用户无法登录。
- AI Provider：已验证真实 HTTP Provider smoke；已验证 non-json、invalid-schema、timeout 故障注入降级。
- OpenAPI：新增接口已同步，Redocly 校验无 warning。

本轮没有修改数据库 Flyway 基线脚本；新增增量迁移 `V073__user_account_profile_fields.sql`。没有扩展既有状态机，没有把 AI Key 写入仓库或报告。

## 2. 已完成代码项

后端新增/修改：

- `POST /api/admin/users`
- `GET /api/admin/users`
- `PUT /api/admin/users/{id}`
- `POST /api/admin/users/{id}/status`
- `POST /api/admin/users/{id}/reset-password`
- `POST /api/devices`
- `PUT /api/devices/{id}`
- `GET /api/triage`
- `GET /api/audit/ai/invocations`
- `PUT /api/doctors/me/profile`
- `V073__user_account_profile_fields.sql`

前端新增/修改：

- `frontend/src/api/admin.ts` 已移除所有 `unsupportedAdminFeature(...)` 调用。
- `frontend/src/api/doctor.ts` 的 `updateDoctorProfile` 已接真实接口。
- `frontend/src/views/admin/AdminUsersView.vue` 密码长度校验已与后端 8 位最小长度一致，并已支持创建医生账号所需科室、职称、擅长、学历、年限、简介字段。

联调脚本新增/修改：

- `tests/integration/e2e-real-clinic-flow.mjs` 补充禁用用户登录拒绝验证。
- `tests/integration/ai-provider-fault-injection.mjs` 新增 Provider 故障注入。
- `tests/integration/README.md` 补充运行说明。

契约新增/修改：

- `contracts/openapi.yaml` 补充上述新增联调接口。
- `contracts/openapi.yaml` 已同步管理端用户 `realName/phone/email` 与 `doctorTitle` 枚举。
- `redocly.yaml` 关闭既有 Spring 静态/变量路径导致的 `no-ambiguous-paths` 噪声；新增接口均补充 4XX 响应。

## 3. 验证结果

| 检查项 | 命令 | 结果 |
| --- | --- | --- |
| OpenAPI 校验 | `npm run validate` in `tests/contract` | 通过，无 warning |
| 前端类型检查 | `npm run type-check` in `frontend` | 通过 |
| 前端单测 | `npm test -- --run` in `frontend` | 通过，2 files / 8 tests |
| 前端构建 | `npm run build` in `frontend` | 通过；仍有第三方 Rolldown annotation 和 chunk size warning |
| 后端编译 | `mvn -q -DskipTests compile` in `backend` | 通过 |
| 后端测试 | `mvn test` in `backend` | 通过，388 tests |
| Docker 重建 | `docker compose up -d --build mysql backend frontend` | 通过 |
| Docker 状态 | `docker compose ps` | mysql/backend healthy，frontend up |
| 真实 API 冒烟 | `node tests/integration/smoke-real-api.mjs` | 通过 |
| 完整真实闭环 | `node tests/integration/e2e-real-clinic-flow.mjs` | 通过 |
| 真实 AI Provider smoke | `node tests/integration/smoke-ai-provider.mjs` | 通过 |
| AI Provider 故障注入 | `node tests/integration/ai-provider-fault-injection.mjs` | 通过 |
| 脚本语法 | `node --check tests/integration/e2e-real-clinic-flow.mjs` | 通过 |
| 脚本语法 | `node --check tests/integration/ai-provider-fault-injection.mjs` | 通过 |
| 敏感信息扫描 | `node tests/integration/scan-secrets.mjs` | 通过 |
| 脚手架检查 | `node tests/integration/verify-scaffold.mjs` | 通过 |
| 归属检查 | `node tests/integration/check-ownership.mjs` | 通过 |
| 协作文档检查 | `node tests/integration/check-collaboration-docs.mjs` | 通过 |
| 差异检查 | `git diff --check` | 通过，仅 Windows LF/CRLF 提示 |

## 4. 动态闭环摘要

真实 API 冒烟：

```json
{
  "backendHealth": "UP",
  "frontend": "OK",
  "registerCode": "SUCCESS",
  "loginCode": "SUCCESS",
  "meCode": "SUCCESS",
  "patientId": 28
}
```

完整真实闭环：

```json
{
  "flow": "SUCCESS",
  "patient": "flow_patient_1782867221676335",
  "patientId": 29,
  "doctorId": 1,
  "scheduleId": 5,
  "appointmentId": 9,
  "encounterId": 8,
  "examinationId": 7,
  "deviceId": 1,
  "medicalRecordId": 5,
  "prescriptionId": 5,
  "encounterStatus": "COMPLETED",
  "examStatus": "REVIEWED",
  "medicalRecordStatus": "CONFIRMED",
  "prescriptionStatus": "CONFIRMED",
  "prescriptionAiReviewStatus": "REVIEWED",
  "disabledUserLogin": 401
}
```

AI Provider：

```json
{
  "provider": "HTTP",
  "result": "SUCCESS",
  "assistDiagnosis": "SUCCESS"
}
```

AI Provider 故障注入：

```json
{
  "provider": "HTTP_FAULT_INJECTION",
  "result": "SUCCESS",
  "checks": [
    "provider-non-json-response -> FAILED degraded",
    "provider-invalid-schema -> FAILED degraded",
    "provider-timeout -> FAILED degraded"
  ]
}
```

故障注入脚本结束后已恢复 backend 到原 Compose 环境，并再次验证真实 AI Provider smoke 通过。

## 5. 当前账号与数据

本地联调种子账号保持不变：

```text
admin: 使用 .env 中 INITIAL_ADMIN_USERNAME / INITIAL_ADMIN_PASSWORD；若已被种子脚本改密，则使用 AdminSeed9!2026
doctor_internal_seed / DoctorSeed9!2026
doctor_emergency_seed / DoctorSeed9!2026
patient_seed / PatientSeed9!2026
```

报告和脚本不会打印 token，也不会打印外部 AI Key。

## 6. 剩余风险

- B3 高敏感管理员操作审计日志本轮按用户确认不补，保留为后续安全合规建议。
- B3 角色 DOCTOR↔ADMIN 变更本轮按用户确认不联动 Doctor/DoctorProfile，保留为后续业务规则决策。
- 管理端分诊记录当前返回 `patient-<id>` 作为显示名，不伪造患者姓名；如页面必须显示真实姓名，需要后端 AI 提供分诊记录关联患者姓名的只读聚合 DTO。
- 本轮未做页面级浏览器截图验证；API、构建和端到端脚本均已通过。

## 7. 提交判断

当前状态达到“联调任务书内容已完成，可提交 `codex/integration` 分支”。不建议声称整个项目产品级完工，但本轮联调报告内剩余项已经完成并验证。
