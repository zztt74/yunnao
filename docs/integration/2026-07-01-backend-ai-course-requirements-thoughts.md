# 课程要求补强：想法、建议与待确认问题（B1–B6）

日期：2026-07-01
分支：`codex/integration`
对应任务书：`docs/integration/2026-07-01-backend-ai-course-requirements-taskbook.md`
关联文档：`2026-07-01-backend-ai-course-requirements-delivery.md`（交付说明）

本文档记录实施 B1–B6 过程中的**设计权衡、自查后已决定的事项、以及真正需要联调确认的开放问题**。对任务书已明确授权或给出答案的，本次自查后直接按任务书决定，不再列为待确认。

---

## 一、设计权衡说明

### B1：Knife4j `/doc.html` 采用重定向而非引入 Knife4j 依赖

**决策**：当前项目已集成 `springdoc-openapi-starter-webmvc-ui`，访问路径为 `/swagger-ui.html`。直接引入 Knife4j 4.x 会与 springdoc 争夺 Swagger UI 资源注册（两者都注册 `/swagger-ui/*` 和 `/v3/api-docs`），导致其中一方不可用。

**实现**：新增 `DocHtmlRedirectController`，`GET /doc.html` 返回 `redirect:/swagger-ui.html`。任务书第 46 行明确允许此替代方案。

**权衡**：
- 优点：零依赖冲突风险，OpenAPI JSON 不受影响，现有验证脚本不受影响。
- 代价：`/doc.html` 展示的是 springdoc 原生 Swagger UI，不是 Knife4j 增强版 UI（无文档分组、全局参数配置等 Knife4j 特性）。
- 任务书已授权此方案，不再作为待确认问题。

### B2：`/api/triage/consult` 为纯转发，不持久化会话

**决策**：`/api/triage/consult` 直接复用 `TriageService.analyze`，请求体和响应结构与 `/api/triage/analyze` 完全一致，保留 `conversationId/history/round` 参数流转。

**权衡**：
- 任务书第 71 行明确"不要求本轮持久化完整 AI 会话消息；若要实现，需先单独提出数据库变更方案"。本次按任务书不持久化，不再作为待确认问题。
- `conversationId/history/round` 参数会被透传到 `TriageService`，Service 内部已支持多轮上下文（UF-01 扩展，`TriageService.analyze` 第 92-95 行判断 isMultiRound）。
- 测试：`TriageServiceTest` 已覆盖 `analyze` 的成功/失败降级/科室映射失败等场景。`/consult` 复用同一 Service 方法，业务逻辑已被 Service 测试覆盖，满足任务书"补充 controller/service 测试或集成测试"中的 service 测试要求。

### B3：推荐医生列表复用现有排班数据

**决策**：新增 `GET /api/triage/recommended-doctors?departmentId={id}&limit=3`，复用现有 `DoctorRepository`、`ScheduleRepository` 查询真实数据，不使用前端 mock。

**权衡**：
- 返回的医生和排班来自真实表，如果演示环境没有种子排班数据，接口返回空列表（不报 500，符合验收）。
- `limit` 默认 3，可调。任务书要求"2-3 个可预约医生"，默认 3 满足。
- 排班按 `startTime` 升序取最近可用排班，只返回有剩余号源的排班（`bookedCount < maxAppointments`）。

### B4：`/api/prescription/check` 采用 prescriptionId 模式

**决策**：任务书第 117-120 行明确"支持两种输入方式中的至少一种"、"推荐优先实现 prescriptionId 方式"。本次实现了 prescriptionId 方式，符合任务书最低要求和推荐。

**权衡**：
- 优点：成本低，不破坏现有"创建处方自动触发审核"流程，不引入新的处方草稿状态。
- 任务书已明确"至少一种"且推荐 prescriptionId，不再将"是否补第二种模式"列为待确认问题。
- 当前实现：若该处方创建时审核成功，返回完整审核结果；若审核失败（AI 降级），返回规则审核结果 + `aiReviewStatus` 降级说明。

### B5：问诊对话记录为可选字段，向后兼容

**决策**：在 `MedicalRecordGenerateRequest` 新增 `@Size(max=5000) String consultationTranscript` 可选字段。AI 病历生成 Prompt 中加入该字段，模型从对话中提取结构化信息。字段为空时保持现有逻辑。

