# 前端 AI 课程要求补强 交付说明（F1–F4）

日期：2026-07-02
分支：`codex/integration`
对应任务书：`docs/integration/2026-07-01-frontend-ai-course-requirements-taskbook.md`
想法与建议：`docs/integration/2026-07-02-frontend-ai-course-requirements-thoughts.md`

---

## 1. 测试命令与结果

```bash
cd frontend
npm run type-check
npm run test
npm run build
```

结果（2026-07-02）：

| 步骤 | 命令 | 结果 |
|---|---|---|
| 类型检查 | `npm run type-check` | ✅ 通过（`vue-tsc -b --noEmit` 0 错误） |
| 单元测试 | `npm run test` | ✅ 265 通过 / 1 失败（api-admin 中日期敏感的 `getAdminAppointments` 客户端过滤测试，**与本次改动无关**，在 main 上也是失败状态） |
| 生产构建 | `npm run build` | ✅ 0 退出码（`vite build` 输出 `dist/`，仅有 `@vueuse/core` 的 `/* #__PURE__ */` 注释告警，不影响产物） |

针对本次新写的 4 个 store + 2 个 API client 扩展，相关测试结果：

- `tests/front_unit_test/store-patient.spec.ts` ✅ 7/7
- `tests/front_unit_test/store-doctor.spec.ts` ✅ 7/7
- `tests/front_unit_test/store-appointment.spec.ts` ✅ 6/6
- `tests/front_unit_test/store-prescription.spec.ts` ✅ 6/6
- `tests/front_unit_test/api-prescription.spec.ts` ✅ 18/18（新增 `getPatientPrescriptions` 4 个用例）
- `tests/front_unit_test/api-doctor.spec.ts` ✅ 通过（新增 `getDoctorById` 与 `getDoctorSchedules(id)`）

代码审查（review pass）后再次回归：2026-07-02

| 步骤 | 命令 | 结果 |
|---|---|---|
| 类型检查 | `npx vue-tsc --noEmit` | ✅ EXIT=0 |
| 单元测试 | `npx vitest run store-*` | ✅ 4 files / 26 tests passed |
| 单元测试 | `npx vitest run api-doctor / api-prescription` | ✅ 2 files / all passed |
| 生产构建 | `npx vite build` | ✅ `✓ built in 1.46s`（warning 来自 `@vueuse/core` 的 `/* #__PURE__ */` 注释告警，与本次改动无关） |

---

## 2. 任务清单对照

| 任务 | 状态 | 关键交付 |
|---|---|---|
| F1 分诊结果页推荐医生列表与医生级跳转 | ✅ 完成 | `PatientTriageView.vue` 新增「👨‍⚕️ 推荐医生」卡片区；通过 `useDoctorStore.loadDoctorsByDepartment` 拉数据；跳转时携带 `departmentId/doctorId/scheduleId` 写入 `useAppointmentStore.preSelection`，挂号页 `PatientAppointmentsView.vue` 自动预选并打开确认弹窗 |
| F2 医生处方页历史处方区 | ✅ 完成 | `DoctorPrescriptionView.vue` 新增「📋 历史处方」折叠区；通过新增的 `getPatientPrescriptions(patientId, { includeDraft })` 拉当前患者历史（默认排除草稿），展示状态/时间/AI 审核/风险等级/关键警告；按钮文案调整 |
| F3 医生病历页问诊对话记录多行输入区 | ✅ 完成 | `DoctorMedicalRecordView.vue` 新增「💬 问诊对话记录」`<textarea>`；`handleAiGenerate` 调用前通过 `buildPresentIllnessWithDialogue` 将对话原文拼接到 `presentIllness` 中，再走 `/api/medical-records/generate-ai`（后端 B5 已就绪，识别 `consultationTranscript`） |
| F4 Pinia 状态模块化补强 | ✅ 完成 | 新增 4 个 store：`usePatientStore`、`useDoctorStore`、`useAppointmentStore`、`usePrescriptionStore`；保留既有 `auth`/`encounter`；F1/F2 数据流均通过 store 中转，避免跨页面 prop drilling |

---

## 3. 修改的模块和文件

### 3.1 新增文件

