# 用户反馈问题后端实现：想法、建议与待确认问题

日期：2026-07-01
分支：`codex/integration`
来源反馈：`2026-07-01-user-feedback-triage-exam-checklist-issues.md`
关联交付：`2026-07-01-user-feedback-backend-delivery-report.md`

本文档不重复交付清单，只记录实施 UF-01/UF-02/UF-03 后端部分过程中的**判断依据、取舍、风险与待确认问题**。所有内容为建议性。

---

## 一、UF-01 智能问诊多轮上下文

### 1. 反馈诉求
患者端分诊结果页"对话记录"和"推荐理由"重复；AI 无追问时只能重新发起，不能延续对话。

### 2. 后端范围判断
反馈文档明确：前端展示重复是前端 AI 责任，**后端只负责"如果要支持连续问诊，扩展契约字段"**。

### 3. 实现判断与决策

**决策：采用"上下文传入式"多轮分诊，不做会话持久化。**

理由：
- 现有 `TriageRecord` 是"一次分诊一条记录"的模型，业务上是"分析症状→给建议→挂号"的单轮场景。
- 患者端"延续对话"的真实诉求是"补充症状再问一次"，不是医疗会话长期跟进。
- 做持久化会话（新增 conversation 表、消息表、TTL）成本大、与现有挂号流程不直接闭环、医疗责任边界模糊（AI 多轮追问是否构成诊断？）。
- 反馈文档自己也写"当前接口未接收 history"——最小扩展就是把 history 作为请求参数传进来，AI 拿到上下文做更精准分析，每次仍生成一条 TriageRecord。

### 4. 契约扩展
- `TriageAnalyzeRequest` 新增可选字段：
  - `conversationId`（String，前端生成 UUID 用于关联同一会话的多轮请求）
  - `history`（List<ChatMessage>，历史对话，role=user/assistant + content）
  - `round`（int，第几轮，从 1 开始）
- `TriageAnalyzeResponse` 新增可选字段：
  - `conversationId`（回显）
  - `round`（回显）
  - `isFinal`（boolean，AI 判断是否已可终结，true 时前端展示最终建议）
  - `followUpQuestion`（String，AI 主动追问的问题；isFinal=true 时为 null）
- `ChatMessage` record：`role`（USER/ASSISTANT）+ `content`

### 5. AI 能力契约
现有 `AITriageService.analyze(TriageAIRequest)` 是单轮。扩展方式：
- 新增重载 `analyze(TriageAIRequest req, List<ChatMessage> history, Integer round)`
- 默认实现：把 history 拼到 prompt 里交给 AI provider
- 老接口保持兼容

### 6. 数据库
**不新增表、不新增字段。** conversationId 只在请求/响应里流转，TriageRecord 不存（每次分诊仍生成独立记录，conversationId 仅用于前端串联展示）。理由：
- 不擅自改 DB 基线（任务书红线）
- 持久化会话非反馈诉求核心，"对话能延续"靠上下文传入即可达成
- 若后续要审计多轮会话，再单独立项

### 7. 风险与待确认
- **R1（P2，待产品确认）**：AI 多轮追问是否构成"医疗行为"？若产品要求 AI 不得追问（只做单轮分诊），则 `followUpQuestion` 永远为 null、`isFinal` 永远为 true，多轮扩展退化为单轮。当前实现保留能力、由 AI provider 决定是否追问，**默认行为由 AI 配置控制，业务侧不强制**。
- **R2（P3，记录）**：history 传完整内容可能泄露隐私给 AI provider。现有 `buildAIRequest` 已做最小化（不带 patientId/姓名）。history 里前端应只传症状描述，不传姓名/手机号。**契约层约束**：DTO 注释明确"history 内容只能是症状/主诉，禁止传隐私"。
- **R3（P3，记录）**：未做多轮 token 计费/限流。若 AI provider 按调用计费，多轮可能放大成本。运维侧关注。

---

## 二、UF-02 患者端检查检验流程追踪

