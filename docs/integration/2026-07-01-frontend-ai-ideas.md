# 前端 AI 想法、建议与问题

日期：2026-07-01
范围：[F1-F6] 前端 AI 交付任务执行过程中产生的非任务本体内容
说明：本文档不包含计划任务本身的实现细节，只记录过程性想法、契约讨论、可优化项和需联调 AI 决策的问题。

---

## 1. 接口契约与字段映射问题

### 1.1 PUT /api/doctors/me/profile 字段范围

- 现状：契约（contracts/openapi.yaml:787-825）只接受 `specialty` 和 `introduction`，不接收 phone/email。
- 影响：`DoctorProfile` 类型与 `DoctorResponse` 中都没有 phone/email 字段（base_data.yaml:298）。
- 建议：若后续需要医生自助维护电话/邮箱，需后端 AI 扩展 `/api/doctors/me/profile` 契约，并把字段加入 `DoctorResponse`。当前已在 UI 显式把 phone/email 列为只读展示。
- 决策方：后端 AI + 联调 AI（是否扩大契约范围）。

### 1.2 GET /api/triage 不支持 priority/keyword 筛选

- 现状：契约（openapi.yaml:1716-1747）只支持 page/size。
- 影响：管理端分诊页的优先级和关键字筛选目前在前端完成（fetch size=100 + 客户端过滤）。当全量记录超过 100 时，最早的数据会被截断，统计卡片的"总记录（后端）"展示后端真实 total，优先级分布卡片基于已加载数据；UI 顶部新增"已加载 X / 总 Y"提示。
- 建议：让后端 AI 在 `GET /api/triage` 增加 `priority`、`keyword`（或 `patientId`/`dateRange`）参数，必要时同步提供 `GET /api/triage/stats` 聚合接口。前端分页切到"每次翻页都重新 fetch"以避免截断。
- 决策方：后端 AI + 联调 AI。

### 1.3 GET /api/audit/ai/invocations 不下发 model/provider

- 现状：后端 DTO（AIInvocationResponse.java）当前不下发 provider/model，**这两个字段只在 attempts 里有**。
- 已自补：前端 AI 日志表"供应商/模型"列在主记录上展示 `--`；点击行展开 attempts 详情（来自 `/api/audit/ai/invocations/{id}/attempts`），从 attempts 里读 provider/model/promptVersion。
- 后续建议：若需要主记录级直接展示模型名，再让后端在 AIInvocationResponse 增加 `modelName` 字段。

### 1.4 GET /api/audit/ai/invocations 不支持日期范围筛选

- 现状：契约（openapi.yaml:2834-2865）只列了 capability/status/page/size；实际后端 controller（AuditController.java:107-117）已支持 `success: Boolean`、`businessType: String`、`startDate: LocalDate`、`endDate: LocalDate`。
- 已自补：前端在 AdminAiLogsView 增加 success/businessType/startDate/endDate 四个筛选条件并对接后端契约。
- 备注：openapi.yaml 描述与后端 controller 不一致（缺 success/businessType/date 三个参数），契约文档需同步后端 controller。

### 1.5 后端 user 列表不返回 lastLoginAt

- 现状：`BackendAdminUserResponse` 没有 `lastLoginAt`，前端 `mapBackendUser` 写死为 `null`，UI 显示"从未登录"。
- 影响：管理员看不到任何用户的最后登录时间。
- 建议：在 `/api/admin/users` 返回的 DTO 中加入 `lastLoginAt` 字段；后端在登录成功时记录时间。

---

## 2. 页面与组件层观察

### 2.1 AdminHomeView / AdminStatisticsView 的统计驾驶舱

- 两个页面独立调用 `getStatisticsSummary`，加载策略略冗余：若同时打开两个页面会重复请求。
- 建议：后续可考虑把 dashboard 概览数据放进 Pinia store，由 `useStatisticsStore` 统一管理并提供 `invalidate()` 钩子。
- 决策方：前端 AI（内部优化，无需联调决策）。

### 2.2 AdminDevicesView 设备分类映射

- 表单的 `category` 与后端 `type` 字段是同一值（都是字符串）。`device.ts` 中的 `deviceCategory()` 将后端的任意 `type` 字符串映射到 4 个枚举（默认 EXAMINATION），可能会导致原本不是 EXAMINATION 的设备被错分类展示。
- 建议：后端在 `GET /api/devices` 响应中显式给出 `category` 字段（而不是用 `type` 复用），或前端按 `type` 原始值展示而不强行归类。
- 决策方：联调 AI。

### 2.3 设备 `applicableItems` 是字符串数组

- 后端 `notes` 是字符串（最大 512），前端把 `applicableItems.join(', ')` 塞进 `notes`。
- 风险：逗号分割在多语言或含逗号场景下会错位；超过 512 字会截断。
- 建议：后端把 `applicableItems` 升级为独立的 JSON 数组字段。

### 2.4 医生个人资料不返回 phone/email

- 同 1.1，UI 现状是只读展示 `--`。
- 体验问题：用户输入无效。建议后端支持后再放开。

