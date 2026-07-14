# AINovel 模块与功能实现全景

> 基线：2026-07 当前工作区。本文描述实际路由、Controller、持久化和前端入口；历史审计结论以 `doc/audit/` 为准。

## 前端入口与权限

| 区域 | 路由 | 当前实现 |
| --- | --- | --- |
| 公开页 | `/`、`/pricing`、`/login`、`/register`、`/sso/callback` | 首页、静态定价、SSO 跳转和回调处理 |
| 创作者页 | `/dashboard`、`/novels`、`/worlds`、`/workbench`、`/materials`、`/settings`、`/profile` | `ProtectedRoute` 校验普通用户会话 |
| 管理员页 | `/admin/login`、`/admin/*` | 独立管理员令牌和 `AdminRoute` 校验 |

`AuthContext` 从本地 token 调用 `GET /api/v1/user/profile` 恢复普通用户会话。管理员令牌独立保存，并通过 `GET /api/v1/admin-auth/me` 校验。

## 创作者工作流

1. **Dashboard 与小说管理**：`Dashboard.tsx` 读取用户统计；`NovelManager.tsx` 管理故事卡；`CreateNovel.tsx` 支持普通创建或一键构思。
2. **故事与大纲**：工作台的“故事构思”“故事管理”“大纲编排”通过 `StoryController` 管理故事卡、角色、章节和场景。
3. **稿件写作**：`ManuscriptWriter.tsx` 管理多稿件、场景标签、富文本编辑、自动保存、会话统计和右侧功能面板。
4. **场景生成**：`ManuscriptController` 的指定稿件路径支持 `mode=fast|crafted`。精雕模式由 `SceneGenerationPromptBuilder` 调用 `SlopPatternSamplingService` 和 `PromptAssemblyService` 注入约束；旧生成路径保持快速模式兼容。
5. **质量与修订**：`SlopQualityController`、`SlopDriftController`、`PlotQualityController` 分别提供文本诊断、长篇 drift 和剧情质量能力。候选修订只有在用户采纳时写回正文。

## 工作台与 v2 能力

工作台通过 `?tab=` 选择以下标签：`conception`、`stories`、`outline`、`writing`、`search`、`lorebook`、`graph`、`analysis`、`v2`。

| 能力 | 前端入口 | 后端入口 |
| --- | --- | --- |
| Lorebook、实体与图谱 | `LorebookPanel`、`KnowledgeGraphTab` | `V2ContextController` |
| 风格画像与角色声音 | 设置-风格画像 | `V2StyleController` |
| Beta Reader 与连续性 | 工作台质量相关入口 | `V2AnalysisController` |
| 版本、分支和自动保存 | 稿件版本侧栏 | `V2VersionController` |
| 导出 | 稿件导出侧栏 | `V2ExportController` |
| 模型偏好与对比 | 设置-模型偏好 | `V2ModelController` |
| 布局、会话、目标和快捷键 | 设置-工作台体验 | `V2WorkspaceController` |

所有 v2 资源权限复用 `ResourceAccessGuard` 和各领域服务的所有者校验；前端 API 统一位于 `frontend/src/lib/api-client.ts`。

## 素材、世界观与账户

- **素材**：`MaterialController` 提供创建、TXT 导入、审核、检索、查重、合并和引用查询。普通素材页提供列表、创建、导入和审核；合并与引用治理主要在管理员后台。
- **世界观**：`WorldController` 与 `WorldDefinitionController` 管理草稿、模块字段、预检、发布和模块生成。`WorldEditor.tsx` 是实际编辑入口。
- **账户与积分**：`UserController` 和 `EconomyService` 维护项目专属积分、兑换码、通用积分转换、订单与流水。密码和 SSO 账号由 user-service 管理。
- **管理员后台**：`AdminController`、`AdminOperationsController`、`AdminOpsController` 提供运营、素材治理、资产审计、质量巡检、积分、维护模式和运维观测。

## 安全、配置与存储

- `JwtAuthFilter` 解析令牌；当 user-service 签名不能由本服务验证时，`UserSessionValidator` 通过 `uid + sid` 的 gRPC 会话校验建立会话。
- user-service、ai-service 和 pay-service 使用 `env.txt`/环境变量提供的静态地址；`ExternalSecurityStartupValidator` 在启动时校验鉴权配置。
- `V1__baseline.sql` 是完整基线，后续结构变更通过 Flyway 迁移；`V2__slop_patterns.sql` 新增并初始化精雕模式库。`V3__backfill_baselined_v2_persistence.sql` 为曾在 v2 表落库前执行 V1 baseline 的旧库补齐 24 张 v2 持久化表及稿件当前分支外键；完整 V1 新库执行 V3 时不会改写已有结构。
- 主要持久化域包括故事、角色、大纲、稿件、世界、素材、质量运行、v2 上下文/版本/导出、风格和积分账本。

## 当前文档边界

- API 细节见 `doc/api/`；用户操作见根目录 `user-doc/`。
- `doc/audit/`、`doc/test/` 和旧设计文档是可追溯快照，不作为当前代码地图。
