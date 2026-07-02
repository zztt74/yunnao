# 后端 AI 实现说明：真实上手网页问题修复

日期：2026-07-03

---

## B-HW-02：处方患者端可见性

**问题**：患者处方列表缺少展示字段，DRAFT 处方对患者暴露。

**实现**：
- PrescriptionResponse 新增 `doctorName`、`departmentName`、`patientName`、`updatedAt`，toResponse 中查询 Doctor/Patient/Department 填充
- PrescriptionRepository 新增 `findByPatientIdAndStatusIn`，getPrescriptionsByPatient 判断 PATIENT 角色时仅返回 CONFIRMED/VOIDED
- 新增常量 `PATIENT_VISIBLE_STATUSES = List.of("CONFIRMED", "VOIDED")`

## B-HW-03：关闭管理员首次登录强制改密

**问题**：mustChangePassword 贯穿登录和拦截逻辑。

**实现**：
- AdminInitializer：`mustChangePassword(false)`
- JwtAuthenticationFilter：移除 mustChangePassword 拦截块和 ALLOWED_PATHS_WHEN_MUST_CHANGE
- AdminUserService.resetPassword：不再设置 `mustChangePassword(true)`

## B-HW-04：用户管理列表补齐电话和最后登录

**问题**：缺少 lastLoginAt，医生/患者创建时未同步电话姓名。

**实现**：
- V075 迁移：user_account 增加 `last_login_at`，audit_log 增加 `target_name`
- UserAccount 实体新增 `lastLoginAt` 字段
- AuthService.login：登录成功后 `user.setLastLoginAt(LocalDateTime.now())`
- DoctorService.createDoctor：同步 realName/phone/email 至 UserAccount
- PatientService.register：同步 realName/phone 至 UserAccount
- AdminUserResponse：新增 `lastLoginAt` 字段
- 合并 resetFailedLogin 中的 save 与 lastLoginAt 的 save 为单次保存，避免测试中 double-save

## B-HW-05：新增医生接口 400 与错误响应

**问题**：缺少 phone/email 字段和校验。

**实现**：
- DoctorCreateRequest 新增 `phone`（@Pattern 手机号）和 `email`（@Email）字段
- DoctorService.createDoctor 中 UserAccount builder 使用 `blankToNull` 处理空值

## B-HW-06：患者管理列表能力确认

**问题**：缺少账号筛选和 username 展示。

**实现**：
- PatientRepository.searchPatients 新增 `keyword` 参数，LEFT JOIN UserAccount 模糊匹配 username
- PatientService.listPatients 签名增加 keyword 参数
- PatientController 列表接口增加 `keyword` 请求参数
- PatientResponse 新增 `username` 字段，toResponse 查询 UserAccount 填充

## B-HW-07：AI 分诊多轮上下文真实生效

**问题**：多轮默认方法忽略 history。

**实现**：
- AITriageServiceImpl：覆写 `analyze(request, history, round)`，内部方法 `analyzeWithHistory` 统一处理
- 将 history 中的 USER/ASSISTANT 内容拼入 sanitizedInput（便于 Mock 关键词路由覆盖前序症状）
- InvocationSpec 新增 `history` 字段，透传给 AIProviderRequest
- AIProviderRequest 新增 `history` 字段及兼容构造函数
- HttpLLMProvider.buildRequestBody：history 消息按顺序拼接到 system 与当前 user 之间
- MockAIProvider 不需额外改动（关键词路由已通过 sanitizedInput 中的历史内容覆盖）

## B-HW-08：统计驾驶舱接口 500 修复

（前一轮已完成）

## B-HW-09：登录日志真实记录用户名和角色

**问题**：登录时安全上下文未建立，operatorType 统一为 USER/SYSTEM。

**实现**：
- AuditAspect：AUTH_LOGIN 场景专用 `extractLoginOperatorInfo`
  - 成功时从 LoginResponse 获取 userId、username、roles
  - 失败时从 LoginRequest 参数获取输入用户名
- `resolveOperatorType`：从角色集合/列表中提取优先级 ADMIN > DOCTOR > PATIENT

## B-HW-10：操作日志语义补齐

**问题**：operatorType 无角色语义，缺少目标名称。

**实现**：
- AuditLog 实体新增 `targetName` 字段（V075 迁移已加列）
- AuditLogResponse 新增 `targetName` 字段
- AuditAspect：已认证请求 operatorType 改为真实角色（ADMIN/DOCTOR/PATIENT）
- 新增 `extractTargetName`：通过反射从方法参数/返回值的 `name()` 方法提取目标名称

## B-HW-11：AI 调用审计供应商和模型

**问题**：AIInvocationResponse 不含 provider/model。

**实现**：
- AIInvocationResponse 新增 `provider`、`model` 字段
- `from(entity, latestAttempt)` 重载方法从最新 attempt 提取 provider/model
- `mapModelDisplay`：deepseek-* → "v4 flash"
- HttpLLMProvider.name() → "DeepSeek"，MockAIProvider.name() → "Mock"
- AuditService 新增 `getLatestAttempt` 方法
- AIInvocationAttemptRepository 新增 `findByInvocationIdOrderByAttemptIndexDesc`
- AuditController 列表/详情接口调用 getLatestAttempt 填充 provider/model