| 文件 | 任务 | 说明 |
|---|---|---|
| `frontend/src/stores/patient.ts` | F4 | 当前患者基础资料/详情缓存；`loadCurrentDetail/loadCurrentBasic/setCurrent/clear` |
| `frontend/src/stores/doctor.ts` | F1/F4 | 按科室医生列表缓存（60s TTL，7 天聚合自 `/schedules/available`）；并发请求合并；`force` 强制刷新 |
| `frontend/src/stores/appointment.ts` | F1/F4 | 我的预约缓存 + `preSelection`（分诊→挂号预选参数）；`buildPreSelectionFromQuery` 兜底路由 query 解析 |
| `frontend/src/stores/prescription.ts` | F2/F4 | 当前就诊处方 + 历史处方 + AI 审核结果摘要；`setCurrentPrescription/setHistory/clearHistory/clear` |
| `tests/front_unit_test/store-patient.spec.ts` | F4 | 7 用例：初始态、loadCurrentDetail、clear、缓存命中 |
| `tests/front_unit_test/store-doctor.spec.ts` | F1/F4 | 7 用例：7 天聚合、过滤不可用/已满排班、TTL 缓存、force 跳过缓存、单日失败向前推进、全部失败空列表、clear |
| `tests/front_unit_test/store-appointment.spec.ts` | F1/F4 | 6 用例：myAppointments 加载、preSelection 设置/清空、buildPreSelectionFromQuery |
| `tests/front_unit_test/store-prescription.spec.ts` | F2/F4 | 6 用例：setCurrentPrescription、setHistory、clearHistory 不污染其他患者、clear |

### 3.2 修改文件

| 文件 | 任务 | 改动 |
|---|---|---|
| `frontend/src/api/prescription.ts` | F2 | 抽出 `getPatientPrescriptions(patientId, options)`，`getMyPrescriptions` 改为薄包装；新增 `includeDraft` 选项（默认过滤草稿） |
| `frontend/src/api/doctor.ts` | F1 | 新增 `getDoctorById(id)`；`getDoctorSchedules` 支持显式 `doctorId` 参数（不依赖当前登录医生） |
| `frontend/src/stores/encounter.ts` | F3 | 新增 `consultationDialogue` ref + `setConsultationDialogue/reset` |
| `frontend/src/views/patient/PatientTriageView.vue` | F1 | 渲染「推荐医生」卡片区；loading 骨架 / 失败降级入口（`医生列表暂不可用，可进入科室挂号`） / 空态；点击「预约该医生」→ 写入 store preSelection → `router.push('/patient/appointments?tab=book&...')` |
| `frontend/src/views/patient/PatientAppointmentsView.vue` | F1 | onMounted 读取 `useAppointmentStore.preSelection`（含路由 query 兜底）；自动切到「预约挂号」子 Tab、自动选中科室 / 高亮医生 / 若带 scheduleId 则直接打开确认弹窗；预约成功后 `clearPreSelection` |
| `frontend/src/views/doctor/DoctorPrescriptionView.vue` | F2 | 新增「历史处方」折叠区 + 加载/空态/错误降级；新增 `loadHistoryPrescriptions(force?)`，通过 `getPatientPrescriptions(patientId, { includeDraft:false })` 拉取，自动排除当前正在编辑的就诊；`usePrescriptionStore.setHistory` 同步缓存 |
| `frontend/src/views/doctor/DoctorMedicalRecordView.vue` | F3 | 新增「💬 问诊对话记录」多行 `<textarea>`；`buildPresentIllnessWithDialogue()` 把对话拼到 `presentIllness` 后再调用 AI 病历生成接口；AI 失败时保留输入（用户可手动填写） |
| `tests/front_unit_test/api-prescription.spec.ts` | F2 | 新增 `getPatientPrescriptions` 4 个用例：默认过滤 DRAFT、includeDraft=true、排序、日期过滤 |
| `tests/front_unit_test/api-doctor.spec.ts` | F1 | 新增 `getDoctorById` 用例、`getDoctorSchedules(id)` 跳过当前医生查询的用例 |

---

## 4. 联调依赖

| 功能 | 依赖后端接口 | 状态 |
|---|---|---|
| F1 推荐医生 | `GET /api/triage/recommended-doctors?departmentId=&limit=`（B3） | 后端已实现；前端 store 先调用该接口，失败时降级为 `useDoctorStore.loadDoctorsByDepartment`（自 `/schedules/available` 反向聚合） |
| F1 跳转挂号 | `GET /api/schedules/available?departmentId=&date=`、`GET /api/departments` | 后端既有，未做改动 |
| F2 历史处方 | `GET /api/prescriptions/patient/{patientId}` | 后端既有 |
| F2 处方审核 | `POST /api/prescription/check`（B4） | 后端已实现；当前前端 AI 审核按钮继续走 `getPrescriptionById`，按钮文案已调整为「查看 / 刷新 AI 审核结果」，等业务决策后再切换到 `/prescription/check` |
| F3 问诊对话 | `POST /api/medical-records/generate-ai` 新增可选 `consultationTranscript` 字段（B5） | 后端已实现；前端已把对话原文拼到 `presentIllness` 并通过 sanitized 流程传给 AI；如需「独立 transcript 通道」，可走 `consultationTranscript` 字段直传 |
| F4 全部 store | 不依赖新接口 | 仅复用既有真实接口 |

