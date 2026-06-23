# 智慧云脑诊疗平台

四人协作项目的 Stage 0 技术骨架。当前只提供前端、后端、AI Provider、契约、测试和部署边界，不包含未经任务卡批准的医疗业务实现。

## 技术栈

- 前端：Vue 3、TypeScript、Vite、Pinia、Axios、Element Plus、ECharts
- 后端：Java 17、Spring Boot 3、Maven、Spring Security、JPA、Flyway
- 数据库：MySQL 8
- AI：`AIProvider` 边界，默认 Mock 模式
- 联调：OpenAPI 3.1、Redocly、Prism、Docker Compose、GitHub Actions

## 目录

```text
backend/       后端与 AI Provider
frontend/      单一 Vue 应用及角色路由边界
contracts/     唯一 OpenAPI 契约与公共 Schema
tests/         契约、集成和 E2E 测试
deploy/        容器构建与 Nginx 配置
postman/       OpenAPI 派生集合说明
智慧云脑诊疗平台_AI协作文档_v2/  v2.5 协作基线与任务卡
```

## 本地开发

### 后端

要求 JDK 17、Maven 和可用的 MySQL 8。必须设置数据库密码：

```powershell
$env:JAVA_HOME='C:\Users\cdk\AppData\Local\Programs\Temurin\jdk-17.0.19+10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:DB_PASSWORD='your-local-password'
cd backend
mvn spring-boot:run
```

### 前端

要求 Node.js 22.12 或更高版本：

```powershell
cd frontend
npm install
npm run dev
```

默认通过 Vite 将 `/api` 代理到 `http://localhost:8080`。

### 契约

```powershell
cd tests/contract
npm install
npm run validate
```

## Docker Compose

复制 `.env.example` 为 `.env` 并替换本地密码，然后启动：

```powershell
docker compose up --build
```

- 前端：<http://localhost:8088>
- 后端健康检查：<http://localhost:8080/actuator/health>
- Swagger UI：<http://localhost:8080/swagger-ui.html>

可选启动 Prism API Mock Server：

```powershell
docker compose --profile mock-api up prism
```

Mock Server 地址为 <http://localhost:4010>。

## 验证

```powershell
cd backend
mvn test

cd ..\frontend
npm run test
npm run type-check
npm run build

cd ..\tests\contract
npm run validate

cd ..\..
node tests/integration/verify-scaffold.mjs
docker compose config
```

业务开发前先阅读 `智慧云脑诊疗平台_AI协作文档_v2/README.md` 和对应角色任务书，只修改任务卡允许的目录。
