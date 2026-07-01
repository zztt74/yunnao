# 用户反馈前端处理记录（UF-01/02/03）

日期：2026-07-01
分支：`codex/integration`
来源：docs/integration/2026-07-01-user-feedback-triage-exam-checklist-issues.md

本文件记录 UF-01/02/03 三项前端处理细节、需后端/联调 AI 决策的事项、以及前端可继续打磨的 UX 建议。

---

## 1. UF-01：智能问诊结果页

### 1.1 已自补

| 改动 | 位置 | 说明 |
|---|---|---|
| 移除"推荐理由"独立块 | `PatientTriageView.vue:295-298` 原"推荐理由"section 删除 | AI 气泡内已含 `reason`，对话记录即可承担展示 |
| "X级优先" → "紧急程度：X" | priority label + header 文本 | 改为"紧急程度：紧急/较高/一般/较低"，避免"低级优先"这种患者难理解的措辞 |
| "第 N 轮" → "问诊轮次 N" | `round-tag` | 明确是问诊轮次，便于患者理解 |
| "AI 已给出最终建议"卡片 → 继续输入区 | `followup-card` v-else-if 分支移除 | 改为"继续描述"区，无追问时也允许继续输入主诉或补充信息 |
| `canAskMore` 拆出 `hasFollowUpQuestion` | 顶部 computed | 决定追问区显示"AI 追问"还是"自由补充" |
| placeholder 文案分场景 | `form-textarea` 内的 placeholder | 有追问时："请回复 AI 的追问..."；无追问时："继续输入症状或补充信息..." |
| 按钮文案分场景 | 提交按钮 | 有追问时："继续追问"；无追问时："继续描述" |

### 1.2 已知未解决（需后端契约）

- **后端 `/api/triage/analyze` 不接收 history**（已记录在原反馈文档）。前端即使把 history 塞到请求体（当前 `consultTriage` 实际未发送 history，见 `frontend/src/api/triage.ts`），后端也会忽略，每次仍是单轮分析。
- 真正"延续上下文"需要后端 AI：
  - 在 `/triage/analyze` 接收 `history` 字段
  - 或新增 `/triage/conversation` 会话式接口，带 `conversationId`
  - 或在响应中增加 `isFinal: boolean`，让前端明确知道是否还能继续问
- 决策方：后端 AI + 联调 AI（已记入反馈文档责任划分）。

### 1.3 UX 建议（非阻塞）

- 连续追问时建议把整段对话做成"上下滚动时间线"（类似聊天），避免与"分诊结果卡片"挤在一个 result-card 里。
- 当 `followUpQuestion` 为空时，可以补一句"AI 暂未追问，可继续描述或直接前往挂号"的中性文案，避免患者误以为"不能再问"。
- 如能拿到后端的 `confidence`（置信度）字段，可在优先级徽章下加一行"AI 判断置信度：xx%"，帮助患者理解结果可信度。

---

## 2. UF-02：患者端检查检验流程追踪

### 2.1 已自补

| 改动 | 位置 | 说明 |
|---|---|---|
| 移除 API client 层的 `REVIEWED` 过滤 | `frontend/src/api/examination.ts:60` | 改回不过滤；权限与字段范围仍由后端控制 |
| `getMyExaminations` 支持 `status` 参数 | `examination.ts:55` | 新增 status 维度，文档用 `--文档原 wording--` |
| 列表卡片加状态边框色 | `PatientExaminationsView.vue:675-694` CSS | `exam-card-{status}` 5 种状态对应左 border |
| 列表卡片右侧 status-badge 动态化 | 同上 | 5 种状态对应不同 label + class（待检查/检查中/待审核/已审核/已取消） |
| 顶部新增状态筛选 chip | `status-row` | 6 选项：全部状态 + 5 种状态 |
| 详情页顶部新增"状态步骤卡" | `status-step-card` | 始终显示，让患者知道当前在哪步、下一步是什么 |
| 详情页真实报告（labItems/findings/impression/aiInterpretation）仅 REVIEWED 时展示 | `v-if="detailIsReviewed"` | 避免给患者看到未审核的草稿 |
| 未审核时显示"锁定提示" | `locked-section` | "完整报告将在医生审核完成后开放" |
| 空状态文案调整 | `empty-tip` | "医生开立后会在此显示完整流程与结果"（不再只暗示 REVIEWED） |
| 列表卡片 purpose 缺省处理 | `purpose || '常规检查'` | 后端 purpose 为空时不再显示 `目的：` |

