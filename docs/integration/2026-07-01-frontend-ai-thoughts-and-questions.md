# 前端 AI 交付：想法、建议与待确认问题（F1–F6 复盘）

日期：2026-07-01
分支：`codex/integration`
对应任务书：`2026-07-01-frontend-ai-delivery-plan.md`
关联文档：`2026-07-01-frontend-ai-delivery-report.md`（交付说明）、`2026-07-01-backend-ai-delivery-report.md`（后端 B1–B8）

本文档只记录实施 F1–F6 过程中观察到的**字段缺口、交互取舍、性能隐患与改进建议**，不重复交付清单。所有内容均为建议性，不改变已交付代码；标注优先级（P1 高 / P2 中 / P3 低）。

---

## 一、字段缺口（后端不返回，前端做了降级）

### 1. F3 用户管理缺 realName/phone/email（P1，阻塞完整交付）

**现象**：后端 `user_account` 表无 `realName`/`phone`/`email` 字段（后端 Q2 已记录），`AdminUserResponse` 不返回这些。前端 `UserManageResponse` 接口保留了这三个字段，但 `mapBackendUser` 用 `username` 兜底 `realName`，`phone`/`email` 恒为空字符串。

**前端取舍**：
- 列表表格移除了「姓名」「电话」列，只保留账号/角色/状态/最后登录。
- 编辑表单提示「后端第一阶段仅支持角色更新；姓名/手机/邮箱待 UserAccount 表扩展后启用」。
- `UserCreateRequest` 不收集 realName/phone/email。

**建议**：和后端 Q2 一致，建议走 DB 基线变更流程扩展 `user_account` 表，或新建 `user_profile` 1:1 表。一旦后端补字段，前端只需：①`UserManageResponse` 填真值；②列表加回姓名/电话列；③`UserCreateRequest`/`UserUpdateRequest` 补字段；④`AdminUsersView.vue` 编辑表单解禁。改动集中、可控。

### 2. F5 AI 调用日志无 provider/model（P2，已降级移除）

**现象**：后端 `AIInvocationResponse` 不返回 `provider`/`model`（实体不存储）。原前端 `mapAiInvocation` 硬编码 `provider: 'backend-ai-provider'`、`model: ''`，`AdminAiLogsView.vue` 展示「供应商」「模型」两列——**这是假数据**，违反任务书「不用本地数组假装后端成功」精神。

**前端取舍**：
- `AiInvocationLog` 类型移除 `provider`/`model` 字段。
- `mapAiInvocation` 不再硬编码。
- 表格移除这两列，改为展示后端真实返回的 `attemptCount`（尝试次数）、`status`、`operatorId`。
- 测试断言 `not.toHaveProperty('provider')`/`not.toHaveProperty('model')` 防回归。

**建议**：如果产品确实需要展示「用了哪个 AI 供应商/模型」，需要后端在 `ai_invocation` 实体加字段并在响应中返回。当前移除是正确的，保留假数据更危险。

### 3. F4 分诊记录无患者姓名、无关键字搜索（P2，已降级）

**现象**：后端 `TriageRecordResponse` 只有 `patientId`，不含 `patientName`。原前端 `mapTriageRecord` 硬编码 `patientName: 'patient-${record.patientId}'`——**假数据**。同时后端 `GET /api/triage` 只支持结构化筛选（patientId/priority/departmentId/时间），不支持症状/AI 摘要的关键字模糊搜索。

**前端取舍**：
- 新增 `batchPatientNames`：对每页去重 patientId 批量并发调 `/patients/{id}` 补姓名，单个失败回退「患者 #ID」，不阻断列表。
- 移除原客户端关键字过滤（症状/AI 摘要等大字段不能下推后端，客户端二次过滤与分页不兼容）。
- 改为服务端结构化筛选：优先级/科室/起止日期/患者ID 精确匹配。

**建议**：
- **患者姓名**：可让后端在 `GET /api/triage` 的 SQL 里 JOIN `patient` 表直接返回 `patientName`，避免前端 N+1 调用（见第二节性能问题 4）。
- **关键字搜索**：如果产品需要按症状搜索，后端需加 `symptoms LIKE` 参数。当前用结构化筛选（优先级/科室/时间）已能覆盖大部分管理场景。

---

## 二、性能与交互隐患

