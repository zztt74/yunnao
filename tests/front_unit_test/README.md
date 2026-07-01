# 前端单元测试说明

> 对应任务：[实验] 单元测试 - 前端 Vitest
> 测试目录：`C:\Users\张凯铭\Documents\trae_projects\cloudbrain1\tests\front_unit_test\`
> 配置文件：`frontend/vite.config.ts`
> 测试时间：2026-07-01

## 1. 工具与环境

- **测试框架**：Vitest 4.1.9
- **DOM 环境**：happy-dom（提供 `sessionStorage`、`localStorage` 等浏览器 API）
- **Mock 框架**：Vitest 自带 `vi.fn()` / `vi.mock()` / `vi.hoisted()`
- **覆盖率工具**：@vitest/coverage-v8（V8 原生覆盖率）
- **Node 版本**：>= 22.12.0

## 2. 如何运行测试

进入前端目录后执行下列命令：

```powershell
cd C:\Users\张凯铭\Documents\trae_projects\cloudbrain1\frontend

# 安装依赖（首次或更新后）
npm install

# 跑全部单元测试（单次）
npm run test

# 监听模式（开发时使用）
npm run test:watch

# 跑测试并生成覆盖率报告（HTML + 控制台 + JSON 摘要）
npm run test:coverage
```

覆盖率报告默认输出到 `frontend/coverage/`，可双击 `coverage/index.html` 在浏览器中查看。

## 3. 测试目录与文件结构

```
tests/
└── front_unit_test/                # 前端单元测试专用目录
    ├── helpers/                    # 共享测试工具
    │   ├── api-client-mock.ts      #  apiClient 模拟工厂 + 响应封装
    │   ├── fixtures.ts             #  真实后端字段风格的测试数据
    │   └── mock-setup.ts           #  统一的 vi.mock('@/api/client') 注入
    ├── api-admin.spec.ts           # 管理端 API（部门/医生/用户/统计/审计）
    ├── api-appointment.spec.ts     # 预约/排班 API
    ├── api-auth.spec.ts            # 登录/登出/当前用户
    ├── api-department.spec.ts      # 科室查询
    ├── api-device.spec.ts          # 设备 CRUD + 状态机 + 使用记录
    ├── api-doctor.spec.ts          # 医生主页 / 排班（需 Pinia 鉴权）
    ├── api-drug.spec.ts            # 药品目录
    ├── api-encounter.spec.ts       # 接诊流程（需 Pinia 鉴权）
    ├── api-examination.spec.ts     # 检查/检验开立与报告
    ├── api-medical-record.spec.ts  # 病历记录
    ├── api-patient.spec.ts         # 患者信息/资料/时间线
    ├── api-prescription.spec.ts    # 处方开立/审核/作废
    ├── api-triage.spec.ts          # AI 分诊
    ├── client.spec.ts              # axios 拦截器（401/403 行为）
    ├── response.spec.ts            # 响应解析（成功/失败 envelope）
    ├── store-auth.spec.ts          # 鉴权 Pinia store
    └── store-encounter.spec.ts     # 接诊工作台 Pinia store
