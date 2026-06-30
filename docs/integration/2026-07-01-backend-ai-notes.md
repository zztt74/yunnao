# 后端 AI 交付实施笔记（B1–B8）

日期：2026-07-01
分支：`codex/integration`
对应任务书：`2026-07-01-backend-ai-delivery-plan.md`

本文档记录实施过程中的设计决策、字段映射、待联调 AI / 契约组确认的问题，以及给前端 AI 的交接要点。实施完成后会更新最终状态。

---

## 0. 通用约定（基于现有代码模式确认）

- **响应结构**：`ApiResponse<T>`（code/message/data/traceId），成功 code=`SUCCESS`。
- **分页**：`PageResponse<T>`（items/page/pageSize/total/totalPages），page 从 1 开始；Spring Data Page 0-based 自动 +1。
- **权限校验**：两种并存写法
  - 类级 `@PreAuthorize("hasRole('ADMIN')")`（StatisticsController、AuditController）
  - 方法内 `SecurityUtils.getCurrentUser()` + 角色判断 + `throw new BusinessException("PERMISSION_DENIED", "...", 403)`（DoctorController、PatientController）
  - 新接口按所属 Controller 既有风格延续。
- **错误码**：各模块 `XxxErrorCode` 枚举，`toException()` 生成 `BusinessException(code, message, httpStatus)`。
- **traceId**：从 `httpRequest.getAttribute("traceId")` 取。
- **登录状态校验**（B3 依赖）：`AuthService.checkAccountStatus` 校验 `enabled`（禁用抛 AUTH_ACCOUNT_DISABLED）和 `accountNonLocked`（锁定抛 AUTH_ACCOUNT_LOCKED）。所以「禁用不能登录」「锁定不能登录」由现有逻辑保证。

---

## B1：医生本人资料更新 `PUT /api/doctors/me`

### 现有模型
- `Doctor`：userId, departmentId, name, title, specialty, status, createdAt, updatedAt
- `DoctorProfile`：doctorId, education, experienceYears, introduction, createdAt, updatedAt

### 决策
- Doctor/DoctorProfile **均无 phone/email 字段**，故只更新任务书允许的「已有字段」：`specialty`（Doctor）+ `education`/`experienceYears`/`introduction`（DoctorProfile）。
- 不允许自行修改 `departmentId`/`status`/`title`（任务书要求）。
- 权限：当前用户必须含 `DOCTOR` 角色；按 `doctor.user_id` 查档案，查不到返回 403（非医生账号）。
- 路径：新增 `@PutMapping("/me")`，与既有 `@PutMapping("/{id}")` 共存。Spring MVC 优先匹配字面量 `/me`，不冲突。

### 待确认
- 无。

---

## B2：设备档案创建和更新 `POST /api/devices`、`PUT /api/devices/{id}`

### 现有模型 vs 任务书建议字段
| 任务书建议 | 现有 Device 字段 | 处理 |
|---|---|---|
| code | code（unique） | 直接用 |
| name | name | 直接用 |
| category | **type** | 映射：category → type |
| location | location | 直接用 |
| departmentId | departmentId | 直接用 |
| applicableItems | **无** | 暂不实现（不擅自加列） |
| status | status | 创建时默认 AVAILABLE |
| enabled | **无独立字段** | 由 status 表达（DISABLED=停用） |

### 决策
- 遵循任务书「不擅自修改数据库基线」，按现有字段实现，创建/更新支持：code, name, type, departmentId, location, manufacturer, model, serialNumber, notes, purchaseDate, warrantyUntil。创建时 status 默认 `AVAILABLE`。
- `code` 唯一：表已有 unique 约束 + Service 层 `existsByCode` 校验返回 409。
- PUT **不接受 status**：状态变更必须走既有 `POST /api/devices/{id}/status`（任务书要求「基础档案更新不要绕过状态历史」）。
- 「更新时不允许破坏正在使用中的设备状态」：PUT 只改基础档案字段，不动 status；IN_USE 设备仍可改基础信息（如名称、位置），不影响占用状态。
- 权限：管理员（参考 DeviceController 既无类级注解，按一致风格用 SecurityUtils 校验 ADMIN）。