### 2.2 5 种状态的下一步提示文案

| 状态 | 标签 | 提示文案（直接展示给患者） |
|---|---|---|
| ORDERED | 待检查 | 医生已为您开立此检查，请按照医嘱前往指定地点完成检查。 |
| IN_PROGRESS | 检查中 | 检查正在进行中，请耐心等候。完成检查后，结果将由相关人员录入系统。 |
| RESULT_ENTERED | 待审核 | 检查结果已录入，正在等待医生审核。审核完成后您可在此查看完整报告。 |
| REVIEWED | 已审核 | 报告已由医生审核，可点击查看完整结果与 AI 辅助解读。 |
| CANCELLED | 已取消 | 该检查已取消。如有疑问，请联系您的接诊医生。 |

### 2.3 已知未解决（需后端契约/产品决策）

- **当前后端注释倾向"患者只能查看 REVIEWED 结果"**（`ExaminationService.java` 注释），但 controller 实际上返回了全状态订单。**真实权限策略需后端 AI + 产品确认**：
  - 方案 A：保留后端注释的策略（只让患者看 REVIEWED），前端状态全展示就只对医生端有意义
  - 方案 B：放开权限，前端能展示全状态流程（当前已实现）
- **DTO 未提供面向患者的引导字段**（检查地点、执行科室、执行人员、设备、下一步动作），导致文案只能给通用提示。需要：
  - 确认 `deviceId/location/executorName/nextAction` 字段是否在 ExaminationOrderResponse 中暴露
  - 或新建 `/api/patient/examinations/{id}/flow` 详情接口
- **设备使用记录**目前按 encounter 关联（医生端使用），与 patient 检查流程无直接映射，需后端 AI 评估是否需要新增"按 examinationOrderId 查设备使用"接口。
- 决策方：后端 AI + 联调 AI（已记入反馈文档责任划分）。

### 2.4 E2E 建议

按反馈文档执行顺序第 3 步：补一条"开立 -> 患者看到待检查 -> 录入结果 -> 患者看到待审核 -> 审核 -> 患者看到报告"的 E2E，由联调 AI 负责。

---

## 3. UF-03：完成就诊清单三态

### 3.1 已自补

| 改动 | 位置 | 说明 |
|---|---|---|
| 状态枚举从 boolean 改为三态 | `checklist` computed 中每项的 `state: 'done' \| 'pending' \| 'notApplicable'` | `DoctorEncounterOverview.vue:43-88` |
| 文案调整 | "检查检验已全部审核" → "检查检验处理状态"；"处方已确认或作废" → "处方处理状态" | 不再暗示"全部完成" |
| "未开具检查检验" → "本次未开具检查检验" | hint 文本 | 与"未开具"语义对齐 |
| "未开具处方" → "本次未开具处方" | hint 文本 | 同上 |
| 三态视觉 | `.state-done`（绿） / `.state-pending`（橙） / `.state-na`（灰） | CSS `DoctorEncounterOverview.vue:332-388` |
| 三态图标 | ✓ / • / – | 让"不适用"明显区别于"已完成"和"待处理" |
| `allDone` 忽略 notApplicable | `checklist.value.every(item => item.state === 'done' || item.state === 'notApplicable')` | 保持原有"未开具不阻塞完成"语义 |

### 3.2 不动后端业务规则

- 业务规则上"无检查检验/无处方不算 pending"继续由后端 `EncounterService.canCompleteEncounter` 控制
- 前端只做"显示三态"和"汇总不阻塞判断"
- 若产品后续要求"必须显式确认不需要检查/处方"，再调整后端规则 + 增加 DTO 字段

