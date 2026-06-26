# 任务三b：检查检验解读 — 交付说明

> 任务编号：STAGE-AI-3b
> 目标分支：`feature/ai-result-interpretation`
> 契约版本：AI 协作文档 v2.5
> 依据：[AI能力集成开发计划.md#L232-L287](file:///d:/shixun/AI能力集成开发计划.md#L232-L287)、[32_AI能力契约规范.md](file:///d:/shixun/yunnao/智慧云脑诊疗平台_AI协作文档_v2/contracts/32_AI能力契约规范.md)、[13_AI能力集成AI任务书.md 第3.5节](file:///d:/shixun/yunnao/智慧云脑诊疗平台_AI协作文档_v2/roles/13_AI能力集成AI任务书.md)、[42_PR与交付规范.md](file:///d:/shixun/yunnao/智慧云脑诊疗平台_AI协作文档_v2/workflow/42_PR与交付规范.md)

---

## 1. 实现的 AI 能力

检查检验解读：将 [AIResultInterpretationService](file:///d:/shixun/yunnao/backend/src/main/java/com/neusoft/cloudbrain/ai/api/AIResultInterpretationService.java) 从关键词 Mock 升级为 Provider + Prompt + Schema 的真实能力实现，输出异常项、通俗解释、关注点、随访建议和安全声明（5 字段），不修改原始检查数值，禁止编造输入中不存在的检查结果。

---

## 2. 输入输出 Schema

### 输入（[ResultInterpretationAIRequest](file:///d:/shixun/yunnao/backend/src/main/java/com/neusoft/cloudbrain/ai/dto/ResultInterpretationAIRequest.java)）

| 字段 | 类型 | 说明 |
|---|---|---|
| itemName | String | 项目名称 |
| resultText | String | 结果文本 |
| normalRange | String | 参考范围 |
| orderType | String | 类型（LAB/EXAM 等） |

不包含患者 ID、姓名、手机号等隐私信息（最小化原则）。

### 输出（[ResultInterpretationAIResult](file:///d:/shixun/yunnao/backend/src/main/java/com/neusoft/cloudbrain/ai/dto/ResultInterpretationAIResult.java)）

| 字段 | 类型 | 受控规则 |
|---|---|---|
| abnormalItems | List\<String\> | 异常指标列表（无异常时为空列表） |
| plainLanguageExplanation | String | 通俗语言解释（面向非专业人士） |
| possibleAttentionPoints | List\<String\> | 需关注的要点（无异常时为空列表） |
| followUpSuggestion | String | 随访建议（复查/就诊/生活方式） |
| disclaimer | String | 固定免责声明 |

**字段重命名对齐**：依据 [13_AI能力集成AI任务书.md 第3.5节](file:///d:/shixun/yunnao/智慧云脑诊疗平台_AI协作文档_v2/roles/13_AI能力集成AI任务书.md)，原 `followUpAdvice` 重命名为 `followUpSuggestion`，并新增 `possibleAttentionPoints` 字段。

---

## 3. Prompt 文件

[result_interpretation_v1.txt](file:///d:/shixun/yunnao/backend/src/main/resources/prompts/result_interpretation_v1.txt)

本次更新内容（对照 [32_AI能力契约规范.md 第6节](file:///d:/shixun/yunnao/智慧云脑诊疗平台_AI协作文档_v2/contracts/32_AI能力契约规范.md)）：

- 明确任务边界（解读异常指标，列出关注点，给出随访建议）
- 指定输出 JSON Schema（5 字段：abnormalItems、plainLanguageExplanation、possibleAttentionPoints、followUpSuggestion、disclaimer）
- 禁止编造未提供的指标或数值
- 不得修改原始检查数值，仅做解读和说明
- 危急值必须重点提示
- 输入为空或无异常时返回空数组，plainLanguageExplanation 说明"暂无明显异常"
- 不得输出个人隐私信息和数据库 ID
- 输出纯 JSON，不包含 markdown 代码块标记

---

## 4. Provider 变化

### AIResultInterpretationServiceImpl 改造

[AIResultInterpretationServiceImpl](file:///d:/shixun/yunnao/backend/src/main/java/com/neusoft/cloudbrain/ai/application/AIResultInterpretationServiceImpl.java) 变更：

1. **Schema 校验对齐**：`parseResultInterpretationResponse` 校验 3 个必填字符串字段（plainLanguageExplanation、followUpSuggestion、disclaimer），解析 2 个数组字段（abnormalItems、possibleAttentionPoints，缺失时返回空列表）
2. **错误码**：
   - 超时/Provider 异常 → `AI_RESULT_INTERPRETATION_FAILED`（HTTP 504）
   - 非法 JSON / Schema 校验失败 → `AI_RESULT_INTERPRETATION_FAILED`（HTTP 500）

### ResultInterpretationAIResult DTO 改造

[ResultInterpretationAIResult](file:///d:/shixun/yunnao/backend/src/main/java/com/neusoft/cloudbrain/ai/dto/ResultInterpretationAIResult.java) 变更：

1. **Canonical 构造函数**：5 字段（对齐 AI 任务书 Schema，含 possibleAttentionPoints、followUpSuggestion）
2. **向后兼容构造函数**：4 参数（含 followUpAdvice），委托到 5 参数构造函数，possibleAttentionPoints 填充空列表。保留此构造函数是因为禁止修改的 [ExaminationServiceTest](file:///d:/shixun/yunnao/backend/src/test/java/com/neusoft/cloudbrain/examination/service/ExaminationServiceTest.java) 以 4 参数构造 DTO
3. **followUpAdvice() 访问器**：返回 `followUpSuggestion`，供 [ExaminationService](file:///d:/shixun/yunnao/backend/src/main/java/com/neusoft/cloudbrain/examination/service/ExaminationService.java#L200) 调用 `result.setAiFollowUpAdvice(interpretation.followUpAdvice())` 使用

### MockAIProvider 调整

[MockAIProvider](file:///d:/shixun/yunnao/backend/src/main/java/com/neusoft/cloudbrain/ai/provider/MockAIProvider.java) 变更：

- 空结果响应：使用 5 字段 Schema（followUpSuggestion 替代 followUpAdvice，新增 possibleAttentionPoints 空数组）
- 正常响应：各异常路径（偏高/偏低/阳性/正常）均补充 possibleAttentionPoints
- 新增高风险场景：`buildHighRiskResultInterpretationResponse()`，危急值解读，明确"原始数值未被修改，请以报告为准"，关注点含"立即就诊""持续监护生命体征""复核原始数值"
- `buildHighRiskResponse` 新增 `result_interpretation` 分支

---

## 5. Mock 场景

6 场景全覆盖（对照 [32_AI能力契约规范.md 第4节](file:///d:/shixun/yunnao/智慧云脑诊疗平台_AI协作文档_v2/contracts/32_AI能力契约规范.md)）：

| 场景 | 触发关键词 | 行为 |
|---|---|---|
| 正常结果 | 默认（偏高/偏低/阳性/正常） | 基于结果文本解读异常指标（5 字段） |
| 高风险（危急值） | 危急/critical 或 MOCK_HIGH_RISK | 危急值解读，明确"原始数值未被修改"，建议立即就诊 |
| 空结果 | MOCK_EMPTY | abnormalItems 和 possibleAttentionPoints 为空数组，plainLanguageExplanation 说明"暂无明显异常" |
| 超时 | MOCK_TIMEOUT | 抛 AIProviderException(retryable=true) |
| 非法 JSON | MOCK_INVALID_JSON | 返回非 JSON 文本 |
| Provider 异常 | MOCK_PROVIDER_ERROR | 抛 AIProviderException(retryable=false, 500) |

---

## 6. 超时和降级策略

```text
超时 → 重试 1 次 → 仍失败 → AIInvocation(FAILED) → 抛 AI_RESULT_INTERPRETATION_FAILED (HTTP 504) → 业务不影响（ExaminationService 捕获，AiStatus=FAILED）
非法 JSON → AI_RESULT_INTERPRETATION_FAILED (HTTP 500) → 不无限修复 → 降级
缺失必填字段 → AIInvalidResponseException → AI_RESULT_INTERPRETATION_FAILED (HTTP 500) → 降级
```

- 重试仅对超时/5xx，非法 JSON 不重试
- 不无限修复模型输出
- 1 次业务调用 = 1 条 AIInvocation + N 条 AIInvocationAttempt
- AI 解读失败不影响 ExaminationService 业务主流程（结果仍保存，AiStatus=FAILED）

---

## 7. 测试结果

### AIResultInterpretationServiceImplTest — 7 个用例

| 分组 | 用例数 | 说明 |
|---|---:|---|
| 正常调用 | 1 | 解读异常指标（5 字段），followUpAdvice() 向后兼容访问器验证 |
| 高风险（危急值） | 1 | 危急值解读，plainLanguageExplanation 含"原始数值未被修改" |
| 空结果 | 1 | 空输入返回空集合+说明而非编造 |
| 超时 | 1 | Provider 超时降级为 BusinessException 504 |
| 非法 JSON | 1 | 响应非法降级为 BusinessException 500 |
| Provider 异常 | 1 | Provider 异常降级为 BusinessException 504 |
| Schema 校验 | 1 | 缺失必填字段 followUpSuggestion 抛出 BusinessException 500 |

### MockAIProviderTest — 16 个用例

含七场景分流 + 五能力响应格式 + 安全声明 + mock 标识。结果解读响应断言已更新为 5 字段（possibleAttentionPoints、followUpSuggestion），不再使用 followUpAdvice。

### 全量测试结果

```
Tests run: 39, Failures: 0, Errors: 0, Skipped: 0
  - AIResultInterpretationServiceImplTest: 7
  - MockAIProviderTest: 16
  - ExaminationServiceTest（业务模块回归）: 16
BUILD SUCCESS
```

无回归。

---

## 8. 对后端业务模块的调用说明

- AI 结果解读不直接对外暴露 HTTP，由 examination 业务模块编排调用
- 调用入口：`AIResultInterpretationService.interpret(ResultInterpretationAIRequest)`
- 输入不含患者隐私 ID（最小化原则）
- 异常时 ExaminationService 捕获 Exception，AiStatus=FAILED，业务主流程不受影响：
  - `AI_RESULT_INTERPRETATION_FAILED` (504) → AiStatus=FAILED，AiFailureReason 记录原因
  - `AI_RESULT_INTERPRETATION_FAILED` (500) → AiStatus=FAILED，AiFailureReason 记录原因
- `ResultInterpretationAIResult.followUpAdvice()` 返回 followUpSuggestion，供 ExaminationService.setAiFollowUpAdvice 使用（向后兼容）

---

## 9. 潜在安全风险

- Prompt 中明确禁止编造和修改原始数值，但真实模型可能不遵守 → 由 JsonSchemaParser 做必填字段校验兜底，Prompt 显式要求"不得修改原始检查数值"
- 输入已做脱敏（不含患者隐私 ID），但需业务模块确保传入前完成脱敏
- 日志和 AIInvocation 中不保存 API Key（由 AIInvocationRecorder 保证）

---

## 10. 未解决问题

- JSON Schema 校验仍为手写降级方案（必填字段校验），是否引入 `com.networknt:json-schema-validator` 待后端角色确认
- possibleAttentionPoints 目前由 Mock 静态填充，真实模型输出的关注点质量需联调验证

---

## 交付物对比

| 任务卡要求 | 交付状态 |
|---|---|
| prompts/result-interpretation-system.txt | ✅ [result_interpretation_v1.txt](file:///d:/shixun/yunnao/backend/src/main/resources/prompts/result_interpretation_v1.txt)（按 PromptManager 命名规范 `{capability}_v{version}.txt`） |
| AIResultInterpretationServiceImpl 改造 | ✅ Provider 调用 + Schema 校验（5 字段） + 调用记录 + 错误码 |
| Mock 场景 6 种 | ✅ 正常/高风险（危急值）/空结果/超时/非法JSON/异常 |
| 不修改原始检查数值 | ✅ Prompt 规则 + 高风险 Mock 明确"原始数值未被修改" |
| 空输入返回空集合+说明 | ✅ abnormalItems/possibleAttentionPoints 空数组 + "暂无明显异常" |

## 修改边界遵守

```text
允许修改：ai/** ✅、prompts/result_interpretation_v1.txt ✅
禁止修改：examination 业务模块 ✅（未修改）、laboratory ✅、medicalrecord ✅、其他业务模块 ✅、pom.xml ✅、contracts/** ✅
```

修改文件清单：
1. `backend/src/main/resources/prompts/result_interpretation_v1.txt` — Prompt 对齐 5 字段 Schema
2. `backend/src/main/java/.../ai/dto/ResultInterpretationAIResult.java` — 5 字段 canonical 构造 + 向后兼容 4 参数构造 + followUpAdvice() 访问器
3. `backend/src/main/java/.../ai/application/AIResultInterpretationServiceImpl.java` — Schema 校验 5 字段 + 错误码
4. `backend/src/main/java/.../ai/provider/MockAIProvider.java` — 5 字段 Schema + 新增危急值高风险场景
5. `backend/src/test/java/.../ai/application/AIResultInterpretationServiceImplTest.java` — 7 测试用例（6 Mock 场景 + 1 Schema 校验）
6. `backend/src/test/java/.../ai/provider/MockAIProviderTest.java` — 结果解读断言更新（5 字段，不再使用 followUpAdvice）