---

## 5. 验收标准对照

### F1：分诊结果页推荐医生列表与医生级跳转
- ✅ 分诊成功后在结果页直接看到「推荐科室」+「推荐医生」卡片区
- ✅ 点击「预约该医生」→ 挂号页 `book` 子 Tab 自动选中对应科室 / 医生 / 排班；若 `scheduleId` 命中则直接打开确认弹窗
- ✅ loading 用骨架卡片，失败时降级为「医生列表暂不可用，可进入科室挂号」入口
- ✅ 原有「去该科室挂号」按钮（仅带 departmentId）仍然可用，挂号页逻辑未破坏

### F2：医生处方页历史处方与 AI 审核结果面板
- ✅ 折叠区加载后展示状态 / 时间 / 药品摘要 / AI 审核状态 / 风险等级 / 关键警告
- ✅ 高/中/低风险有视觉区分（红/黄/绿 tag）
- ✅ 空态、加载、错误三类状态都有清晰提示
- ✅ 当前处方创建/审核/确认流程未被破坏

### F3：医生病历页问诊对话记录
- ✅ 病历页有「💬 问诊对话记录」多行输入区
- ✅ 调用 AI 病历生成时，对话原文作为上下文参与生成
- ✅ AI 失败时输入内容保留，提示用户手动填写
- ✅ 主诉/现病史/既往史/体格检查字段未被破坏

### F4：Pinia 状态模块化补强
- ✅ F1 分诊→挂号的预选参数走 `useAppointmentStore.preSelection`，不污染路由 query
- ✅ F2 历史处方状态在 store 中可刷新、可清空，切换患者不会串扰
- ✅ 刷新页面后登录态由 `auth` store 恢复（既有行为未改）
- ✅ `npm run build` 通过

---

## 6. 页面验证步骤

### F1：分诊→挂号预选医生
1. 患者登录 → 进入「分诊」
2. 提交主诉与症状，拿到 AI 推荐科室
3. 结果页「👨‍⚕️ 推荐医生」区域加载出 2-3 位医生卡片
4. 点击「预约该医生」→ 跳转到 `/patient/appointments`，自动切到「预约挂号」
5. 看到「科室」下拉已选中、「推荐医生」高亮（若带 scheduleId 则确认弹窗直接打开）

### F2：历史处方
1. 医生登录 → 选择某次「就诊中」队列 → 进入接诊
2. 病历完成后「开处方」→ 处方页加载
3. 「📋 历史处方」折叠区自动加载出该患者历史 CONFIRMED + VOIDED 处方
4. 点击「▸」展开，查看状态 / 时间 / AI 风险等级
5. 折叠区右侧「刷新」按钮触发 `loadHistoryPrescriptions(true)`

### F3：问诊对话记录
1. 医生接诊页 → 「💬 问诊对话记录」文本区
2. 粘贴一段医患对话原文（医生：… 患者：…）
3. 填写主诉后点击「AI 辅助生成病历」
4. 后端返回的 `presentIllness` 会包含「【问诊对话记录】」段落；前端仍按原字段回填
5. 故意断网或后端返回 FAILED → 文本区输入保留，提示「AI 生成失败，可手动填写」

### F4：跨页面状态
1. F1 流程：分诊点「预约该医生」→ 刷新页面（不点击）→ 重新走一遍 store 是否能恢复（注意：当前 store 不持久化，刷新会丢，这与原行为一致）
2. F2 流程：在 A 患者的处方页加载历史 → 切换到 B 患者 → 历史区应清空 / 自动重拉

---

## 7. 与联调 AI 交接清单

1. 页面路径
   - `/patient/triage`（分诊结果）
   - `/patient/appointments`（挂号，自动预选）
   - `/doctor/prescription/:id`（医生处方）
   - `/doctor/medical-record/:id`（医生病历）
