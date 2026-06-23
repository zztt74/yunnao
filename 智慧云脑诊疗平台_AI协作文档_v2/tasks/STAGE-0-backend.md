# STAGE-0-BE：后端骨架

## 1. 基本信息

```text
任务编号：STAGE-0-BE
任务名称：Spring Boot 模块化单体骨架
任务类型：角色子任务
当前状态：DONE
负责人角色：后端开发
评审人：联调测试与集成
优先级：P0
目标分支：feature/project-scaffold
关联父任务：STAGE-0
关联同级任务：STAGE-0-FE、STAGE-0-AI、STAGE-0-INT
依赖任务：STAGE-0-INT 公共响应和数据库约定
契约版本或提交：AI 协作文档 v2.5；OpenAPI 0.1.0
变更申请编号：CHANGE-001、CHANGE-002、CHANGE-003、CHANGE-004、CHANGE-005
```

## 2. 目标与前置条件

建立 Java 17、Spring Boot 3、Maven、Flyway 和模块包结构，使后端角色可以按业务薄切片继续开发。

前置条件：

- 公共响应、分页、时区和数据库规则已冻结；
- Maven 依赖由后端角色负责；
- 不实现登录、JWT 或医疗业务接口。

## 3. 交付范围

```text
应用启动类
ApiResponse 和 PageResponse
Spring Security 无状态技术边界
业务模块 package-info
application.yml
Flyway V001 基础认证表
后端单元测试入口
```

## 4. 接口、页面与 AI

```text
业务接口：不适用，不创建 Controller
页面：不适用
AI：只允许依赖 AI Service 接口，不实现 Provider
```

## 5. 数据库

```text
新增表：user_account、role、user_role
迁移脚本：backend/src/main/resources/db/migration/V001__base_auth.sql
字符集：utf8mb4
排序规则：utf8mb4_0900_ai_ci
审计和版本字段：按共享规则
```

## 6. 修改边界

```text
允许修改目录：backend/**，排除 backend/**/ai/**、backend/src/main/resources/prompts/**、backend/src/main/resources/application-ai.yml、backend/src/test/**/ai/**
禁止修改目录：frontend/**、contracts/**、deploy/**、.github/**
公共文件变更申请：无
```

## 7. 验收与测试

```text
正常流程：JDK 17 下测试和打包成功
异常流程：缺少 DB_PASSWORD 时配置解析不得使用源码默认密码
权限：不越过 AI、前端和联调目录
安全：无源码密钥和真实账号
契约：响应和分页类型与 OpenAPI 公共 Schema 一致
统计口径：不适用
后端测试：mvn verify
前端检查：不适用，非前端任务
契约测试：由 STAGE-0-INT 执行
E2E：不适用，未实现业务接口
```

## 8. 交付说明

```text
修改文件：backend/ 下非 AI 范围
测试结果：JDK 17 下 mvn verify 通过，2 个测试中后端公共响应测试通过
遗留问题：具体安全、业务 Service、Controller 和集成测试由后续业务任务实现
对其他角色的影响：提供 Maven 工程、模块包和数据库迁移入口
是否允许进入联调：是
回滚方式：撤销 backend/ 下本任务文件
```

## 9. 状态变更记录

| 时间 | 执行人 | 原状态 | 目标状态 | 原因或证据 |
|---|---|---|---|---|
| 2026-06-22 | 联调测试与集成 | `DRAFT` | `READY` | 范围和公共约定确认 |
| 2026-06-22 | 后端开发 | `READY` | `IN_PROGRESS` | Maven 工程开始初始化 |
| 2026-06-23 | 后端开发 | `IN_PROGRESS` | `REVIEW` | JDK 17 构建和测试通过 |
| 2026-06-23 | 联调测试与集成 | `REVIEW` | `DONE` | 后端骨架、迁移入口和测试通过 |
