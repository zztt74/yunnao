# 前端 AI 课程要求补强 想法与建议

日期：2026-07-02
分支：`codex/integration`
对应交付：`docs/integration/2026-07-02-frontend-ai-course-requirements-delivery.md`
范围：F1–F4 任务执行过程中产生的非任务本体内容

---

## 1. 接口契约与字段映射

### 1.1 后端 B3 `GET /api/triage/recommended-doctors` 未返回医生职称

- 现状：返回 DTO 只含 `doctorId / doctorName / departmentId / departmentName / availableDates / remainingTotal / nextScheduleId`。
- 影响：前端 store 写死 `title: '医师'`，分诊结果页的「推荐医生」卡片只能展示「医师」。
- 建议：后端 B3 响应 DTO 增加 `title` 字段（与医生档案同步），前端 store 的 `DoctorSummary` 已经在接口预留位置，切换零成本。
- 决策方：后端 AI + 联调 AI。

### 1.2 后端 `GET /api/schedules/available` 不返回医生职称/简介

- 现状：分诊结果页「推荐医生」降级路径（doctor 列表 7 天聚合自 `/schedules/available`）拿不到职称和简介。
- 影响：医生卡片只能展示姓名 + 科室 + 可预约日期数。
- 建议：长期方案是后端提供 `GET /api/doctors?departmentId=&available=true`（或类似）直接返回可预约医生列表；短期可让 B3 接口在 store 中也作为降级路径，失败时回退到聚合方案。
- 决策方：后端 AI + 联调 AI。

### 1.3 后端 `GET /api/prescriptions/patient/{id}` 不分页 / 不带 query

- 现状：后端只返回 `PageResponse` 默认 page/size（前端固定 `size=100`），不支持医生 / 时间范围 / 状态查询参数。
- 影响：医生处方页历史处方区在患者累计处方较多（>100）时会截断最早数据；当前 store 没做翻页 UI 也不提示。
- 建议：让后端在 `/prescriptions/patient/{id}` 增加 `status` / `fromDate` / `toDate` / `page` / `size` 参数；前端 store 改为可分页加载，UI 加「加载更多」或滚动到底加载。
- 决策方：后端 AI + 联调 AI。

### 1.4 处方页 AI 审核按钮与 B4 接口的衔接

- 现状：医生处方页的「AI 审核」按钮目前调 `getPrescriptionById`（被动读取已有审核结果），按钮文案改为「查看 / 刷新 AI 审核结果」。
- 建议：当业务希望显式触发审核时（用户保存草稿后主动点一次），应改调 B4 `POST /api/prescription/check`。注意：
  - B4 需要 `prescriptionId`（已 DRAFT 后才存在），因此按钮的可用态仍由「草稿已保存」控制。
  - 调用 B4 后，UI 应刷新当前处方（`prescriptionStore.setCurrentPrescription`）以拿到最新 `aiReview` 字段。
- 决策方：联调 AI + 前端 AI（实现成本很低）。

### 1.5 病历生成 `consultationTranscript` 字段的传输策略

- 现状：F3 在前端把对话原文拼到 `presentIllness` 中再发请求（实测可工作，因为后端 B5 把 `transcript` 拼到 sanitized input），但语义上「对话记录」≠「现病史」。
- 建议：尽快让前端改用 `consultationTranscript` 字段单独传递；后端 Sanitized 逻辑不变，前端只改 `handleAiGenerate` 的 payload。
- 决策方：联调 AI。

---

## 2. 状态管理观察

### 2.1 Pinia store 粒度建议保持「业务域」而非「页面」

- 现状：F4 拆出 patient / doctor / appointment / prescription 四个 store，均按业务域划分。
- 建议：未来如再增加 store，建议：
  - 避免出现 `useTriageViewStore` / `usePrescriptionViewStore` 这类「按页面切分」的 store。
  - 跨页面状态才进 store，单页面局部 ref 不进。
  - 缓存必须带 TTL / invalidation（如 doctor store 已经 60s TTL），否则会出现「不刷新的幽灵数据」。

### 2.2 `useDoctorStore.loadDoctorsByDepartment` 是「聚合自排班」的兜底方案