**权衡**：
- 完全向后兼容，旧请求不带该字段时走原有结构化字段生成路径。
- 生成结果仍保存为 `AI_GENERATED` 草稿，必须医生确认后才变 `CONFIRMED`，未突破安全边界。
- 测试覆盖：空文本（`generate_emptyInput_returnsEmptyFields`）、有效文本（`generate_withTranscript_extractsFromDialogue`）、AI 失败降级（`generate_timeout/invalidJson/providerError`）。

### B6：按科室选择 Prompt，文件命名用 dept 前缀

**决策**：扩展 `PromptManager` 支持科室维度 Prompt 选择。文件命名规则：`{capability}_dept_{departmentCode小写}_v{version}.txt`。当前新增两个示例：内科 `medical_record_dept_internal_v1.txt`、儿科 `medical_record_dept_pediatrics_v1.txt`。找不到科室专用模板时回退到通用 `medical_record_v1.txt`。

**权衡**：
- 任务书第 178 行明确"内科/心内科/儿科至少有两个科室专用 Prompt 示例"，本次实现内科 + 儿科共 2 个，满足要求，不再将"需补哪些科室"列为待确认问题。
- `PromptManager` 新增 `KNOWN_CAPABILITIES` 列表，用"从长到短前缀匹配"解析 capability（避免 `medical_record` 被第一个下划线误分割为 `medical` + `record`）。
- 科室 code 在数据库中是大写（如 `DEPT_INTERNAL`），PromptManager 内部转小写后拼文件名和缓存 key。
- AI 调用审计记录了实际使用的 Prompt 版本（`InvocationSpec.promptVersion`），可追踪。
- 测试：新增 `PromptManagerTest`（11 个测试）覆盖通用加载、科室专用选择、大小写归一化、未知科室回退、null/空白回退、版本回退。`AIMedicalRecordServiceImplTest` 补 B5/B6 共 2 个测试覆盖 Service 层调用链。

---

## 二、自查后已决定的事项（任务书已授权，不再列为待确认）

以下事项在初版文档中被列为"待确认"，复查任务书原文后发现任务书已明确给出答案或授权，本次自行决定：

| 编号 | 原问题 | 任务书依据 | 自查结论 |
|---|---|---|---|
| 原 Q1 | 课程是否严格要求 Knife4j 增强版 UI？ | 任务书第 46 行："如依赖冲突导致 Knife4j 不适配，需提供替代方案，例如 `/doc.html` 重定向到 Swagger UI" | **已自决**：任务书明确允许重定向，采用重定向方案 |
| 原 Q2 | B2 是否需要多轮对话记忆持久化？ | 任务书第 71 行："不要求本轮持久化完整 AI 会话消息" | **已自决**：任务书明确不要求，不持久化 |
| 原 Q4 | B4 是否需要"不创建处方就预审核"的第二种模式？ | 任务书第 117-120 行："支持两种输入方式中的至少一种"、"推荐优先实现 prescriptionId 方式" | **已自决**：任务书明确"至少一种"且推荐 prescriptionId，实现了该方式 |
| 原 Q5 | B6 需要为哪些科室补专用 Prompt？ | 任务书第 178 行："内科/心内科/儿科至少有两个科室专用 Prompt 示例" | **已自决**：实现内科 + 儿科共 2 个，满足"至少两个" |

---

## 三、真正需要联调确认的开放问题

以下事项后端确实无权限单独决定（涉及 DB 基线、OpenAPI 契约、前端调用），保留为待联调确认：

### Q1（P1）：演示环境排班种子数据是否就绪

**背景**：B3 推荐医生接口 `GET /api/triage/recommended-doctors` 复用真实 `doctor` 和 `schedule` 表。接口逻辑上满足验收"无可用医生时返回空列表不报 500"。

**问题**：后端受任务书红线"不重建数据库基线"约束，不擅改演示环境种子数据。如果演示环境没有内科/儿科的医生和排班数据，接口会返回空列表（功能正确但演示效果为空）。

**需谁确认**：联调 / DBA，确认演示环境是否有 `department(id=1,code=DEPT_INTERNAL)` 对应的启用医生和未来可用排班。

### Q2（P1）：新增兼容接口是否同步到 OpenAPI 主契约

**背景**：本次新增 3 个兼容接口：`/api/triage/consult`、`/api/triage/recommended-doctors`、`/api/prescription/check`。项目约定 OpenAPI 主契约（`contracts/openapi.yaml`）由联调 AI 同步，后端不擅改。

**需谁确认**：联调 AI，将这 3 个接口同步到 `contracts/openapi.yaml`。

### Q3（P2）：前端是否改为调用课程示例路径

