# 任务三a：病历生成 — 交付说明

> 任务编号：STAGE-AI-3a
> 目标分支：`feature/ai-medical-record`
> 契约版本：AI 协作文档 v2.5
> 依据：[AI能力集成开发计划.md#L178-L231](file:///d:/shixun/AI能力集成开发计划.md#L178-L231)、[32_AI能力契约规范.md](file:///d:/shixun/yunnao/智慧云脑诊疗平台_AI协作文档_v2/contracts/32_AI能力契约规范.md)、[13_AI能力集成AI任务书.md](file:///d:/shixun/yunnao/智慧云脑诊疗平台_AI协作文档_v2/roles/13_AI能力集成AI任务书.md)、[42_PR与交付规范.md](file:///d:/shixun/yunnao/智慧云脑诊疗平台_AI协作文档_v2/workflow/42_PR与交付规范.md)

---

## 1. 实现的 AI 能力

病历生成：将 [AIMedicalRecordService](file:///d:/shixun/yunnao/backend/src/main/java/com/neusoft/cloudbrain/ai/api/AIMedicalRecordService.java) 从关键词 Mock 升级为 Provider + Prompt + Schema 的真实能力实现，输出病历草稿（6 字段），缺失信息留空或标记，禁止编造，不得自动确认病历。

---

## 2. 输入输出 Schema

### 输入（[MedicalRecordAIRequest](file:///d:/shixun/yunnao/backend/src/main/java/com/neusoft/cloudbrain/ai/dto/MedicalRecordAIRequest.java)）

| 字段 | 类型 | 说明 |
|---|---|---|
| chiefComplaint | String | 主诉 |
| presentIllness | String | 现病史 |
| pastHistory | String | 既往史 |
| physicalExamination | String | 体格检查 |
| preliminaryDiagnoses | List\<String\> | 初步诊断列表 |
| treatmentSuggestion | String | 治疗建议 |

不包含患者 ID、姓名、手机号等隐私信息（最小化原则）。

### 输出（[MedicalRecordAIResult](file:///d:/shixun/yunnao/backend/src/main/java/com/neusoft/cloudbrain/ai/dto/MedicalRecordAIResult.java)）

| 字段 | 类型 | 受控规则 |
|---|---|---|
| chiefComplaint | String | 主诉（简洁概括症状及持续时间） |
| presentIllness | String | 现病史（按时间线整理） |
| pastHistory | String | 既往史（缺失时留空或"不详"） |
| physicalExamination | String | 体格检查（缺失时留空或"待查"） |
| preliminaryDiagnosis | String | 初步诊断（信息不足时"待进一步明确"） |
| treatmentSuggestion | String | 诊疗建议（信息不足时留空） |

**disclaimer 不在 AI 输出 Schema 中**（依据 [13_AI能力集成AI任务书.md 第3.3节](file:///d:/shixun/yunnao/智慧云脑诊疗平台_AI协作文档_v2/roles/13_AI能力集成AI任务书.md)）。安全声明由 `MedicalRecordAIResult.disclaimer()` 固定方法返回，供业务模块 [MedicalRecordService](file:///d:/shixun/yunnao/backend/src/main/java/com/neusoft/cloudbrain/medicalrecord/service/MedicalRecordService.java) 拼接"【说明】"段落使用。

---

## 3. Prompt 文件

[medical_record_v1.txt](file:///d:/shixun/yunnao/backend/src/main/resources/prompts/medical_record_v1.txt)

本次更新内容（对照 [32_AI能力契约规范.md 第6节](file:///d:/shixun/yunnao/智慧云脑诊疗平台_AI协作文档_v2/contracts/32_AI能力契约规范.md)）：

- 明确任务边界（病历草稿辅助生成，仅供医生参考）
- 指定输出 JSON Schema（6 字段，不含 disclaimer）
- 禁止编造未提及的症状、体征或诊断
- 缺失信息留空（空字符串）或如实标注"不详""待查"
- 不得在输出中包含个人隐私信息和数据库 ID
- 不得自动确认病历，输出仅为草稿
- 不得作出确定性医疗承诺
- 输出纯 JSON，不包含 markdown 代码块标记

---

## 4. Provider 变化

### AIMedicalRecordServiceImpl 改造

[AIMedicalRecordServiceImpl](file:///d:/shixun/yunnao/backend/src/main/java/com/neusoft/cloudbrain/ai/application/AIMedicalRecordServiceImpl.java) 变更：

1. **Schema 校验对齐**：`parseMedicalRecordResponse` 校验 6 个必填字段（chiefComplaint、presentIllness、pastHistory、physicalExamination、preliminaryDiagnosis、treatmentSuggestion），不再读取 disclaimer 字段
2. **错误码**：
   - 超时/Provider 异常 → `AI_MEDICAL_RECORD_FAILED`（HTTP 504）
   - 非法 JSON / Schema 校验失败 → `AI_MEDICAL_RECORD_FAILED`（HTTP 500）
3. **调用编排**：通过 `AIInvocationRecorder` 统一调用 Provider（含重试和调用记录），关键不再下沉到 Service

### MedicalRecordAIResult DTO 改造

[MedicalRecordAIResult](file:///d:/shixun/yunnao/backend/src/main/java/com/neusoft/cloudbrain/ai/dto/MedicalRecordAIResult.java) 变更：

1. **Canonical 构造函数**：6 字段（对齐 AI 任务书 Schema，不含 disclaimer）
2. **向后兼容构造函数**：7 参数（含 disclaimer），委托到 6 参数构造函数，忽略传入的 disclaimer 参数。保留此构造函数是因为禁止修改的 [MedicalRecordServiceTest](file:///d:/shixun/yunnao/backend/src/test/java/com/neusoft/cloudbrain/medicalrecord/service/MedicalRecordServiceTest.java) 以 7 参数构造 DTO
3. **disclaimer() 方法**：返回固定安全声明 `"本病历草稿由 AI 辅助生成，仅供医生参考，需医生确认后形成正式病历"`，供 MedicalRecordService 拼接病历内容使用

### MockAIProvider 调整

[MockAIProvider](file:///d:/shixun/yunnao/backend/src/main/java/com/neusoft/cloudbrain/ai/provider/MockAIProvider.java) 变更：

- 正常响应：移除 disclaimer 字段，pastHistory/physicalExamination 标记为"不详"/"待查"
- 空结果响应：移除 disclaimer 字段，6 字段全为空字符串
- 新增高风险场景：`buildHighRiskMedicalRecordResponse()`，胸痛症状生成高风险草稿，诊断含"待查"字样不自动确诊

---

## 5. Mock 场景

6 场景全覆盖（对照 [32_AI能力契约规范.md 第4节](file:///d:/shixun/yunnao/智慧云脑诊疗平台_AI协作文档_v2/contracts/32_AI能力契约规范.md)）：

| 场景 | 触发关键词 | 行为 |
|---|---|---|
| 正常结果 | 默认 | 基于问诊内容整理病历草稿（6 字段） |
| 高风险结果 | MOCK_HIGH_RISK 或胸痛关键词 | 胸痛→"胸痛待查，警惕急性心肌梗死" + 急诊建议，不自动确诊 |
| 空结果 | MOCK_EMPTY | 6 字段全为空字符串，不编造内容 |
| 超时 | MOCK_TIMEOUT | 抛 AIProviderException(retryable=true) |
| 非法 JSON | MOCK_INVALID_JSON | 返回非 JSON 文本 |
| Provider 异常 | MOCK_PROVIDER_ERROR | 抛 AIProviderException(retryable=false, 500) |

---

## 6. 超时和降级策略

```text
超时 → 重试 1 次 → 仍失败 → AIInvocation(FAILED) → 抛 AI_MEDICAL_RECORD_FAILED (HTTP 504) → 业务进手动填写
非法 JSON → AI_MEDICAL_RECORD_FAILED (HTTP 500) → 不无限修复 → 降级
缺失必填字段 → AIInvalidResponseException → AI_MEDICAL_RECORD_FAILED (HTTP 500) → 降级
```

- 重试仅对超时/5xx，非法 JSON 不重试
- 不无限修复模型输出
- 1 次业务调用 = 1 条 AIInvocation + N 条 AIInvocationAttempt

---

## 7. 测试结果

### AIMedicalRecordServiceImplTest — 7 个用例

| 分组 | 用例数 | 说明 |
|---|---:|---|
| 正常调用 | 1 | 生成病历草稿（6 字段），disclaimer() 返回固定安全声明 |
| 高风险场景 | 1 | 胸痛症状生成高风险草稿，诊断含"待查"不自动确诊 |
| 空结果 | 1 | 空输入返回空字段而非编造 |
| 超时 | 1 | Provider 超时降级为 BusinessException 504 |
| 非法 JSON | 1 | 响应非法降级为 BusinessException 500 |
| Provider 异常 | 1 | Provider 异常降级为 BusinessException 504 |
| Schema 校验 | 1 | 缺失必填字段 treatmentSuggestion 抛出 BusinessException 500 |

### MockAIProviderTest — 16 个用例

含七场景分流 + 五能力响应格式 + 安全声明 + mock 标识。病历生成响应断言已更新为 6 字段不含 disclaimer。

### 全量测试结果

```
Tests run: 23, Failures: 0, Errors: 0, Skipped: 0
  - AIMedicalRecordServiceImplTest: 7
  - MockAIProviderTest: 16
BUILD SUCCESS
```

### MedicalRecordServiceTest（业务模块回归）— 16 个用例全部通过

```
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

无回归。

---

## 8. 对后端业务模块的调用说明

- AI 病历生成不直接对外暴露 HTTP，由 medicalrecord 业务模块编排调用
- 调用入口：`AIMedicalRecordService.generate(MedicalRecordAIRequest)`
- 输入不含患者隐私 ID（最小化原则）
- 异常时业务模块捕获 `BusinessException`，按错误码降级：
  - `AI_MEDICAL_RECORD_FAILED` (504) → 转医生手工填写
  - `AI_MEDICAL_RECORD_FAILED` (500) → 转医生手工填写
- `MedicalRecordAIResult.disclaimer()` 返回固定安全声明，供 MedicalRecordService 拼接"【说明】"段落

---

## 9. 潜在安全风险

- Prompt 中明确禁止编造和自动确诊，但真实模型可能不遵守 → 由 JsonSchemaParser 做必填字段校验兜底
- 输入已做脱敏（不含患者隐私 ID），但需业务模块确保传入前完成脱敏
- 日志和 AIInvocation 中不保存 API Key（由 AIInvocationRecorder 保证）

---

## 10. 未解决问题

- JSON Schema 校验仍为手写降级方案（必填字段校验），是否引入 `com.networknt:json-schema-validator` 待后端角色确认
- disclaimer 作为固定常量返回，如未来需多语言或动态配置，需另行评估

---

## 交付物对比

| 任务卡要求 | 交付状态 |
|---|---|
| prompts/medical-record-system.txt | ✅ [medical_record_v1.txt](file:///d:/shixun/yunnao/backend/src/main/resources/prompts/medical_record_v1.txt)（按 PromptManager 命名规范 `{capability}_v{version}.txt`） |
| AIMedicalRecordServiceImpl 改造 | ✅ Provider 调用 + Schema 校验（6 字段） + 调用记录 + 错误码 |
| Mock 场景 6 种 | ✅ 正常/高风险/空结果/超时/非法JSON/异常 |
| 缺失信息留空或标记 | ✅ Prompt 约束 + Mock 示例（"不详"/"待查"/空字符串） |
| 不得自动确认病历 | ✅ Prompt 规则 + disclaimer 固定声明"需医生确认后形成正式病历" |

## 修改边界遵守

```text
允许修改：ai/** ✅、prompts/medical_record_v1.txt ✅
禁止修改：medicalrecord 业务模块 ✅（未修改）、examination ✅、laboratory ✅、其他业务模块 ✅、pom.xml ✅、contracts/** ✅
```

修改文件清单：
1. `backend/src/main/resources/prompts/medical_record_v1.txt` — Prompt 对齐 6 字段 Schema
2. `backend/src/main/java/.../ai/dto/MedicalRecordAIResult.java` — 6 字段 canonical 构造 + 向后兼容 7 参数构造 + disclaimer() 方法
3. `backend/src/main/java/.../ai/application/AIMedicalRecordServiceImpl.java` — Schema 校验 6 字段 + 错误码
4. `backend/src/main/java/.../ai/provider/MockAIProvider.java` — 移除 disclaimer + 新增高风险场景
5. `backend/src/test/java/.../ai/application/AIMedicalRecordServiceImplTest.java` — 7 测试用例（6 Mock 场景 + 1 Schema 校验）
6. `backend/src/test/java/.../ai/provider/MockAIProviderTest.java` — 病历生成断言更新（6 字段不含 disclaimer）
