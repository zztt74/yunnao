# 后端 AI 交付报告：真实上手网页问题修复

日期：2026-07-03
分支：`codex/integration`
依据文档：`docs/integration/2026-07-02-hands-on-web-feedback-backend-taskbook.md`

---

## 一、修改文件清单

### 后端主代码

| 文件 | 修改内容 |
|------|----------|
| `auth/config/AdminInitializer.java` | B-HW-03：`mustChangePassword(false)` |
| `auth/security/JwtAuthenticationFilter.java` | B-HW-03：移除 mustChangePassword 拦截逻辑 |
| `auth/entity/UserAccount.java` | B-HW-04：新增 `lastLoginAt` 字段 |
| `auth/service/AuthService.java` | B-HW-04：登录成功记录 `lastLoginAt`；合并 `resetFailedLogin` 为单次 save |
| `user/dto/AdminUserResponse.java` | B-HW-04：新增 `lastLoginAt` 字段 |
| `user/dto/ResetPasswordRequest.java` | B-HW-03：Javadoc 更新 |
| `user/service/AdminUserService.java` | B-HW-03：重置密码不再强制 `mustChangePassword`；B-HW-05：createDoctor 传入 phone/email |
| `doctor/dto/DoctorCreateRequest.java` | B-HW-05：新增 `phone`、`email` 字段及校验 |
| `doctor/service/DoctorService.java` | B-HW-05：createDoctor 同步 phone/email 至 UserAccount |
| `patient/dto/PatientResponse.java` | B-HW-06：新增 `username` 字段 |
| `patient/repository/PatientRepository.java` | B-HW-06：searchPatients 增加 `keyword` 参数（关联 user_account.username） |
| `patient/service/PatientService.java` | B-HW-06：listPatients 增加 keyword 参数，toResponse 填充 username |
| `patient/controller/PatientController.java` | B-HW-06：列表接口增加 `keyword` 请求参数 |
| `prescription/dto/PrescriptionResponse.java` | B-HW-02：新增 `doctorName`、`departmentName`、`patientName`、`updatedAt` |
| `prescription/repository/PrescriptionRepository.java` | B-HW-02：新增 `findByPatientIdAndStatusIn` |
| `prescription/service/PrescriptionService.java` | B-HW-02：患者端过滤 DRAFT、toResponse 填充关联展示字段 |
| `statistics/repository/StatisticsRepository.java` | B-HW-08：修复 SQL `created_at`→`changed_at`；safeCount 容错 |
| `ai/api/AITriageService.java` | B-HW-07：默认多轮方法（已有，无需改） |
| `ai/application/AITriageServiceImpl.java` | B-HW-07：覆写多轮 `analyze(request, history, round)`，history 拼入 sanitizedInput |
| `ai/application/AIInvocationRecorder.java` | B-HW-07：`InvocationSpec` 增加 `history` 字段，透传给 AIProviderRequest |
| `ai/provider/AIProviderRequest.java` | B-HW-07：新增 `history` 字段及兼容构造函数 |
| `ai/provider/HttpLLMProvider.java` | B-HW-07：buildRequestBody 支持多轮 messages；B-HW-11：name()→"DeepSeek" |
| `ai/provider/MockAIProvider.java` | B-HW-11：name()→"Mock" |
| `audit/entity/AuditLog.java` | B-HW-10：新增 `targetName` 字段 |
| `audit/dto/AuditLogResponse.java` | B-HW-10：新增 `targetName` 字段 |
| `audit/dto/AIInvocationResponse.java` | B-HW-11：新增 `provider`、`model` 字段，model 映射 deepseek→"v4 flash" |
| `audit/aspect/AuditAspect.java` | B-HW-09/10：登录提取真实 username/角色；operatorType 改为真实角色；新增 targetName 提取 |
| `audit/service/AuditService.java` | B-HW-11：新增 `getLatestAttempt` 方法 |
| `audit/repository/AIInvocationAttemptRepository.java` | B-HW-11：新增 `findByInvocationIdOrderByAttemptIndexDesc` |
| `audit/controller/AuditController.java` | B-HW-11：列表/详情接口传递最新 attempt 填充 provider/model |
| `patient/service/PatientService.java` | B-HW-04：register 同步 realName/phone 至 UserAccount |

### 数据库迁移

| 文件 | 说明 |
|------|------|
| `db/migration/V075__last_login_at_and_audit_target_name.sql` | B-HW-04 + B-HW-10：user_account 增加 `last_login_at`，audit_log 增加 `target_name` |

### 测试文件（更新）

