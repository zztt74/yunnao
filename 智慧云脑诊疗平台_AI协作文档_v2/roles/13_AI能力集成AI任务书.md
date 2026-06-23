# AI 能力集成 AI 任务书

## 1. 角色定位

你负责所有 AI 能力的统一封装、Prompt、结构化响应、Mock Provider、真实 Provider、超时重试和调用记录。

你不负责：

- 患者、挂号等传统业务 CRUD；
- Vue 页面；
- 修改 OpenAPI；
- 修改数据库核心业务结构；
- 自动作出正式医疗决策。

## 2. 负责目录

```text
backend/src/main/java/**/ai/
├── api/
├── application/
├── provider/
├── dto/
├── parser/
├── config/
└── exception/

backend/src/main/resources/prompts/
backend/src/main/resources/application-ai.yml
backend/src/test/**/ai/
```

未经后端角色同意，不修改：

```text
backend/pom.xml
backend/**/patient/**
backend/**/schedule/**
backend/**/appointment/**
backend/**/encounter/**
backend/**/prescription/**
```

如实现 AI Provider 需要新增或调整 Maven 依赖，由 AI 角色提交依赖说明，后端角色统一修改 `backend/pom.xml`。

## 3. AI 能力接口

### 3.1 智能分诊

输入：年龄区间、性别、主诉、症状持续时间、补充描述。

输出：

```text
departmentCode
priority
symptomKeywords
reason
safetyNotice
emergencySuggested
```

不得输出医生 ID。

### 3.2 辅助诊断

输出：

```text
possibleDiagnoses
evidence
missingInformation
riskFactors
suggestedExaminations
disclaimer
```

不得直接写入正式诊断。

### 3.3 病历生成

输出：

```text
chiefComplaint
presentIllness
pastHistory
physicalExamination
preliminaryDiagnosis
treatmentSuggestion
```

缺少信息应留空或明确标记，不得编造。

### 3.4 处方审核

输入必须包含后端确定性规则检查结果。

输出：

```text
riskLevel
allergyWarnings
interactionWarnings
dosageWarnings
recommendations
summary
```

不得自动确认处方。

不得降低或覆盖后端确定性规则命中的风险等级。AI 结果与规则结果冲突时，以后端规则为准，并记录冲突。

### 3.5 检查检验解读

输出：

```text
abnormalItems
plainLanguageExplanation
possibleAttentionPoints
followUpSuggestion
disclaimer
```

不得修改原始数值。

## 4. Provider 架构

```text
AI Service Interface
        │
        ▼
AIProvider
├── MockAIProvider
└── HttpLLMProvider
```

业务模块不感知具体模型厂商。

配置示例：

```yaml
app:
  ai:
    mode: mock
    timeout-ms: 8000
    max-retries: 1
```

## 5. 结构化输出要求

- 优先要求 JSON；
- 使用 Schema 校验；
- 枚举值受控；
- 非法 JSON 返回 `AI_INVALID_RESPONSE`；
- 不无限修复模型输出；
- 原始响应与解析结果分开记录；
- 一次业务 AI 调用只创建一条 `AIInvocation`；
- 每次 Provider 请求或重试创建一条 `AIInvocationAttempt`；
- AI 成功率按 `AIInvocation` 统计，Provider 重试不重复进入分母；
- 日志不保存 API Key；
- 发送给模型的数据最小化；
- 不把模型自然语言直接写入业务表。

## 6. Mock 要求

本节的 Mock 专指后端内部 `MockAIProvider`，不等同于联调角色维护的 OpenAPI API Mock Server。

至少覆盖：

```text
正常结果
高风险结果
空结果
超时
非法 JSON
不存在科室
Provider 异常
```

答辩环境默认可使用 Mock 完成完整流程。

## 7. Prompt 管理

建议文件：

```text
triage-system.txt
diagnosis-system.txt
medical-record-system.txt
prescription-review-system.txt
result-interpretation-system.txt
```

Prompt 必须：

- 明确任务边界；
- 指定 JSON Schema；
- 禁止输出数据库 ID；
- 禁止确定性医疗承诺；
- 信息不足时说明不足；
- 包含辅助参考声明；
- 禁止编造输入中不存在的检查结果。

## 8. 超时与降级

```text
请求超时
→ 有限次数重试
→ 返回统一错误
→ 记录调用失败
→ 业务进入手动流程
```

禁止无限重试。

## 9. 每次交付输出

```text
实现的 AI 能力
输入输出 Schema
Prompt 文件
Provider 变化
Mock 场景
超时和降级策略
测试结果
对后端业务模块的调用说明
潜在安全风险
未解决问题
```

## 10. 实际任务阅读路径

```text
00_开始这里.md
→ 01_场景化阅读导航.md
→ 本任务父任务和 AI 子任务
→ contracts/32_AI能力契约规范.md
→ product/12_业务流程与状态机.md 中相关流程
→ 后端提供的确定性规则输入
→ workflow/41_质量测试与完成定义.md 中 AI 场景
```

涉及对外 HTTP Schema 时由联调 AI 维护 OpenAPI；需要 Maven 依赖时提交依赖说明给后端 AI。
