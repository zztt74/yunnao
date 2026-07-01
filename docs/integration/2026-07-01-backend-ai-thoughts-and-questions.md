# 后端 AI 交付：想法、建议与待确认问题（B1–B8 复盘）

日期：2026-07-01
分支：`codex/integration`
对应任务书：`2026-07-01-backend-ai-delivery-plan.md`
关联文档：`2026-07-01-backend-ai-notes.md`（实施决策）、`2026-07-01-backend-ai-delivery-report.md`（交付说明）

本文档不重复实施决策与交付清单，只记录在实施 B1–B8 过程中观察到的**架构一致性问题、安全隐患、改进建议和需要讨论确认的问题**。所有内容均为建议性，不改变已交付代码；标注了优先级（P1 高 / P2 中 / P3 低）和是否在本次任务范围内。

---

## 一、架构一致性问题

### 1. 权限校验两种模式并存（P2，非本任务范围）

**现象**：项目里权限校验存在两种风格，没有统一规范：
- 类级 `@PreAuthorize("hasRole('ADMIN')")`：`AuditController`、`StatisticsController`
- 方法内 `SecurityUtils.getCurrentUser()` + 角色判断 + `throw new BusinessException("PERMISSION_DENIED", "...", 403)`：`DoctorController`、`PatientController`、本次 B2/B3/B4/B7 新接口

本次新接口按所属 Controller 的既有风格延续，导致同一个项目里混用。

**建议**：
- 简单的"整类限角色"场景统一用 `@PreAuthorize`，由 Spring Security 在过滤器层拦截，更早失败、更少代码。
- 需要细粒度判断（如 B1 按 userId 定位本人档案、B7 管理员可查全量但患者只能查自己）才用方法内校验。
- 建议定一份项目级权限规范文档，后续接口统一遵循。

### 2. 分页参数命名不统一（P2，B8 部分缓解）

**现象**：现有接口分页参数命名不统一：
- `DoctorController`、`ExaminationController`、新接口用 `pageSize`
- `DeviceController`、`AuditController`（部分）用 `size`

B8 通过 `PageUtils.resolvePageSize` 让新接口兼容两者，但**只是兼容，没有收敛**。老接口仍然各自为政。

**建议**：
- 由契约组定一个主参数名（建议 `pageSize`，语义更明确），新老接口逐步统一。
- 收敛时可在 Controller 同时接收 `pageSize` 和 `size`，内部统一用 `pageSize`，老参数保留一段时间标 `@Deprecated`。
- 最终目标是契约层只有一个参数名。

### 3. 通用错误码散落，缺 CommonErrorCode（P3，非本任务范围）

**现象**：各模块有 `XxxErrorCode` 枚举（如 `DeviceErrorCode`、`UserErrorCode`），但通用错误如"无权限"直接 `throw new BusinessException("PERMISSION_DENIED", "...", 403)` 散落在多个 Controller 的 `checkAdminPermission()` 里。字符串硬编码，没有集中定义。

**建议**：建一个 `common/exception/CommonErrorCode` 枚举，集中 `PERMISSION_DENIED`、`VALIDATION_FAILED`、`RESOURCE_NOT_FOUND` 等通用错误码，避免字符串散落和拼写不一致。

### 4. traceId 重复获取，建议拦截器统一注入（P3，非本任务范围）

**现象**：每个 Controller 方法都写 `(String) httpRequest.getAttribute("traceId")`，重复且容易遗漏。`ApiResponse.success` 还强制要求传 traceId。

**建议**：
- 方案 A：用 `@ControllerAdvice` + `ResponseBodyAdvice` 在响应写出前统一包装 `ApiResponse` 并注入 traceId，Controller 直接返回 `T` 或 `PageResponse<T>`。
- 方案 B：提供一个 `TraceContext.currentTraceId()` 静态方法（基于 ThreadLocal，由过滤器设置），Controller 调用 `ApiResponse.success(data)` 即可。
- 这是较大的重构，建议单独立项，不在本次范围。

---

## 二、安全隐患（建议尽快确认）

### 5. JWT 是否校验 tokenVersion？B3 重置密码安全性依赖于此（P1，需确认）

**现象**：B3 重置密码逻辑里做了 `tokenVersion++`，意图是让用户已签发的 JWT 失效，强制重新登录。**但这依赖 JWT 校验过滤器真的检查 tokenVersion**。

**问题**：如果 JWT 校验只验签名和过期时间，不查库比对 tokenVersion，那么重置密码 / 锁定 / 禁用后，旧 token 在过期前仍然有效，这与 B3 的安全语义（"锁定后立即不能操作"）矛盾。

