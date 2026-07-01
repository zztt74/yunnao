# 前端 AI 剩余交付任务 交付说明（F1–F6）

日期：2026-07-01
分支：`codex/integration`
对应任务书：`2026-07-01-frontend-ai-delivery-plan.md`
关联文档：`2026-07-01-backend-ai-delivery-report.md`（后端 B1–B8 交付）、`2026-07-01-frontend-ai-thoughts-and-questions.md`（想法与建议）

---

## 1. 测试命令与结果

```powershell
cd frontend
npm ci
npm run type-check
npm run test -- --run
npm run build
```

结果：

| 命令 | 结果 |
|---|---|
| `npm run type-check` | 通过（`vue-tsc -b --noEmit` 无错误） |
| `npm run test` | 11 个测试通过（原 8 个 + F4/F5 新增 3 个），0 失败 |
| `npm run build` | 构建成功，产出 `dist/`（1.77s，仅有 `@vueuse/core` PURE 注解与 chunk 体积告警，非阻断） |

运行时 mock 扫描：

```powershell
rg -n "@/api/mock|admin-mock|doctor-mock|medical-mock|mock-token|mock-ai-fail" frontend/src
```

无命中，无运行时 mock。

---

## 2. 接入的真实接口清单

| 任务 | 后端接口 | 方法 | 说明 |
|---|---|---|---|
| F1 | `/api/doctors/me` | PUT | 医生更新本人资料（specialty/education/experienceYears/introduction） |
| F2 | `/api/devices` | POST | 新建设备（对齐 B2 DeviceCreateRequest 全字段） |
| F2 | `/api/devices/{id}` | PUT | 更新设备基础信息（不含 code/status） |
| F3 | `/api/admin/users` | GET | 用户分页列表 |
| F3 | `/api/admin/users` | POST | 创建用户（ADMIN/DOCTOR，DOCTOR 含档案字段） |
| F3 | `/api/admin/users/{id}` | PUT | 更新用户角色（第一阶段仅 role） |
| F3 | `/api/admin/users/{id}/status` | POST | 启用/禁用/锁定 |
| F3 | `/api/admin/users/{id}/reset-password` | POST | 重置密码 |
| F4 | `/api/triage` | GET | 全量分诊记录分页（patientId/priority/departmentId/startDate/endDate） |
| F4 | `/api/patients/{id}` | GET | 批量补齐分诊记录患者姓名 |
| F5 | `/api/audit/ai/invocations` | GET | AI 调用日志分页（capability/success/businessType/startDate/endDate） |
| F6 | `/api/statistics/dashboard` | GET | 已包含 `totalPatientCount`，前端映射为 `totalPatients` |

---

## 3. 修改的模块和文件

### F1：医生个人资料保存

- `frontend/src/types/doctor.ts`
  - `DoctorProfileUpdateRequest` 改为 `{ specialty, education, experienceYears, introduction }`，对齐 B1。
  - `DoctorProfile` 接口新增 `education`、`experienceYears`。
- `frontend/src/api/doctor.ts`
  - 修复 `updateDoctorProfile` 端点：`/doctors/me/profile` → `/doctors/me`。
  - 请求体补齐 `education`/`experienceYears`，响应映射同步加这两字段。
- `frontend/src/views/doctor/DoctorProfileView.vue`
  - 表单字段替换：移除手机/邮箱，改为学历/从业年限。
  - 校验：`experienceYearsValid` 替换原 `phoneValid`/`emailValid`。
  - 查看/编辑模式同步显示新字段。

### F2：设备管理新增/编辑

- `frontend/src/types/device.ts`
  - `DeviceResponse` 扩展后端 B2 真实字段：`type`/`departmentId`/`departmentName`/`manufacturer`/`model`/`serialNumber`/`notes`/`purchaseDate`/`warrantyUntil`。
- `frontend/src/api/device.ts`
  - `mapBackendDevice` 补齐全部新字段映射。
- `frontend/src/api/admin.ts`
  - `backendCreateDevicePayload`/`backendUpdateDevicePayload` 重写，发送 B2 全字段。