### 4. F4 患者姓名批量查询存在 N+1 风险（P2，已缓解未根除）

**现象**：`batchPatientNames` 对每页（默认 10 条）去重后的 patientId 并发调 `/patients/{id}`。最坏情况一页 10 个不同患者 = 10 次额外 HTTP 请求。

**现状缓解**：
- `Promise.all` 并发，不串行。
- 按 unique patientId 去重，同页同患者只查一次。
- 单个失败不阻断，回退「患者 #ID」。

**未根除的问题**：
- 仍是 N 次请求（虽并发），page size 大时放大。
- `/patients/{id}` 可能还会触发 `/patients/{id}/profile`（如果走 `getPatientDetail`），但当前 `batchPatientNames` 只调 `/patients/{id}` 拿 `PatientResponse.name`，**不调 profile**，已是最小开销。

**建议**：后端在 `GET /api/triage` 响应里直接带 `patientName`（JOIN patient 表），前端去掉 `batchPatientNames`。这是根治方案。

### 5. F4/F5 统计卡片改为「本页」口径（P3，需产品确认）

**现象**：原 `AdminTriageView`/`AdminAiLogsView` 的统计卡片基于「全量已加载数据」计算（旧实现一次拉 100 条）。改为服务端分页后，前端只持有当前页数据，统计卡片只能反映「本页」。

**前端取舍**：
- 分诊卡片标签改为「本页低/中/高/急诊优先级」，总数用后端 `total`。
- AI 日志卡片标签改为「本页成功率」「本页平均耗时」。
- 这样表述准确，不会误导管理员以为这是全院统计。

**建议**：如果产品要「全局统计」，应走 `/api/statistics/ai/summary`（已存在）和类似的分诊统计接口，而不是靠列表页累加。当前方案诚实但不完整。

### 6. F4 移除客户端关键字过滤可能影响体验（P3）

**现象**：原 `AdminTriageView` 有「搜索患者姓名、症状、AI 摘要、推荐科室、推荐理由」的关键字框，客户端过滤。改为服务端分页后，客户端只能过滤当前页数据，跨页搜索失效，所以直接移除了关键字框。

**影响**：管理员不能按「胸痛」搜症状了，只能按优先级/科室/时间/患者ID 筛选。

**建议**：
- 短期：可保留关键字框，但提示「仅搜索当前页」（体验一般）。
- 中期：后端加 `symptoms LIKE` 参数，前端恢复关键字框走服务端搜索。
- 当前取舍优先保证分页正确性，牺牲了搜索便利性。

---

## 三、类型与契约一致性

### 7. DeviceResponse 同时保留 legacy 与 B2 字段（P3，技术债）

**现象**：`DeviceResponse` 既有 legacy `category`/`applicableItems`/`enabled`，又有 B2 真实字段 `type`/`departmentId`/`manufacturer`/`model`/...。`mapBackendDevice` 用 `type` 派生 `category`，`status==='DISABLED'` 派生 `enabled=false`，`applicableItems` 拼凑 `[type, model, notes]`。

**原因**：后端 B2 字段映射差异（`category→type`、`enabled→status`、无 `applicableItems`，后端 Q5 已记录），前端为兼容既有视图字段做了派生。

**建议**：
- 等后端 Q5 决策：要么扩展 `device` 表对齐任务书字段（`category`/`applicableItems`/`enabled`），要么契约组确认接受 `type`/`status` 命名。
- 决策后，前端清理 `DeviceResponse` 的 legacy 字段和 `mapBackendDevice` 的派生逻辑，消除技术债。

### 8. UserManageResponse 保留 realName/phone/email 空字段（P3）

**现象**：`UserManageResponse` 仍声明 `realName`/`phone`/`email`，但后端不返回，`mapBackendUser` 填空字符串/username 兜底。

**建议**：等后端 Q2 决策。若扩表，填真值；若不扩，可把这三字段改为 `optional` 或移除，让类型诚实反映后端能力。当前保留是为了后续扩字段时前端类型不用大改。

### 9. 分页参数 pageSize/size 双兼容（P2，契约未收敛）

**现象**：后端 B8 让新接口同时接受 `pageSize` 和 `size`（优先 `pageSize`）。前端统一用 `pageSize`，但 `getUsers` 里仍写 `size: 100`（老接口风格），`getAdminSchedules`/`getAdminAppointments` 也用 `size`。

