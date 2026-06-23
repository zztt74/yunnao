# STAGE-0-AI：AI 能力骨架

## 1. 基本信息

```text
任务编号：STAGE-0-AI
任务名称：AI Service 与 MockAIProvider 骨架
任务类型：角色子任务
当前状态：DONE
负责人角色：AI 能力集成
评审人：后端开发、联调测试与集成
优先级：P0
目标分支：feature/project-scaffold
关联父任务：STAGE-0
关联同级任务：STAGE-0-BE、STAGE-0-FE、STAGE-0-INT
依赖任务：STAGE-0-BE Maven 工程
契约版本或提交：AI 协作文档 v2.5
变更申请编号：CHANGE-001、CHANGE-002、CHANGE-003、CHANGE-004、CHANGE-005
```

## 2. 目标与前置条件

建立 AI Service、Provider、配置和 Prompt 目录边界，使后续 AI 任务可以在不侵入传统业务模块的情况下实现。

前置条件：

- Maven 工程可编译；
- AI 结果只能作为建议或草稿；
- Stage 0 不实现真实模型调用和具体能力 Schema。

## 3. AI 能力

```text
AI 接口：AITriageService、AIDiagnosisService、AIMedicalRecordService、AIPrescriptionReviewService、AIResultInterpretationService
Provider：AIProvider、MockAIProvider
Mock 场景：安全的基础可用响应，明确 mock=true 和“仅供辅助参考”
真实 Provider：不适用，只保留扩展边界
结构化 Schema：不适用，由对应业务任务冻结
确定性规则输入：不适用，由处方任务冻结
降级方式：AI_MODE=MOCK
```

## 4. 接口、数据和页面

```text
HTTP 接口：不适用
数据库表：不适用，不创建 AIInvocation
页面：不适用
Prompt：只建立目录说明，不编写具体业务 Prompt
```

## 5. 修改边界

```text
允许修改目录：backend/src/main/java/**/ai/**、backend/src/main/resources/prompts/**、backend/src/main/resources/application-ai.yml、backend/src/test/**/ai/**
禁止修改目录：传统业务模块、frontend/**、contracts/**、数据库迁移
公共文件变更申请：Maven 依赖由后端任务统一处理
```

## 6. 验收与测试

```text
正常流程：Mock Provider 返回明确标识的辅助响应
异常流程：具体高风险、超时、非法 JSON 等场景不适用，等待能力任务冻结 Schema
权限：不修改传统业务模块
安全：不保存 API Key，不发送真实患者数据
契约：不创建未经批准的 AI HTTP 契约
统计口径：不适用
后端测试：MockAIProviderTest
前端检查：不适用
契约测试：不适用，无 AI HTTP 接口
E2E：不适用，未实现业务能力
```

## 7. 交付说明

```text
修改文件：backend/src/main/java/**/ai/**、backend/src/main/resources/prompts/**、backend/src/main/resources/application-ai.yml、backend/src/test/**/ai/**
测试结果：Mock Provider 单元测试通过
遗留问题：具体 Schema、Prompt、异常 Mock、调用记录和真实 Provider 由后续 AI 任务实现
对其他角色的影响：后端只依赖 AI Service 接口，不直接依赖 Provider
是否允许进入联调：是，作为后续能力开发边界
回滚方式：撤销 AI 专属目录下本任务文件
```

## 8. 状态变更记录

| 时间 | 执行人 | 原状态 | 目标状态 | 原因或证据 |
|---|---|---|---|---|
| 2026-06-22 | 联调测试与集成 | `DRAFT` | `READY` | Maven 工程和 AI 边界确认 |
| 2026-06-22 | AI 能力集成 | `READY` | `IN_PROGRESS` | AI Provider 骨架开始初始化 |
| 2026-06-23 | AI 能力集成 | `IN_PROGRESS` | `REVIEW` | Mock Provider 测试通过 |
| 2026-06-23 | 后端开发、联调测试与集成 | `REVIEW` | `DONE` | AI Service、Provider 和配置扩展边界通过评审 |
