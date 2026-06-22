# 阶段 0 项目骨架设计

## 1. 目标

为“智慧云脑诊疗平台”建立符合 AI 协作文档 v2 的可运行基础骨架，使后端、前端、AI 能力和联调测试四个角色可以在明确的目录所有权下开始按业务薄切片开发。

本阶段只建立基础设施、模块边界、最小健康检查和验证链路，不实现登录、患者、排班、挂号等具体医疗业务。

## 2. 范围

### 2.1 本阶段包含

- Java 17、Spring Boot 3 后端工程；
- Vue 3、TypeScript、Vite 前端工程；
- 模块化单体后端包结构；
- 单 Vue 项目的患者端、医生端和管理员端路由基础；
- 统一响应结构、全局异常处理和请求 `traceId`；
- AI Service、Provider 和 Mock Provider 的最小边界；
- Flyway 迁移目录和基线迁移；
- `contracts/openapi.yaml` 契约入口；
- 契约、集成和 E2E 测试目录；
- Docker Compose、环境变量示例和启动说明；
- 后端测试、前端类型检查与构建、OpenAPI 校验和目录检查。

### 2.2 本阶段不包含

- JWT 登录和角色权限的业务实现；
- 医疗业务数据库表；
- 患者、医生、科室、排班或挂号接口；
- 正式 AI Prompt 和真实模型调用；
- 真实患者数据；
- 完整业务页面；
- 微服务、注册中心和分布式事务；
- 支付、医保、真实药房、PACS 或 LIS 集成。

## 3. 总体架构

系统采用：

```text
Vue 3 单页应用
+ Spring Boot 3 模块化单体
+ MySQL 8 单数据库
+ 统一 AI Gateway
+ Mock/HTTP AI Provider
+ OpenAPI 单一契约
```

开发环境通过 Vite 代理访问 Spring Boot `/api`。演示环境预留 Nginx、Spring Boot Jar 和 MySQL 的 Docker Compose 编排。

## 4. 仓库结构

```text
yunnao/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/neusoft/cloudbrain/
│       │   │   ├── CloudBrainApplication.java
│       │   │   ├── common/
│       │   │   ├── auth/
│       │   │   ├── user/
│       │   │   ├── patient/
│       │   │   ├── doctor/
│       │   │   ├── department/
│       │   │   ├── schedule/
│       │   │   ├── appointment/
│       │   │   ├── triage/
│       │   │   ├── encounter/
│       │   │   ├── examination/
│       │   │   ├── laboratory/
│       │   │   ├── medicalrecord/
│       │   │   ├── prescription/
│       │   │   ├── device/
│       │   │   ├── statistics/
│       │   │   ├── audit/
│       │   │   └── ai/
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── db/migration/
│       │       └── prompts/
│       └── test/
├── frontend/
│   ├── package.json
│   ├── vite.config.ts
│   └── src/
│       ├── api/
│       ├── components/
│       ├── layouts/
│       ├── modules/
│       ├── router/
│       ├── stores/
│       ├── types/
│       ├── utils/
│       └── views/
├── contracts/
│   ├── openapi.yaml
│   └── schemas/
├── tests/
│   ├── contract/
│   ├── integration/
│   └── e2e/
├── postman/
├── deploy/
├── docs/integration/
├── .github/workflows/
├── docker-compose.yml
├── .env.example
└── README.md
```

空业务模块使用 `package-info.java` 或 README 保持在 Git 中，不创建伪业务 Controller、Service、Repository 或 Entity。

## 5. 后端设计

### 5.1 基础依赖

- Spring Web；
- Spring Validation；
- Spring Security；
- Spring Data JPA；
- Flyway；
- MySQL Driver；
- Spring Boot Test；
- H2，仅用于测试；
- Springdoc OpenAPI，用于后续 Swagger 验证。

### 5.2 公共基础设施

`common` 模块只提供一套基础能力：

- `ApiResponse<T>`：固定 `code`、`message`、`data`、`traceId`；
- `PageResponse<T>`：固定 `items`、`page`、`pageSize`、`total`、`totalPages`；
- `ErrorCode`：提供阶段 0 所需的 `SUCCESS`、`VALIDATION_FAILED` 和 `SYSTEM_INTERNAL_ERROR`；
- `GlobalExceptionHandler`：将校验错误和未处理异常转换为统一响应；
- `TraceIdFilter`：接收或生成请求追踪 ID，并写入响应头及统一响应；
- `HealthController`：提供 `GET /api/health`，用于启动和集成验证。

阶段 0 不实现第二套响应、分页、异常或日志体系。

### 5.3 模块边界

每个业务模块保留以下目标分层：

```text
controller
application
domain
repository
dto
mapper
```