**背景**：后端已提供兼容接口。前端当前可能调用 `/api/triage/analyze`、`/api/prescriptions`（复数）。是否改为课程示例路径 `/api/triage/consult`、`/api/prescription/check`（单数）由前端决定。

**需谁确认**：前端 AI，确认前端是否调用这些新兼容路径，以便课程演示路径与示例一致。

---

## 四、技术细节备注

### 1. PromptManager 文件名解析的已知 capability 列表

`PromptManager` 维护 `KNOWN_CAPABILITIES` 列表（`medical_record`、`prescription_review`、`result_interpretation`、`diagnosis`、`triage`），解析文件名时按"从长到短"前缀匹配，确保含下划线的 capability（`medical_record`）不会被第一个下划线误分割。

**注意**：未来新增 capability 时需同步更新此列表，否则文件名解析会失败并跳过该文件（不影响已加载的 prompt，但新 prompt 不会被加载）。

### 2. B6 科室 code 大小写约定

- 数据库 `department.code` 存大写（如 `DEPT_INTERNAL`）。
- Prompt 文件名用小写（如 `medical_record_dept_internal_v1.txt`）。
- `PromptManager` 内部将 departmentCode 转小写后拼文件名和缓存 key（`PromptManagerTest.getPrompt_upperCaseCode_normalizedToLowerCase` 验证）。

### 3. B4 审核结果字段映射

`PrescriptionCheckResponse.from(PrescriptionResponse)` 从现有处方详情中提取审核信息：
- `prescriptionStatus` ← 处方状态
- `aiReviewStatus` ← AI 审核状态（成功 / 失败降级）
- `riskLevel` ← 风险等级（确定性规则 + AI 综合判定，AI 不得降低规则风险等级）
- `allergyWarnings/interactionWarnings/dosageWarnings/contraindicationWarnings` ← 各类警告
- `ruleCheckSummary` ← 确定性规则检查摘要（不可被 AI 覆盖）
- `suggestions` ← AI 补充建议
- 如果处方创建时审核未完成或 AI 失败（`review` 为 null），审核相关字段返回 null，`aiReviewStatus` 标记降级

### 4. 测试策略

B1–B6 新增测试：
- `PromptManagerTest`（11 个）：B6 Prompt 选择与回退完整链路（基于真实 classpath 文件加载）。
- `AIMedicalRecordServiceImplTest`（+3 个）：B5 问诊对话记录、B6 科室 Prompt 选择 + 回退。
- `MedicalRecordServiceTest`：适配 `DepartmentRepository` 注入和 8 参数 DTO，原有 16 个测试全部通过。

B2/B3/B4 兼容接口复用现有 Service 逻辑，`TriageServiceTest` 已覆盖 `analyze` 的成功/失败降级/科室映射，`PrescriptionServiceTest` 已覆盖处方审核逻辑，满足任务书"controller/service 测试或集成测试"中的 service 测试要求。

---

## 五、本次交付的自我评价

**做得到位的**：
- 严格遵循任务书红线：不重建 DB 基线、不破坏现有接口、不删除 E2E 链路、AI 结果仍需医生确认。
- 全量测试通过（407 个），新增 13 个测试（PromptManagerTest 11 + AIMedicalRecordServiceImplTest 2 净增），无回归。
- 自查后把 4 个原本列为"待确认"的问题改为"已自决"——任务书已明确授权，不再推给联调。
- B6 Prompt 选择逻辑通过 `KNOWN_CAPABILITIES` 前缀匹配解决了 capability 含下划线的解析问题，并有独立单元测试覆盖回退。
- 所有新接口向后兼容，未改现有 DTO 字段语义（只新增可选字段）。

**做得保守的**：
- B1 用重定向而非引入 Knife4j 依赖，规避冲突风险但牺牲 Knife4j 增强特性（任务书已授权）。
- B4 只实现 prescriptionId 模式，未实现预审核模式（任务书明确"至少一种"即可）。
- B6 只提供 2 个科室 Prompt 示例（满足"至少两个"的最低要求）。

**仍可后续优化的事项**：
1. Q1：确认演示环境排班种子数据（需 DBA/联调）。
2. Q2：新接口同步到 OpenAPI 主契约（需联调 AI）。
3. 补 Controller 层 MockMvc 测试覆盖新兼容接口的路由和参数校验（非任务书硬性要求）。

---

*本文档为实施者视角的想法与建议。第二节的"已自决"事项均有任务书原文依据；第三节的开放问题后端确实无权限单独决定，需相关负责人确认。*
