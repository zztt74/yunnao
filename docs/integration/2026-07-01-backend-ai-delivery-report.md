# 后端 AI 剩余交付任务 交付说明（B1–B8）

日期：2026-07-01
分支：`codex/integration`
对应任务书：`2026-07-01-backend-ai-delivery-plan.md`
设计笔记：`2026-07-01-backend-ai-notes.md`

---

## 1. 测试命令与结果

```bash
cd backend
mvn test
```

结果：

```
Tests run: 388, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

原 360 个测试 + B1–B8 新增 21 个 + 复查后新增 6 个 + Q2/Q6 收尾新增 1 个，全部通过，无回归。

git 分支状态：

```
## codex/integration...origin/codex/integration
```

---

## 2. 完成的接口清单

| 编号 | 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|---|
| B1 | PUT | `/api/doctors/me` | DOCTOR | 医生更新本人资料（专长/学历/从业年限/简介） |
| B2 | POST | `/api/devices` | ADMIN | 新增设备档案 |
| B2 | PUT | `/api/devices/{id}` | ADMIN | 更新设备档案基础信息（不改 status/code） |
| B3 | GET | `/api/admin/users` | ADMIN | 用户分页列表（角色/状态/关键字筛选） |
| B3 | POST | `/api/admin/users` | ADMIN | 创建用户（ADMIN/DOCTOR，PATIENT 拒绝） |
| B3 | PUT | `/api/admin/users/{id}` | ADMIN | 更新用户（角色 + realName/phone/email；不联动医生档案） |
| B3 | POST | `/api/admin/users/{id}/status` | ADMIN | 启用/禁用/锁定 |
| B3 | POST | `/api/admin/users/{id}/reset-password` | ADMIN | 重置密码 |
| B4 | GET | `/api/triage` | ADMIN | 全量分诊记录分页（患者/优先级/科室/时间筛选） |
| B5 | GET | `/api/audit/ai/invocations` | ADMIN | AI 调用日志分页（能力/成功/业务类型/时间筛选） |
| B6 | GET | `/api/statistics/dashboard` | ADMIN | 扩展返回 `totalPatientCount`（真实患者总数） |
| B7 | GET | `/api/patients` | ADMIN | 患者分页列表（姓名/手机号/状态筛选） |
| B8 | — | — | — | 新接口兼容 `pageSize` 和 `size`，统一转换 |

分页约定：`page` 从 1 开始；`pageSize`/`size` 优先 `pageSize`，默认 20，上限 100。

---

## 3. 修改的模块和文件

### 新增文件
- `common/api/PageUtils.java` — B8 分页工具
- `doctor/dto/DoctorProfileUpdateRequest.java` — B1
- `device/dto/DeviceCreateRequest.java`、`DeviceUpdateRequest.java` — B2
- `user/exception/UserErrorCode.java` — B3
- `user/dto/`（AdminUserResponse、AdminUserCreateRequest、AdminUserUpdateRequest、UserStatusChangeRequest、ResetPasswordRequest）— B3
- `user/service/AdminUserService.java` — B3
- `user/controller/AdminUserController.java` — B3
- `user/service/AdminUserServiceTest.java` — B3 测试（14 个）
- `docs/integration/2026-07-01-backend-ai-notes.md` — 设计笔记

### 修改文件
- `doctor/controller/DoctorController.java`、`doctor/service/DoctorService.java` — B1（+3 测试）
- `device/controller/DeviceController.java`、`device/service/DeviceService.java`、`device/repository/DeviceRepository.java`、`device/exception/DeviceErrorCode.java` — B2（+4 测试）
- `audit/controller/AuditController.java`、`audit/service/AuditService.java`、`audit/repository/AIInvocationRepository.java` — B5
- `auth/repository/UserAccountRepository.java` — B3 搜索查询
- `patient/controller/PatientController.java`、`patient/service/PatientService.java`、`patient/repository/PatientRepository.java` — B7
- `statistics/dto/DashboardSummary.java`、`statistics/repository/StatisticsRepository.java` — B6（+1 测试同步）
- `triage/controller/TriageController.java`、`triage/service/TriageService.java`、`triage/repository/TriageRecordRepository.java` — B4

### 复查后补充改动（2026-07-01）
- `user/controller/AdminUserControllerTest.java` — 新增（B3 权限 MockMvc 测试 5 个：非管理员 403×3、管理员 200、分页转换）
- `user/dto/AdminUserResponse.java` — 去掉 `tokenVersion` 字段（信息最小化，前端无需）
- `device/service/DeviceService.java` — 注入 `DepartmentRepository`，createDevice/updateDevice 加科室存在性校验；createDevice save 包 try-catch 转 409（并发竞态兜底）
- `device/exception/DeviceErrorCode.java` — 新增 `DEVICE_DEPARTMENT_NOT_FOUND`(404)
- `device/service/DeviceServiceTest.java` — 补 departmentId mock + 1 个校验测试
- `user/service/AdminUserService.java` — createAdmin save 包 try-catch 转 409（用户名并发竞态兜底）
- `auth/entity/UserAccount.java`、`db/migration/V073__user_account_profile_fields.sql` — Q2 收尾：新增并持久化 `real_name`/`phone`/`email`
- `frontend/src/views/admin/AdminUsersView.vue`、`frontend/src/api/admin.ts`、`contracts/openapi.yaml` — Q6 收尾：创建医生账号字段与前端/契约对齐

---

## 4. 数据库是否需要变更

**已通过增量 Flyway 迁移变更数据库。** 未修改既有基线脚本；新增 `V073__user_account_profile_fields.sql`，为 `user_account` 增加 `real_name`、`phone`、`email`，用于管理端用户资料持久化。

两点字段映射差异（已在设计笔记记录，需联调 AI/契约组确认是否后续扩展）：

1. **B2 设备字段**：任务书建议 `category`/`applicableItems`/`enabled`，现有 `device` 表为 `type`/（无）/`status`。实现按现有字段：`category→type`、`enabled→status(DISABLED)`、`applicableItems` 暂不实现。
2. **B3 用户字段**：已按联调确认扩展 `user_account.real_name/phone/email`，管理端创建、列表、更新均已持久化并返回这些字段。

---

## 5. 权限规则说明

- **B1**：仅 `DOCTOR` 角色可调用，按 `doctor.user_id` 定位本人档案；非医生账号（user_id 未关联医生）返回 403。
- **B2/B3/B4/B7**：仅 `ADMIN`，Controller 内 `checkAdminPermission()` 校验，非管理员返回 403。
- **B5**：`AuditController` 类级 `@PreAuthorize("hasRole('ADMIN')")`，自动限管理员。
- **B6**：`/api/statistics/dashboard` 既有权限（管理员），仅扩展返回字段。
- **B3 状态语义**：
  - `DISABLE`：`enabled=false`，登录被 `AuthService.checkAccountStatus` 拒绝（AUTH_ACCOUNT_DISABLED）。
  - `LOCK`：`accountNonLocked=false`，登录被拒绝（AUTH_ACCOUNT_LOCKED）。
  - `ENABLE`：恢复启用并清锁定/失败计数。
- **B3 重置密码**：设新 hash + `mustChangePassword=true` + `tokenVersion++`（使旧 Token 失效），用户可用新密码登录。
- **B3 创建**：`ADMIN` 只建账号；`DOCTOR` 复用 `DoctorService.createDoctor` 同步建医生档案（含科室启用校验）；`PATIENT` 拒绝（走自注册）。

---

## 6. 仍未完成或需前端/契约组配合的事项

### 需联调 AI 同步 OpenAPI 主契约
已由联调 AI 同步到 `contracts/openapi.yaml`，并通过 `tests/contract` OpenAPI 校验。当前无后端任务书范围内的 OpenAPI 阻塞项。

### 需前端 AI 配合
- **B6**：`DashboardSummary` 新增 `totalPatientCount` 字段（record 加字段，前向变更），前端需同步读取，不再显示固定 0。
- **B2**：前端设备创建/更新表单按现有字段（`type` 而非 `category`，无 `applicableItems`/`enabled`）。
- **B3**：用户管理表单的 `realName/phone/email` 与创建医生字段已由联调收尾对齐。角色变更按本轮决策不联动 Doctor/DoctorProfile。

### 待契约组决策
- B8：分页主参数名最终收敛为 `pageSize` 或 `size`（当前新接口两者兼容）。
- B2 字段扩展：是否扩展 `device.applicableItems`、是否把 `type` 重命名/映射为 `category`，后续如做需单独走变更。

---

## 7. 关键 DTO 字段速览（供前端对接）

### B1 DoctorProfileUpdateRequest
`specialty`(string,max255), `education`(string,max64), `experienceYears`(int,≥0), `introduction`(string)

### B2 DeviceCreateRequest
`code`(必填,max32), `name`(必填,max128), `type`(必填,max32), `departmentId`(long), `location`(max128), `manufacturer`(max128), `model`(max64), `serialNumber`(max64), `notes`(max512), `purchaseDate`(date), `warrantyUntil`(date)

DeviceUpdateRequest：同上但无 `code`（不可改），无 `status`（走状态接口）。

### B3 AdminUserCreateRequest
`username`(必填,max64), `password`(必填,8-64), `role`(必填,ADMIN/DOCTOR), `realName`/`phone`/`email`, `departmentId`/`doctorName`/`doctorTitle`/`specialty`/`education`/`experienceYears`/`introduction`（DOCTOR 时 departmentId 必填，doctorName 可由 realName 回填；doctorTitle 默认 ATTENDING）

AdminUserResponse：`id, username, realName, phone, email, enabled, accountNonLocked, accountNonExpired, credentialsNonExpired, mustChangePassword, roles(Set<String>), createdAt, updatedAt`（不含 passwordHash、tokenVersion）

UserStatusChangeRequest：`action`(ENABLE/DISABLE/LOCK)
ResetPasswordRequest：`newPassword`(8-64)

### B4/B5/B7 通用分页响应
`PageResponse<T>`：`items, page, pageSize, total, totalPages`

筛选参数见第 2 节接口清单。

---

## 8. 复查与自处理（2026-07-01）

B1–B8 交付后做了一轮逐文件复查 + 重跑测试，随后完成 Q2/Q6 收尾并再次重跑，结果：388 tests pass，BUILD SUCCESS，无回归。

复查遵循任务书红线（不擅自改 DB 基线、不擅自扩 OpenAPI、不擅自改前端、不擅自扩医疗业务规则），对能自己查证/修复的项直接处理，只保留真正需他人决策的项。

### 已查证（无需改动）
- **JWT tokenVersion 安全语义**：`JwtAuthenticationFilter`（第 65–69 行）和 `JwtService`（第 86–88 行）都校验 tokenVersion，过滤器还校验 `enabled`/`accountNonLocked`。B3 禁用/锁定/重置密码对已登录会话立即生效，安全语义成立。
- **createDoctor 副作用**：`AdminUserService.createUser` 复用 `DoctorService.createDoctor`，未发现额外副作用。

### 已自处理修复（5 项）
1. `AdminUserResponse` 去掉 `tokenVersion` 字段（信息最小化，前端无需）。
2. `DeviceService.createDevice` save 包 try-catch `DataIntegrityViolationException` 转 `DEVICE_CODE_DUPLICATED`(409)，兜底并发竞态。
3. `AdminUserService.createAdmin` save 包 try-catch 转 `USER_USERNAME_DUPLICATED`(409)，兜底用户名并发竞态。
4. `DeviceService` 注入 `DepartmentRepository`，createDevice/updateDevice 加 `validateDepartment` 存在性校验；新增错误码 `DEVICE_DEPARTMENT_NOT_FOUND`(404)。
5. 新增 `AdminUserControllerTest`（MockMvc，5 个权限测试：非管理员 403×3、管理员 200、分页 page=2→0-based 转换），验证 `GlobalExceptionHandler` 把 `BusinessException("PERMISSION_DENIED",403)` 正确转 HTTP 403。

### 仍需后续单独立项（非本轮后端阻塞）
详见 `2026-07-01-backend-ai-thoughts-and-questions.md` 第九节，要点：
- device 表扩 `applicableItems`、字段名对齐 `category`（B2，需走变更流程）。
- 分页主参数名收敛 `pageSize` 或 `size`（契约组决策）。
- B3 高敏感操作审计日志（用户已确认本轮不补）。
- B3 角色 DOCTOR↔ADMIN 变更时 Doctor 档案联动（用户已确认本轮不联动）。
- 权限校验风格、通用错误码、traceId 包装等架构级收敛。

### 复查结论
B1–B8 全部满足任务书验收标准，无阻塞项。上述自处理改动已在第 3 节「复查后补充改动」列出文件清单。
