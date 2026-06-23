# STAGE-0-FE：前端骨架

## 1. 基本信息

```text
任务编号：STAGE-0-FE
任务名称：Vue 3 单项目三类角色路由骨架
任务类型：角色子任务
当前状态：DONE
负责人角色：前端开发
评审人：联调测试与集成
优先级：P0
目标分支：feature/project-scaffold
关联父任务：STAGE-0
关联同级任务：STAGE-0-BE、STAGE-0-AI、STAGE-0-INT
依赖任务：STAGE-0-INT 公共响应 Schema
契约版本或提交：AI 协作文档 v2.5；OpenAPI 0.1.0
变更申请编号：CHANGE-001、CHANGE-002、CHANGE-003、CHANGE-004、CHANGE-005
```

## 2. 目标与前置条件

建立单一 Vue 应用、严格 TypeScript、统一 Axios Client、Pinia 认证存储边界和三类角色路由边界。

前置条件：

- 公共响应和分页字段已冻结；
- 不创建长期前端 Mock 字段；
- 不实现登录和医疗业务页面。

## 3. 页面和路由

```text
状态页：/
患者端边界：/patient
医生端边界：/doctor
管理端边界：/admin
404：catch-all
加载、空数据、业务错误：不适用，当前页面无异步业务请求
权限：只建立 Token 存储位置，不实现权限判断
敏感信息：不显示 Token、账号和患者数据
```

## 4. 接口与状态

```text
API Client：frontend/src/api/client.ts 中唯一 Axios 实例
统一响应：code、message、data、traceId
分页：items、page、pageSize、total、totalPages
认证存储：Pinia 内存 + sessionStorage
业务接口：不适用
```

## 5. 修改边界

```text
允许修改目录：frontend/**
禁止修改目录：backend/**、contracts/**、tests/e2e/**、deploy/**
公共文件变更申请：无
```

## 6. 验收与测试

```text
正常流程：首页和三类角色路由可加载
异常流程：未知路由显示 404
权限：不越过前端目录
安全：sessionStorage 只定义 Access Token 和最小角色信息
契约：前端公共类型与 OpenAPI 公共 Schema 一致
统计口径：不适用
前端测试：npm run test
前端类型检查：npm run type-check
前端构建：npm run build
后端测试：不适用，非后端任务
E2E：业务 E2E 不适用；由联调角色验证技术路由
```

## 7. 交付说明

```text
修改文件：frontend/**
测试结果：单元测试、类型检查和生产构建通过；技术路由浏览器验证通过
遗留问题：登录、权限守卫、401/403 和业务页面由后续任务实现
对其他角色的影响：后端和联调可依赖统一 API Client 与公共类型位置
是否允许进入联调：是
回滚方式：撤销 frontend/ 下本任务文件
```

## 8. 状态变更记录

| 时间 | 执行人 | 原状态 | 目标状态 | 原因或证据 |
|---|---|---|---|---|
| 2026-06-22 | 联调测试与集成 | `DRAFT` | `READY` | 公共响应 Schema 确认 |
| 2026-06-22 | 前端开发 | `READY` | `IN_PROGRESS` | 前端工程开始初始化 |
| 2026-06-23 | 前端开发 | `IN_PROGRESS` | `REVIEW` | 测试、类型检查、构建和路由验证通过 |
| 2026-06-23 | 联调测试与集成 | `REVIEW` | `DONE` | 前端骨架、公共 API 边界和路由验证通过 |
