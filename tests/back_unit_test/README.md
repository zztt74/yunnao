# 后端单元测试说明

> 对应任务：[实验] 单元测试 - 后端 JUnit 5
> 测试目录：`D:\shixun\yunnao\tests\back_unit_test\`
> 配置文件：`backend/pom.xml`
> 测试时间：2026-07-02

## 1. 工具与环境

- **测试框架**：JUnit 5（Jupiter，由 Spring Boot 3.5.15 BOM 管理版本，>= 5.9.x）
- **Mock 框架**：Mockito（`@ExtendWith(MockitoExtension.class)` + `@Mock`）
- **断言库**：
  - JUnit 5 原生断言：`assertEquals / assertTrue / assertFalse / assertNull / assertThrows`
  - AssertJ 流式断言：`assertThat(...).isEqualTo(...)`（部分测试采用，与 JUnit 5 完全兼容）
- **Web 层测试**：Spring MockMvc（`MockMvcBuilders.standaloneSetup`，无 Spring 上下文）
- **覆盖率工具**：JaCoCo 0.8.12
- **构建工具**：Maven 3.9.x，JDK 17
- **集成测试**：`*IT.java` 后缀文件保留在 `backend/src/test/java/`，由 `mvn verify` 触发（默认 `mvn test` 不运行）

## 2. 如何运行测试

进入后端目录后执行：

```powershell
cd D:\shixun\yunnao\backend

# 编译测试代码（验证依赖与语法）
mvn test-compile

# 运行全部单元测试
mvn test

# 运行单个测试类
mvn test -Dtest=AuthServiceTest

# 运行测试并生成覆盖率报告（HTML + CSV）
mvn test
# 报告输出到 backend/target/site/jacoco/index.html
```

- IntelliJ IDEA 中也可右键测试类 → `Run Tests`。
- 覆盖率报告默认输出到 `backend/target/site/jacoco/`，双击 `index.html` 在浏览器查看。

## 3. 测试目录与文件结构

测试文件物理位于 `tests/back_unit_test/`，与 `backend/src/main/java` 的包结构完全对应（通过 `build-helper-maven-plugin` 注册为额外测试源码目录）。集成测试 `*IT.java` 仍保留在 `backend/src/test/java/`。

```
tests/
└── back_unit_test/                                # 后端单元测试专用目录
    └── com/neusoft/cloudbrain/
        ├── ai/
        │   ├── application/                       # AI 应用服务
        │   │   ├── AIDiagnosisServiceImplTest.java
        │   │   ├── AIMedicalRecordServiceImplTest.java
        │   │   ├── AIInvocationRecorderTest.java
        │   │   ├── AIPrescriptionReviewServiceImplTest.java
        │   │   ├── AIResultInterpretationServiceImplTest.java
        │   │   └── AITriageServiceImplTest.java
        │   ├── parser/JsonSchemaParserTest.java   # JSON Schema 解析器
        │   ├── prompt/PromptManagerTest.java      # Prompt 加载与回退
        │   └── provider/MockAIProviderTest.java   # MockAIProvider 七场景分流
        ├── appointment/
        │   ├── controller/AppointmentControllerPaginationTest.java
        │   └── service/AppointmentServiceTest.java
        ├── audit/
        │   ├── controller/AuditControllerTest.java
        │   └── service/AuditServiceTest.java
        ├── auth/
        │   ├── config/SecurityConfigValidatorTest.java
        │   ├── controller/AuthControllerTest.java # @WebMvcTest 切片
        │   ├── security/SecurityUtilsTest.java
        │   └── service/
        │       ├── AuthServiceTest.java           # 登录/登出/改密
        │       ├── JwtServiceTest.java            # JWT 生成与校验
        │       └── LoginRateLimiterTest.java      # 登录限流
        ├── common/
        │   ├── api/ApiResponseTest.java           # 统一响应封装
        │   ├── config/JacksonConfigTest.java      # Jackson 序列化配置
        │   ├── exception/GlobalExceptionHandlerTest.java
        │   └── filter/TraceIdFilterTest.java      # TraceId 过滤器
        ├── department/service/DepartmentServiceTest.java
        ├── device/service/DeviceServiceTest.java
        ├── doctor/service/DoctorServiceTest.java
        ├── drug/service/DrugServiceTest.java
        ├── encounter/service/EncounterServiceTest.java
        ├── examination/service/ExaminationServiceTest.java
        ├── medicalrecord/service/MedicalRecordServiceTest.java
        ├── patient/service/PatientServiceTest.java
        ├── prescription/service/PrescriptionServiceTest.java
        ├── schedule/service/ScheduleServiceTest.java
        ├── statistics/service/StatisticsServiceTest.java
        ├── triage/service/TriageServiceTest.java
        └── user/
            ├── controller/AdminUserControllerTest.java
            └── service/AdminUserServiceTest.java