### 1. 反馈诉求
患者端只看到 REVIEWED 结果，看不到 ORDERED/IN_PROGRESS/RESULT_ENTERED 阶段，无"去哪里/找谁/用什么设备"提示。

### 2. 后端范围判断
反馈文档把责任拆成：
- 前端：展示全状态（前端 AI 责任）
- 后端：**确认患者能否看未审核申请** + **若要展示执行信息，DTO 要补字段**

### 3. 决策一：患者可见未审核申请

**决策：允许患者查看本人所有状态的检查申请列表，但 RESULT_TEXT/CONCLUSION 等结果内容仅 REVIEWED 后可见。**

理由：
- 反馈诉求是"流程追踪"，看不到 ORDERED 患者就不知道"医生开了什么检查、要不要去做"。
- 后端注释 `患者只能查看 REVIEWED 结果` 是 `getResultByOrderId` 的逻辑（查结果详情），**不是 `getOrdersByPatient` 的逻辑**。当前 `getOrdersByPatient` 实际未做状态过滤（查全量，只是前端 filter 了 REVIEWED）。
- 所以**列表接口本身已经返回全状态**，无需改后端代码——前端去掉 filter 即可。
- 但结果详情 `getResultByOrderId` 的"患者只能 REVIEWED"是合理的安全边界（未审核结果可能不准、误导患者），保留。

**实现**：
- 新增 `GET /api/examinations/patient/{patientId}/tracking` 专门给流程追踪用，返回全状态列表 + 流程引导字段。
- 保留 `GET /api/examinations/patient/{patientId}` 不动（向后兼容，前端老 filter 逻辑不会破）。
- 结果详情接口不动（保持 REVIEWED 才能查结果内容）。

### 4. 决策二：流程引导字段

反馈要"去哪里/找谁/用什么设备"。现有 `ExaminationOrder` 只有 patientId/doctorId/itemCode/itemName/status/时间戳，**没有 location/executorName/deviceId/nextAction**。

**决策：新增 `ExaminationTrackingResponse` DTO，聚合现有可获取的引导信息，不扩 ExaminationOrder 表。**

可聚合字段：
- `orderId`、`itemName`、`orderType`、`status`（来自 ExaminationOrder）
- `doctorName`（来自 Doctor，已可 JOIN）
- `departmentName`、`departmentLocation`（来自 Department，已可 JOIN）
- `deviceName`、`deviceLocation`（来自 DeviceUsage JOIN Device，**按 encounterId 关联**——现有 DeviceUsage 有 encounterId 字段）
- `nextAction`（基于 status 派生：ORDERED→"请前往 {departmentName} 检查"、IN_PROGRESS→"检查进行中"、RESULT_ENTERED→"等待医生审核"、REVIEWED→"已审核可查看报告"、CANCELLED→"已取消"）
- `timeline`（List<LocalDateTime>：orderedAt, inProgressAt, resultEnteredAt, reviewedAt, cancelledAt）

**不扩表的理由**：
- location 已存在 Department.location 和 Device.location，无需在 ExaminationOrder 上冗余。
- executorName 可由 DeviceUsage.usedBy → UserAccount.username 推导（但管理员/医生才看得到执行人，患者端脱敏为"检查技师"即可）。
- 不擅自改 DB 基线（任务书红线）。

### 5. 设备关联方式

DeviceUsage 按 encounterId 关联，不是按 examinationOrderId。同一 encounter 可能有多条 ExaminationOrder（多项检查），也有多条 DeviceUsage（多次设备使用）。

**关联策略**：
- 用 `encounterId + itemName LIKE device.type/name` 做模糊关联，**不保证精确匹配**（一个 encounter 多项检查时无法确定哪条 DeviceUsage 对应哪条 ExaminationOrder）。
- 或更保守：**只返回 encounter 下所有 DeviceUsage 的设备名/位置列表，让前端自己展示**，不做精确映射。
- **采用保守方案**：DTO 里 deviceName/deviceLocation 为 null 时不展示（多数检查申请可能根本没关联设备使用记录，比如 LABORATORY 类）。

