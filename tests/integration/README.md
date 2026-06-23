# Integration Tests

该目录由联调角色维护。

Stage 0 只验证目录、配置和契约不变量：

```powershell
node tests/integration/verify-scaffold.mjs
node tests/integration/check-ownership.mjs
node tests/integration/scan-secrets.mjs
node tests/integration/check-collaboration-docs.mjs
```

后续业务接口进入 `READY` 后，在此增加需要 MySQL、后端或 Mock Server 的跨模块测试。

`check-ownership.mjs` 验证每个受治理目录只有一个静态负责人，并要求动态任务卡声明负责人角色。它不替代 PR 中按任务卡检查实际越权修改。

`scan-secrets.mjs` 只扫描高置信度密钥格式和禁止的默认密码。正式部署还应在仓库或 CI 平台启用专用 Secret Scanning。

`check-collaboration-docs.mjs` 验证场景化入口、领域目录、角色阅读路径和旧 `shared/00-07` 退役状态。
