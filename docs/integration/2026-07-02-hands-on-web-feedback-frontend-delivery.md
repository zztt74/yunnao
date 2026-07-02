# 前端交付说明：2026-07-02 实际上手网页问题修复

> 依据任务书：`docs/integration/2026-07-02-hands-on-web-feedback-frontend-taskbook.md`
> 依据问题文档：`docs/integration/2026-07-02-hands-on-web-feedback-issues.md`
> 分支：`codex/integration`
> 交付日期：2026-07-02

## 一、修改文件清单

### 1.1 业务代码（`frontend/src/`）

| 路径 | 任务 | 说明 |
| --- | --- | --- |
| `api/admin.ts` | F-HW-04 / F-HW-09 / F-HW-10 / F-HW-11 | 扩展 `BackendAdminUserResponse`，重构 `mapBackendUser`；重构 `getLoginLogs`/`mapAuditLog` 做 SYSTEM 占位降级；`mapAiInvocation` 兼容 attempts 预取 |
| `api/client.ts` | F-HW-05 | 响应拦截器从 4xx/5xx body 中提取 `message` 覆盖默认英文错误 |
| `api/prescription.ts` | F-HW-02 | 重构 `mapPrescription` 增加医生/科室/患者姓名与药品字段空值兜底；`getMyPrescriptions` 透传 `includeDraft` |
| `types/admin.ts` | F-HW-04 / F-HW-09 / F-HW-10 | 扩展 `UserManageResponse.displayName` / `lastLoginAt` 三态；`LoginLog.role` 改可空；`OperationLog` 增加可选 `targetName` |
| `stores/auth.ts` | F-HW-03 | 保留 `mustChangePassword` 契约字段但不再触发强制跳转 |
| `router/index.ts` | F-HW-03 / F-HW-06 | 患者管理路由名改为「患者管理」；新增 `/admin/change-password` 路由 |
| `layouts/AppLayout.vue` | F-HW-03 / F-HW-06 | 菜单项改为「患者管理」并新增「修改密码」 |
| `views/admin/AdminChangePasswordView.vue` | F-HW-03 | 新增：管理员主动修改密码页（替代首次强制改密流程） |
| `views/admin/AdminDoctorsView.vue` | F-HW-05 | 密码最小长度校验改为 8 位；优化失败错误提示展示后端 message |
| `views/admin/AdminLoginLogsView.vue` | F-HW-09 | 移除 IP 列；用户名/角色对 SYSTEM 与 null 友好降级 |
| `views/admin/AdminOperationLogsView.vue` | F-HW-10 | targetType 翻译为中文（医生/患者/排班/处方/检查检验等）；操作人/动作/目标/详情统一展示 |
| `views/admin/AdminAiLogsView.vue` | F-HW-11 | 列表行预取 attempts 填充 provider/model；provider key 映射为品牌名（DeepSeek 等），mock 显式显示「本地模拟」 |
| `views/admin/AdminPatientsView.vue` | F-HW-06 | 页面标题/描述/菜单统一为「患者管理」；增加状态筛选；默认加载全部 |
| `views/admin/AdminStatisticsView.vue` | F-HW-08 | 移除 `Promise.all`，每个统计区域独立降级 + 独立重试 |
| `views/admin/AdminUsersView.vue` | F-HW-04 | `formatDateTime` 支持三态 `lastLoginAt` 显示 |
| `views/patient/PatientPrescriptionsView.vue` | F-HW-02 | 列表/详情字段空值兜底；DRAFT 状态显示「待医生确认」；onActivated 自动刷新；401/403/500/网络四类错误独立提示 |
| `views/patient/PatientTriageView.vue` | F-HW-07 | 多轮分诊沿用 `conversationId`，第二轮回传 history/round；刷新推荐医生/挂号跳转 |

### 1.2 新增页面

- `frontend/src/views/admin/AdminChangePasswordView.vue`

### 1.3 测试（`tests/front_unit_test/`）

| 路径 | 任务 | 说明 |
| --- | --- | --- |
| `api-admin.spec.ts` | F-HW-04 / F-HW-09 / F-HW-10 / F-HW-11 | 扩展用户/医生/登录/操作/AI 日志与统计相关测试，验证 SYSTEM 降级、角色映射、targetType 翻译、provider/model 兜底；新增 F-HW-04 三态 lastLoginAt、displayName 兜底、phone/email 空值兜底 3 条测试 |
| `api-prescription.spec.ts` | F-HW-02 | 验证医生/科室/患者姓名与药品字段空值兜底、DRAFT includeDraft 透传 |
| `api-triage.spec.ts` | F-HW-07 | 验证多轮分诊 `conversationId/history/round` 透传 |

## 二、任务完成状态

