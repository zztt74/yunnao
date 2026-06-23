# STAGE-0-INT：契约、联调与部署骨架

## 1. 基本信息

```text
任务编号：STAGE-0-INT
任务名称：OpenAPI、Mock Server、CI 与 Docker 骨架
任务类型：角色子任务 / 契约 / 基础设施
当前状态：DONE
负责人角色：联调测试与集成
架构审核：整体架构设计 AI
评审人：项目负责人
优先级：P0
目标分支：feature/project-scaffold
关联父任务：STAGE-0
关联同级任务：STAGE-0-BE、STAGE-0-FE、STAGE-0-AI
依赖任务：无
契约版本或提交：AI 协作文档 v2.5；OpenAPI 0.1.0
变更申请编号：CHANGE-001、CHANGE-002、CHANGE-003、CHANGE-004、CHANGE-005
```

## 2. 目标与前置条件

建立单一 OpenAPI 根契约、公共 Schema、API Mock Server、契约验证、集成检查、Docker Compose、CI 和启动说明。

前置条件：

- Stage 0 不允许猜测医疗业务接口；
- 公共响应、分页、时区和错误码规则已确认；
- 各开发角色目录所有权已确认。

## 3. 契约和 Mock

```text
OpenAPI：仓库根 contracts/openapi.yaml，OpenAPI 3.1
公共 Schema：contracts/schemas/common.yaml
枚举索引：仓库根 contracts/enums.md
业务路径：paths: {}，本阶段不定义
API Mock Server：Prism 读取同一 OpenAPI
后端 MockAIProvider：不属于本任务
```

## 4. 部署与集成

```text
Docker 服务：mysql、backend、frontend、可选 prism
CI：后端、前端、契约、Compose、目录所有权映射和敏感信息检查
Postman：只说明从 OpenAPI 派生，不维护第二套字段
初始化账号和演示病例：不适用，登录与业务数据尚未实现
数据库重置：删除 mysql-data volume；仅用于本地开发
日志查看：docker compose logs <service>
```

## 5. 修改边界

```text
允许修改目录：contracts/**、tests/**、postman/**、deploy/**、.github/**、docs/integration/**、docs/changes/**、docker-compose.yml、README.md、.gitignore、.env.example、.dockerignore、redocly.yaml
禁止修改目录：backend 业务实现、frontend/src/**、backend/src/main/resources/prompts/**
公共文件变更申请：阶段 0 父任务授权；治理文档由架构角色维护
```

## 6. 验收与测试

```text
正常流程：OpenAPI、脚手架检查和 Compose 模型校验通过
异常流程：新增业务路径时检查失败；疑似密钥或无所有权路径时检查失败
权限：联调角色不修改业务实现和前端页面
安全：.env 不提交；示例只含占位值
契约：无猜测业务 URL，公共 Schema 可验证
统计口径：不适用
后端测试：由 STAGE-0-BE 执行
前端类型检查和构建：由 STAGE-0-FE 执行
契约测试：npm run validate
E2E：业务 E2E 不适用；技术路由由浏览器验证
目录所有权：node tests/integration/check-ownership.mjs
敏感信息：node tests/integration/scan-secrets.mjs
Compose：docker compose config --quiet
```

## 7. 交付说明

```text
修改文件：contracts/**、tests/**、postman/**、deploy/**、.github/**、docker-compose.yml、README.md、.gitignore、.env.example、.dockerignore、redocly.yaml
测试结果：OpenAPI、脚手架、Compose 和技术路由验证通过
遗留问题：业务契约、业务 E2E、初始化账号和演示病例由后续阶段补充
对其他角色的影响：提供契约根文件、Mock Server、CI 和统一启动方式
是否允许进入联调：是
回滚方式：撤销联调资产；保留已批准变更记录
```

## 8. 状态变更记录

| 时间 | 执行人 | 原状态 | 目标状态 | 原因或证据 |
|---|---|---|---|---|
| 2026-06-22 | 项目负责人 | `DRAFT` | `READY` | 阶段 0 集成范围批准 |
| 2026-06-22 | 联调测试与集成 | `READY` | `IN_PROGRESS` | 契约和部署骨架开始初始化 |
| 2026-06-23 | 联调测试与集成 | `IN_PROGRESS` | `REVIEW` | 契约、Compose 和脚手架验证通过 |
| 2026-06-23 | 项目负责人 | `REVIEW` | `DONE` | OpenAPI、CI、治理检查和部署骨架通过评审 |