---

## 3. 测试与质量

### 3.1 单元测试覆盖

- `src/api/real-client.spec.ts` 只覆盖了登录、患者、科室、排班/预约、triage、设备 6 个 API 客户端。
- 本轮新调整的 API（`updateDoctorProfile` / `getTriageRecords` / `getAiInvocationLogs` / `backendDeviceType`）未补 spec。
- 建议：下次增改 API client 时同步补充 vi.mock 测试用例。计划里也只要求"无运行时 mock"，未强制单测覆盖率。

### 3.2 构建告警

- 第三方包 `@vueuse/core` 含有 `/* #__PURE__ */` 注释，位置不在 Vite/Rolldown 期望处，会触发 `[INVALID_ANNOTATION]` 告警（不影响产物，但污染日志）。
- 建议：等 vueuse 升级或 Vite 兼容更新，本任务范围内不动。

### 3.3 打包体积

- `dist/assets/index-*.js` 1.34MB（gzip 414KB），超过 Vite 默认 500KB 警告阈值。
- 主要来源是 element-plus 全量引入。
- 建议：后续做按需引入（`unplugin-vue-components` + `ElementPlusResolver`）或拆 chunk。
- 决策方：前端 AI（不在本任务范围）。

---

## 4. 需要联调 AI 决策的事项

| 编号 | 主题 | 待决内容 | 建议方案 |
| --- | --- | --- | --- |
| D-1 | Triage 记录筛选是否下推后端 | 客户端过滤 vs 服务端过滤 | 服务端：补 priority/keyword/dateRange 参数；客户端：保留现状先收敛 |
| D-2 | 设备 `type` 是否改 `category` | 复用字符串 vs 新增枚举字段 | 新增 `category` 字段，type 保持自定义设备型号 |
| D-3 | 用户 `lastLoginAt` 何时回填 | 是否引入登录时间记录 | 引入，记录到 user 表 |
| D-4 | AI 日志 `model`/`provider` 是否下发 | 字段命名 + 数据脱敏 | 下发模型名；不在 DTO 暴露任何 API Key |
| D-5 | 医生 phone/email 维护范围 | 是否扩展 `/doctors/me/profile` | 暂不扩展，UI 明确告知 |
| D-6 | AI 日志 attempts 详情接口 | 是否需要单独接口 | 提供 `GET /api/audit/ai/invocations/{id}/attempts` |

---

## 5. 其他备注

- 本次未在 `frontend/src/api/mock/**` 写入任何新文件；现有 mock 目录按计划保持未恢复（任务一"不允许做"项）。
- 全量接口都已通过 `parseApiResponse` 解析，未发现 JSON 解析兜底漏洞。
- 真实接口字段映射问题已按计划"先记录、再交接"处理（见 1.x 节），未自行改后端契约。
- "联调检查"中的 `smoke-real-api.mjs` 需要后端运行，本次未执行（环境无 backend）；接口契约层面的检查（`scan-secrets.mjs`、mock 扫描、type-check/test/build、`git diff --check`）均已通过。

---

## 6. 交付清单（与计划第七节对齐）

1. 接入的真实接口清单
   - `PUT /api/doctors/me/profile`（F1，已支持 specialty/introduction）
   - `POST /api/devices`、`PUT /api/devices/{id}`、`POST /api/devices/{id}/status`（F2）
   - `GET /api/admin/users`、相关 CRUD/状态/重置密码（F3）
   - `GET /api/triage`（F4，分页 + 客户端筛选 + "已加载 X / 总 Y" 提示）
   - `GET /api/audit/ai/invocations`（F5，分页 + success/businessType/dateRange/capability 过滤 + 行展开 attempts）
   - `GET /api/audit/ai/invocations/{id}/attempts`（F5，新增）
   - `GET /api/statistics/dashboard` 含 `totalPatientCount`（F6）

2. 修改的页面与 API client
   - `frontend/src/api/admin.ts`（F2/F4/F5，含 backendDeviceType 简化、getAiInvocationLogs 契约补齐、getAiInvocationAttempts 新增）
   - `frontend/src/types/admin.ts`（F5，AiInvocationLog/AiInvocationAttempt 类型补齐 attemptCount 等）
   - `frontend/src/api/doctor.ts`（F1）
   - `frontend/src/types/doctor.ts`（F1）
   - `frontend/src/views/doctor/DoctorProfileView.vue`（F1）
   - `frontend/src/views/admin/AdminTriageView.vue`（F4，stats 标签与分页提示）
   - `frontend/src/views/admin/AdminAiLogsView.vue`（F5，success/date/businessType 筛选 + 切页不闪 + attempts 展开）
   - F3/F6 仅做代码 review 与微调，主体已就位

3. 仍依赖后端/契约组的事项：见 1.x 与 D-1..D-7。

4. 页面验收步骤：见 任务一_AI基础设施与Provider化_交付说明.md、任务二_智能分诊能力_交付说明.md 中既有步骤；F1-F6 涉及页面已在 UI 内补了重新加载/错误提示。
