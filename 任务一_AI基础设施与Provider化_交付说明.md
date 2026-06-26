# 任务一：AI 基础设施与 Provider 化 — 交付说明

- **任务编号**：STAGE-AI-1
- **任务类型**：基础设施
- **契约版本**：AI 协作文档 v2.5
- **目标分支**：`feature/ai-infrastructure`
- **交付状态**：✅ 已完成（编译通过 + 测试全绿）

---

## 一、交付物清单

按 `AI能力集成开发计划.md` 任务一交付物表逐项对照：

| 类别 | 交付物路径 | 状态 |
|---|---|---|
| 配置 | [backend/src/main/resources/application-ai.yml](file:///d:/shixun/yunnao/backend/src/main/resources/application-ai.yml) | ✅ |
| 配置类 | [ai/config/AIProperties.java](file:///d:/shixun/yunnao/backend/src/main/java/com/neusoft/cloudbrain/ai/config/AIProperties.java)、[ai/config/AIConfig.java](file:///d:/shixun/yunnao/backend/src/main/java/com/neusoft/cloudbrain/ai/config/AIConfig.java) | ✅ |
| 异常 | [ai/exception/AIInvalidResponseException.java](file:///d:/shixun/yunnao/backend/src/main/java/com/neusoft/cloudbrain/ai/exception/AIInvalidResponseException.java)、[ai/exception/AIProviderException.java](file:///d:/shixun/yunnao/backend/src/main/java/com/neusoft/cloudbrain/ai/exception/AIProviderException.java) | ✅ |
| 解析器 | [ai/parser/JsonSchemaParser.java](file:///d:/shixun/yunnao/backend/src/main/java/com/neusoft/cloudbrain/ai/parser/JsonSchemaParser.java) | ✅ |
| 真实 Provider | [ai/provider/HttpLLMProvider.java](file:///d:/shixun/yunnao/backend/src/main/java/com/neusoft/cloudbrain/ai/provider/HttpLLMProvider.java) | ✅ |
| Mock 改造 | [ai/provider/MockAIProvider.java](file:///d:/shixun/yunnao/backend/src/main/java/com/neusoft/cloudbrain/ai/provider/MockAIProvider.java) | ✅ |
| 记录器 | [ai/application/AIInvocationRecorder.java](file:///d:/shixun/yunnao/backend/src/main/java/com/neusoft/cloudbrain/ai/application/AIInvocationRecorder.java) | ✅ |
| Prompt 目录 | [backend/src/main/resources/prompts/](file:///d:/shixun/yunnao/backend/src/main/resources/prompts/)（5 个 system prompt 骨架） | ✅ |
| Service 改造 | 5 个 `*ServiceImpl`（triage / diagnosis / medical_record / prescription_review / result_interpretation） | ✅ |
| 单元测试 | MockAIProviderTest / JsonSchemaParserTest / AIInvocationRecorderTest / 5 个 *ServiceImplTest | ✅ |

---

## 二、架构总览

```
┌─────────────────────────────────────────────────────────────┐
│  业务模块（triage / prescription / medicalrecord / ...）     │
│   仅依赖 ai.api.*Service 接口，不感知 AI 实现细节             │
└──────────────────────────┬──────────────────────────────────┘
                           │ 调用
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  ai.application.*ServiceImpl                                 │
│   组装 InvocationSpec → 调 recorder.invoke() → 解析响应      │
│   异常降级为 BusinessException（504/500）                     │
└──────────────────────────┬──────────────────────────────────┘
                           │ invoke(spec, parser)
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  AIInvocationRecorder（编排 + 记录）                          │
│   - 1 业务调用 → 1 AIInvocation                              │
│   - 每次重试  → 1 AIInvocationAttempt                        │
│   - 重试仅对超时/5xx；非法 JSON 不重试                        │
│   - traceId 透传（MDC）                                       │
└──────┬───────────────────────────────┬──────────────────────┘
       │ generate()                    │ 记录
       ▼                               ▼
┌──────────────┐              ┌──────────────────┐
│  AIProvider  │              │ audit.repository │
│ (Mock/Http)  │              │ (Invocation/     │
└──────────────┘              │  Attempt)        │
                              └──────────────────┘
```

**分层职责**：
- **Provider 层**：`MockAIProvider`（七场景分流）/ `HttpLLMProvider`（RestClient 调真实 LLM），由 `AIConfig` 按 `app.ai.mode` 选择。
- **Prompt 层**：`PromptManager` 启动时从 `classpath:prompts/` 加载并缓存最新版本，按 capability 提供 system prompt + 版本号。
- **Parser 层**：`JsonSchemaParser` 负责 markdown fence 剥离、JSON 提取、必填字段校验、枚举受控校验，非法响应映射为 `AIInvalidResponseException`。
- **Recorder 层**：`AIInvocationRecorder` 编排调用、重试、记录，确保统计口径 `1 调用 = 1 Invocation + N Attempt`。

---

## 三、配置说明（application-ai.yml）

```yaml
app:
  ai:
    mode: ${AI_MODE:MOCK}              # MOCK 或 HTTP
    timeout-ms: ${AI_TIMEOUT_MS:8000}  # 单次 Provider 请求超时
    max-retries: ${AI_MAX_RETRIES:1}   # 最大重试次数（仅对超时/5xx）
    http:
      api-url: ${AI_API_URL:}          # 真实 LLM API 地址（可缺省，默认 Mock）
      api-key: ${AI_API_KEY:}          # API Key（仅注入 Provider，不落日志/记录）
      model: ${AI_MODEL:default}
    mock:                              # 七场景触发关键词
      timeout-keyword: MOCK_TIMEOUT
      invalid-json-keyword: MOCK_INVALID_JSON
      not-exist-dept-keyword: MOCK_NOT_EXIST_DEPT
      provider-error-keyword: MOCK_PROVIDER_ERROR
      empty-keyword: MOCK_EMPTY
      high-risk-keyword: MOCK_HIGH_RISK
```

切换真实 LLM：设置 `AI_MODE=HTTP` + `AI_API_URL` + `AI_API_KEY` 即可，业务代码零改动。

---

## 四、Mock 七场景分流

`MockAIProvider.generate()` 按 `app.ai.mock.*-keyword` 配置的关键词路由：

| 场景 | 触发关键词 | 行为 |
|---|---|---|
| 正常 | （无关键词） | 按医疗关键词路由到对应科室/诊断/病历等结构化 JSON |
| 高风险 | `MOCK_HIGH_RISK` | 返回 HIGH 优先级 + 急性心肌梗死等高风险结果 |
| 空结果 | `MOCK_EMPTY` | 返回空 departmentCode / 空数组 |
| 超时 | `MOCK_TIMEOUT` | 抛 `AIProviderException(retryable=true)` |
| 非法 JSON | `MOCK_INVALID_JSON` | 返回非 JSON 文本（供 Parser 触发 `AI_INVALID_RESPONSE`） |
| 不存在科室 | `MOCK_NOT_EXIST_DEPT` | 返回 `DEPT_NOT_EXIST_999` |
| Provider 异常 | `MOCK_PROVIDER_ERROR` | 抛 `AIProviderException(retryable=false, httpStatus=500)` |

---

## 五、验收条件对照

| 验收条件 | 实现说明 | 验证 |
|---|---|---|
| 正常流程：Mock 返回符合 Schema 的辅助响应，带 `mock=true` | `AIProviderResponse.mock()` 始终为 true；所有响应含 `disclaimer` 安全声明 | MockAIProviderTest |
| 异常流程：七场景全部可触发并按预期降级 | 见第四节，七场景均有专门测试用例 | MockAIProviderTest（7 场景 + 5 能力字段） |
| 权限：不修改传统业务模块 | 修改边界严格遵守，仅改动 `ai/**`、`prompts/**`、`application-ai.yml`、`test/**/ai/**` | 代码审查 |
| 安全：日志/记录中无 API Key、无患者隐私 ID | HttpLLMProvider 仅在请求头注入 Bearer token，不记录；输入经 `sanitizedInput` 脱敏 | HttpLLMProvider + ServiceImpl |
| 契约：不创建未经批准的 AI HTTP 契约 | 未新增任何 AI HTTP 端点；HttpLLMProvider 调用外部 LLM，不暴露内部契约 | — |
| 统计口径：1 调用 = 1 Invocation + N Attempt，重试不重复进分母 | `startInvocation` 创建 1 条 Invocation；每次 attempt 单独 `recordAttempt`；`updateAttemptCount` 仅更新计数 | AIInvocationRecorderTest |

---

## 六、测试结果

### 必须运行的测试（全部通过）

| 测试类 | 用例数 | 覆盖点 |
|---|---|---|
| [MockAIProviderTest](file:///d:/shixun/yunnao/backend/src/test/java/com/neusoft/cloudbrain/ai/provider/MockAIProviderTest.java) | 16 | 七场景分流 + 五能力响应字段 + 安全声明 + mock 标识 |
| [JsonSchemaParserTest](file:///d:/shixun/yunnao/backend/src/test/java/com/neusoft/cloudbrain/ai/parser/JsonSchemaParserTest.java) | 15 | markdown fence 剥离 / JSON 提取 / 必填校验 / 枚举校验 / 字符串数组解析 |
| [AIInvocationRecorderTest](file:///d:/shixun/yunnao/backend/src/test/java/com/neusoft/cloudbrain/ai/application/AIInvocationRecorderTest.java) | 8 | 成功单次 / 超时重试成功 / 重试耗尽 / 非法JSON不重试 / 不可重试不重试 / 5xx重试 / maxRetries=0 / attemptCount 更新 |
| [AITriageServiceImplTest](file:///d:/shixun/yunnao/backend/src/test/java/com/neusoft/cloudbrain/ai/application/AITriageServiceImplTest.java) | 3 | 正常调用 / Provider异常降级504 / JSON 解析 |
| [AIDiagnosisServiceImplTest](file:///d:/shixun/yunnao/backend/src/test/java/com/neusoft/cloudbrain/ai/application/AIDiagnosisServiceImplTest.java) | 3 | 正常调用 / 降级 / 非法枚举 |
| [AIMedicalRecordServiceImplTest](file:///d:/shixun/yunnao/backend/src/test/java/com/neusoft/cloudbrain/ai/application/AIMedicalRecordServiceImplTest.java) | 3 | 正常调用 / 降级 / 缺失必填 |
| [AIPrescriptionReviewServiceImplTest](file:///d:/shixun/yunnao/backend/src/test/java/com/neusoft/cloudbrain/ai/application/AIPrescriptionReviewServiceImplTest.java) | 5 | 正常 / 确定性规则不被降级 / 降级 / 非法枚举 / 无规则结果 |
| [AIResultInterpretationServiceImplTest](file:///d:/shixun/yunnao/backend/src/test/java/com/neusoft/cloudbrain/ai/application/AIResultInterpretationServiceImplTest.java) | 4 | 正常 / 降级 / 缺失必填 / 正常值空异常项 |

**回归验证**：TriageServiceTest / PrescriptionServiceTest / MedicalRecordServiceTest / ExaminationServiceTest 全部通过，业务模块通过 `@Mock` 依赖 AI Service 接口，接口签名未变，无回归。

### 运行命令

```bash
# AI 模块全部测试
mvn -pl . test -Dtest="com.neusoft.cloudbrain.ai.**" -DfailIfNoTests=false

# 受影响的业务模块回归
mvn -pl . test -Dtest="TriageServiceTest,PrescriptionServiceTest,MedicalRecordServiceTest,ExaminationServiceTest"
```

---

## 七、修改边界遵守情况

```text
允许修改：backend/src/main/java/**/ai/**         ✅ 全部在此范围
         backend/src/main/resources/prompts/**   ✅ 新增 5 个 prompt 骨架
         backend/src/main/resources/application-ai.yml ✅
         backend/src/test/**/ai/**               ✅ 8 个测试类
禁止修改：backend/pom.xml                          ✅ 未改动
         传统业务模块                              ✅ 未改动
         frontend/**、contracts/**、数据库迁移     ✅ 未改动
```

---

## 八、遗留问题（需提交变更申请）

1. **时间字段类型不一致**：`AIInvocation` / `AIInvocationAttempt` 时间字段为 `LocalDateTime`，与 `33_错误码与时间规范.md` 第 5 节要求的 `OffsetDateTime` 不一致 → 提交变更申请给后端/审计角色统一调整。

2. **JSON Schema 校验方式**：未引入第三方 JSON Schema 校验库（如 `everit-org/json-schema`），采用 `JsonSchemaParser` 手写校验（必填字段 + 枚举受控）。当前能力字段相对稳定，手写校验可满足；若后续 Schema 复杂度上升，建议后端角色统一引入第三方库。

---

## 九、后续任务衔接

本任务交付的 Provider / Prompt / Parser / Recorder 四层基础设施，可直接被任务二~任务六（五大 AI 能力正式接入）复用：

- 业务模块只需注入 `ai.api.*Service` 接口，传入对应 Request DTO；
- ServiceImpl 已统一走 `recorder.invoke(spec, parser)` 模板，新增能力只需补充 Prompt + Parser 逻辑；
- 切换真实 LLM 仅需改配置，不动业务代码。

任务一为后续能力任务提供了「不侵入业务模块」的统一接入基础。