- `frontend/src/views/admin/AdminDevicesView.vue`
  - 表单弹层重写：科室下拉、制造商、型号、序列号、采购/保修日期、备注。
  - 卡片展示：科室、制造商/型号、序列号、采购与保修日期、备注。
  - 编辑模式 `code` 不可改。

### F3：管理端用户管理

- `frontend/src/types/admin.ts`
  - `UserCreateRequest` 重写为单 `role: 'ADMIN' | 'DOCTOR'` + DOCTOR 条件字段（departmentId/doctorName/doctorTitle/specialty/education/experienceYears/introduction）。
  - `UserUpdateRequest` 简化为 `{ role? }`。
  - `UserManageResponse` 新增 `mustChangePassword?`，注释说明 realName/phone/email 后端无字段。
- `frontend/src/api/admin.ts`
  - `createUser` 按 role 条件发送 DOCTOR 档案字段。
  - `updateUser` 仅发 role。
  - `mapBackendUser` 用 username 兜底 realName，phone/email 恒空。
- `frontend/src/views/admin/AdminUsersView.vue`
  - 表单：单选角色 radio + DOCTOR 条件字段区。
  - 表格移除姓名/电话列（后端无字段）。
  - 编辑模式提示第一阶段仅支持角色更新。

### F4：管理端全量分诊记录

- `frontend/src/types/triage.ts`
  - 新增 `AdminTriageQuery`（patientId/priority/departmentId/startDate/endDate/page/pageSize）。
- `frontend/src/api/admin.ts`
  - `getTriageRecords(query)` 重写：服务端筛选 + 分页，返回 `PageResult<AdminTriageRecord>`。
  - 新增 `batchPatientNames`：对每页去重 patientId 批量调 `/patients/{id}` 补姓名，失败回退「患者 #ID」。
  - `mapTriageRecord` 接受 patientName 参数，不再硬编码 `patient-${id}`。
- `frontend/src/views/admin/AdminTriageView.vue`
  - 重写为服务端筛选（优先级/科室/起止日期/患者ID）+ 分页（上一页/下一页）。
  - 移除原客户端关键字过滤（症状/AI 摘要等大字段不能下推到后端，客户端二次过滤与分页不兼容，改为服务端结构化筛选）。
  - 统计卡片改为「本页优先级分布」+ 总记录数。

### F5：AI 调用日志列表

- `frontend/src/types/admin.ts`
  - `AiInvocationLog` 重写：移除 fake `provider`/`model`，新增 `status`/`attemptCount`/`operatorId`，对齐 B5 `AIInvocationResponse`。
  - 新增 `AiInvocationLogQuery`。
- `frontend/src/api/admin.ts`
  - `getAiInvocationLogs(query)` 重写：服务端筛选 + 分页，返回 `PageResult<AiInvocationLog>`。
  - `mapAiInvocation` 移除硬编码 `'backend-ai-provider'`/`''`。
  - `AIInvocationResponse` 后端接口补齐 attemptCount/operatorId/finishedAt/createdAt/updatedAt。
- `frontend/src/views/admin/AdminAiLogsView.vue`
  - 表格移除「供应商」「模型」两列（后端实体不存储）。
  - 新增「尝试次数」列。
  - 筛选改为服务端：调用类型/调用结果/业务类型/起止日期。
  - 分页：上一页/下一页。
  - 汇总统计标注「本页」口径。

### F6：统计总患者数（验证完成）

- `frontend/src/api/admin.ts`
  - `getStatisticsSummary` 已映射 `totalPatients: dashboard.totalPatientCount`。
- `frontend/src/views/admin/AdminHomeView.vue`
  - `secondaryStats` 展示 `总患者数 = s.totalPatients`。
- `frontend/src/views/admin/AdminStatisticsView.vue`
  - `summaryMetrics` 展示 `总患者数 = s.totalPatients`。

### 测试

- `frontend/src/api/real-client.spec.ts`
  - 新增 3 个测试：
    1. 分诊记录服务端筛选 + 患者姓名批量补齐（验证 `/triage` 与 `/patients/{id}` 调用）。
    2. 患者查询失败时回退「患者 #ID」。
    3. AI 调用日志服务端筛选 + 不再返回 fake provider/model。

---

## 4. 仍依赖后端/契约组的事项