**需要确认**：
- `JwtAuthenticationFilter`（或等价过滤器）在每次请求时是否查 `UserAccount.tokenVersion` 与 token 里的 version 比对？
- 如果没有，B3 的"锁定/禁用立即生效"只对**下一次登录**有效，已登录会话在 token 过期前仍可操作。
- 如果没做，建议补上（性能上可考虑缓存 tokenVersion + 短 TTL）。

这是本次交付里我认为**最需要立刻确认**的安全点。

### 6. 设备 code 唯一校验存在并发竞态（P2，DB 约束兜底）

**现象**：B2 创建设备时用 `existsByCode(code)` 检查唯一性，但"先查后插"在并发下有竞态——两个请求同时查到不存在，同时插入。

**现状**：`device.code` 有 DB unique 约束兜底，第二个插入会抛 `DataIntegrityViolationException`，但这个异常默认会被全局异常处理器转成 500，而不是业务期望的 409。

**建议**：
- 在 `DeviceService.createDevice` 捕获 `DataIntegrityViolationException`，转抛 `DEVICE_CODE_DUPLICATED`（409）。
- 或者依赖 DB 约束 + 异常转换，去掉 Service 层 `existsByCode` 预检查（但预检查能给出更友好的错误信息，建议保留预检查 + 异常转换双保险）。
- 同样的模式适用于其他"先查后插"场景（如 B3 创建用户 `existsByUsername`）。

### 7. AdminUserResponse 暴露 tokenVersion 给前端（P3，需讨论）

**现象**：B3 的 `AdminUserResponse` 返回了 `tokenVersion` 字段。这是内部安全机制字段，前端 UI 通常不需要展示。

**建议**：
- 评估前端是否真的需要。如果只是"展示用户信息"，可以去掉 `tokenVersion`。
- 如果前端需要判断"用户是否需要重新登录"，可保留，但要确认不会因此泄露内部机制给非管理员。
- 优先级低，当前返回值不构成漏洞，只是信息最小化原则的建议。

---

## 三、数据模型问题

### 8. UserAccount 缺 realName/phone/email，是真实设计缺陷（P1，已收尾）

**原现象**：B3 任务书要求"更新姓名/手机/邮箱"，但 `user_account` 表只有 username/passwordHash/状态字段，没有任何联系方式或真实姓名字段。医生有 `Doctor` 表、患者有 `Patient` 表存这些，但**纯管理员账号无处存放**。

**当前状态**：已按联调确认采用方案 A，通过 `V073__user_account_profile_fields.sql` 扩展 `user_account.real_name/phone/email`。`AdminUserCreateRequest`、`AdminUserUpdateRequest`、`AdminUserResponse`、`AdminUserService` 和前端管理端表单均已同步，B3 用户资料字段已可持久化。

**结论**：Q2 已关闭，不再是后端任务书阻塞。

### 9. Doctor 与 DoctorProfile 分两表但总是一起更新（P3，可讨论）

**现象**：`Doctor`（基础信息）和 `DoctorProfile`（扩展信息：学历/年限/简介）是 1:1 关系，B1 更新时两张表都要改。日常使用中几乎没有"只改 Doctor 不改 Profile"的场景。

**建议**：可讨论是否合并为一张表，减少 JOIN 和事务复杂度。但这是历史设计，重构成本与收益需评估，不在本次范围。仅作记录。

### 10. DashboardSummary 用 record 加字段是前向不兼容变更（P2，已告知前端）

**现象**：B6 在 `DashboardSummary` record 加了 `totalPatientCount`。record 是不可变且字段固定，加字段后：
- 旧前端反序列化会忽略新字段（JSON 层面兼容）。
- 但如果前端用了强类型映射（如 TypeScript interface），需手动加字段，否则编译报错或显示 undefined。

**已处理**：交付说明已告知前端 AI 同步。

**建议**：未来类似统计响应，可考虑：
- 用 `Map<String, Object>` 或可扩展结构（牺牲类型安全）。
- 或提供 v2 接口（`/api/statistics/dashboard/v2`），老接口保留，渐进迁移。
- record 适合稳定结构，不适合会演进的聚合响应。供后续设计参考。

---

## 四、测试覆盖

### 11. 新接口缺 Controller 层 MockMvc 测试（P2，建议补）

**现象**：本次新增测试都是 Service 层单测（`@ExtendWith(MockitoExtension.class)`），验证业务逻辑。Controller 层（路由、参数校验、权限拦截、响应包装）没有测试。