**建议**：和后端 Q4 一致，等契约组定一个主参数名后，前端统一替换。当前混用不影响功能，但代码风格不一致。

---

## 四、可观测性与运维

### 10. F3 用户管理操作未写审计日志（P2，与后端第 13 条一致）

**现象**：后端 B3 的创建用户/禁用/锁定/重置密码未调 AuditService 留痕。前端只是调用方，无法补救。

**建议**：推动后端补审计。前端可在操作成功后 `console.info` 一条本地日志辅助调试，但不能替代服务端审计。

### 11. 前端错误提示口径不统一（P3）

**现象**：各 View 的 `loadError`/`ElMessage.error` 直接展示 `e.message`，可能是后端 `BusinessException` 的 message（中文）或 axios 超时错误（英文）。

**建议**：统一一个 `formatApiError(e)` 工具，区分：
- 业务错误（code + message）→ 显示中文 message。
- 网络错误（超时/断网）→ 「网络异常，请稍后重试」。
- 403 → 「无权限执行该操作」。
当前不影响功能，但体验不一致。

---

## 五、需要讨论确认的问题清单

| # | 问题 | 需谁确认 | 优先级 | 状态 |
|---|---|---|---|---|
| FQ1 | F3 用户管理 realName/phone/email 何时补？走扩表还是 user_profile？ | 产品 + DBA | P1 | 待确认（同后端 Q2） |
| FQ2 | F5 AI 日志是否需要 provider/model？若需要，后端实体扩字段 | 产品 + 后端 | P2 | 待确认（前端已移除两列） |
| FQ3 | F4 分诊记录是否需要关键字搜索（症状/AI 摘要）？若需要后端加 LIKE 参数 | 产品 + 后端 | P2 | 待确认（前端已移除关键字框） |
| FQ4 | F4 患者姓名能否后端 JOIN 返回，避免前端 N+1？ | 后端 | P2 | 待确认（前端已做并发+回退） |
| FQ5 | DeviceResponse legacy 字段（category/applicableItems/enabled）何时清理？ | 联调 AI + 后端 | P3 | 待确认（同后端 Q5） |
| FQ6 | 分页参数 pageSize/size 何时收敛？ | 契约组 | P2 | 待确认（同后端 Q4） |
| FQ7 | F4/F5 统计卡片是否需要全局口径？是否走统计接口而非列表累加？ | 产品 | P3 | 待确认 |
| FQ8 | OpenAPI 主契约何时同步 B1–B7 新接口？ | 联调 AI | P1 | 待确认（同后端 Q3） |
| FQ9 | 前端错误提示是否统一 formatApiError？ | 前端架构 | P3 | 待确认 |

---

## 六、自我评价

**做到位的**：
- 严格遵循任务书「不恢复 mock / 不假装后端成功」红线，发现 `patient-${id}`、`backend-ai-provider` 等假数据后主动清除并补真实调用。
- F4 患者姓名补齐做了并发去重 + 失败回退，不阻断列表。
- F5 移除 provider/model 后补了 `not.toHaveProperty` 断言防回归。
- 三项校验（type-check / test / build）全过，新增 3 个测试覆盖 F4/F5 关键路径。
- 字段缺口都在代码注释 + 类型注释 + 本文档记录，等决策后改造成本可控。

**做得保守的**：
- F4 移除了客户端关键字搜索，牺牲了搜索便利性换取分页正确性——这是诚实取舍，但产品可能不满意。
- F4/F5 统计卡片改为「本页」口径，不再假装是全局统计。
- 没有自行扩展后端字段或 OpenAPI 契约（遵循任务书红线）。

**最需要立刻跟进的**：
1. **FQ1（UserAccount 扩字段）**——F3 完整交付的唯一阻塞项，和后端 Q2 同步推进。
2. **FQ8（OpenAPI 契约同步）**——前端无法正式对接契约，和后端 Q3 同步。
3. **FQ4（分诊患者姓名 JOIN）**——前端 N+1 虽缓解但未根治，后端 JOIN 一行 SQL 解决。

---

*本文档为实施者视角的复盘与建议，不构成已交付代码的一部分。所有建议项均需相关负责人评估后决定是否实施。*