| 文件 | 说明 |
|------|------|
| `DoctorServiceTest.java` | 适配 DoctorCreateRequest 新字段顺序 |
| `AdminUserControllerTest.java` | 适配 AdminUserResponse 新增 lastLoginAt |
| `AdminUserServiceTest.java` | B-HW-03：重置密码断言 mustChangePassword=false |
| `PatientServiceTest.java` | B-HW-06：listPatients 新增 keyword 参数、新增 keyword 筛选测试 |
| `PatientControllerTest.java` | 适配 PatientResponse 新增 username、listPatients 5 参数 |
| `PrescriptionControllerTest.java` | 适配 PrescriptionResponse 新字段 |
| `PrescriptionCheckControllerTest.java` | 适配 PrescriptionResponse 新字段 |
| `PrescriptionServiceTest.java` | 注入 DepartmentRepository mock |
| `AITriageServiceImplTest.java` | B-HW-07：新增多轮上下文测试 |
| `MockAIProviderTest.java` | B-HW-11：name 断言 "Mock" |
| `MockAIProviderIT.java` | B-HW-11：name 断言 "Mock" |
| `AuthServiceTest.java` | 适配合并后的单次 save |

---

## 二、每个 B-HW 任务完成状态

| 任务 | 状态 | 说明 |
|------|------|------|
| B-HW-02 | ✅ 完成 | 患者端处方过滤 DRAFT，响应补齐 doctorName/departmentName/patientName/updatedAt |
| B-HW-03 | ✅ 完成 | 关闭管理员首次登录强制改密，移除 JwtAuthenticationFilter 拦截 |
| B-HW-04 | ✅ 完成 | 登录记录 lastLoginAt，医生创建同步 phone/email/realName，患者注册同步 realName/phone |
| B-HW-05 | ✅ 完成 | DoctorCreateRequest 新增 phone/email 校验，createDoctor 同步至 UserAccount |
| B-HW-06 | ✅ 完成 | 患者列表支持 keyword（账号模糊）、PatientResponse 新增 username |
| B-HW-07 | ✅ 完成 | 多轮分诊覆写，history 拼入 sanitizedInput + 透传给 Provider，HttpLLMProvider 支持 messages |
| B-HW-08 | ✅ 完成 | SQL 字段修复 changed_at，safeCount 容错 |
| B-HW-09 | ✅ 完成 | 登录日志从 LoginResponse 提取真实 username/角色，operatorType 改为 ADMIN/DOCTOR/PATIENT |
| B-HW-10 | ✅ 完成 | AuditLog 新增 targetName，operatorType 改为真实角色，反射提取目标名称 |
| B-HW-11 | ✅ 完成 | AIInvocationResponse 新增 provider/model，DeepSeek 调用显示 "DeepSeek"/"v4 flash"，Mock 显示 "Mock" |

---

## 三、接口字段变更说明

### 新增字段

| 接口/DTO | 新增字段 | 来源任务 |
|----------|----------|----------|
| `AdminUserResponse` | `lastLoginAt` | B-HW-04 |
| `PatientResponse` | `username` | B-HW-06 |
| `PrescriptionResponse` | `doctorName`, `departmentName`, `patientName`, `updatedAt` | B-HW-02 |
| `AuditLogResponse` | `targetName` | B-HW-10 |
| `AIInvocationResponse` | `provider`, `model` | B-HW-11 |
| `DoctorCreateRequest` | `phone`, `email` | B-HW-05 |

### 新增请求参数

| 接口 | 参数 | 说明 | 来源任务 |
|------|------|------|----------|
| `GET /api/patients` | `keyword` | 账号关键字模糊筛选 | B-HW-06 |

### 行为变更

| 变更 | 说明 | 来源任务 |
|------|------|----------|
| `operatorType` | 从 USER/SYSTEM 改为 ADMIN/DOCTOR/PATIENT/UNKNOWN/SYSTEM | B-HW-09/10 |
| Provider name | HttpLLMProvider→"DeepSeek"，MockAIProvider→"Mock" | B-HW-11 |
| 模型显示映射 | deepseek-* → "v4 flash" | B-HW-11 |
| mustChangePassword | 不再强制为 true | B-HW-03 |
| 患者处方列表 | DRAFT 状态不对 PATIENT 角色暴露 | B-HW-02 |

---

## 四、需要前端 AI 或联调 AI 配合的事项

1. **B-HW-02**：PrescriptionResponse 新增 4 个字段，前端类型需同步更新
2. **B-HW-05**：DoctorCreateRequest 新增 phone/email 字段，前端表单需同步
3. **B-HW-06**：患者列表接口新增 keyword 参数，PatientResponse 新增 username 字段
4. **B-HW-09/10**：审计日志 operatorType 从 USER 变为真实角色，新增 targetName 字段
5. **B-HW-11**：AI 调用记录新增 provider/model 字段，前端可展示供应商和模型信息
6. **OpenAPI**：以上字段变更需同步到 `contracts/openapi.yaml`

---

## 五、测试结果

```
Tests run: 723, Failures: 0, Errors: 0, Skipped: 0
```

全部 723 个单元测试通过。