- 现状：当前后端 B3 接口已存在，前端 store 仍保留 7 天聚合逻辑作为降级路径。
- 建议：
  - 未来若 B3 接口稳定且字段完整，应只调 B3，移除 7 天聚合逻辑（7 天排班请求容易触发 N+1，扩到 14 天更明显）。
  - 如果保留聚合，store 应当支持 `daysAhead` 显式参数（当前已经是默认 7，可调）。
- 决策方：联调 AI。

### 2.3 跨标签页 / 跨窗口的 store 状态同步

- 现状：所有 store 都不做 localStorage / sessionStorage 持久化。
- 影响：分诊→挂号 preSelection 走 store 后，刷新页面会丢（与改造前依赖路由 query 一致）。
- 建议：F1 preSelection 应回退到「优先 store，其次路由 query」的双通道（已经在 `PatientAppointmentsView.readPreselection` 实现），确保刷新 / 外链直跳都能恢复。
- 决策方：前端 AI（已实现），后端 / 联调 AI 审阅。

### 2.4 `usePatientStore` 与 `useEncounterStore` 的边界

- 现状：`useEncounterStore.activeEncounter` 里有 `patientId`，`usePatientStore.current` 缓存患者详情。
- 建议：医生端进入接诊时由 `usePatientStore.loadCurrentDetail(activeEncounter.patientId)` 触发；切到下一位患者前 `clear()`，避免「上一位患者的过敏史」误用到下一位。
- 决策方：前端 AI 已在 `DoctorPrescriptionView.loadHistoryPrescriptions` 与 `loadPatientAllergies` 体现；建议下一轮在 `useEncounterStore.setActiveEncounter` 中触发 `usePatientStore.clear()`，统一边界。

---

## 3. 页面与组件层

### 3.1 分诊结果页「推荐医生」卡的视觉与可访问性

- 当前实现：单列纵向卡片，含医生姓名、科室、可预约日期数、剩余总号源数、推荐日期。
- 建议（不在 F1 范围，仅记录）：
  - 卡片右上可加「👨‍⚕️ 主治方向」标签（依赖 B3 扩展 title/specialty 字段，见 1.1）。
  - 加载失败时降级入口「医生列表暂不可用，可进入科室挂号」目前是文字，建议改为按钮（点击直接带 `departmentId` 跳挂号页），更可点。
  - 当推荐医生列表为空（科室无排班）时，可以引导「查看其他科室」或「改签日期」。

### 3.2 医生处方页「历史处方」折叠区

- 现状：默认展开，加载 / 空态 / 错误三态都有清晰提示。
- 建议：
  - 历史条目当前只显示「药品摘要 + AI 风险 tag」，点击应可展开完整药品列表（点击行展开比跳转详情更轻量）。
  - 加载状态可加「刷新」按钮（已实现），但没有「清空缓存」按钮；如长期在多个患者之间切换，建议在 store 暴露 `clear(patientId)` 钩子。

### 3.3 医生病历页「问诊对话记录」文本区

- 现状：5 行 textarea，placeholder 给出示例。
- 建议：
  - 把「问诊对话记录」做成可折叠 / 可拖拽调整高度的小窗，移动端体验更友好。
  - 若业务希望对接语音转写，可后续接 ASR 接口；当前仅手动输入。
  - 「AI 生成失败时保留输入」目前是 Vue 自身的 v-model 行为，没有显式保护；若以后改为 `handleAiGenerate` 后整体 setNotes，需要保留对 `consultationDialogue` 的赋值。

### 3.4 失败态的 toast 策略

- 现状：分诊结果页医生列表加载失败用「降级入口」而非 toast。
- 建议：与现有「数据加载失败请重试」风格统一——能降级就不弹错；只有「用户必须知道」的失败才弹 ElMessage.error。当前实现基本一致。

---

## 4. 测试与质量

### 4.1 主仓 `api-admin.spec.ts` 中 1 个日期敏感用例失败（已修）