### 待联调 AI / 契约组确认
- **字段映射差异**：category/applicableItems/enabled 与现有表不符。是否接受映射？或后续是否要扩展 Device 表（需走变更流程，不在本任务范围）。
- 新增 `POST/PUT /api/devices` 未进 OpenAPI 主契约，需联调 AI 同步。

---

## B3：管理员用户管理 `/api/admin/users`

### 现有模型
- `UserAccount`：username, passwordHash, enabled, accountNonLocked, accountNonExpired, credentialsNonExpired, mustChangePassword, failedLoginAttempts, lockoutUntil, tokenVersion, roles。**无 realName/phone/email 字段。**

### 决策
- 包路径：放 `com.neusoft.cloudbrain.user`（已预留 package-info：「User account and role assignments」），Controller 路径 `/api/admin/users`。
- **列表**：分页，按角色（role）/状态（enabled）/关键字（username 模糊）筛选。
- **创建**：
  - `ADMIN`：只建账号。
  - `DOCTOR`：请求须带医生档案字段（departmentId、name、title 等），同步建 Doctor + DoctorProfile；不带则 400 拒绝。
  - `PATIENT`：不支持后台创建，返回 400 提示走自注册（任务书建议）。
- **更新**：任务书要求「更新姓名、手机号、邮箱、角色」。但 UserAccount 无姓名/手机/邮箱字段 → **第一阶段只支持更新角色**；姓名/手机/邮箱待联调 AI 确认是否扩展 UserAccount 表（禁止擅自改表）。
- **状态变更**：`enabled=true`→启用；`enabled=false`→禁用；`accountNonLocked=false`→锁定。
- **重置密码**：设新 passwordHash + `mustChangePassword=true`（强制下次改密，安全合理）。
- 权限：管理员。

### 待联调 AI / 契约组确认（重点）
- **UserAccount 缺 realName/phone/email**：「更新姓名、手机号、邮箱」无法完整实现。是否扩展 user_account 表？若扩展需走数据库基线变更流程。当前先只交付「角色更新 + 状态 + 重置密码 + 列表 + 创建」。
- 创建 DOCTOR 同步建档案的字段集合需与前端 DOCTOR 创建表单对齐。

---

## B4：管理员全量分诊记录查询 `GET /api/triage`

### 决策
- 既有 `GET /api/triage/{id}`、`GET /api/triage/patient/{patientId}`，新增 `GET /api/triage`（根列表）不冲突。
- 查询参数：page, pageSize/size（B8 兼容）, patientId, priority（→aiPriority）, departmentId（→mappedDepartmentId）, startDate, endDate。
- 权限：管理员可全量；非管理员返回 403（患者仍走 `/patient/{patientId}` 看自己的）。
- Repository：用 `@Query` 多条件分页（参数为空时跳过该条件）。

### 待确认
- 无。

---

## B5：AI 调用日志分页列表 `GET /api/audit/ai/invocations`

### 决策
- 既有 `GET /api/audit/ai/invocations/{id}`，新增 `GET /api/audit/ai/invocations`（列表）。Spring MVC 字面量与 `{id}` 共存 OK。
- AuditController 类级 `@PreAuthorize("hasRole('ADMIN')")`，新接口自动限管理员。
- 查询参数：page, pageSize/size, capability, success, businessType, startDate, endDate。
- `success` 映射：`true`→status=SUCCESS；`false`→status!=SUCCESS（含 FAILED/PENDING）；不传→全部。
- 脱敏：沿用 `AIInvocationResponse`/`AIInvocationAttemptResponse`（实体本身不存 API Key；attempt 只存 requestSummary/responseSummary 摘要，非完整请求头）。满足「不泄露 AI Key、完整敏感请求头」。

### 待确认
- 无。

---

## B6：患者总数统计

### 决策
- 在 `DashboardSummary` 增加 `totalPatientCount` 字段（record 加字段）。
- `StatisticsRepository.getDashboardSummary` 增加 `SELECT COUNT(*) FROM patient`。
- 注意：`StatisticsService.getDashboardSummary` 有 `@Cacheable(key='dashboard')`。加字段后缓存对象结构变化，部署时需清缓存（开发期无影响）。

### 待联调 AI / 前端 AI 确认
- DashboardSummary 是 record，加字段是**前向不兼容变更**，前端需同步读取 `totalPatientCount`。需告知前端 AI。

---

## B7：患者管理员分页查询 `GET /api/patients`

