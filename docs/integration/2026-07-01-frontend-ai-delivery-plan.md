# 前端 AI 剩余交付任务书

日期：2026-07-01  
分支：`codex/integration`  
目标：接入后端补齐后的真实接口，完成患者端、医生端、管理端主要页面真实闭环，确保没有运行时 mock 和假成功。

协作角色限定：本项目剩余工作只划分为后端 AI、前端 AI、联调 AI。前端 AI 只负责本文档内的前端 API client、页面接入、页面级测试与问题反馈；契约整理、AI 能力验收、E2E、Docker、最终提交由联调 AI 统一负责。

## 一、工作边界

### 允许做

- 修改 `frontend/src/api/**`、`frontend/src/views/**`、`frontend/src/types/**`。
- 接入后端真实接口。
- 补页面加载态、空状态、错误提示。
- 补前端 API client 测试。
- 修复真实接口字段映射问题。

### 暂不允许擅自做

- 不恢复 `frontend/src/api/mock/**`。
- 不用本地数组假装后端成功。
- 不擅自改后端契约；发现接口缺口先记录并交给后端 AI 和联调 AI。
- 不把 AI Key、token、密码写进前端代码。
- 不做大规模 UI 重构，除非页面已经无法正常使用。

## 二、当前前端状态

已清理：

- 运行时 mock 已清理。
- `frontend/src/api/mock/admin-mock.ts`
- `frontend/src/api/mock/doctor-mock.ts`
- `frontend/src/api/mock/medical-mock.ts`

已接真实接口：

- 登录、注册、改密码。
- 患者信息、患者档案、患者预约、患者分诊、患者时间线。
- 科室、医生、排班、挂号。
- 医生队列、接诊、诊断、病人详情。
- 病历、检查、处方、药品、设备。
- 部分管理端：科室、医生、排班、挂号、患者搜索、设备查询/状态、统计、操作日志。

当前显式阻塞：

- 管理端用户管理。
- 管理端设备创建/编辑。
- 管理端全量分诊记录。
- 管理端 AI 调用日志列表。
- 医生个人资料保存。
- 统计总患者数。

## 三、必须完成的前端任务

### 任务 F1：医生个人资料保存

等待后端：

- `PUT /api/doctors/me` 或等价接口。

前端要做：

- 更新 `frontend/src/api/doctor.ts` 的 `updateDoctorProfile`。
- 恢复 `DoctorProfileView.vue` 保存成功流程。
- 保存失败时显示后端错误，不吞异常。

验收：

- 医生账号登录后能编辑并保存个人资料。
- 刷新页面后仍能看到更新后的内容。

### 任务 F2：设备管理新增/编辑

等待后端：

- `POST /api/devices`
- `PUT /api/devices/{id}`

前端要做：

- 更新 `frontend/src/api/admin.ts` 中 `createDevice`、`updateDevice`。
- 检查 `AdminDevicesView.vue` 表单字段与后端 DTO 是否一致。
- 新增/编辑成功后刷新列表。

验收：

- 管理员可新建设备。
- 管理员可编辑设备。
- 状态变更仍使用独立状态接口。

### 任务 F3：管理端用户管理

等待后端：

- 用户列表、创建、更新、状态变更、重置密码接口。

前端要做：

- 更新 `getUsers`、`createUser`、`updateUser`、`changeUserStatus`、`resetUserPassword`。
- 检查 `AdminUsersView.vue` 的字段、角色、状态展示。
- 增加清晰错误提示。

验收：

- 用户列表真实显示。
- 可禁用/启用/锁定用户。
- 可重置密码。
- 不再出现“接口未提供”。

### 任务 F4：管理端全量分诊记录

等待后端：

- `GET /api/triage` 或等价分页接口。

前端要做：

- 更新 `getTriageRecords`。
- 检查 `AdminTriageView.vue` 是否支持分页、筛选、空状态。

验收：

- 管理员可查看真实分诊记录。
- 筛选条件可用。

### 任务 F5：AI 调用日志列表

等待后端：

- `GET /api/audit/ai/invocations`

前端要做：

- 更新 `getAiInvocationLogs`。
- 检查 `AdminAiLogsView.vue` 展示字段：能力类型、模型、成功/失败、耗时、错误信息、调用时间。
- 如有详情入口，接 attempts 详情接口。

验收：

- AI 日志页面显示真实 AI 调用记录。
- 不显示敏感 Key。

### 任务 F6：统计总患者数

等待后端：

- dashboard 增加 `totalPatientCount` 或独立 count 接口。

前端要做：

- 更新 `getStatisticsSummary` 字段映射。
- 管理端首页和统计页显示真实患者总数。

验收：

- 不再固定显示 0。

## 四、与后端 AI、联调 AI 的交接

前端 AI 接入每个真实接口后，需要给联调 AI 提供：

1. 页面路径。
2. 调用的 API client 方法。
3. 依赖的后端接口。
4. 页面可操作步骤。
5. 已知错误提示或空状态。

如果发现接口字段不一致，先记录“前端期望字段 / 后端实际字段 / 影响页面”，交给后端 AI 和联调 AI 决策。

## 五、完整闭环页面验收

前端 AI 需要配合联调 AI 跑以下流程：

1. 患者登录。
2. 患者预约挂号。
3. 医生登录。
4. 医生查看队列。
5. 医生开始接诊。
6. 医生查看患者详情。
7. 医生下 AI 辅助诊断或最终诊断。
8. 医生开检查。
9. 医生录入/审核检查结果。
10. 医生生成/确认病历。
11. 医生开具/确认处方。
12. 医生完成就诊。
13. 患者查看病历、检查、处方、时间线。
14. 管理员查看统计、日志、设备、用户。

## 六、前端提交前检查

必须运行：

```powershell
cd frontend
npm run type-check
npm run test
npm run build
```

联调检查：

```powershell
node tests/integration/smoke-real-api.mjs
node tests/integration/scan-secrets.mjs
git diff --check
```

运行时 mock 扫描：

```powershell
rg -n "@/api/mock|frontend/src/api/mock|admin-mock|doctor-mock|medical-mock|mock-token|mock-ai-fail|\[MOCK\]|mock" frontend/src tests -g "!frontend/src/api/real-client.spec.ts"
```

要求：

- 除单元测试 `vi.mock` 外，运行时代码不得出现本地 mock。

## 七、交付说明格式

前端 AI 完成后，请输出：

1. 接入的真实接口清单。
2. 修改的页面和 API client。
3. 仍依赖后端/契约组的事项。
4. 页面验收截图或文字步骤。
5. 测试命令和结果。