**现状**：现有项目 Controller 测试也偏少，本次延续了现状。

**建议**：
- 对 B3（最复杂）、B1（权限边界）补 `@WebMvcTest` + MockMvc 测试，覆盖：
  - 权限：非 ADMIN 调 `/api/admin/users` 返回 403。
  - 参数校验：`ResetPasswordRequest.newPassword` 长度不足返回 400。
  - 路由：`/me` 与 `/{id}` 优先级。
- 不在本次任务书硬性要求内，但能显著提升信心。

### 12. 集成测试未覆盖新接口（P2，建议补）

**现象**：项目有 `BaseIntegrationTest`（基于 `@SpringBootTest` 的集成测试），但本次新接口未加集成测试用例。

**建议**：至少为 B3 创建用户 → 列表查询 → 状态变更 → 重置密码 → 登录验证全链路加一个集成测试，验证端到端可用。优先级中等。

---

## 五、可观测性与运维

### 13. B3 用户管理操作未记录审计日志（P2，建议补）

**现象**：项目有 `AuditService` 记录 AI 调用审计，但 B3 的管理员操作（创建用户、禁用、锁定、重置密码）**没有写审计日志**。这些是高敏感操作，应留痕。

**建议**：
- B3 的 `createUser`/`changeStatus`/`resetPassword` 调用 `AuditService` 记录一条审计（操作人、操作类型、目标用户、时间）。
- 如果 `AuditService` 当前只面向 AI 调用，可建独立的 `AdminActionLog` 或扩展 AuditService 的 businessType。
- 这是安全合规建议，不在任务书要求内，但强烈建议补。

**本轮结论**：用户已确认第 13 条本轮不补。保留为后续安全合规建议，不作为本轮后端交付阻塞。

### 14. @Cacheable dashboard 加字段后需清缓存（P3，部署注意）

**现象**：`StatisticsService.getDashboardSummary` 有 `@Cacheable(key='dashboard')`。B6 加了 `totalPatientCount` 字段后，缓存里旧对象结构不匹配。

**影响**：开发期无影响（缓存通常重启清空）；生产部署时如果缓存是 Redis 等持久化缓存，旧缓存反序列化可能出问题。

**建议**：部署 B6 时手动清除 `dashboard` 缓存 key，或在缓存配置里加版本号（`dashboard:v2`）。已记在 notes，这里再次提示。

---

## 六、需要讨论确认的问题清单

以下是需要联调 AI / 契约组 / 产品回答的开放问题，汇总以便逐项推进：

| # | 问题 | 需谁确认 | 优先级 | 状态 |
|---|---|---|---|---|
| Q1 | JWT 校验是否检查 tokenVersion？若否，B3 锁定/禁用/重置密码对已登录会话不立即生效 | 后端安全 | P1 | 已查证：已校验 |
| Q2 | UserAccount 是否扩展 realName/phone/email 字段？B3 完整交付依赖此决策 | 产品 + DBA | P1 | 已完成：V073 扩字段 |
| Q3 | OpenAPI 主契约同步流程：谁负责、何时同步？本次新增接口是否进契约 | 联调 AI | P1 | 已完成：OpenAPI 已同步 |
| Q4 | 分页主参数名最终用 `pageSize` 还是 `size`？需收敛 | 契约组 | P2 | 待确认 |
| Q5 | B2 设备字段映射（category→type 等）是否接受？还是要扩展 device 表 | 联调 AI + DBA | P2 | 待确认 |
| Q6 | B3 创建 DOCTOR 时，前端表单字段集合需对齐（departmentId/doctorName/doctorTitle/specialty...） | 前端/联调 | P2 | 已完成：表单/API/OpenAPI 对齐 |
| Q7 | 前端如何感知 B6 DashboardSummary 新字段？是否需要版本化接口 | 前端 AI | P2 | 已完成：前端统计映射已对齐 |
| Q8 | B3 高敏感操作是否要补审计日志？审计范围和存储方式 | 后端 + 安全 | P2 | 本轮关闭：用户确认不补 |
| Q9 | 权限校验风格是否统一？何时收敛为 @PreAuthorize 为主 | 后端架构 | P3 | 待确认 |
| Q10 | DoctorService.createDoctor 在 B3 复用时是否有副作用（发通知、初始化数据）需注意 | 后端 | P3 | 已查证：无额外副作用 |

---

## 七、本次交付的自我评价