| 任务 | 状态 | 备注 |
| --- | --- | --- |
| F-HW-02 患者端处方页同步展示与白屏兜底 | ✅ | 字段空值兜底、四类错误状态、DRAFT 文案、onActivated 重拉 |
| F-HW-03 删除管理员首次登录强制改密前端流程 | ✅ | 移除强制跳转，新增 `AdminChangePasswordView`，保留后端契约字段供审计 |
| F-HW-04 管理员用户管理电话和最后登录展示 | ✅ | 扩展 `displayName/lastLoginAt`，三态时间显示，兜底安全映射 |
| F-HW-05 管理员新增医生表单与错误提示 | ✅ | 密码校验改为 8 位；后端 message 覆盖默认英文 |
| F-HW-06 患者查询改为患者管理 | ✅ | 菜单/标题/路由统一为「患者管理」；默认展示全部；状态筛选项 |
| F-HW-07 患者端 AI 分诊多轮上下文 | ✅ | 沿用 conversationId/传递 history/round；失败保留本地历史 |
| F-HW-08 统计驾驶舱前端降级 | ✅ | 移除 `Promise.all`，每个区域独立错误状态 + 独立重试 |
| F-HW-09 登录日志展示修复 | ✅ | 移除 IP 列；SYSTEM 与未知角色降级；筛选同步 |
| F-HW-10 操作日志展示修复 | ✅ | targetType 翻译；操作人/动作/目标/详情统一；空值友好显示 |
| F-HW-11 AI 调用记录供应商和模型展示 | ✅ | attempts 预取填充列表行；provider key → 品牌名；mock 显式「本地模拟」 |

## 三、需要后端 / 联调处理的证据

1. **F-HW-11 provider/model 列表下发**
   - 现状：后端 `AIInvocationResponse` 不下发 provider/model，仅在 `attempts` 详情中可查。
   - 前端兜底：列表加载后并行预取当前页所有行的 attempts，从最近一次 attempt 提取 provider/model 填充表格列；预取失败静默，UI 降级为 `--`。
   - 建议：后端在 `AIInvocationResponse` 中新增 `provider` / `model` 字段（取最后一次 attempt 的值），避免 N+1 请求。

2. **F-HW-03 mustChangePassword 残留**
   - 现状：后端仍返回 `mustChangePassword` 字段，前端已停止强制跳转。
   - 建议：联调确认后端是否真正保留该字段；若已废弃，前端 `stores/auth.ts` 中的 `mustChangePassword` 计算属性可移除（已加注释说明）。

3. **F-HW-10 目标名称/摘要下发**
   - 现状：后端 `AuditLogResponse` 没有目标名称字段，前端只能展示 targetType（已翻译）+ targetId 作为次要信息。
   - 建议：后端补充 `targetName` / `targetSummary` 字段，UI 已预留 `targetNameText` 渲染入口。

4. **F-HW-04 用户管理 lastLoginAt / displayName 下发**
   - 现状：后端可能尚未统一下发 `lastLoginAt` 与 `displayName` 字段。
   - 建议：后端确认 DTO 与前端契约一致；前端已用三态语义兼容历史版本。

5. **F-HW-08 统计接口稳定性**
   - 现状：设备统计 SQL 偶发 500。
   - 前端兜底：单接口失败不影响其他区域，可独立重试。
   - 建议：后端优化该 SQL（已在历史问题文档中标记）。

## 四、构建与测试结果

### 4.1 类型检查

```
> cloud-brain-frontend@0.1.0 type-check
> vue-tsc -b --noEmit
```

✅ 通过（无错误）

### 4.2 单元测试

```
Test Files  23 passed (23)
     Tests  276 passed (276)
  Duration  24.35s
```

✅ 全部通过（276/276）

新增/扩展覆盖：
- F-HW-02：处方字段空值兜底（2 用例）
- F-HW-04：用户管理 displayName 兜底、lastLoginAt 三态、phone/email 空值兜底（3 用例）
- F-HW-07：分诊多轮 history/round 透传
- F-HW-09：登录日志去 IP、SYSTEM 与未知角色降级
- F-HW-10：操作日志 SYSTEM 降级、null targetType 降级
- F-HW-11：AI 调用记录 provider/model 兜底
- F-HW-06：患者管理 status 透传

### 4.3 生产构建

```
✓ built in 1.32s
dist/index.html                     0.50 kB │ gzip:   0.35 kB
dist/assets/index-CgxFnT1l.css    571.11 kB │ gzip:  74.86 kB
dist/assets/index-BPCXArki.js   1,376.85 kB │ gzip: 424.73 kB
```

✅ 构建成功（构建期出现的 `INVALID_ANNOTATION` 警告来自第三方 `node_modules/@vueuse/core` 内部注释位置，与本次代码无关；体积警告建议后续做按需/路由级 code split）。

## 五、回滚说明

本次只修改 `frontend/src/` 与 `tests/front_unit_test/`，未触碰后端接口契约、数据库与 OpenAPI 主契约。回滚只需：

```bash
git checkout -- frontend/src tests/front_unit_test
```

## 六、未尽事项与遗留建议

详见配套文档 `docs/integration/2026-07-02-hands-on-web-feedback-frontend-thoughts.md`。
