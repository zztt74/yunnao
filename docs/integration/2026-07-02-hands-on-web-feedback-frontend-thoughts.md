# 前端想法 / 建议 / 问题记录：2026-07-02 实际上手网页问题修复

> 范围：本次任务书（`docs/integration/2026-07-02-hands-on-web-feedback-frontend-taskbook.md`）实施过程中发现的想法、改进建议与待后端/联调确认的问题清单。
> 仅作为交流与决策参考，不阻塞本次交付。

---

## 一、需求/契约相关问题（建议后端/产品/联调确认）

### Q1. `AIInvocationResponse` 是否应该在列表层下发 `provider` / `model`？
- 现象：F-HW-11 要求列表展示供应商与模型，后端 DTO 当前不下发，仅在 `attempts` 详情接口可查。
- 前端兜底：列表加载后并行预取当前页全部 attempts（N+1）填充行；任一预取失败静默降级 `--`。
- 建议：
  1. **后端优先**：在 `AIInvocationResponse` 中新增 `provider` / `model` 字段（取最后一次 attempt 的 `provider`/`model`），消除 N+1。
  2. **后端次优**：增加列表级批量 attempts 聚合接口 `/audit/ai/invocations/providers`，按 `invocationId[]` 返回最后一次 attempt 摘要。
  3. 短期保留 N+1 兜底也可接受，但当 `pageSize=20` 时峰值并发 20 个请求需要后端确保限流与缓存。

### Q2. 登录日志 / 操作日志是否计划提供查询参数（如 `operatorId`、时间区间、targetType）？
- 现象：当前实现固定传 `action=AUTH_LOGIN, page=1, size=100` / 不传筛选条件，演示环境超过 100 条会被截断。
- 建议：后端支持 `from/to` 时间区间 + 关键字搜索，前端可补上日期选择器。

### Q3. 管理员用户管理的 `lastLoginAt` 与 `displayName` 是否已统一下发？
- 现象：本次前端用三态语义兼容「已下发 / 明确为 null / 未下发」三种情况，但 DTO 是否稳定仍需联调确认。
- 建议：联调跑一遍真实后端数据，确认 DTO 与前端契约一致；若后端永远不会下发 `lastLoginAt`，建议在 DTO 中删除以减少歧义。

### Q4. 操作日志的目标名称 `targetName` 字段是否可由后端补充？
- 现象：F-HW-10 要求展示「目标类型 + 目标名称/ID」，但后端 `AuditLogResponse` 不下发 `targetName`。
- 前端兜底：仅展示「目标类型（已翻译）+ `#<id>`」作为次要信息。
- 建议：后端在审计写入时记录 `targetName` 快照，便于事后追溯（例如「对`李医生`(`#7`)做账号停用」而不是「对 `DOCTOR #7` 做账号停用」）。

### Q5. `mustChangePassword` 是否还在后端契约中？
- 现象：F-HW-03 已移除前端强制跳转，`stores/auth.ts` 中保留 `mustChangePassword` 字段但仅供审计/将来判断。
- 建议：联调确认后端是否真正保留该字段；若已废弃，前端计算属性可同步移除。

### Q6. 管理员「修改密码」路径是否需要后端独立接口？
- 现象：F-HW-03 复用了患者端的 `changePassword`（`/api/auth/change-password`）。管理员调用该接口可行，但需要确认角色与权限校验。
- 建议：若管理员需走 `/api/admin/users/<id>/password` 之类的专用接口，UI 需相应调整。

---

## 二、前端工程改进建议

### S1. 统一「空值兜底」工具函数
- 现象：本次在 `mapPrescription`、`mapBackendUser`、`mapAuditLog`、`mapLoginLogs` 中都各自实现 `safeName` / 兜底逻辑。
- 建议：抽出 `frontend/src/utils/safe-text.ts`，提供 `safeStr`、`safeNum`、`safeDateText` 统一实现；后续 field-mapper 改造时统一引用。

### S2. 字段映射层应该稳定接入类型守卫
- 现象：`mapPrescription`、`mapAuditLog` 等手写映射在 DTO 字段更新时容易漏改，导致 silent fail。
- 建议：把每个后端 DTO 接口用 zod / valibot 写 schema，mapper 入口先 `parse` 一次；schema 失败时上报埋点 + 降级。
- 收益：后端字段重命名 / 拼写错误可即时发现，而不是前端展示成 `--` 后才被发现。

### S3. 统一错误捕获与展示策略
- 现象：本次为 `AdminStatisticsView`、`PatientPrescriptionsView` 各自写了「单接口失败降级 + 重试」模式。
- 建议：抽象 `useAsyncResource`（或类似 composable），集中管理 `loading/error/data/retry`，并在内部封装 401/403/500/网络四类降级 + ElMessage 提示策略。
- 收益：新增页面时不再复制粘贴 `loadError + loadErrorCode + try/catch/finally` 模板。

### S4. `LoginLog.role` / `OperationLog.operatorId` 兼容 null 的影响面
- 现象：本次把 `role` 改为可空，`operatorId` 在 0 兜底。下游所有消费这些字段的视图需复核。
- 建议：跑一次 `vue-tsc` + `npm run build` 已通过；建议再以真实后端做一轮走查，验证无遗漏消费点。

