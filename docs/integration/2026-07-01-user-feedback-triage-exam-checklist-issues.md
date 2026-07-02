# 用户反馈问题核查记录：问诊、检查检验、完成就诊清单

日期：2026-07-01
分支：`codex/integration`
来源：用户截图与反馈

本文件只记录核查结论和责任划分，不直接改变业务实现。

## 1. 智能问诊结果页信息重复、不能延续对话

### 是否属实

属实。

### 现象

- 分诊结果页里“对话记录”的 AI 气泡和“推荐理由”展示的是同一段内容，视觉上重复。
- 页面在 AI 无追问时显示“AI 已给出最终建议 / 如需进一步问诊，请重新发起问诊”，会让患者只能重新开始，前面病情对话不能自然延续。
- 顶部“低级优先”“第 1 轮”的业务含义对患者不清楚。

### 代码证据

- `frontend/src/views/patient/PatientTriageView.vue`
  - 首轮响应后把 `triageResult.reason` 直接追加到对话记录 AI 气泡。
  - 同一页面又单独渲染“推荐理由：triageResult.reason”。
  - 无追问时渲染“AI 已给出最终建议 / 如需进一步问诊，请重新发起问诊”。
  - 顶部展示 `priorityConfig[priority].label + 级优先` 和 `第 {{ round }} 轮`。
- `frontend/src/api/triage.ts`
  - `history` 只保留在前端类型和本地状态里。
  - 实际请求 `/triage/analyze` 只发送 `patientId/symptoms/duration/supplement`，没有把对话历史发给后端。
  - 代码注释也写明“后端当前冻结接口只接收本轮症状、持续时间和补充信息，history 保留给页面状态使用”。

### 根因

- 前端把“推荐理由”同时当作 AI 对话内容和结果说明展示，导致重复。
- 当前后端分诊接口是单轮分析接口，不是会话式问诊接口；前端虽然有 `history`，但没有后端持久会话或上下文续问能力。
- “低级优先/第 1 轮”是内部优先级和轮次标签，缺少患者可理解的解释文案。

### 建议处理

- 前端 AI：
  - 去掉独立“推荐理由”块，或只在对话记录中展示一次。
  - 去掉“AI 已给出最终建议”卡片，改为在对话记录下提供继续输入框。
  - 将“低级优先”改成患者可读文案，例如“建议常规就诊”或“紧急程度：低”。
  - “第 1 轮”如果没有明确价值，建议隐藏；如果保留，需要说明是“问诊轮次”。
- 后端 AI：
  - 如果要真正支持连续问诊，需要新增或扩展契约：`conversationId`、`history`、`isFinal`、`followUpQuestion` 等字段。
  - 当前 `/api/triage/analyze` 只能做单轮分诊，不能保证“接着前面对话继续”。
- 联调 AI：
  - 负责记录该契约差异。
  - 若后端扩展多轮问诊接口，需同步 OpenAPI、前端 API client 和 E2E。

### 责任划分

| 子问题 | 责任角色 | 说明 |
|---|---|---|
| 删除重复推荐理由、最终建议卡片、优化“低级优先/第 1 轮”文案 | 前端 AI | 纯页面展示和交互调整 |
| 添加可继续输入的对话框 | 前端 AI + 后端 AI | 前端可先做 UI；真实连续上下文需要后端契约支持 |
| 多轮问诊历史持久化/上下文传入 AI | 后端 AI | 当前接口未接收 history |
| OpenAPI 与 E2E 更新 | 联调 AI | 后端契约变更后执行 |

## 2. 患者端检查检验缺少完整流程提示

### 是否属实

属实。

### 现象

- 患者端“检查检验”页面只看到已审核结果。
- 没有展示“尚未检查 / 检查中 / 待审核 / 请前往哪里 / 找谁 / 使用什么设备 / 检查完请找医生”这类过程提示。

### 代码证据

- `frontend/src/api/examination.ts`
  - `getMyExaminations()` 从 `/examinations/patient/{patientId}` 获取列表后，前端直接 `.filter((item) => item.status === 'REVIEWED')`。
  - 因此 `ORDERED`、`IN_PROGRESS`、`RESULT_ENTERED` 等中间状态在患者端被过滤掉。
- `frontend/src/views/patient/PatientExaminationsView.vue`
  - 空状态文案为“医生开立并审核后将在此显示”。
  - 列表右侧状态写死为“已审核”。
- `backend/src/main/java/com/neusoft/cloudbrain/examination/service/ExaminationService.java`
  - 后端检查检验状态机存在：`ORDERED -> IN_PROGRESS -> RESULT_ENTERED -> REVIEWED`。
  - 注释中明确“患者只能查看 REVIEWED 结果”。
- `frontend/src/views/doctor/DoctorExaminationOrderView.vue`
  - 医生端有设备使用记录、可用设备选择、开始/结束设备使用能力。
  - 这些设备使用信息没有同步展示到患者端检查检验页。

### 根因

- 当前患者端被设计成“结果查看页”，不是“检查流程追踪页”。
- 前端主动过滤掉未审核记录，患者看不到待检查、检查中、待审核阶段。
- 后端检查申请 DTO 里没有明确面向患者的引导字段，例如检查地点、执行科室、执行人员、预约/排队提示、下一步动作。
- 设备使用记录当前主要服务医生端，且按 encounter 关联，不是直接按 examination order 给患者做引导。

### 建议处理

