# 课程要求补强 交付说明（B1–B6）

日期：2026-07-01
分支：`codex/integration`
对应任务书：`docs/integration/2026-07-01-backend-ai-course-requirements-taskbook.md`
想法与待确认问题：`2026-07-01-backend-ai-course-requirements-thoughts.md`

---

## 1. 测试命令与结果

```bash
cd backend
mvn clean test
```

结果：

```
Tests run: 407, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

原 394 个测试 + B5/B6 新增 13 个测试（PromptManagerTest 11 + AIMedicalRecordServiceImplTest 2 净增），全部通过，无回归。

---

## 2. 完成的接口清单

| 编号 | 方法 | 路径 | 说明 |
|---|---|---|---|
| B1 | GET | `/doc.html` | 重定向到 `/swagger-ui.html`（Knife4j 兼容入口） |
| B2 | POST | `/api/triage/consult` | 分诊分析兼容路径，等价于 `/api/triage/analyze` |
| B3 | GET | `/api/triage/recommended-doctors?departmentId={id}&limit=3` | 按科室返回可预约医生及最近排班摘要 |
| B4 | POST | `/api/prescription/check` | 处方审核兼容接口，传入 prescriptionId 返回审核结果 |

B5、B6 为非接口级改动（DTO 字段扩展 + Prompt 模板策略），不新增独立路径：
- B5：`POST /api/medical-records/generate-ai` 请求体新增可选字段 `consultationTranscript`
- B6：`PromptManager` 按科室 code 选择专用 Prompt 模板

---

## 3. 修改的模块和文件

### 新增文件

| 文件 | 任务 | 说明 |
|---|---|---|
| `common/config/DocHtmlRedirectController.java` | B1 | `/doc.html` → `/swagger-ui.html` 重定向 |
| `prescription/controller/PrescriptionCheckController.java` | B4 | `POST /api/prescription/check` 兼容接口 |
| `prescription/dto/PrescriptionCheckRequest.java` | B4 | 请求 DTO（`prescriptionId`） |
| `prescription/dto/PrescriptionCheckResponse.java` | B4 | 响应 DTO（含 `from(PrescriptionResponse)` 工厂方法） |
| `triage/dto/TriageRecommendedDoctorResponse.java` | B3 | 推荐医生响应 DTO（含 `ScheduleSummary` 内部 record） |
| `resources/prompts/medical_record_dept_internal_v1.txt` | B6 | 内科专用病历生成 Prompt |
| `resources/prompts/medical_record_dept_pediatrics_v1.txt` | B6 | 儿科专用病历生成 Prompt |

### 修改文件

| 文件 | 任务 | 改动 |
|---|---|---|
| `triage/controller/TriageController.java` | B2/B3 | 新增 `POST /consult` 和 `GET /recommended-doctors` 端点 |
| `triage/service/TriageService.java` | B3 | 新增 `getRecommendedDoctors(departmentId, limit)` 和 `toRecommendedDoctor` 私有方法 |
| `medicalrecord/dto/MedicalRecordGenerateRequest.java` | B5 | 新增 `@Size(max=5000) String consultationTranscript` 字段（7→8 参数） |
| `ai/dto/MedicalRecordAIRequest.java` | B5/B6 | 新增 `consultationTranscript` 和 `departmentCode` 字段（6→8 参数） |
| `ai/application/AIMedicalRecordServiceImpl.java` | B5/B6 | sanitizedInput 加入 transcript；按科室选择 Prompt（`getPrompt(capability, departmentCode)`） |
| `medicalrecord/service/MedicalRecordService.java` | B5/B6 | 注入 `DepartmentRepository`，从 encounter 获取 departmentId → department code 传给 AI |
| `ai/prompt/PromptManager.java` | B6 | 新增 `KNOWN_CAPABILITIES` 前缀匹配；新增 `getPrompt(capability, departmentCode)` 和 `getPromptVersion(capability, departmentCode)`；`buildCacheKey` 支持科室专用 key |
| `resources/prompts/medical_record_v1.txt` | B5 | 加入问诊对话记录说明 |
| `ai/dto/MedicalRecordAIRequest.java` | B5/B6 | record 从 6 参数扩展为 8 参数 |

### 修改的测试文件

| 文件 | 改动 |
|---|---|
| `ai/prompt/PromptManagerTest.java` | **新增**（11 个测试）：B6 Prompt 选择与回退完整链路（通用加载、科室专用选择、大小写归一化、未知/null/空白回退、版本回退） |
| `ai/application/AIMedicalRecordServiceImplTest.java` | 7 处 `MedicalRecordAIRequest` 从 6 参数改 8 参数；setUp 改用 `nullable` + `lenient`；新增 B5（问诊对话）、B6（科室 Prompt + 回退）共 3 个测试 |
| `medicalrecord/service/MedicalRecordServiceTest.java` | 新增 `@Mock DepartmentRepository`；2 处 `MedicalRecordGenerateRequest` 从 7 参数改 8 参数；2 个 AI 测试中 mock `departmentRepository.findById(1L)` |

---

## 4. 数据库是否需要变更

**无数据库变更。** B1–B6 均未修改既有 Flyway 基线脚本，未新增迁移。

B3 推荐医生接口复用现有 `doctor`、`schedule`、`department` 表；B6 科室 Prompt 通过文件系统模板实现，不涉及表结构变更。

---

## 5. 验收标准对照

### B1：Knife4j `/doc.html`
- ✅ 后端启动后 `GET /doc.html` 返回 302 重定向到 `/swagger-ui.html`
- ✅ OpenAPI JSON 仍可通过 `/v3/api-docs` 访问
- ✅ 后端测试通过

### B2：`/api/triage/consult` 兼容接口
- ✅ `/api/triage/consult` 与 `/api/triage/analyze` 在同等输入下返回同等业务结果（复用同一 Service 方法）
- ✅ 原有 `/api/triage/analyze` 不受影响
- ✅ 请求体和响应结构完全一致，保留 `conversationId/history/round` 参数

### B3：推荐医生列表
- ✅ 前端可根据分诊推荐科室 `departmentId` 获取可预约医生
- ✅ 无可用医生时返回空列表，不报 500
- ✅ 数据仅来自真实 `doctor` 和 `schedule` 表，不使用前端 mock
- ✅ 返回字段含医生 ID、科室 ID、姓名、职称、可预约日期/排班 ID、剩余号源摘要

### B4：`/api/prescription/check` 兼容接口
- ✅ 前端点击 AI 审核可显式调用该接口（传入 `prescriptionId`）
- ✅ 高风险处方返回明确风险提示（`riskLevel` 字段）
- ✅ AI 失败时仍返回规则审核结果和降级说明（`aiReviewStatus` 标记降级）
- ✅ 确定性规则优先，AI 不得降低规则风险等级（由现有 `PrescriptionService` 保证）

### B5：问诊对话记录参与病历生成
- ✅ 请求中带 `consultationTranscript` 时，AI 从对话中提取结构化字段
- ✅ 旧请求不带该字段时仍兼容（字段可选）
- ✅ 生成结果保存为 `AI_GENERATED` 草稿，需医生确认
- ✅ 测试覆盖空文本、有效文本、AI 失败降级

### B6：按科室 Prompt 工程优化
- ✅ 内科 + 儿科两个科室专用 Prompt 示例（满足"至少两个"）
- ✅ 回退逻辑可用：找不到科室模板时用通用 `medical_record_v1.txt`，不报错
- ✅ AI 调用审计记录实际使用的 Prompt 版本（`InvocationSpec.promptVersion`）
- ✅ Prompt 模板强调 AI 辅助、医生确认、不编造检查结果
- ✅ 单元测试覆盖 Prompt 选择和回退

---

## 6. 联调交接点

交给联调 AI 验证的场景（对应任务书第五节）：

| 场景 | 接口 | 验证要点 |
|---|---|---|
| `/doc.html` 可访问 | `GET /doc.html` | 返回 302 → `/swagger-ui.html` |
| 分诊兼容接口 | `POST /api/triage/consult` | 与 `/analyze` 等价 |
| 推荐医生列表 | `GET /api/triage/recommended-doctors?departmentId=1&limit=3` | 返回真实医生和排班 |
| 处方审核兼容接口 | `POST /api/prescription/check` | 传 prescriptionId 返回审核结果 |
| 带问诊对话的病历生成 | `POST /api/medical-records/generate-ai`（body 含 `consultationTranscript`） | AI 从对话提取结构化字段 |
| 按科室 Prompt 调用审计 | 审计记录中 `promptVersion` 字段 | 内科就诊用 `dept_internal` 版本 |

---

## 7. 待联调确认事项

详见 `2026-07-01-backend-ai-course-requirements-thoughts.md` 第二节，要点：

1. **Q3（P1）**：演示环境排班种子数据是否就绪，B3 接口依赖真实排班。
2. **Q6（P1）**：新增兼容接口是否需同步到 `contracts/openapi.yaml` 主契约。
3. **Q1（P2）**：课程是否严格要求 Knife4j 增强版 UI（当前为重定向）。
4. **Q4（P2）**：B4 是否需要"不创建处方就预审核"的第二种模式。
5. **Q7（P2）**：前端是否改为调用课程示例路径（`/consult`、`/check`）。

---

## 8. 关键 DTO 字段速览（供前端对接）

### B3 TriageRecommendedDoctorResponse
```
doctorId(Long), doctorName(String), doctorTitle(String),
departmentId(Long), departmentName(String), specialty(String),
availableSchedules(List<ScheduleSummary>)
```
`ScheduleSummary`: `scheduleId(Long), scheduleDate(LocalDate), startTime(String), endTime(String), remainingCount(Integer)`

### B4 PrescriptionCheckRequest
```
prescriptionId(Long, 必填)
```

### B4 PrescriptionCheckResponse
```
prescriptionId(Long), encounterId(Long),
prescriptionStatus(String), aiReviewStatus(String),
riskLevel(String),
allergyWarnings(List), interactionWarnings(List),
dosageWarnings(List), contraindicationWarnings(List),
suggestions(String), summary(String), ruleCheckSummary(String)
```

### B5 MedicalRecordGenerateRequest（新增字段）
```
consultationTranscript(String, 可选, max 5000 字)
```
其余字段不变：`encounterId, chiefComplaint, presentIllness, pastHistory, physicalExamination, preliminaryDiagnoses, treatmentSuggestion`

---

*B1–B6 全部满足任务书验收标准，无阻塞项。待联调确认事项见想法文档。*