### S5. 路由菜单与页面标题一致性
- 现象：本次把「患者查询」统一改为「患者管理」，但其它菜单项与页面标题仍存在拼写不一致（需在后续迭代中扫一遍）。
- 建议：把菜单项 / 页面 header / 面包屑等标题字段抽出为 `MenuItem.title` / `PageHeader.title`，统一由 router meta 派生。

### S6. 测试用例 `it('…')` 命名过长时拆分多步断言
- 现象：F-HW-02 的 `uses safe fallbacks for doctor/department/patient when encounter is missing` 在一个用例里同时断言 5 个字段，可读性下降。
- 建议：复杂映射可拆为 `safe doctorName` / `safe departmentName` / `safe items` 等独立用例。

### S7. 中文枚举集中管理
- 现象：`AdminOperationLogsView.vue` 中维护了 `targetTypeLabels` / `actionLabels` 两份中文映射；`AdminAiLogsView.vue` 维护 `providerDisplayMap`。
- 建议：抽出 `frontend/src/locales/zh-CN/audit.ts`（或类似 `constants/labels.ts`），按业务域聚合；后续接入 i18n 时无需到处散落。

### S8. 前端体积优化
- 现象：`vue-tsc` 之后 `npm run build` 警告 `dist/assets/index-BPCXArki.js 1,376.85 kB / gzip 424.73 kB`。
- 建议：
  1. 路由级 `defineAsyncComponent` / `() => import()` 切分；
  2. Element Plus 按需引入（`unplugin-vue-components` + `unplugin-auto-import`）；
  3. ECharts 按需引入 / `echarts/core` 替代全量；
  4. 评估 pinia / axios 是否可被 tree-shake 友好化。
- 收益：首屏 JS 体积有望从 1.3MB 降至 500-700KB 区间。

### S9. 表单校验与后端契约的同步
- 现象：F-HW-05 修复了密码长度 6 → 8 的契约不一致。
- 建议：在 `frontend/src/api/doctor.ts`（或 zod schema）层面把后端字段约束（账号长度、手机号正则、职称枚举）集中声明，新增大/小视图直接复用，而不是每个页面写一份。

### S10. CI 上加 `npm run type-check` 作为阻断项
- 现象：当前 `package.json` 有 `type-check` 脚本，但 CI 是否启用需要确认。
- 建议：在 `.github/workflows/frontend.yml` 中把 `npm run type-check && npm run test && npm run build` 串为 PR 必跑，避免回归。

---

## 三、联调 / 演示相关建议

### D1. 演示前预热「演示数据」
- 现象：登录日志/操作日志/AI 调用记录在演示前未跑过一批样例数据，可能出现「页面看起来是空的」或「只有失败记录」。
- 建议：联调 AI 跑一遍 demo 流程：登录 3-5 个不同角色账号 + 开立 1-2 张处方 + 触发 1 次 AI 调用，灌入审计。

### D2. `本地模拟` 与 `DeepSeek` 的视觉区分
- 现象：F-HW-11 已用 `providerDisplayMap` 把 `mock` / `local` 映射为「本地模拟」，但表格列使用相同的 `cell-mono` 字号 / 颜色。
- 建议：演示前给「本地模拟」一个明显但克制的视觉差异（如 chip 背景色：#f0f5ff → #fff7e6），避免评审误以为「这页是真实 AI」。

### D3. 等待中的「loading 骨架」/「错误重试」做一次全量过场
- 现象：F-HW-08 统计驾驶舱已实现独立降级，但其它页面（AI 调用记录、用户管理、设备管理）尚未做独立降级。
- 建议：在演示前使用浏览器 DevTools 的「Network: Slow 3G」手动触发一次各页面 500，验证降级 UI 文本与重试入口可用。

### D4. AI 调用记录列表的 N+1 预取演示风险
- 现象：F-HW-11 列表加载后并行预取 attempts；演示环境若 attempts 限流或慢，会出现列表渲染完成但 provider/model 持续 `--` 几秒后刷新。
- 建议：演示文档中注明「列表显示后约 1-2 秒 provider/model 自动补齐」，避免评审误以为缺失。
- 长期方案：见 Q1，建议后端在列表 DTO 一次性下发。

---

## 四、可被纳入下一轮工作（不阻塞本次交付）

| 主题 | 类型 | 备注 |
| --- | --- | --- |
| 菜单/标题一致性格点扫描 | 前端优化 | 抽出统一 meta 派生 |
| 字段映射层 zod 接入 | 重构 | 见 S2 |
| `useAsyncResource` composable | 重构 | 见 S3 |
| 路由级 code split | 性能 | 见 S8 |
| Element Plus 按需引入 | 性能 | 见 S8 |
| i18n 接入准备 | 长期 | 见 S7 |
| 审计日志查询参数（时间/操作人） | 后端 | 见 Q2 |
| `targetName` 审计字段 | 后端 | 见 Q4 |
| 列表级 provider/model 字段 | 后端 | 见 Q1 |

---

## 五、备注

- 本次代码修改未涉及后端接口语义、数据库、OpenAPI 主契约；后端可独立 review 各自路由。
- 所有「我」对类型守卫、composable、code split 的建议仅为可选方向，最终由团队决定。
- 本文档随交付文档 `2026-07-02-hands-on-web-feedback-frontend-delivery.md` 一起归档到 `docs/integration/`，便于后续追溯。