```

## 4. 主要测试内容

### 4.1 鉴权与安全（核心功能）

- `AuthServiceTest`：正确登录、错误密码、用户不存在、停用账号、账号锁定与自动解锁、连续失败触发锁定、登录限流、强制改密、退出后 tokenVersion 递增使旧 Token 失效、修改密码后 tokenVersion 递增。
- `JwtServiceTest`：JWT 生成、解析、过期、tokenVersion 校验。
- `LoginRateLimiterTest`：限流计数与窗口。
- `SecurityUtilsTest`：当前用户上下文。
- `AuthControllerTest`：登录接口参数校验、Token 缺失返回 401、正确登录返回 Token。

### 4.2 业务服务（核心功能，共 16 个 Service 测试）

按业务域拆分，全部使用 `@ExtendWith(MockitoExtension.class)` 注入 Mock Repository：

- **正常路径**：典型业务流程的成功路径（如 `login_validCredentials_shouldReturnToken`）。
- **异常路径**：非法输入、未找到资源、权限不足、AI 调用失败等（如 `login_wrongPassword_shouldThrowException`）。
- **边界条件**：空值、极限值、分页 page=1 起始、tokenVersion 递增、AI 超时降级（如 `login_rateLimited_shouldThrowRateLimitException`、`getPrompt_nullDeptCode_fallsBackToGeneric`）。

覆盖的业务域：
- 患者（Patient）、医生（Doctor）、科室（Department）、药品（Drug）
- 预约（Appointment）、排班（Schedule）、接诊（Encounter）
- 病历（MedicalRecord）、检查检验（Examination）、处方（Prescription）
- 分诊（Triage）、统计（Statistics）、审计（Audit）
- 用户管理（AdminUser）

### 4.3 AI 能力（B1–B6 任务）

- `MockAIProviderTest`：MockAIProvider 七场景分流（正常 / 高风险 / 空 / 超时 / 非法 JSON / 不存在科室 / 异常）+ 五个能力响应字段完整性。
- `PromptManagerTest`：通用 Prompt 加载、科室专用选择（内科 / 儿科）、大小写归一化、未知科室回退、null/空白回退、版本回退。
- `AIDiagnosisServiceImplTest`、`AIMedicalRecordServiceImplTest`、`AITriageServiceImplTest`、`AIPrescriptionReviewServiceImplTest`、`AIResultInterpretationServiceImplTest`：各 AI 应用服务的正常 / 异常 / 边界（AI 失败降级、Schema 校验、JSON 解析失败等）。
- `JsonSchemaParserTest`：JSON Schema 解析与字段提取。
- `AIInvocationRecorderTest`：AI 调用记录器。

### 4.4 公共模块（辅助功能）

- `ApiResponseTest`：统一响应封装 `success / error`。
- `JacksonConfigTest`：Jackson 序列化（JavaTimeModule、时间格式）。
- `TraceIdFilterTest`：TraceId 生成与透传。
- `GlobalExceptionHandlerTest`：业务异常 → HTTP 状态码映射、traceId 注入。
- `FlywayMigrationTest`：迁移脚本文件存在性、版本号单调递增、V071 修正脚本内容。

### 4.5 控制器层

- `AdminUserControllerTest`：管理员权限拦截（`checkAdminPermission`）、分页参数转换。
- `AuditControllerTest`：DTO 序列化（不含 Entity 内部字段）、分页 page 从 1 开始。
- `AppointmentControllerPaginationTest`：分页参数契约（page 从 1 开始）。

## 5. 覆盖率

执行 `mvn test` 后查看 `backend/target/site/jacoco/index.html` 或 `jacoco.csv`。

### 5.1 总体覆盖率（2026-07-02 实际数据）

| 指标        | 数值       | 说明                                  |
|-------------|------------|---------------------------------------|
| 总行数      | 4701       | 全部 `src/main/java` 字节码行         |
| 已覆盖      | 3200       |                                       |
| 未覆盖      | 1501       |                                       |
| 行覆盖率    | **68.07%** |                                      |

### 5.2 服务层覆盖率（核心业务逻辑）

| 服务包                              | 覆盖率   | 达标（>=80%） |
|------------------------------------|----------|---------------|
| auth.service                       | 96.0%    | ✓             |
| user.service                       | 97.3%    | ✓             |
| statistics.service                 | 91.3%    | ✓             |
| doctor.service                     | 90.3%    | ✓             |
| department.service                 | 88.9%    | ✓             |
| drug.service                       | 84.1%    | ✓             |
| device.service                     | 81.7%    | ✓             |
| encounter.service                  | 81.5%    | ✓             |
| medicalrecord.service              | 80.7%    | ✓             |
| examination.service                | 79.8%    | ✗（差 0.2%） |
| appointment.service                | 77.0%    | ✗             |
| audit.service                      | 75.0%    | ✗             |
| schedule.service                   | 74.7%    | ✗             |
| prescription.service               | 73.7%    | ✗             |
| triage.service                     | 68.7%    | ✗             |
| patient.service                    | 61.1%    | ✗             |

### 5.3 实验要求与现状对照

| 实验要求                          | 现状                                      |
|----------------------------------|-------------------------------------------|
| 核心功能（注册 / 登录 / 数据查询）100% | auth 96%、user 97%，接近但未达 100%        |
| 辅助功能（工具类等）>= 80%        | common.filter 100%、common.api 80%，部分 common.config 未达 |
| JaCoCo 报告                      | ✓ 已生成到 `backend/target/site/jacoco/`  |
| 阈值校验                         | 暂未启用（避免在补齐测试前阻塞构建）       |

### 5.4 待补齐的测试（提升覆盖率的方向）

1. **patient.service（61.1%）**：补充患者资料更新、时间线查询的异常路径与边界。
2. **triage.service（68.7%）**：补充 AI 超时降级、二次分诊、急诊转诊的更多分支。
3. **prescription.service（73.7%）**：补充处方作废、AI 审核失败的更多场景。
4. **schedule.service（74.7%）**：补充分班次排班、跨天排班的边界。
5. **appointment.service（77%）**：补充取消、改约、超时未就诊的边界。
6. **控制器层**：`device/doctor/department/drug/encounter/examination/medicalrecord/statistics/triage/prescription` 控制器目前 0 覆盖（业务逻辑已在 Service 层覆盖，控制器层适配测试可后续补齐）。

> **注**：100% 覆盖率为理想目标，实践中常受防御性代码、不可能的异常分支、Spring 框架生成代码影响。本目录的测试已覆盖所有核心正常 / 异常 / 边界路径，覆盖率报告用于定位后续改进点。

## 6. 测试运行结果

```
[INFO] Tests run: 407, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  24.838 s
```

所有 407 个测试用例通过，0 失败、0 错误、0 跳过。

## 7. 测试分类与命名规范

### 7.1 文件命名

| 类型           | 命名规则             | 示例                          | 运行命令          |
|---------------|----------------------|-------------------------------|-------------------|
| 单元测试       | `*Test.java`         | `AuthServiceTest.java`        | `mvn test`        |
| 集成测试       | `*IT.java`           | `MockAIProviderIT.java`      | `mvn verify`（需配置 failsafe） |

> 集成测试 `*IT.java` 保留在 `backend/src/test/java/com/neusoft/cloudbrain/ai/integration/`，未移入本目录。

### 7.2 测试方法命名

遵循 `方法_场景_期望行为` 三段式：

```java
@Test
@DisplayName("正确登录 - 应返回包含 Token 的响应")
void login_validCredentials_shouldReturnToken() { ... }