### 决策
- 既有 `GET /api/patients/search`（返回 List，非分页）、`/me`、`/{id}`。新增 `GET /api/patients`（根列表分页）不冲突。
- 查询参数：page, pageSize/size, name（模糊）, phone（精确）, status。
- 权限：管理员。
- 保留 `/search` 不动（B8 不破坏兼容）。

### 待确认
- 无。

---

## B8：统一分页参数

### 决策
- 新接口（B4/B5/B7）Controller 层同时接收 `pageSize` 与 `size`，提供工具方法 `resolvePageSize(Integer pageSize, Integer size)`：
  - 优先 `pageSize`，为空则用 `size`，都为空默认 20；上限 100。
- 不改动既有接口（DoctorController 用 pageSize、Examination/Device/Audit 用 size），只在新接口兼容两者。
- 最终主参数名由契约组决定。

### 待确认
- 契约组确认主参数名后，统一收敛。

---

## 全局交接要点（给前端 AI / 联调 AI）

1. 所有新接口未进 OpenAPI 主契约，交付说明里提供完整路径/方法/权限/DTO，由联调 AI 同步。
2. B3 因 UserAccount 表字段限制，第一阶段不交付「姓名/手机/邮箱更新」。
3. B2 字段映射：前端发起设备创建/更新时按现有字段（type 而非 category）。
4. B6 DashboardSummary 加字段，前端需同步。
5. 所有新分页接口兼容 `page`+`pageSize`/`size`，page 从 1 开始。

---

## 复查后补充决策（2026-07-01）

B1–B8 交付后逐文件复查 + 重跑测试（387 pass）。对能自查/自修的项直接处理，未达任务书红线的才保留为待确认。各任务补充决策如下。

### B2 补充
- **departmentId 存在性校验**：原实现只查 `existsByCode`，未校验 departmentId 是否存在（对比 `DoctorService` 有注入 `DepartmentRepository`）。补注入 `DepartmentRepository`，createDevice/updateDevice 调 `validateDepartment(departmentId)`：非空且 `existsById=false` 抛 `DEVICE_DEPARTMENT_NOT_FOUND`(404)。
- **并发竞态兜底**：`existsByCode` 预检查 + save 存在竞态窗口。save 包 try-catch `DataIntegrityViolationException` 转 `DEVICE_CODE_DUPLICATED`(409)，DB unique 约束兜底，不再返回 500。
- 新增测试：`createDevice_shouldThrowWhenDepartmentNotFound`（departmentId=999→404）；既有两测试补 `departmentRepository.existsById` mock。

### B3 补充
- **AdminUserResponse 去 tokenVersion**：原 DTO 含 `tokenVersion`，但前端用户管理无需此内部安全字段。去掉以做信息最小化。`from()` 同步去掉 `u.getTokenVersion()`。
- **并发竞态兜底**：createAdmin 的 save 包 try-catch `DataIntegrityViolationException` 转 `USER_USERNAME_DUPLICATED`(409)，兜底用户名并发竞态。
- **AdminUserControllerTest（MockMvc）**：原仅有 `AdminUserServiceTest`（Service 层 14 个），Controller 层权限分支无测试。新增 5 个 MockMvc 测试（standaloneSetup + GlobalExceptionHandler + TraceIdFilter）：
  - 非管理员 list/create/resetPassword → 403×3
  - 管理员 list → 200
  - page=2 转 0-based 校验
  - 通过 `SecurityContextHolder.getContext().setAuthentication()` 注入 `AuthPrincipal` 控制权限，无需 `mockStatic(SecurityUtils.class)`。

### 复查验证项（无需改动，记录结论）
- **JWT tokenVersion**：`JwtAuthenticationFilter`（第 65–69 行）+ `JwtService`（第 86–88 行）均校验 tokenVersion，过滤器还校验 `enabled`/`accountNonLocked`。B3 禁用/锁定/重置密码对已登录会话立即生效，安全语义成立。
- **createDoctor 副作用**：`AdminUserService.createUser` 复用 `DoctorService.createDoctor`，无额外副作用。

### 仍待他人确认（7 项）
见 `2026-07-01-backend-ai-thoughts-and-questions.md` 第九节。均因触及任务书红线（DB 基线 / OpenAPI / 前端 / 医疗业务规则 / 契约组决策）而不能自处理。