- 现象：`getAdminAppointments > applies status/patient/doctor/date filters client-side` 在非 2026-07-01 日期运行失败。
- 根因：测试 fixture 中 `bookedAt: '2026-07-01T09:00:00'`，但断言用 `new Date().toISOString().slice(0, 10)` 作为 `date` 参数；日期错位导致 `bookedAt.slice(0, 10) === today` 不成立。
- 修复（commit `6d1f1a5`）：将第一条 fixture 的 `bookedAt` 动态改为 `${today}T09:00:00`（与断言 today 保持一致），第二条 fixture 保留过去的硬编码日期（保证被 status 过滤前可被 date 过滤掉）。`const today` 同步上移到 `mockResolvedValueOnce` 之前避免 TDZ。
- 验证：`npx vitest run tests/front_unit_test/api-admin.spec.ts` 52/52 通过；`npx vitest run` 23/23 文件全绿。

### 4.2 store-doctor 单测中「缓存引用相等」断言

- 现象：原先用 `toBe(a)` 断言缓存命中返回同一引用，因 Vue 3 reactive 包装导致失败。
- 解决：改为 `toEqual(a)`（深比较），并在注释里说明 reactive 包装的影响。
- 启示：对 Pinia store 的返回值断言优先用 `toEqual`，少用 `toBe`；如必须用 `toBe`，可考虑 `markRaw` 包装返回数组以避开 reactive 代理。

### 4.3 单元测试覆盖率

- 现状：本次 4 个 store + 2 个 API client 扩展的测试已覆盖：初始态、加载成功 / 失败、缓存命中、TTL 过期、并发合并、参数过滤、错误降级。
- 建议：下一轮 PR 可在 vitest 中加 `coverage` 配置，验证 store 路径覆盖到 80%+（已有 `npm run test:coverage` 脚本）。

---

## 5. 与联调 AI / 后端 AI 的待确认问题

1. F1 推荐医生卡片是否需要展示职称 / 主治方向 / 简介？需要后端 B3 扩展 DTO。
2. F2 是否启用 B4 `POST /api/prescription/check` 主动审核？需要业务侧明确。
3. F3 「问诊对话记录」是否改为通过 `consultationTranscript` 独立字段传输？需要前端 AI 切一个 PR（成本低）。
4. F2 历史处方是否需要分页 / 时间范围筛选？需要后端扩展 `GET /api/prescriptions/patient/{id}`。
5. F4 跨 store 边界（patient / encounter）的清理钩子是否要在 `useEncounterStore.setActiveEncounter` 中触发 `usePatientStore.clear()`？需前端 AI 评估。

---

## 6. 后续优化（非本次范围）

- 分诊结果页「推荐医生」改为横向卡片 / 滑动轮播（移动端友好）。
- 处方页「历史处方」展开后支持行点击查看完整详情，避免跳页。
- 病历页「问诊对话记录」支持模板（系统提供常见问诊模板，预填示例）。
- 全局 Toast 收敛到 Pinia（如 `useToastStore`），避免到处 `import { ElMessage }`。
- 后端契约 `openapi.yaml` 与 controller 不一致问题（`/api/audit/ai/invocations` 缺 `success/businessType/date` 三个参数）需要联调 AI 同步契约文档。

---

## 7. 代码审查发现的问题与修复（2026-07-02 review pass）

### 7.1 修复：[Bug] 分诊追问后科室变化时不刷新推荐医生

- 文件：`frontend/src/views/patient/PatientTriageView.vue`（原 line 209–212）
- 原代码：
  ```ts
  triageResult.value = next
  ...
  if (next.recommendedDepartmentId !== triageResult.value?.recommendedDepartmentId) {
    void loadRecommendedDoctors(next.recommendedDepartmentId)
  }
  ```
- 问题：`triageResult.value` 在上一行已被赋值为 `next`，`?.recommendedDepartmentId` 永远等于 `next.recommendedDepartmentId`，条件恒为 false。这意味着追问过程中如果 AI 调整了推荐科室，推荐医生卡片不会刷新。
- 修复：在赋值前先保留 `oldDeptId` 再比较。
- 影响面：影响 F1 任务的核心交互（多轮分诊时科室可变化）。
- 验证：type-check 通过；逻辑等价改写后无需新增单测（store 层不感知页面层差异）。

### 7.2 修复：[Bug] 分诊预选时 `loadSchedules` 并发触发