### 3.3 UX 建议（非阻塞）

- 鼠标悬停可在 hint 旁加一个 ❓ 图标，弹出"为什么本次不适用？"的解释卡片
- pending 状态可点击"去处理"按钮直接跳转到对应子页面（已有路由）
- notApplicable 状态可加"+ 新开检查"快捷入口（仅当产品允许就诊中新增检查时）

---

## 4. 自审补充（2026-07-01 第二轮）

按"先自审、再问"原则，本轮检查发现 5 处可独立修的问题：

| # | 问题 | 修复 | 位置 |
|---|---|---|---|
| 1 | `getMyExaminations` 加了 `status?` 参数但 View 层 `loadList` 没传，类型与调用不一致 | 删除 `status?` 参数；状态筛选在 View 层客户端做（与 `typeFilter` 一致） | [examination.ts:51-76](file:///C:/Users/张凯铭/Documents/trae_projects/cloudbrain1/frontend/src/api/examination.ts) |
| 2 | `.status-step-card.status-*` 的 `border-color` 死代码：`.detail-section` 没设 `border-width`，颜色不生效 | 改成 `border-left: 4px solid` + `border-left-color`，与列表卡片 `exam-card-{status}` 视觉对齐 | [PatientExaminationsView.vue:1075-1098](file:///C:/Users/张凯铭/Documents/trae_projects/cloudbrain1/frontend/src/views/patient/PatientExaminationsView.vue) |
| 3 | 详情页基本信息"目的"缺省时显示空串，列表卡片已用 `purpose \|\| '常规检查'` 兜底 | 详情页同步兜底；顺便给 `departmentName/doctorName` 加 `\|\| '--'` 兜底 | [PatientExaminationsView.vue:316-355](file:///C:/Users/张凯铭/Documents/trae_projects/cloudbrain1/frontend/src/views/patient/PatientExaminationsView.vue) |
| 4 | `.status-chip` 缺 `:hover` 反馈，用户点不到的位置不知道能否点击 | 加 `:hover` 改 `border-color` 与 `color` | [PatientExaminationsView.vue:723-726](file:///C:/Users/张凯铭/Documents/trae_projects/cloudbrain1/frontend/src/views/patient/PatientExaminationsView.vue) |
| 5 | 状态步骤卡视觉层次与列表卡片边框色不一致 | 与列表卡片同色系：ordered 橙、in_progress 蓝、result_entered 紫、reviewed 绿、cancelled 灰 | 同 #2 |

### 验证

```text
npm run type-check  ✓
npm run test        ✓ 8 passed
npm run build       ✓ 1.56s（仅 vueuse 注释 + 包体警告）
secret 扫描         ✓
mock 扫描           ✓ 无匹配
git diff --check    ✓
```

---

## 5. 待后端/联调 AI 决策汇总

| 编号 | 主题 | 当前状态 | 建议 |
|---|---|---|---|
| D-1 (UF-01) | 多轮分诊 history 持久化 | 前端 UI 已做（无追问也可继续输入），后端未支持 | 评估是否新增 `/triage/conversation` 会话接口或扩展现有契约 |
| D-2 (UF-02) | 患者端检查流程权限 | 前端展示全状态，后端注释倾向只返回 REVIEWED | 后端 AI + 产品确认权限策略 |
| D-3 (UF-02) | 检查流程 DTO 字段 | 当前 DTO 无 location/executor/device/nextAction | 后端 AI 评估是否新增面向患者的引导字段 |
| D-4 (UF-02) | 设备使用与检查流程关联 | 当前按 encounter 关联 | 后端 AI 评估是否需按 examinationOrderId 查询 |
| D-5 (UF-02) | 患者端 E2E | 当前未覆盖检查全流程 | 联调 AI 补一条"开立->待检查->待审核->已审核" E2E |
| D-6 (UF-03) | 是否必须显式确认"不需要检查/处方" | 当前业务规则允许"不需要" | 产品 + 后端 AI 评估是否需要新增业务字段 |