- 前端 AI：
  - 患者端检查检验页改为展示全状态列表，而不是只展示 `REVIEWED`。
  - 按状态显示下一步文案：
    - `ORDERED`：医生已开立，请等待/前往指定地点检查。
    - `IN_PROGRESS`：检查进行中。
    - `RESULT_ENTERED`：结果已录入，等待医生审核。
    - `REVIEWED`：已审核，可查看报告。
    - `CANCELLED`：已取消，显示取消原因。
- 后端 AI：
  - 确认患者是否允许查看未审核检查申请。如果允许，需要调整后端权限/返回策略。
  - 若要精确提示“去哪里、找谁、用什么设备”，需要在检查申请或设备使用关系中补齐字段。
  - 可考虑把 `deviceId/location/executorName/nextAction` 纳入患者检查检验响应。
- 联调 AI：
  - 负责确认 OpenAPI 是否需要新增字段。
  - 补一条患者查看检查流程的 E2E：开立检查 -> 患者看到待检查 -> 录入结果 -> 患者看到待审核 -> 审核后看到报告。

### 责任划分

| 子问题 | 责任角色 | 说明 |
|---|---|---|
| 患者端展示全状态流程 | 前端 AI | 当前前端过滤 REVIEWED，需要改展示逻辑 |
| 患者是否可见未审核申请 | 后端 AI + 产品确认 | 后端注释当前倾向只允许患者看 REVIEWED |
| 检查地点、执行人员、设备引导字段 | 后端 AI | 当前 DTO/表结构不足以稳定展示 |
| OpenAPI/E2E 更新 | 联调 AI | 字段和流程确认后同步 |

## 3. 完成就诊检查清单中“未开具检查/处方”被显示为已完成

### 是否属实

属实，但属于“规则可通过，视觉表达误导”。

### 现象

- 医生端“完成就诊检查清单”中，未开具检查检验时显示绿色勾选“检查检验已全部审核 / 未开具检查检验”。
- 未开具处方时显示绿色勾选“处方已确认或作废 / 未开具处方”。
- 用户会理解成“这两项已经处理过”，但实际只是“没有开具，因此不阻塞完成就诊”。

### 代码证据

- `frontend/src/views/doctor/DoctorEncounterOverview.vue`
  - 检查项逻辑：`examinations.value.length > 0 ? !hasPendingExams.value : true`。
  - 处方项逻辑：`done: !hasDraftPrescription.value`。当 `prescription` 为 `null` 时，`hasDraftPrescription=false`，因此 `done=true`。
  - 这正是截图里后两项初始绿色打钩的原因。
- `backend/src/main/java/com/neusoft/cloudbrain/encounter/service/EncounterService.java`
  - 完成就诊前置条件检查：
    - 任一检查检验处于 `ORDERED/IN_PROGRESS/RESULT_ENTERED` 时不得完成。
    - 处方可不存在；存在处方时不能是 `DRAFT`。
- `backend/src/main/java/com/neusoft/cloudbrain/examination/repository/ExaminationOrderRepository.java`
  - 只统计 `ORDERED/IN_PROGRESS/RESULT_ENTERED` 为 pending。
- `backend/src/main/java/com/neusoft/cloudbrain/prescription/repository/PrescriptionRepository.java`
  - 只统计 `DRAFT` 处方为 pending。

### 根因

- 后端业务规则允许“不需要检查/不需要处方”的就诊完成。
- 前端把“不适用/未开具”渲染成了与“已审核/已确认”同样的绿色完成态，缺少“不适用”状态。

### 建议处理

- 前端 AI：
  - 将清单项状态拆成三态：`done`、`pending`、`notApplicable`。
  - “未开具检查检验”“未开具处方”应显示为灰色或中性状态，例如“不适用 / 本次未开具”，不要用绿色勾选。
  - 文案从“检查检验已全部审核”改为“检查检验处理状态”；从“处方已确认或作废”改为“处方处理状态”。
- 后端 AI：
  - 如果产品要求“每次就诊必须显式确认是否不需要检查/处方”，则需要新增业务字段或医生确认动作。
  - 如果继续沿用当前规则，后端无需改。
- 联调 AI：
  - 若前端只做三态展示，不需要 OpenAPI 变更。
  - 若新增“不需要检查/不需要处方”的显式确认，则需要契约和 E2E 更新。

### 责任划分

| 子问题 | 责任角色 | 说明 |
|---|---|---|
| 清单视觉三态和文案调整 | 前端 AI | 当前主要是展示误导 |
| 是否必须显式确认“不需要检查/处方” | 产品 + 后端 AI | 这会改变完成就诊业务规则 |
| 契约/E2E 更新 | 联调 AI | 仅在新增字段或动作时需要 |

## 汇总优先级

| 编号 | 问题 | 是否属实 | 优先级 | 主要责任 |
|---|---|---|---|---|
| UF-01 | 智能问诊结果页重复展示、不能延续上下文 | 是 | P1 | 前端 AI + 后端 AI |
| UF-02 | 患者端检查检验缺少流程追踪与下一步提示 | 是 | P1 | 前端 AI + 后端 AI |
| UF-03 | 完成就诊清单把“不适用”显示成绿色完成 | 是 | P2 | 前端 AI |

## 本轮建议结论

这三项都不是 Docker 或数据问题，而是页面交互、契约能力和业务规则表达不一致导致的体验问题。

建议执行顺序：

1. 前端先修 UF-03，把“不适用”从绿色完成态中拆出来，风险低、改动小。
2. 前端先修 UF-01 的重复展示和最终建议卡片；后端另行评估是否扩展多轮问诊契约。
3. UF-02 需要先确认患者是否能查看未审核检查申请，再决定是纯前端展示全状态，还是后端扩展患者检查流程 DTO。