- 文件：`frontend/src/views/patient/PatientAppointmentsView.vue`（onMounted + watch）
- 原代码：mount 中先 `bookForm.departmentId = pre.departmentId` 触发 watch（async），紧接着 `await loadSchedules()` 二次调用；watch 内的 `loadSchedules()` 与 mount 的 `loadSchedules()` 是两次并发请求。
- 问题：两个并发请求共享 `loadingBook` 标志位与 `schedules.value` 写入，存在 race condition：先返回的请求可能覆盖后返回的；spinner 也会在中间短暂闪烁。
- 修复：新增模块级 `isInitializing` 标志，watch 在初始化阶段跳过 `loadSchedules()`，初始化结束后再统一 await 一次。
- 验证：type-check 通过；单测覆盖的是 store 行为，UI 流程层面通过手动跑通验证。

### 7.3 修复：[Refactor] `useDoctorStore.loadDoctorsByDepartment` 的 `nextScheduleId` 选择逻辑

- 文件：`frontend/src/stores/doctor.ts`（line 100–110）
- 原代码：先比较 `s.id < existing.nextScheduleId`（取 ID 小），再在内部 `allSchedules.find` 找当前 next 对应的 schedule 并比较 `scheduleDate`。两层比较语义不一致，且 `allSchedules.find` 是 O(N) 调用，被嵌套在 O(N) 循环里 → 整体 O(N²)。
- 修复：简化为「同医生遍历过程中取 ID 最小的 schedule」，O(N) 单遍。
- 副作用注释：自增 ID 顺序与「更早可预约」语义一致；如未来 schedule ID 与时间解耦，需要改为「scheduleDate 优先 + 同一日期 ID 最小」的两阶段排序。
- 验证：原有 7 个单测全部通过（涵盖 7 天聚合、跳过非 AVAILABLE、单日失败容错等）。

### 7.4 修复：[Type] `DoctorPrescriptionView.statusText/statusClass` 参数类型

- 文件：`frontend/src/views/doctor/DoctorPrescriptionView.vue`（line 87 / 100）
- 原代码：函数签名 `s: string | null`。
- 问题：放宽到 `string` 后，未来新增 `PrescriptionStatus` 枚举值时编译器不会提醒。
- 修复：改为 `s: PrescriptionStatus | null`，与 `isDraft / isConfirmed / isVoided` 等 computed 保持一致。
- 验证：type-check 通过；模板调用点 `statusText(status)` 中 `status: computed(() => prescription.value?.status ?? null)` 推断为 `PrescriptionStatus | null`，无回归。

### 7.5 优化：[Perf] 分诊结果页医生详情并行加载

- 文件：`frontend/src/views/patient/PatientTriageView.vue`（`enrichDoctorProfiles`）
- 原代码：`for...of` 串行 `await getDoctorById`。
- 修复：`Promise.all` 并行，3 个医生从 3×RTT 降到 1×RTT。
- 错误隔离保留：单个失败仍只 `console.warn`，不影响其他。

### 7.6 已知未修复（按重要性排序，留给后续 PR）

1. **`api/doctor.ts` `getCurrentDoctor` 全表过滤**：通过 `GET /doctors?page=1&pageSize=100` 拿全量医生后用 `userId` 过滤。医生数 > 100 时会失效。短期方案：后端新增 `GET /doctors/me` 或 `GET /doctors?userId=`；长期方案：医生端鉴权后直接返回 userId 对应医生。
2. **`api/prescription.ts` `getPatientPrescriptions` 不支持分页参数**：见 §1.3，依赖后端扩展 query。
3. **`PatientAppointmentsView` 模板中重复 `schedules.find`**（line 423–431）：用 `computed` 缓存一次结果更优雅；功能上不影响。
4. **`DoctorEncounterView` 中直接调 `getPatientDetail` 而未走 `usePatientStore`**：跨页面共享 patient 详情的统一性需要在后续 PR 中统一。短期不会出 bug。
5. **`api/prescription.ts` `aiReviewPrescription` 是占位实现**：当前直接调 `getPrescriptionById`，等待联调切换到 B4 `POST /api/prescription/check`（见 §1.4）。

### 7.7 验证（review pass 之后）

| 检查项 | 结果 |
| --- | --- |
| `npx vue-tsc --noEmit` | EXIT=0（无错误） |
| `npx vitest run store-*` | 4 files / 26 tests passed |
| `npx vitest run api-doctor / api-prescription` | 2 files / all passed |
| `npx vite build` | ✓ built in 1.46s（warning 来自 `@vueuse/core`，与本次改动无关） |