**做得到位的**：
- 严格遵循任务书边界；Q2 由联调确认后通过增量 Flyway 迁移完成，不修改既有 DB 基线脚本。
- 全量测试通过（388 个），无回归。
- B3 状态语义与现有 `AuthService.checkAccountStatus` 闭环验证（禁用/锁定登录会被拒），不是凭空实现。
- B8 用工具类兼容分页参数，没有破坏老接口。

**做得保守的**：
- B3 角色变更按本轮决定不联动 Doctor/DoctorProfile，避免擅自扩展医疗业务规则。
- B3 高敏感操作审计日志按本轮决定不补，保留为后续安全合规建议。
- B6 用 record 加字段是前向不兼容，没有做版本化——因为任务书要求扩展返回，未要求版本化，权衡后选择最小改动。

**仍可后续优化的事项**：
1. **Q4（分页参数收敛）**——当前已兼容 `pageSize`/`size`，是否统一需契约决策。
2. **Q5（设备字段扩展）**——`applicableItems` 和 `category` 是否扩表，需单独变更。
3. **Q9（权限风格统一）**——架构级收敛，不影响当前交付。

---

## 八、代码审查补充发现（2026-07-01 复查）

对 B1–B8 逐文件复查后，发现以下此前未记录的问题。所有问题均为非阻断，B1–B8 均符合任务书验收标准。

### 15. B2 设备创建/更新未校验 departmentId 存在性（P2，建议补）

**现象**：`DeviceService.createDevice` 和 `updateDevice` 接收 `departmentId` 并直接写入设备，**没有校验科室是否存在或是否启用**。对比 `DoctorService.createDoctor`（第 64-68 行）是校验了科室存在且 `ENABLED` 的。`DeviceService` 未注入 `DepartmentRepository`，所以根本无法校验。

**影响**：管理员可能把设备挂到一个不存在或已停用的科室下，造成数据不一致（设备列表按科室筛选时出现孤儿设备）。

**建议**：注入 `DepartmentRepository`，在 createDevice/updateDevice 中校验 departmentId 非空时科室存在。是否要求科室 ENABLED 可讨论（设备不像医生那样依赖科室启用状态接诊，可放宽到只校验存在）。

### 16. B3 角色变更 DOCTOR→ADMIN 产生 Doctor 孤儿档案（P2，建议讨论）

**现象**：`AdminUserService.updateUser` 更新角色时，`user.getRoles().clear(); user.getRoles().add(role);` 直接替换角色集合。如果一个 DOCTOR 账号（已有关联的 `Doctor` + `DoctorProfile` 记录）被改成 ADMIN，**Doctor/DoctorProfile 记录不会被清理**，变成孤儿数据。反之 ADMIN→DOCTOR 也不会自动创建 Doctor 档案。

**影响**：
- DOCTOR→ADMIN：残留 Doctor 档案，`doctor.user_id` 仍指向该账号，医生列表查询可能仍出现该人（但账号角色已是 ADMIN，权限上不再是医生）。
- ADMIN→DOCTOR：账号有 DOCTOR 角色但无 Doctor 档案，调用 B1 `PUT /api/doctors/me` 会因 `findByUserId` 返回空而抛 403——体验上是"已是医生却不能更新资料"。

**建议**：
- 方案 A（保守）：禁止 DOCTOR↔ADMIN 角色互转，只允许在"无档案"账号上切换，或要求先处理档案。
- 方案 B（联动）：DOCTOR→ADMIN 时软删或归档 Doctor/DoctorProfile；ADMIN→DOCTOR 时要求补齐档案字段（类似 createDoctor 的必填校验）。
- 任务书对角色变更的联动未作要求，当前实现满足"更新角色"字面验收，但数据一致性建议与产品确认预期行为。

**本轮结论**：用户已确认第 16 条不联动。当前实现保持账号角色更新与医生档案解耦，后续如要禁止互转或联动归档需单独立项。

### 复查结论