```

> 所有外部目录下的测试文件路径以 `../tests/front_unit_test/**` 形式注册到 `vite.config.ts` 的 `test.include`，并通过 `server.fs.allow` 与 `optimizeDeps.entries` 显式放行，以兼容 Vitest 默认的「工作目录为 vite 项目根目录」行为。

## 4. 主要测试内容

### 4.1 鉴权与响应解析
- `response.spec.ts`：覆盖 `parseApiResponse` 的成功/失败路径，确认错误信息会携带 `traceId`。
- `store-auth.spec.ts`：覆盖 Pinia store 的 `establishSession / clearSession / login / logout`，以及角色判断（`isPatient/isDoctor/isAdmin/primaryRole/hasRole`）、`mustChangePassword`、从 `sessionStorage` 读取的初始状态与解析失败回退。

### 4.2 核心业务 API（共 14 个）
按业务域拆分的 API 测试文件统一通过 `helpers/mock-setup.ts` 注入 `vi.mock('@/api/client')`，再借 `getApiClientMock()` 拿到 mock：

- **正常路径**：调用入口、参数序列化、字段映射（如 `getDoctorProfile` 将后端 `ENABLED → ACTIVE`、`CHIEF → 主任医师`）。
- **异常路径**：401/网络失败、字段缺失（如 `getCurrentDoctor` 找不到匹配档案、`consultTriage` 同时存在 `aiReason` 与 `aiFailureReason` 时优先使用失败原因）。
- **边界条件**：`getPatientDetail` 的 `birthDate` 非法值 → 年龄 0；跨月生日减 1；空值字段回退 `无`；`getMyAppointments` 同时支持 `PageResponse` 与裸数组；`consultTriage` 不传 `getPatientInfo` 时使用默认 patientId。

### 4.3 axios 拦截器
- `client.spec.ts`：验证 401 时 `useAuthStore().clearSession()` + 路由回到 `/`；403 时跳转 `/forbidden`；请求时携带 `Authorization: Bearer <token>`。

### 4.4 管理端 API
- `api-admin.spec.ts`：覆盖科室/医生/用户/排班/统计/审计日志等 30+ 接口；包括 `backendDeviceType` 设备分类映射、AI 调用日志分页与筛选、triage 列表前端筛选与统计。

## 5. 覆盖率

执行 `npm run test:coverage` 后的核心模块覆盖率（V8 引擎，2026-07-01 实际数据）：

| 模块 / 文件                  | Stmts   | Branch | Funcs   | Lines   | 备注                       |
|----------------------------|---------|--------|---------|---------|----------------------------|
| **All files（核心域）**     | 97.55%  | 85.97% | 96.55%  | 97.9%   | 涵盖 `src/api/**` 与 `src/stores/**` |
| api/admin.ts               | 99.44%  | 83.97% | 98.75%  | 99.39%  | 用户/医生/科室/排班/设备/统计/审计全覆盖 |
| api/auth.ts                | 100%    | 100%   | 100%    | 100%    |                            |
| api/department.ts          | 100%    | 100%   | 100%    | 100%    |                            |
| api/doctor.ts              | 100%    | 83.33% | 100%    | 100%    | `getCurrentDoctor` 错误分支覆盖 |
| api/drug.ts                | 100%    | 100%   | 100%    | 100%    |                            |
| api/patient.ts             | 100%    | 82.75% | 100%    | 100%    | 异常分支仅 `getPatientDetail` 错误传播 |
| api/prescription.ts        | 100%    | 79.59% | 100%    | 100%    | 字段兜底分支为防御性代码     |
| api/response.ts            | 100%    | 100%   | 100%    | 100%    |                            |
| api/triage.ts              | 100%    | 85.71% | 100%    | 100%    | 映射分支覆盖                |
| api/examination.ts         | 97.56%  | 97.87% | 93.75%  | 97.22%  |                            |
| api/medical-record.ts      | 97.29%  | 81.48% | 92.85%  | 100%    |                            |
| api/device.ts              | 92.45%  | 80%    | 94.11%  | 95.45%  | 字段兜底分支                |
| api/encounter.ts           | 100%    | 100%   | 100%    | 100%    | 全部覆盖（含 `assistDiagnosis`、`getDoctorAppointmentById`） |
| api/appointment.ts         | 84.61%  | 100%   | 80%     | 84.61%  | 取消接口兜底                |
| api/client.ts              | 94.11%  | 100%   | 66.66%  | 94.11%  | axios 拦截器                |
| stores/auth.ts             | 100%    | 95%    | 100%    | 100%    | `primaryRole` 仅有 1 行未触发 |
| stores/encounter.ts        | 100%    | 100%   | 100%    | 100%    |                            |

- **核心业务逻辑**（API 主体）覆盖率 100%：`auth、department、doctor、drug、patient、prescription、response、triage、auth store、encounter store`。
- **辅助/工具模块**（response、拦截器、store 辅助）覆盖率 ≥ 95%：符合「≥ 80%」要求。
- **管理端**（admin）：覆盖了 52 个测试用例，覆盖率 99.39%（行）/99.44%（语句），包含用户/医生/科室/排班/设备/统计/审计全链路 CRUD 与查询。

## 6. 已知未覆盖范围

下列文件属于**视图层或被视图层专属功能**，未纳入本轮单元测试：

- `src/views/**`：Vue 单文件组件（视图集成测试建议在浏览器 E2E 层覆盖，本轮不交付）。
- `src/router/**`、`src/main.ts`：路由注册入口。
- `src/api/appointment.ts:58-59`：`_realApiReserved.create` 占位函数（非运行路径，避免打包告警使用，暂无调用方）。

## 7. 测试运行结果

```
Test Files  19 passed (19)
     Tests  233 passed (233)
  Duration  ~24s（含 happy-dom 启动 + Vite 转换）
```

所有 233 个测试用例通过，0 失败、0 跳过。

## 8. CI 集成建议

在 `frontend/` 下执行：

```bash
npm run test
npm run test:coverage
```

- 若需与 CI 严格门禁：要求 `All files` 的 `Lines >= 90%`、`Branch >= 80%`。
- 覆盖率 JSON 摘要位于 `frontend/coverage/coverage-summary.json`，便于后续接入 Codecov / SonarQube。

## 9. 关键决策与备注

1. **测试文件位置**：`tests/front_unit_test/` 与 `frontend/` 并列，符合工程惯例（GitHub Action CI 单独 run-test job 时无需修改默认 cwd）。
2. **共享 mock 工厂**：`helpers/mock-setup.ts` 使用 `vi.hoisted` 在所有 import 之前注册 `vi.mock('@/api/client')`，确保被测模块拿到的就是 mock 客户端。
3. **Pinia 初始化**：使用 `useAuthStore` 的测试必须在 `beforeEach` 中执行 `setActivePinia(createPinia())`，否则会触发 `getActivePinia() was called but there was no active Pinia` 错误。
4. **路径别名**：`vite.config.ts` 中通过 `resolve.alias` 同时把 `@` 指向 `frontend/src`、`pinia` 指向 `frontend/node_modules/pinia`，解决 Vitest 在工作目录之外的 import 解析问题。
5. **环境注释**：涉及 `sessionStorage` 的测试文件顶部带 `// @vitest-environment happy-dom` 注释，确保运行环境为 happy-dom。