### 6. 风险与待确认
- **R4（P1，待产品确认）**：患者能否看到"未审核结果的内容"？当前实现保留"REVIEWED 才能查结果详情"。若产品要求 RESULT_ENTERED 时患者可见结果（提前看），需放宽 `checkResultAccessForPatient`。
- **R5（P2，待确认）**：检查申请是否要记 location/executor 字段？若产品要求精确引导"去 B 超 3 号室找张医生"，需在 ExaminationOrder 加字段。当前用 Department.location 兜底，不精确。
- **R6（P3，记录）**：DeviceUsage 与 ExaminationOrder 无直接外键，设备关联是模糊的。若要精确，需在 DeviceUsage 加 examinationOrderId 字段（改表）。

---

## 三、UF-03 完成就诊清单"未开具"显示为完成

### 1. 反馈诉求
未开具检查/处方时清单显示绿色完成，误导。

### 2. 后端范围判断
反馈文档明确：**前端三态展示是前端 AI 责任；后端只在"产品要求必须显式确认不需要检查/处方"时才改业务规则**。

### 3. 决策：后端不改

理由：
- 反馈文档建议处理明确写"如果继续沿用当前规则，后端无需改"。
- 当前业务规则合理：医生没开检查/处方表示临床判断不需要，不阻塞完成就诊。
- 前端三态展示即可解决视觉误导，不需后端配合。
- 若产品后续要求"显式确认不需要"，再单独立项加字段（如 `examinationSkippedReason`），不在本次范围。

### 4. 风险与待确认
- **R7（P2，待产品确认）**：是否要求医生完成就诊时显式确认"本次不需要检查/处方"？若是，后端需扩 Encounter 加 `examinationSkipped`/`prescriptionSkipped` 字段 + 完成校验逻辑。当前不做。

---

## 四、实现总结

| 项 | 后端要做 | 后端不做 | 风险 |
|---|---|---|---|
| UF-01 | 扩 TriageAnalyzeRequest/Response 加 conversationId/history/round/isFinal/followUpQuestion + AITriageService 重载 | 不做会话持久化、不改 TriageRecord 表、不改 DB 基线 | R1 AI 追问是否医疗行为（产品）、R2 隐私（约束在 DTO 注释）、R3 计费 |
| UF-02 | 新增 /api/examinations/patient/{patientId}/tracking + ExaminationTrackingResponse DTO（聚合 Doctor/Department/Device 信息） | 不改 getOrdersByPatient（保持全状态返回，前端去掉 filter）、不改 getResultByOrderId（保留 REVIEWED 才能查结果）、不扩 ExaminationOrder 表 | R4 未审核结果可见性（产品）、R5 精确引导字段（产品）、R6 DeviceUsage 关联模糊 |
| UF-03 | 无 | 不改完成就诊校验、不加显式确认字段 | R7 产品是否要求显式确认 |

---

## 五、待确认问题清单

| # | 问题 | 谁决策 | 优先级 |
|---|---|---|---|
| Q1 | AI 多轮追问是否允许？若不允许，followUpQuestion 恒为 null、isFinal 恒为 true | 产品 + 医疗合规 | P2 |
| Q2 | 患者能否查看 RESULT_ENTERED 阶段的结果内容（提前看未审核结果）？ | 产品 | P1 |
| Q3 | 检查申请是否需要记 location/executor 字段以做精确引导？ | 产品 + DBA | P2 |
| Q4 | DeviceUsage 是否加 examinationOrderId 以精确关联设备和检查申请？ | DBA + 后端架构 | P3 |
| Q5 | 完成就诊是否要求医生显式确认"不需要检查/处方"？ | 产品 | P2 |

以上 5 项均不阻塞本次后端交付（按反馈文档"当前规则即可"的边界实现），保留为后续产品/架构决策。

---

*本文档为实施者视角的判断与建议，不构成已交付代码的一部分。所有建议项需相关负责人评估后决定是否实施。*
