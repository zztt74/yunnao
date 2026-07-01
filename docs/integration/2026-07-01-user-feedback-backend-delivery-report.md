# 用户反馈问题后端实现 交付说明（UF-01 / UF-02 / UF-03）

日期：2026-07-01
分支：`codex/integration`
来源反馈：`2026-07-01-user-feedback-triage-exam-checklist-issues.md`
设计笔记/想法：`2026-07-01-user-feedback-thoughts-and-questions.md`

---

## 1. 测试命令与结果

```bash
cd backend
mvn test
```

结果：

```
Tests run: 394, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

原 388 个测试 + 本次新增 6 个（UF-02 流程追踪 4 个 + UF-01 多轮分诊 2 个），全部通过，无回归。

---

## 2. 反馈诉求与后端处理对照

| 反馈编号 | 诉求 | 后端范围 | 处理结论 |
|---|---|---|---|
| UF-01 | 智能问诊结果页重复、不能延续对话 | 扩展契约支持多轮上下文 | 已实现：请求/响应加 conversationId/history/round/isFinal/followUpQuestion |
| UF-02 | 患者端检查检验缺少流程提示 | 允许看未审核申请 + 补引导字段 | 已实现：新增 /tracking 接口 + ExaminationTrackingResponse DTO |
| UF-03 | 完成就诊清单"未开具"显示为完成 | 仅产品要求显式确认时才改业务规则 | 未改后端：反馈文档明确"沿用当前规则后端无需改"，前端三态展示即可 |

---

## 3. 完成的接口清单

| 编号 | 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|---|
| UF-01 | POST | `/api/triage/analyze` | PATIENT | 扩展支持多轮：请求加 conversationId/history/round（可选），响应加 conversationId/round/isFinal/followUpQuestion |
| UF-02 | GET | `/api/examinations/patient/{patientId}/tracking` | PATIENT（本人） | 患者检查流程追踪：返回全状态申请 + 引导字段（医生名/科室/设备/nextAction） |
| UF-02 | GET | `/api/examinations/encounter/{encounterId}/tracking` | DOCTOR/ADMIN | 按就诊查询检查流程追踪（医生端可用） |

注：
- UF-01 单轮完全兼容，老调用方式（4 参数）不受影响。
- UF-02 不改 `/api/examinations/patient/{patientId}`（老分页接口保留），前端可逐步迁移到 /tracking。
- UF-02 不改 `/api/examinations/{id}/result`，结果详情仍仅 REVIEWED 可见（安全边界保留）。

---

## 4. 修改的模块和文件

### 新增文件
- `triage/dto/ChatMessage.java` — UF-01 对话消息 record（role + content）
- `examination/dto/ExaminationTrackingResponse.java` — UF-02 流程追踪响应 DTO
- `docs/integration/2026-07-01-user-feedback-thoughts-and-questions.md` — 想法/问题文档
- `docs/integration/2026-07-01-user-feedback-backend-delivery-report.md` — 本交付说明

### 修改文件
- `triage/dto/TriageAnalyzeRequest.java` — 加 conversationId/history/round（可选）+ 单轮兼容构造器
- `triage/dto/TriageAnalyzeResponse.java` — 加 conversationId/round/isFinal/followUpQuestion
- `triage/service/TriageService.java` — analyze 处理多轮：传 history 给 AI provider、回显上下文
- `ai/api/AITriageService.java` — 新增 analyze(request, history, round) 默认重载（向后兼容）
- `examination/service/ExaminationService.java` — 注入 DepartmentRepository/DeviceRepository/DeviceUsageRepository；新增 getTrackingByPatient/getTrackingByEncounter + toTrackingResponse
- `examination/controller/ExaminationController.java` — 新增 2 个 /tracking 端点
- `examination/repository/ExaminationOrderRepository.java` — 加 findByPatientId(Long) 无分页重载

### 测试文件
- `examination/service/ExaminationServiceTest.java` — 补 3 个 @Mock + 4 个 UF-02 测试
- `triage/service/TriageServiceTest.java` — 2 个 UF-01 测试（多轮/单轮兼容）

---

## 5. 数据库是否需要变更

**不需要变更数据库基线。** 所有实现基于现有表结构：

- UF-01：conversationId/history/round 仅在请求/响应里流转，**TriageRecord 不存** conversationId（每次分诊仍生成独立记录）。若后续要审计多轮会话，再单独立项。
- UF-02：流程引导字段（doctorName/departmentName/deviceName/deviceLocation）通过 JOIN 现有 Doctor/Department/DeviceUsage/Device 表聚合，不扩 ExaminationOrder 表。

---

## 6. 关键设计决策

### UF-01 多轮扩展方式
- **采用"上下文传入式"，不做会话持久化。** 反馈诉求是"补充症状再问一次"，不是长期医疗会话跟进；持久化会话成本大、医疗责任边界模糊。
- 每轮仍生成一条独立 TriageRecord，conversationId 仅在响应里回显用于前端串联展示。
- AI 能力契约 `AITriageService` 加默认重载 `analyze(request, history, round)`，默认委托给单轮（具体 provider 可重写）。
- **当前 AI 契约 `TriageAIResult` 无显式 isFinal/followUpQuestion 字段**，因此 isFinal 默认 true、followUpQuestion 默认 null。若后续 AI provider 支持主动追问，再扩 TriageAIResult。详见 thoughts 文档 R1。

### UF-02 患者可见未审核申请
- **列表可见全状态，结果内容仅 REVIEWED 可见。** 反馈诉求是流程追踪（看不到 ORDERED 患者不知道要去做什么），但未审核结果可能不准、误导患者，保留安全边界。
- 新增独立 `/tracking` 接口而非改老接口，**老 `/patient/{patientId}` 保持全状态返回不动**，前端老 filter REVIEWED 逻辑不会破。
- 设备关联：DeviceUsage 按 encounterId 关联，**非精确关联 examinationOrderId**。一个 encounter 多项检查 + 多次设备使用时无法精确匹配，因此取该就诊下首个设备使用记录的设备信息（保守展示）。LABORATORY 类通常无设备使用记录，相关字段为 null。详见 thoughts 文档 R6。

### UF-03 后端不改
- 反馈文档明确"如果继续沿用当前规则，后端无需改"。
- 当前业务规则合理：医生没开检查/处方表示临床判断不需要，不阻塞完成就诊。
- 前端三态展示（done/pending/notApplicable）即可解决视觉误导。
- 若产品后续要求"显式确认不需要检查/处方"，再单独立项加字段，不在本次范围。

---

## 7. 关键 DTO 字段速览（供前端对接）

### UF-01 TriageAnalyzeRequest（扩展）
原：patientId, symptoms, duration, supplement
新增（可选）：conversationId(String), history(List<ChatMessage>), round(int, 1-20)

ChatMessage：role(USER/ASSISTANT), content(max 2000，只能症状描述，禁止传隐私)

### UF-01 TriageAnalyzeResponse（扩展）
原字段全部保留
新增：conversationId(String), round(int), isFinal(boolean), followUpQuestion(String)

单轮兼容：不带请求字段时，响应 conversationId=null、round=1、isFinal=true、followUpQuestion=null

### UF-02 ExaminationTrackingResponse
orderId, encounterId, orderType, itemCode, itemName, status,
doctorName, departmentId, departmentName, departmentLocation, nextAction,
deviceName, deviceLocation,
orderedAt, inProgressAt, resultEnteredAt, reviewedAt, cancelledAt, cancelReason

nextAction 派生规则：
- ORDERED → "医生已开立，请前往{departmentName}检查"
- IN_PROGRESS → "检查进行中"
- RESULT_ENTERED → "结果已录入，等待医生审核"
- REVIEWED → "已审核，可查看报告"
- CANCELLED → "已取消"

---

## 8. 仍需前端/联调/产品配合的事项

### 需前端 AI 配合
- **UF-01**：前端去掉"对话记录"和"推荐理由"重复展示；用 conversationId 串联多轮；isFinal=true 时展示最终建议卡片，false 时提供继续输入框。
- **UF-02**：患者端检查检验页改用 `/api/examinations/patient/{patientId}/tracking`，展示全状态 + nextAction 文案；去掉前端 filter REVIEWED。
- **UF-03**：前端清单项改三态（done/pending/notApplicable），"未开具"显示灰色"不适用"而非绿色完成。

### 需联调 AI 同步
- UF-01 新接口字段（conversationId/history/round/isFinal/followUpQuestion）需同步 OpenAPI。
- UF-02 新接口 `/api/examinations/patient/{patientId}/tracking`、`/api/examinations/encounter/{encounterId}/tracking` 需同步 OpenAPI。
- 补一条 E2E：开立检查 → 患者看 /tracking 显示 ORDERED → 录入结果 → 患者看 RESULT_ENTERED → 审核后看 REVIEWED。

### 待产品/契约组决策（详见 thoughts 文档第五节）
- Q1：AI 多轮追问是否允许？若不允许，followUpQuestion 恒 null、isFinal 恒 true（当前默认行为）。
- Q2：患者能否查看 RESULT_ENTERED 阶段的结果内容（提前看未审核结果）？当前保留"仅 REVIEWED 可见"。
- Q3：检查申请是否需要记 location/executor 字段以做精确引导？当前用 Department.description 兜底。
- Q4：DeviceUsage 是否加 examinationOrderId 以精确关联设备和检查申请？
- Q5：完成就诊是否要求医生显式确认"不需要检查/处方"？

以上 5 项均不阻塞本次后端交付（按反馈文档"当前规则即可"的边界实现）。

---

## 9. 风险与已知限制

- **UF-01 R1**：当前 AI provider 默认实现忽略 history（委托给单轮），真实多轮需 AI provider 重写 `analyze(request, history, round)`。后端契约已就绪，AI 实现侧待配置。
- **UF-01 R2**：history 传完整内容可能泄露隐私给 AI provider。已在 DTO 注释明确"content 只能是症状描述，禁止传姓名/手机号"，但后端无法强制校验内容性质。
- **UF-02 R6**：DeviceUsage 与 ExaminationOrder 无直接外键，设备关联是模糊的（按 encounterId 取首个）。若要精确，需在 DeviceUsage 加 examinationOrderId 字段（改表）。
- **UF-02**：Department 实体无 location 字段，departmentLocation 用 description 兜底。若后续 Department 加 location 字段，替换 toTrackingResponse 里对应取值即可。

---

*本交付严格遵循反馈文档边界与任务书红线（不擅自改 DB 基线、不擅自扩 OpenAPI、不擅自改前端、不擅自扩医疗业务规则）。所有可自决项已处理，需他人决策项保留在 thoughts 文档。*