| 任务 | 验收符合度 | 实现 | 测试 | 结论 |
|---|---|---|---|---|
| B1 | 符合 | Controller 校验 DOCTOR + Service 按 userId 定位，无档案自动创建，不改 dept/status/title | 3 个测试（存在/无档案/非医生403） | 通过 |
| B2 | 符合 | POST/PUT + 管理员权限，code 唯一校验，createDevice 默认 AVAILABLE，updateDevice 不动 status/code | 4 个测试（创建/重复409/更新不改status+code/404） | 通过 |
| B3 | 符合 | 5 端点全管理员权限，createUser 区分 ADMIN/DOCTOR/PATIENT，realName/phone/email 已持久化，changeStatus 三动作，resetPassword 设 hash+mustChange+tokenVersion++ | 15 个测试 | 通过 |
| B4 | 符合 | GET 根端点 + 管理员权限，字段映射正确（priority→aiPriority、departmentId→mappedDepartmentId） | 复用既有 | 通过 |
| B5 | 符合 | GET 列表 + 类级 @PreAuthorize，success 映射正确，实体不存 API Key/请求头 | 复用既有 | 通过 |
| B6 | 符合 | DashboardSummary 加 totalPatientCount，`SELECT COUNT(*) FROM patient` 真实查询，测试同步 | 测试同步加第6参数 | 通过 |
| B7 | 符合 | GET 根端点 + 管理员权限，name 模糊/phone 精确/status 精确，字段与实体一致 | 复用既有 | 通过 |
| B8 | 符合 | PageUtils 兼容 pageSize/size（优先 pageSize，默认 20，上限 100），B3/B4/B5/B7 均已使用，未动老接口 | — | 通过 |

全量测试：`Tests run: 388, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS`。

无阻断性问题。上述 15、16 两条为新发现的建议项，1–14 为此前已记录的建议/待确认项。

---

## 九、复查后自处理记录（2026-07-01）

用户反馈"不需要询问的就自己做"。据此，对前述条目中可自查或可自修的，本次已自行处理；真正需要他人决策的保留。

### 已自行查证（无需询问）

- **第 5 条 / Q1（JWT tokenVersion 校验）**：已查 `JwtAuthenticationFilter`（第 65-69 行）与 `JwtService`（第 86-88 行），均校验 tokenVersion，且过滤器同时校验 `enabled` 与 `accountNonLocked`。结论：B3 的 DISABLE/LOCK/重置密码**对已登录会话也立即生效**，安全语义完全成立，无需任何处理。
- **Q10（createDoctor 副作用）**：已查 `DoctorService.createDoctor` 全文，仅做用户名检查、科室校验、建 UserAccount+Doctor+DoctorProfile，无发通知、无初始化其他数据。结论：B3 复用 createDoctor 无副作用需注意。

### 已自行修复（任务书允许范围：补必要校验/错误码/测试）

1. **第 7 条（AdminUserResponse 暴露 tokenVersion）**：已从 DTO 去掉 tokenVersion 字段（前端无需，信息最小化）。`AdminUserResponse.from` 同步调整，无测试引用该字段。
2. **第 6 条（设备 code 并发竞态）**：`DeviceService.createDevice` 的 save 已包 try-catch `DataIntegrityViolationException` 转 `DEVICE_CODE_DUPLICATED`（409）。DB 唯一约束兜底异常现在返回业务错误码而非 500。
3. **第 15 条（设备 departmentId 未校验）**：`DeviceService` 注入 `DepartmentRepository`，createDevice/updateDevice 调 `validateDepartment`（非空时校验存在，不存在抛 `DEVICE_DEPARTMENT_NOT_FOUND` 404）。新增错误码 + 1 个测试。
4. **B3 用户名并发竞态**：`AdminUserService.createAdmin` 的 save 已包 try-catch `DataIntegrityViolationException` 转 `USER_USERNAME_DUPLICATED`（409）。
5. **第 11 条（缺 Controller MockMvc 测试）**：新增 `AdminUserControllerTest`（5 个测试），覆盖非管理员 403（列表/创建/重置密码）、管理员 200、page 参数 1-based 转 0-based。用 SecurityContextHolder 注入 principal，无需 mockStatic。

### 仍需他人确认（保留）

- **Q2 UserAccount 扩字段（realName/phone/email）**：已通过 `V073__user_account_profile_fields.sql` 完成，B3 创建/列表/更新已持久化。
- **Q3 OpenAPI 主契约同步**：已由联调 AI 同步，OpenAPI 校验通过。
- **Q4 分页主参数名**：任务书明确由契约组决定，B8 仅做兼容。
- **第 16 条（角色变更 DOCTOR↔ADMIN 联动）**：用户已确认本轮不联动，保留现状。
- **第 13 条（B3 审计日志）**：用户已确认本轮不补，保留为后续安全合规建议。
- **第 1/9 条（权限校验风格统一）**：架构级收敛，不在本次范围。
- **Q6/Q7（前端字段对齐）**：已由联调收尾完成。

### 测试结果

Q2/Q6 收尾后全量测试：`Tests run: 388, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS`。

---

*本文档为实施者视角的复盘与建议，不构成已交付代码的一部分。所有建议项均需相关负责人评估后决定是否实施。*
