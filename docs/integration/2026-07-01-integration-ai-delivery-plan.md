# 联调 AI 剩余交付任务书

日期：2026-07-01  
分支：`codex/integration`  
目标：作为唯一联调负责人，统筹后端 AI、前端 AI 的交付，完成契约核对、AI 真实能力验收、端到端测试、Docker 验收和最终提交准备。

协作角色限定：本项目剩余工作只划分为后端 AI、前端 AI、联调 AI。不存在单独的契约 AI、测试 AI、AI 能力 AI；这些工作全部归入联调 AI。

## 一、联调 AI 总职责

联调 AI 负责：

- 维护 `codex/integration` 分支。
- 汇总后端 AI 和前端 AI 的接口变更。
- 检查前后端契约一致性。
- 管理 OpenAPI 待同步事项。
- 管理 DeepSeek/HTTP Provider 真实 AI 能力验收。
- 维护 Docker Compose 联调环境。
- 维护种子数据和真实 API 冒烟脚本。
- 跑完整端到端业务闭环。
- 输出最终验收报告和提交建议。

联调 AI 不直接负责：

- 大量后端业务接口实现；这属于后端 AI。
- 大量前端页面开发和 UI 接入；这属于前端 AI。

但联调 AI 可以做小范围联调修复，例如字段映射、脚本修正、配置修正、文档补充。

## 二、联调 AI 对后端 AI 的验收清单

后端 AI 每交付一个模块，联调 AI 必须检查：

1. 接口是否真实存在。
2. 请求字段和响应字段是否与前端需求一致。
3. 权限是否符合角色边界。
4. 状态机是否拒绝非法流转。
5. 错误码是否可被前端展示。
6. 是否需要种子数据。
7. 是否需要 OpenAPI 同步。

重点验收后端模块：

- 医生本人资料更新。
- 设备档案创建/更新。
- 管理员用户管理。
- 管理员全量分诊记录。
- AI 调用日志分页列表。
- 患者总数统计。
- 患者管理员分页查询。

## 三、联调 AI 对前端 AI 的验收清单

前端 AI 每交付一个页面或 API client，联调 AI 必须检查：

1. 是否调用真实后端接口。
2. 是否存在运行时 mock 或假成功。
3. 页面加载态、空状态、错误提示是否可用。
4. 字段映射是否正确。
5. 刷新页面后数据是否仍然来自后端。
6. 权限错误是否能正确提示或跳转。

重点验收前端页面：

- 医生个人资料页。
- 管理端用户管理页。
- 管理端设备管理页。
- 管理端分诊记录页。
- 管理端 AI 日志页。
- 管理端统计页。
- 患者预约、病历、检查、处方、时间线。
- 医生队列、接诊、诊断、检查、病历、处方。

## 四、契约一致性任务

联调 AI 负责维护契约问题清单，不单独分派给第四个角色。

必须核对：

- OpenAPI 是否覆盖当前真实后端接口。
- 前端 types 是否与后端 DTO 一致。
- 分页参数是否统一。
- 错误码是否统一。
- 状态枚举是否统一。
- 权限矩阵是否清楚。

当前必须同步的 OpenAPI 缺口：

- 检查检验。
- 病历。
- 处方。
- 设备。
- 统计。
- 审计。
- AI 诊断。
- AI 调用日志。
- 管理端用户管理。
- 管理端分诊列表。

验收标准：

- OpenAPI 校验通过，或 warning 已记录。
- 前端 API client 能根据契约核对字段。
- 后端新增接口均进入联调清单。

## 五、AI 真实能力验收任务

联调 AI 负责 DeepSeek/HTTP Provider 的真实能力验收，不单独分派给第四个角色。

必须验收五类能力：

1. 智能分诊。
2. 辅助诊断。
3. 病历生成。
4. 处方审核。
5. 检查检验解读。

每类能力至少验证：

- 正常输入。
- 缺字段或短输入。
- 模型返回非 JSON。
- 模型超时。
- 模型返回业务不可信内容。

验收标准：

- 有结构化输出。
- 有失败降级。
- 有审计记录。
- 不泄露 AI Key。
- 不把 AI 建议当作医生最终诊断。

## 六、完整端到端验收任务

联调 AI 必须跑通至少一条真实业务闭环：

1. 患者注册或使用种子患者。
2. 患者预约挂号。
3. 医生查看队列。
4. 医生开始接诊。
5. 医生查看患者详情。
6. 医生下诊断。
7. 医生开检查。
8. 设备使用开始/结束。
9. 检查结果录入与审核。
10. 医生生成并确认病历。
11. 医生创建并确认处方。
12. 医生完成就诊。
13. 患者查看病历、检查、处方、时间线。
14. 管理员查看统计和日志。

验收标准：

- 至少一条完整流程成功。
- 每一步记录账号、页面、接口或脚本输出。
- 失败步骤必须记录责任方：后端 AI、前端 AI 或联调 AI。

## 七、权限验收任务

联调 AI 必须验证：

- 患者不能看其他患者数据。
- 医生只能看关联接诊患者的数据。
- 非管理员不能访问管理端接口。
- 禁用用户不能登录。
- 初始管理员首次登录必须改密码。

验收标准：

- 权限错误码符合约定。
- 前端展示合理错误。
- 未授权访问不能泄露数据。

## 八、Docker 和脚本任务

联调 AI 负责维护：

- `docker-compose.yml`
- `.env.example`
- `tests/integration/seed-real-clinic-data.mjs`
- `tests/integration/smoke-real-api.mjs`
- `tests/integration/scan-secrets.mjs`
- `tests/integration/README.md`
- `docs/integration/**`

必须验证：

```powershell
docker compose up -d --build
docker compose ps
node tests/integration/seed-real-clinic-data.mjs
node tests/integration/smoke-real-api.mjs
node tests/integration/scan-secrets.mjs
```

## 九、最终提交前检查

联调 AI 最终必须运行：

```powershell
git status --short --branch
cd frontend
npm run type-check
npm run test
npm run build
cd ..
cd backend
mvn test
cd ..
node tests/integration/scan-secrets.mjs
node tests/integration/seed-real-clinic-data.mjs
node tests/integration/smoke-real-api.mjs
git diff --check
```

如某条命令不能运行，必须写明原因。

## 十、最终验收报告

联调 AI 最终输出一份验收报告，必须包含：

1. 已实现模块。
2. 未实现模块。
3. 已知风险。
4. 账号和种子数据说明。
5. Docker 启动步骤。
6. 测试命令和结果。
7. OpenAPI 和前后端契约状态。
8. AI 能力验收状态。
9. 是否可以提交。

## 十一、最终可提交标准

项目达到以下条件后，才建议提交：

- 没有运行时 mock。
- 三端核心页面无“接口未提供”的核心阻塞。
- 至少一条完整真实业务闭环跑通。
- Docker 本地可启动。
- OpenAPI 与主要后端接口一致，或差异已列入验收报告。
- AI Key 不入库、不入日志明文、不入 Git。
- 构建、测试、冒烟、敏感信息扫描通过。
- 已知未完成项写入验收报告。