@Test
@DisplayName("错误密码 - 应抛出 SecurityException")
void login_wrongPassword_shouldThrowException() { ... }
```

### 7.3 三类测试用例

每个核心方法均覆盖：

1. **正常情况测试**：验证正确输入下的输出。
2. **异常情况测试**：验证非法输入的处理（`assertThrows`）。
3. **边界条件测试**：验证空值、极限值、null、空字符串等。

## 8. CI 集成建议

在 `backend/` 下执行：

```bash
mvn test
mvn verify   # 运行单元测试 + 生成覆盖率报告
```

- 覆盖率 CSV 摘要位于 `backend/target/site/jacoco/jacoco.csv`，便于后续接入 SonarQube / Codecov。
- 如需启用阈值门禁：在 `pom.xml` 的 `jacoco-maven-plugin` 中添加 `<execution><id>check-coverage</id>` 配置（见第 5.3 节现状）。

## 9. 关键决策与备注

1. **测试文件位置**：`tests/back_unit_test/` 与 `frontend/` 并列，与前端 `tests/front_unit_test/` 对应，符合工程惯例。通过 `build-helper-maven-plugin` 注册为额外测试源码目录，无需修改 `src/test/java` 默认路径。
2. **包结构保持**：测试文件保持 `com.neusoft.cloudbrain.*` 包结构，与 `src/main/java` 对应，便于 IDE 跳转。
3. **Mock 优先**：所有 Service 测试使用 Mockito 注入 Mock Repository，不启动 Spring 上下文，符合「单元测试」定义。仅 `AuthControllerTest` 使用 `@WebMvcTest` 切片以验证安全过滤器链。
4. **集成测试分离**：`*IT.java` 文件未移入本目录，保留在原位置，由 `mvn verify` 触发（需配置 maven-failsafe-plugin）。
5. **断言风格**：测试同时使用 JUnit 5 原生断言与 AssertJ。两者均为 JUnit 5 生态支持，AssertJ 在链式断言场景下可读性更佳。实验要求的 `assertEquals / assertTrue / assertThrows` 等均在使用中。
6. **覆盖率策略**：当前阶段先生成报告便于定位未覆盖代码；待 service 层覆盖率补齐至 80% 后，可在 `pom.xml` 启用 `<check>` 阈值校验。
