# 枚举与状态字典

本文件是 Stage 0 索引，不替代业务状态机。新增或修改枚举前必须有批准的任务卡或变更记录，并同步 OpenAPI、数据库约束、前后端类型和测试。

| 枚举 | 值 | 说明 | 权威来源 |
|---|---|---|---|
| `UserRole` | `PATIENT`、`DOCTOR`、`ADMIN` | 基础角色 | `V001__base_auth.sql` |
| `AIProviderMode` | `MOCK`、`REMOTE` | AI Provider 配置枚举；环境变量与应用配置统一使用大写值，Stage 0 默认 `MOCK` | `application-ai.yml` |
| `ApiResultCode` | `SUCCESS` 或已登记错误码 | 统一响应结果 | `智慧云脑诊疗平台_AI协作文档_v2/contracts/30_接口数据与错误契约.md` |

业务状态以 `智慧云脑诊疗平台_AI协作文档_v2/product/12_业务流程与状态机.md` 为准，未进入实现任务前不在 OpenAPI 中复制或扩展。