只有在具体业务任务开始时才创建实际分层文件。禁止为了填满目录而生成无行为类。

### 5.4 数据库

配置 MySQL 8 和 Flyway。基线迁移只创建一个用于验证迁移链路的 `schema_history_marker` 表，不创建医疗业务表。后续业务迁移按文档规定的版本段新增，已执行迁移不得修改。

测试环境使用 H2 MySQL 兼容模式验证应用上下文和迁移。

## 6. AI Gateway 设计

阶段 0 建立最小可扩展边界：

```text
AIService Interface
        │
        ▼
AIProvider
├── MockAIProvider
└── HttpLLMProvider（仅保留未来实现边界，不发起真实请求）
```

具体业务接口保留：

- `AITriageService`
- `AIDiagnosisService`
- `AIMedicalRecordService`
- `AIPrescriptionReviewService`
- `AIResultInterpretationService`

阶段 0 的 Mock 仅证明 Provider 可被选择和调用，不返回可被误认为真实医疗结论的内容。默认配置为：

```yaml
app:
  ai:
    mode: mock
    timeout-ms: 8000
    max-retries: 1
```

真实 Provider 不读取硬编码密钥，后续只能从环境变量获得配置。

## 7. 前端设计

### 7.1 基础依赖

- Vue 3；
- TypeScript 严格模式；
- Vite；
- Element Plus；
- Vue Router；
- Pinia；
- Axios；
- ECharts。

### 7.2 应用结构

使用一个 Vue 应用，通过以下路由区分三端：

```text
/patient/**
/doctor/**
/admin/**
```

阶段 0 提供：

- 根布局；
- 首页或项目状态页；
- 三端占位入口；
- 404 页面；
- 路由守卫挂载点；
- 单例 Axios Client；
- 统一 API 响应类型；
- 健康检查 API；
- 认证 Store 基础状态。

占位页面必须明确标识“基础框架”，不伪装为已完成业务功能。

### 7.3 页面状态

健康检查页面演示初始、加载、成功和错误状态。AI 页面、高风险提示和完整空状态在对应业务任务中实现。

## 8. OpenAPI 与测试设计

`contracts/openapi.yaml` 是接口契约唯一来源。阶段 0 只冻结：

```text
GET /api/health
```

响应使用统一 `ApiResponse`。后续业务接口必须通过任务卡和契约变更流程新增。

验证链路包括：

- 后端单元测试：统一响应和 AI Mock；
- 后端集成测试：应用上下文、健康检查和 Flyway；
- 前端类型检查；
- 前端生产构建；
- OpenAPI 语法和结构校验；
- 目录所有权检查；
- 基础 E2E：项目状态页可加载；
- 敏感信息扫描。

CI 运行这些检查，但不依赖真实 AI Key 或真实患者数据。

## 9. 配置和安全

- `.env.example` 只包含示例变量；
- `.env` 写入 `.gitignore`；
- 数据库密码、JWT 密钥和 AI Key 不提交；
- 默认使用 Mock AI；
- 日志不记录密码、Token、API Key；
- 示例和测试数据只使用虚构内容；
- Java、Node.js、MySQL 和 UTF-8/Asia/Shanghai 时区要求写入 README。

## 10. 错误处理

后端错误同时使用 HTTP 状态码和统一业务错误码。阶段 0 支持：

- 参数校验错误：HTTP 400 + `VALIDATION_FAILED`；
- 未处理异常：HTTP 500 + `SYSTEM_INTERNAL_ERROR`；
- 成功：HTTP 200 + `SUCCESS`。

前端 Axios Client 统一解析响应，不通过多个候选字段兼容不一致数据。

## 11. 验收标准

阶段 0 完成时必须满足：

1. 后端测试通过，且应用可在 Java 17 下构建；
2. 前端 TypeScript 严格检查和生产构建通过；
3. `GET /api/health` 与 OpenAPI 一致；
4. Flyway 能在测试数据库执行基线迁移；
5. AI 默认使用 Mock，且无真实 Key 也能启动；
6. 四个角色的独占目录齐全且边界清楚；
7. Docker Compose 和 README 提供开发启动方式；
8. 不包含真实医疗业务实现或真实患者数据；
9. 不存在第二套响应、Axios、AI Provider 或分页基础设施；
10. Git 变更不覆盖未经确认的用户文件。

## 12. 后续开发顺序

骨架完成后按文档顺序逐个冻结并实现业务薄切片：

```text
登录和权限
→ 患者、医生、科室
→ 排班
→ 挂号
→ AI 分诊
→ 医生接诊
→ 检查检验与设备
→ 病历生成
→ 处方审核
→ 统计和可视化
→ 完整回归与演示环境
```

每个薄切片都需要独立任务卡、契约、正常和异常测试，不在阶段 0 中提前猜测字段。
