# Stage 0 Project Scaffold Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the runnable Stage 0 scaffold defined by the AI collaboration documents v2.2 without implementing medical business features.

**Architecture:** A Vue 3 single-page frontend calls a Spring Boot 3 modular monolith. MySQL 8 is initialized through Flyway, AI capabilities are isolated behind a provider boundary with Mock mode enabled by default, and OpenAPI/CI/Docker assets are owned by integration.

**Tech Stack:** Java 17 target, Spring Boot 3, Maven, Vue 3, TypeScript, Vite, Element Plus, Pinia, Axios, ECharts, MySQL 8, OpenAPI 3.1, Docker Compose.

---

### Task 1: Collaboration task records

**Files:**
- Create: `智慧云脑诊疗平台_AI协作文档_v2/tasks/STAGE-0-parent.md`
- Create: `智慧云脑诊疗平台_AI协作文档_v2/tasks/STAGE-0-backend.md`
- Create: `智慧云脑诊疗平台_AI协作文档_v2/tasks/STAGE-0-frontend.md`
- Create: `智慧云脑诊疗平台_AI协作文档_v2/tasks/STAGE-0-ai.md`
- Create: `智慧云脑诊疗平台_AI协作文档_v2/tasks/STAGE-0-integration.md`

- [x] Create one READY parent task and four READY role tasks.
- [x] Record allowed directories, dependencies, tests, and Stage 0 non-goals.
- [x] Verify every role task links to the parent and names the feature branch.

### Task 2: Backend and AI red tests

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/test/java/com/neusoft/cloudbrain/common/api/ApiResponseTest.java`
- Create: `backend/src/test/java/com/neusoft/cloudbrain/ai/provider/MockAIProviderTest.java`

- [x] Add Java 17 and Spring Boot 3 build configuration.
- [x] Write a test for the frozen response envelope.
- [x] Write a test that Mock AI is explicitly marked as mock and never claims a formal diagnosis.
- [x] Run `mvn test` and confirm failure because production classes do not exist.

### Task 3: Backend and AI minimal implementation

**Files:**
- Create: `backend/src/main/java/com/neusoft/cloudbrain/CloudBrainApplication.java`
- Create: `backend/src/main/java/com/neusoft/cloudbrain/common/api/ApiResponse.java`
- Create: `backend/src/main/java/com/neusoft/cloudbrain/common/api/PageResponse.java`
- Create: `backend/src/main/java/com/neusoft/cloudbrain/common/config/SecurityConfig.java`
- Create: `backend/src/main/java/com/neusoft/cloudbrain/ai/**`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/application-ai.yml`
- Create: `backend/src/main/resources/db/migration/V001__base_auth.sql`
- Create: module `package-info.java` files under documented backend modules.

- [x] Implement only the code required by Stage 0 tests.
- [x] Configure MySQL/Flyway through environment variables and import AI configuration.
- [x] Create a minimal base authentication table with v2.2 audit/security fields.
- [x] Run `mvn test` and `mvn package -DskipTests` successfully.

### Task 4: Frontend red test and implementation

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/src/**`
- Create: `frontend/vite.config.ts`
- Create: `frontend/tsconfig*.json`
- Create: `frontend/src/types/api.spec.ts`

- [x] Write a failing test for unified API response parsing.
- [x] Install dependencies and confirm the test fails because the parser is missing.
- [x] Implement one Vue application with `/patient`, `/doctor`, and `/admin` route boundaries.
- [x] Add one Axios client, Pinia authentication shell, strict TypeScript, and infrastructure-only landing pages.
- [x] Run unit tests, type check, and production build.

### Task 5: Contracts, integration, Docker, and CI

**Files:**
- Create: `contracts/openapi.yaml`
- Create: `contracts/schemas/common.yaml`
- Create: `contracts/enums.md`
- Create: `tests/contract/validate-openapi.mjs`
- Create: `tests/integration/README.md`
- Create: `tests/e2e/README.md`
- Create: `postman/README.md`
- Create: `deploy/backend/Dockerfile`
- Create: `deploy/frontend/Dockerfile`
- Create: `deploy/frontend/nginx.conf`
- Create: `docker-compose.yml`
- Create: `.github/workflows/ci.yml`
- Create: `.env.example`
- Modify: `.gitignore`
- Modify: `README.md`

- [x] Define an OpenAPI 3.1 root document with no guessed medical endpoints.
- [x] Add reusable response/page schemas and documented enums.
- [x] Provide an OpenAPI validation command and API Mock Server through Prism.
- [x] Add MySQL, backend, frontend-nginx, and optional Prism services.
- [x] Add CI checks for backend, frontend, contract, directory ownership, and secret patterns.
- [x] Document local and Docker workflows.

### Task 6: Full verification

- [x] Run backend tests and package with JDK 17 compiling `--release 17`.
- [x] Run frontend tests, strict type check, and production build.
- [x] Run OpenAPI validation and documentation consistency checks.
- [x] Run `docker compose config`.
- [x] Review `git diff --check`, tracked files, ownership boundaries, and secret patterns.
- [x] Record any environment limitation without claiming unavailable runtime verification.
