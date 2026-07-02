# 联调 AI 课程要求补强验收报告

日期：2026-07-02  
分支：`codex/integration`  
对应任务书：`docs/integration/2026-07-01-integration-ai-course-requirements-taskbook.md`

## 1. 本轮联调收尾结论

结论：已完成课程要求补强联调验收。

本轮在前端 AI 与后端 AI 已交付基础上，完成以下联调收口：

- 前端分诊接口切到课程兼容路径 `/api/triage/consult`。
- 前端处方 AI 审核显式调用 `/api/prescription/check`，再刷新处方详情。
- 医生端问诊对话记录改为通过后端新增字段 `consultationTranscript` 独立传入，不再拼接到 `presentIllness`。
- `contracts/openapi.yaml` 同步新增 `/api/triage/consult`、`/api/triage/recommended-doctors`、`/api/prescription/check`、`/api/medical-records/ai-generate`。
- `contracts/schemas/triage_encounter.yaml` 补充推荐医生响应 schema。
- 修复 `/doc.html` 与 `/v3/api-docs` 被安全拦截的问题，放行文档入口。
- 更新真实 E2E，覆盖课程兼容分诊接口、推荐医生接口、问诊对话字段、处方审核兼容接口。

## 2. 验证命令与结果

| 验证项 | 命令/方式 | 结果 |
| --- | --- | --- |
| 前端 API 相关单测 | `npm exec -- vitest run ../tests/front_unit_test/api-triage.spec.ts ../tests/front_unit_test/api-medical-record.spec.ts ../tests/front_unit_test/api-prescription.spec.ts src/api/real-client.spec.ts` | 4 files / 49 tests passed |
| 前端类型检查 | `npm run type-check` | 通过 |
| 前端生产构建 | `npm run build` | 通过；仅有 `@vueuse/core` PURE 注释与 chunk size 警告 |
| 前端完整单测 | `npm run test` | 23 files / 266 tests passed |
| 后端测试 | `mvn -f backend/pom.xml test` | 407 tests passed |
| Docker 构建与启动 | `docker compose up -d --build`，后续重建 backend | 前端、后端、MySQL 均启动；后端 healthy |
| API 文档入口 | `curl -I http://localhost:18080/doc.html` | 302 -> `/swagger-ui.html` |
| OpenAPI JSON | `GET http://localhost:18080/v3/api-docs` | 200 |
| 真实业务闭环 E2E | `node tests/integration/e2e-real-clinic-flow.mjs` | `flow: SUCCESS` |

## 3. I1-I7 验收对照

| 编号 | 验收项 | 结论 | 证据 |
| --- | --- | --- | --- |
| I1 | Knife4j `/doc.html` 文档入口 | 通过 | `/doc.html` 返回 302，跳转 `/swagger-ui.html`；`/v3/api-docs` 返回 200 |
| I2 | 分诊到医生级挂号闭环 | 通过 | E2E 使用 `/api/triage/consult`；推荐医生接口返回真实可预约医生；后续挂号成功 |
| I3 | 多轮问诊边界说明 | 通过 | E2E 验证 `conversationId` 回显与 `round=2`；完整会话消息持久化仍按任务书说明不作为本轮要求 |
| I4 | AI 处方审核闭环 | 通过 | 前端 `aiReviewPrescription` 调用 `/api/prescription/check`；E2E 创建处方后显式调用该接口并确认处方 |
| I5 | AI 病历生成闭环 | 通过 | 医生端传入 `consultationTranscript`；E2E AI 病历生成成功并确认，患者端可见 |
| I6 | Pinia 状态优化 | 通过 | 前端已新增 patient/doctor/appointment/prescription store；完整前端单测 266 通过 |
| I7 | Prompt 工程优化 | 通过 | 后端 PromptManager 与科室 Prompt 测试覆盖；后端 407 测试通过 |

## 4. 真实 E2E 关键结果

最近一次真实 E2E 输出摘要：

- `flow`: `SUCCESS`
- `triageStatus`: `SUCCESS`
- `triageFollowUpRound`: `2`
- `appointmentStatus`: `BOOKED`
- `encounterStatus`: `COMPLETED`
- `medicalRecordStatus`: `CONFIRMED`
- `medicalRecordSource`: `AI_GENERATED`
- `prescriptionStatus`: `CONFIRMED`
- `prescriptionAiReviewStatus`: `REVIEWED`
- 患者端可见病历、处方、就诊记录。
- 管理端可见 dashboard、审计日志、AI 统计。
- 权限边界符合预期：患者越权 403、医生访问管理审计 403、匿名访问患者接口 401、禁用用户登录 401。

## 5. 浏览器演示步骤清单

建议课程演示按以下顺序截图或录屏：

1. 访问 `http://localhost:18080/doc.html`，展示跳转到 Swagger UI。
2. 患者登录，进入智能分诊页。
3. 输入症状并提交分诊，展示推荐科室、推荐医生卡片和安全提示。
4. 点击“预约该医生”，进入挂号页并展示科室/医生预选。
5. 完成挂号，患者端查看挂号记录。
6. 医生登录，进入待诊队列并开始接诊。
7. 医生端病历页输入“问诊对话记录”，点击 AI 生成病历，展示结构化字段回填。
8. 医生确认病历。
9. 医生端处方页开具处方，点击 AI 审核，展示风险等级、警告和建议。
10. 医生确认处方。
11. 患者端查看已确认病历和处方。
12. 管理端查看 AI 调用记录或统计。

## 6. 失败项与阻塞项

当前无阻塞项。

非阻塞说明：

- 前端构建仍有 `@vueuse/core` 的 `/* #__PURE__ */` 注释警告，以及单个 JS chunk 超过 500 kB 的提示；构建退出码为 0，不影响课程演示。
- `/doc.html` 是按任务书授权的兼容重定向方案，不是 Knife4j 增强版 UI。
- 多轮问诊完整消息持久化未实现，任务书已明确本轮不要求。

## 7. 责任归档

| 内容 | 责任方 | 状态 |
| --- | --- | --- |
| Knife4j 兼容入口实现 | 后端 AI | 已完成，联调 AI 补安全放行 |
| 分诊兼容接口与推荐医生接口 | 后端 AI | 已完成，联调 AI 已验证 |
| 分诊结果页推荐医生与跳转 | 前端 AI | 已完成，联调 AI 已验证 |
| 处方审核兼容接口 | 后端 AI | 已完成，联调 AI 已切前端调用并验证 |
| 医生端历史处方与审核展示 | 前端 AI | 已完成，前端单测通过 |
| 问诊对话记录输入 | 前端 AI | 已完成，联调 AI 已切独立字段传参 |
| 按科室 Prompt | 后端 AI | 已完成，后端测试通过 |
| OpenAPI 契约同步 | 联调 AI | 已完成 |
| 真实闭环 E2E | 联调 AI | 已完成 |
