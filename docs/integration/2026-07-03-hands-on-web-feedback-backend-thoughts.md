# 后端 AI 思考与问题：真实上手网页问题修复

日期：2026-07-03

---

## 一、设计权衡

### 1. 多轮 history 同时拼入 sanitizedInput 和透传给 Provider

**选择**：两路并行。

- sanitizedInput 拼入历史：使 MockAIProvider 的关键词路由能覆盖前序症状（如第一轮"发烧"、第二轮"咳嗽胸闷"→综合路由到内科而非其他科室）
- history 原样透传给 AIProviderRequest：HttpLLMProvider 按 OpenAI Chat 格式拼接为多轮 messages，真实 DeepSeek 可理解对话上下文

**权衡**：sanitizedInput 拼入历史会增加冗余（HttpLLMProvider 同时在 messages 和 user content 中包含历史信息），但保证了 Mock 路由的正确性，且对真实 LLM 无副作用（模型会忽略冗余）。

### 2. operatorType 改为真实角色

**选择**：ADMIN > DOCTOR > PATIENT 优先级。

**权衡**：一个账号可能有多个角色（如 ADMIN+DOCTOR），取最高优先级角色。未来若需更精细的多角色展示，可改为 Set 或逗号分隔字符串，但当前任务书要求"区分 ADMIN/DOCTOR/PATIENT"，单值已满足。

### 3. targetName 反射提取

**选择**：通过反射调用参数/返回值的 `name()` 方法提取。

**权衡**：反射有一定性能开销，但审计日志写入频率不高，可接受。对于无 `name()` 方法的 DTO（如 ResetPasswordRequest），targetName 为 null，前端可降级展示 targetType+targetId。

### 4. 登录日志 operatorId

**选择**：成功时从 LoginResponse 取 userId，失败时 operatorId 为 null。

**权衡**：失败登录时用户可能不存在，无法确定 operatorId，只能记录输入用户名。这符合审计原则：记录"谁尝试了什么"而非"谁成功了"。

---

## 二、自定事项

1. **V075 迁移合并**：将 B-HW-04 的 `last_login_at` 和 B-HW-10 的 `target_name` 合并为一个迁移文件，减少迁移文件数量。
2. **resetFailedLogin 合并**：将原本的 `resetFailedLogin` 方法（含条件 save）合并到 login 方法中直接设置 failedLoginAttempts=0 + lastLoginAt 一次 save，减少数据库写入。
3. **PATIENT_VISIBLE_STATUSES**：仅包含 CONFIRMED 和 VOIDED，不包含任务书中提到的 DISPENSED（当前处方状态机无此状态）。

---

## 三、需要确认的问题

### 1. Demo 环境调度种子数据

V074 迁移已插入演示医生/患者数据。V075 新增的 `last_login_at` 和 `target_name` 列均为可空默认 null，不影响现有种子数据。但如果需要演示"最近登录时间"功能，需要手动触发一次登录或更新种子数据。

### 2. OpenAPI 契约同步

以下接口契约需要前端 AI 或联调 AI 同步更新：
- `GET /api/patients`：新增 `keyword` 参数
- `AdminUserResponse`：新增 `lastLoginAt`
- `PatientResponse`：新增 `username`
- `PrescriptionResponse`：新增 `doctorName`、`departmentName`、`patientName`、`updatedAt`
- `AuditLogResponse`：新增 `targetName`，`operatorType` 值域变更
- `AIInvocationResponse`：新增 `provider`、`model`
- `DoctorCreateRequest`：新增 `phone`、`email`

### 3. 前端调用路径

- 处方患者端列表/详情不再依赖 encounter 上下文补字段，前端可直接使用 PrescriptionResponse 中的 doctorName/departmentName/patientName
- 审计日志 operatorType 从 USER 变为 ADMIN/DOCTOR/PATIENT，前端筛选/展示逻辑需适配
- AI 调用记录的 provider/model 可直接用于展示"供应商"和"模型"列