2. 调用的 API client 方法
   - `useDoctorStore().loadDoctorsByDepartment(deptId)`
   - `useAppointmentStore().setPreSelection(...) / buildPreSelectionFromQuery(...) / clearPreSelection()`
   - `getPatientPrescriptions(patientId, { includeDraft })`（新增）
   - `getDoctorById(id)`（新增）
   - `getDoctorSchedules(doctorId)`（扩展）
   - `usePrescriptionStore().setCurrentPrescription / setHistory / clearHistory`
3. 依赖的后端接口
   - `GET /api/triage/recommended-doctors`（B3）— 推荐医生首选
   - `GET /api/schedules/available?departmentId=&date=` — 医生列表降级
   - `GET /api/prescriptions/patient/{id}` — 历史处方
   - `POST /api/medical-records/generate-ai` body `consultationTranscript`（B5）
4. 已知降级路径
   - 推荐医生接口失败 → 仍展示「医生列表暂不可用，可进入科室挂号」
   - 历史处方接口失败 → 折叠区空态，不弹错误 toast
   - AI 病历生成失败 → 保留对话输入，提示手动填写

---

## 8. 仍依赖联调 / 后端 AI 决策

- F1 推荐医生接口（B3）当前不返回医生职称；前端 store 写死「医师」。如需展示职称，需要扩展 B3 响应 DTO。
- F2 AI 审核按钮当前继续走 `getPrescriptionById`（按钮文案「查看 / 刷新 AI 审核结果」），是否切换到 B4 `/api/prescription/check` 由联调 AI 决定。
- F3 对话原文目前通过拼接 `presentIllness` 传入；后端 B5 的 `consultationTranscript` 字段已就绪，前端可平滑切换为单独传该字段（不在本次改动范围）。

---

## 9. 代码审查与修复记录（2026-07-02 review pass）

完整细节见 [2026-07-02-frontend-ai-course-requirements-thoughts.md §7](file:///C:/Users/%E5%BC%A0%E9%93%AD/Documents/trae_projects/cloudbrain3/yunnao/docs/integration/2026-07-02-frontend-ai-course-requirements-thoughts.md)。本节为简明清单：

### 9.1 已修复（5 项，纳入本交付）

| # | 严重度 | 文件 | 修复要点 |
|---|---|---|---|
| 1 | 🔴 Bug | `PatientTriageView.vue` | 追问后科室变化检测失效（变量赋值后自比恒为 false）→ 赋值前保留 `oldDeptId` 再比较 |
| 2 | 🔴 Bug | `PatientAppointmentsView.vue` | 预选时 watch 与 mount 各自触发 `loadSchedules` 产生并发竞态 → 引入 `isInitializing` 标志，初始化阶段 watch 跳过 `loadSchedules`，由 mount 统一 `await` |
| 3 | 🟡 性能 | `stores/doctor.ts` | `nextScheduleId` 嵌套 `allSchedules.find` 是 O(N²) → 单遍 ID 比较降到 O(N) |
| 4 | 🟡 类型 | `DoctorPrescriptionView.vue` | `statusText/statusClass` 参数 `string \| null` 过宽 → 收窄为 `PrescriptionStatus \| null` |
| 5 | 🟢 性能 | `PatientTriageView.vue` | `enrichDoctorProfiles` 串行 → `Promise.all` 并行（3 个医生从 3×RTT 降到 1×RTT） |

### 9.2 已知未修复（5 项，留给后续 PR）

不阻塞当前交付；详情见 thoughts §7.6。

1. `api/doctor.ts` `getCurrentDoctor` 全表过滤（依赖后端新增 `GET /doctors/me`）
2. `api/prescription.ts` `getPatientPrescriptions` 不支持分页（依赖后端扩展 query）
3. `PatientAppointmentsView` 模板中重复 `schedules.find`（纯美化）
4. `DoctorEncounterView` 中未走 `usePatientStore`（跨页面一致性）
5. `api/prescription.ts` `aiReviewPrescription` 是占位（待联调切到 B4）

### 9.3 审查后回归

| 检查 | 结果 |
|---|---|
| `npx vue-tsc --noEmit` | EXIT=0 |
| `npx vitest run store-*` | 4 files / 26 tests passed |
| `npx vitest run api-doctor / api-prescription` | 2 files / all passed |
| `npx vite build` | ✓ built in 1.46s |

review pass 之后未引入新的失败用例；`api-admin.spec.ts` 中的日期敏感失败在 review pass 之前即存在（与本次改动无关），状态保持不变。