| # | 事项 | 阻塞影响 | 负责方 |
|---|---|---|---|
| 1 | OpenAPI 主契约同步 B1–B7 新接口 | 前端无法正式对接契约 | 联调 AI |
| 2 | `user_account` 表扩 `realName`/`phone`/`email` | F3 用户管理无法编辑姓名/手机/邮箱 | 产品 + DBA |
| 3 | 分页主参数名收敛 `pageSize` 或 `size` | 前端需同时兼容两者 | 契约组 |
| 4 | F4 分诊记录无关键字模糊搜索（症状/AI 摘要） | 患者姓名搜索需走 patientId 精确匹配 | 后端（可选扩展） |
| 5 | F5 AI 调用日志无 provider/model | 前端已移除这两列；如需展示需后端实体扩字段 | 后端（可选） |
| 6 | B3 DOCTOR→ADMIN 角色变更时 Doctor 档案孤儿处理 | 数据一致性 | 产品 + 后端 |

---

## 5. 页面验收步骤

### F1 医生个人资料
1. 医生账号登录。
2. 进入「我的资料」。
3. 编辑专长、学历、从业年限、简介，点保存。
4. 刷新页面，确认字段仍为新值。
5. 保存失败时显示后端错误。

### F2 设备管理
1. 管理员登录，进入「设备管理」。
2. 点「新增设备」，填写 code/name/type/科室/制造商/型号/序列号/采购日期/保修日期/备注，提交。
3. 列表出现新设备，科室、制造商/型号正确显示。
4. 点「编辑」，code 灰显不可改，修改其他字段提交，列表刷新。
5. 状态变更走独立按钮（AVAILABLE/IN_USE/MAINTENANCE/DISABLED）。

### F3 用户管理
1. 管理员登录，进入「用户管理」。
2. 列表显示真实用户（账号、角色、状态、最后登录）。
3. 点「新增用户」，选 DOCTOR，填写科室/医生姓名/职称/学历/从业年限/专长/简介，提交。
4. 列表出现新医生用户。
5. 点「编辑」改角色为 ADMIN，提交，角色更新。
6. 点「重置密码」输入新密码，用户下次可用新密码登录。
7. 点「停用」输入原因，用户状态变 DISABLED，该用户登录被拒。
8. 点「启用/解锁」，状态恢复。

### F4 分诊记录
1. 管理员登录，进入「分诊记录」。
2. 列表显示真实分诊记录，患者姓名为真实姓名（非 `patient-N`）。
3. 优先级下拉筛选（低/中/高/急诊）→ 列表刷新。
4. 科室下拉筛选 → 列表刷新。
5. 起止日期筛选 → 列表刷新。
6. 患者 ID 精确筛选 → 列表刷新。
7. 点「上一页/下一页」翻页。
8. 点卡片展开，查看完整症状/AI 推荐理由/安全提示/急诊建议。

### F5 AI 调用日志
1. 管理员登录，进入「AI 调用记录」。
2. 列表显示真实 AI 调用记录，**不再有「供应商」「模型」两列**。
3. 调用类型筛选（分诊/诊断/病历生成/处方审核/检查解读）→ 列表刷新。
4. 调用结果筛选（成功/失败）→ 列表刷新。
5. 业务类型输入筛选 → 列表刷新。
6. 起止日期筛选 → 列表刷新。
7. 翻页，失败记录显示 errorType/errorMessage。
8. 无 API Key、Token 敏感信息泄露。

### F6 统计总患者数
1. 管理员登录，进入「管理首页」。
2. 「全院规模」区「总患者数」显示真实数字（非 0）。
3. 进入「统计驾驶舱」，「核心指标」区「总患者数」同样显示真实数字。

---

## 6. 交付结论

- F1–F6 全部满足任务书验收标准。
- `npm run type-check` / `npm run test` / `npm run build` 三项校验全部通过。
- 运行时无 mock，无假成功。
- 后端字段缺口（realName/phone/email、provider/model、分诊关键字搜索）已在代码注释与 `2026-07-01-frontend-ai-thoughts-and-questions.md` 记录，等联调 AI/契约组决策。

想法、问题与建议见独立文档：`2026-07-01-frontend-ai-thoughts-and-questions.md`。
