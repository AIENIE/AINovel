# AINovel 模块与功能实现全景（代码实扫版）

> 产出时间：2026-02-17
> 
> 分析方式：基于当前仓库前后端源码实扫（路由、页面、Controller、Service、实体与配置），不是仅依据历史文档。

## 1. 模块总览（按分层）

- 前端入口层：公开页、SSO 跳转、路由鉴权、布局与导航。
- 前端业务层：Dashboard、小说管理、工作台、世界构建、素材库、设置、个人中心、后台管理。
- 后端业务层：`user`、`story`、`manuscript`、`world`、`material`、`settings`、`admin`、`ai`。
- 后端支撑层：安全过滤链、远程会话校验、Consul 服务发现、外部微服务适配、初始化与异常处理。
- 数据层：MySQL（JPA 实体）+ Redis 缓存配置（当前业务代码中缓存使用较少）。

## 2. 前端模块与功能点

### 2.1 公开站点与公共路由

模块作用：承接未登录用户入口、品牌展示与错误页回退。

| 功能点 | 功能点作用 | 实现位置 |
| --- | --- | --- |
| 首页落地页 | 展示产品能力、引导登录/注册或进入工作台 | `frontend/src/pages/Index.tsx` |
| 定价页 | 展示套餐文案（静态） | `frontend/src/pages/Pricing.tsx` |
| 404 页 | 兜底未知路由，并可跳转登录 | `frontend/src/pages/NotFound.tsx` |
| 路由注册 | 注册 `/` `/pricing` `*` | `frontend/src/App.tsx` |

### 2.2 统一登录与会话态模块

模块作用：前端只做 SSO 跳转与 token 接收，不做本地账号密码鉴权。

| 功能点 | 功能点作用 | 实现位置 |
| --- | --- | --- |
| 登录页跳转 SSO | 自动跳转后端 `/api/v1/sso/login`，保留 `next` 与一次性 `state` | `frontend/src/pages/auth/Login.tsx` `backend/src/main/java/com/ainovel/app/auth/SsoController.java` |
| 注册页跳转 SSO | 自动跳转后端 `/api/v1/sso/register`，保留 `next` 与一次性 `state` | `frontend/src/pages/auth/Register.tsx` `backend/src/main/java/com/ainovel/app/auth/SsoController.java` |
| SSO 回调处理 | 从 hash 读取 `access_token`，并强制校验 `state` 后写入本地并刷新资料 | `frontend/src/pages/auth/SsoCallback.tsx` |
| SSO URL 生成 | 生成后端中转 URL，管理一次性 `state` 生成/消费 | `frontend/src/lib/sso.ts` |
| 全局登录态 | 初始化 token、拉取 profile、判断 admin、提供登出/刷新 | `frontend/src/contexts/AuthContext.tsx` |
| 路由守卫 | 普通鉴权与管理员鉴权 | `frontend/src/App.tsx`（`ProtectedRoute`、`AdminRoute`） |

### 2.3 Dashboard（模块选择入口）

模块作用：登录后的主入口，统计创作资产并分流到小说/世界两条主线。

| 功能点 | 功能点作用 | 实现位置 |
| --- | --- | --- |
| 创作统计加载 | 获取小说数、世界数、字数、设定条目数 | 前端：`frontend/src/pages/Dashboard.tsx`；后端：`backend/src/main/java/com/ainovel/app/user/UserController.java`（`/v1/user/summary`） |
| 入口分流 | 跳转 `/novels` 与 `/worlds` | `frontend/src/pages/Dashboard.tsx` |

### 2.4 小说管理模块

模块作用：管理故事卡片生命周期，并作为进入工作台的入口。

| 功能点 | 功能点作用 | 实现位置 |
| --- | --- | --- |
| 故事列表 | 展示故事卡并附带字数/进度估算 | `frontend/src/pages/NovelManager.tsx` |
| 新建小说 | 普通创建或一键构思创建 | `frontend/src/pages/CreateNovel.tsx` |
| 删除故事 | 删除故事与关联角色卡 | 前端：`frontend/src/pages/NovelManager.tsx`；后端：`backend/src/main/java/com/ainovel/app/story/StoryService.java`（`deleteStory`） |
| 进工作台 | 通过 `?id=<storyId>` 进入创作工作台 | `frontend/src/pages/NovelManager.tsx` |

### 2.5 创作工作台模块（核心）

模块作用：在同一工作台内完成构思、故事卡、大纲、正文与素材检索。

实现入口：`frontend/src/pages/Workbench/Workbench.tsx`

### 2.5.1 故事构思

| 功能点 | 功能点作用 | 实现位置 |
| --- | --- | --- |
| 一键构思 | 创建故事并自动生成示例角色 | 前端：`frontend/src/pages/Workbench/tabs/StoryConception.tsx`；后端：`backend/src/main/java/com/ainovel/app/story/StoryController.java`（`POST /v1/conception`）、`StoryService.java`（`conception`） |

### 2.5.2 故事卡与角色管理

| 功能点 | 功能点作用 | 实现位置 |
| --- | --- | --- |
| 故事列表与选择 | 切换故事查看详情 | `frontend/src/pages/Workbench/tabs/StoryManager.tsx` |
| 角色增删改查 | 维护角色卡（名字/简介/细节/关系） | 前端：`frontend/src/pages/Workbench/tabs/StoryManager.tsx`；后端：`backend/src/main/java/com/ainovel/app/story/StoryController.java`、`StoryService.java` |
| 删除故事 | 工作台内直接删除故事 | 同上 |

### 2.5.3 大纲编排

| 功能点 | 功能点作用 | 实现位置 |
| --- | --- | --- |
| 故事下大纲管理 | 按故事加载/创建大纲 | 前端：`frontend/src/pages/Workbench/tabs/OutlineWorkbench.tsx`；后端：`StoryController.java`（`/story-cards/{id}/outlines`） |
| 章节/场景编辑 | 编辑标题、摘要、章节场景结构 | 前端：`OutlineWorkbench.tsx`；后端：`backend/src/main/java/com/ainovel/app/story/OutlineService.java`（`saveOutline`、`updateChapter`、`updateScene`） |
| AI 润色摘要 | 对章节或场景摘要发起润色 | 前端：`OutlineWorkbench.tsx`；后端：`StoryController.java`（`POST /v1/outlines/scenes/{id}/refine`） |

### 2.5.4 正文写作（稿件）

| 功能点 | 功能点作用 | 实现位置 |
| --- | --- | --- |
| 自动建稿 | 无稿件时自动创建 `正文稿` | 前端：`frontend/src/pages/Workbench/tabs/ManuscriptWriter.tsx`；后端：`backend/src/main/java/com/ainovel/app/manuscript/ManuscriptController.java`（`POST /v1/outlines/{outlineId}/manuscripts`） |
| 场景正文编辑 | 编辑器按场景维护正文片段 | 前端：`ManuscriptWriter.tsx` + `frontend/src/components/editor/TiptapEditor.tsx`；后端：`ManuscriptService.java`（`updateSection`） |
| 自动保存 | 编辑后延迟保存到后端 | `frontend/src/pages/Workbench/tabs/ManuscriptWriter.tsx` |
| 场景生成 | 生成当前场景正文 | 前端：`ManuscriptWriter.tsx`；后端：`ManuscriptService.java`（`generateForScene`） |
| 角色变化日志 | 分析并记录角色变化 | 后端：`ManuscriptController.java`（`/sections/analyze-character-changes`、`/character-change-logs`）、`ManuscriptService.java` |

### 2.5.5 AI Copilot 侧栏

| 功能点 | 功能点作用 | 实现位置 |
| --- | --- | --- |
| 对话助手 | 在写作上下文内发起 AI 对话 | 前端：`frontend/src/components/ai/CopilotSidebar.tsx`；后端：`backend/src/main/java/com/ainovel/app/ai/AiController.java`（`POST /v1/ai/chat`） |
| 模型切换 | 读取并选择可用模型 | 前端：`CopilotSidebar.tsx`；后端：`AiController.java`（`GET /v1/ai/models`） |
| 文本润色弹窗 | 对选中文本润色并确认替换 | 前端：`frontend/src/components/editor/TiptapEditor.tsx`、`frontend/src/components/ai/AiRefineDialog.tsx`；后端：`AiController.java`（`POST /v1/ai/refine`） |

### 2.5.6 工作台素材检索

| 功能点 | 功能点作用 | 实现位置 |
| --- | --- | --- |
| 关键词检索素材 | 在写作时快速查素材 | 前端：`frontend/src/pages/Workbench/tabs/MaterialSearchPanel.tsx`；后端：`backend/src/main/java/com/ainovel/app/material/MaterialController.java`（`POST /v1/materials/search`） |

### 2.6 世界构建模块

模块作用：提供世界草稿创建、模块化设定编辑、预检发布和模块自动生成。

| 功能点 | 功能点作用 | 实现位置 |
| --- | --- | --- |
| 世界列表/删除草稿 | 世界卡管理，草稿可删 | 前端：`frontend/src/pages/WorldManager.tsx`；后端：`backend/src/main/java/com/ainovel/app/world/WorldService.java`（`list`、`delete`） |
| 新建世界 | 创建世界草稿 | 前端：`frontend/src/pages/CreateWorld.tsx`；后端：`WorldController.java`（`POST /v1/worlds`） |
| 基础信息编辑 | 名称、标语、创作意图、主题、备注 | 前端：`frontend/src/pages/Worlds.tsx` + `frontend/src/pages/WorldBuilder/components/WorldMetadataForm.tsx`；后端：`WorldService.java`（`update`） |
| 模块字段编辑 | 按模块字段写入设定 | 前端：`frontend/src/pages/WorldBuilder/components/WorldModuleEditor.tsx`；后端：`WorldController.java`（`PUT /v1/worlds/{id}/modules`） |
| 字段润色 | 对单字段发起 AI 润色 | 前端：`WorldModuleEditor.tsx`；后端：`WorldController.java`（`POST /modules/{moduleKey}/fields/{fieldKey}/refine`） |
| 预检发布 | 检查缺失模块并触发发布流程 | 前端：`frontend/src/pages/Worlds.tsx`；后端：`WorldService.java`（`preview`、`publish`） |
| 模块自动生成 | 对缺失模块填充占位内容并推进状态 | 前端：`Worlds.tsx`；后端：`WorldService.java`（`generateModule`、`retryModule`） |
| 模块定义拉取 | 拉取模块字段定义用于动态表单 | 前端：`Worlds.tsx`；后端：`backend/src/main/java/com/ainovel/app/world/WorldDefinitionController.java` |

### 2.7 素材库模块

模块作用：统一管理素材创建、导入、审核与复用。

| 功能点 | 功能点作用 | 实现位置 |
| --- | --- | --- |
| 素材列表 | 查看素材与状态 | 前端：`frontend/src/pages/Material/tabs/MaterialList.tsx`；后端：`MaterialController.java`（`GET /v1/materials`） |
| 手工创建 | 创建单条素材 | 前端：`frontend/src/pages/Material/tabs/MaterialCreateForm.tsx`；后端：`MaterialController.java`（`POST /v1/materials`） |
| TXT 上传导入 | 上传文本并轮询导入任务 | 前端：`frontend/src/pages/Material/tabs/MaterialUpload.tsx`；后端：`MaterialController.java`（`/upload`、`/upload/{jobId}`） |
| 审核台 | 审核 pending 素材（通过/驳回） | 前端：`frontend/src/pages/Material/tabs/ReviewDashboard.tsx`；后端：`MaterialController.java`（`/review/pending`、`/review/approve`、`/review/reject`） |
| 自动提示/搜索/合并 | 编辑器提示、查重、合并、引用查询 | 后端：`MaterialController.java`、`MaterialService.java` |

### 2.8 设置与提示词模块

模块作用：管理工作区提示词和世界观提示词模板。

| 功能点 | 功能点作用 | 实现位置 |
| --- | --- | --- |
| 工作区提示词编辑 | 编辑故事构思/章节/正文/润色模板 | 前端：`frontend/src/pages/Settings/tabs/WorkspacePrompts.tsx`；后端：`backend/src/main/java/com/ainovel/app/settings/SettingsController.java`（`/prompt-templates`） |
| 世界观提示词编辑 | 编辑模块模板和字段精修模板 | 前端：`frontend/src/pages/Settings/tabs/WorldPrompts.tsx`；后端：`SettingsController.java`（`/world-prompts`） |
| 提示词说明页 | 展示变量与示例说明 | 前端：`frontend/src/pages/Settings/PromptHelpPage.tsx`、`frontend/src/pages/Settings/WorldPromptHelpPage.tsx` |
| 模板默认值与重置 | 提供默认模板、更新、重置、元数据 | 后端：`backend/src/main/java/com/ainovel/app/settings/SettingsService.java` |

### 2.9 个人中心与积分模块

模块作用：展示用户信息与双余额（项目积分+通用积分），承接本地签到、兑换码与通用积分兑换。

| 功能点 | 功能点作用 | 实现位置 |
| --- | --- | --- |
| 资料展示 | 用户名、邮箱、角色、余额 | 前端：`frontend/src/pages/Profile/ProfilePage.tsx`；后端：`backend/src/main/java/com/ainovel/app/user/UserController.java`（`/profile`） |
| 每日签到 | 在 AINovel 本地账本发放项目积分（北京时间日切）并刷新双余额 | 前端：`ProfilePage.tsx`；后端：`UserController.java` + `backend/src/main/java/com/ainovel/app/economy/EconomyService.java` |
| 兑换码 | 在 AINovel 本地兑换码表核销并入账项目积分 | 同上 |
| 通用积分兑换 | 调用 PayService 扣减通用积分后，在本地账本入账项目积分（1:1） | 前端：`ProfilePage.tsx`；后端：`UserController.java` + `EconomyService.java` + `BillingGrpcClient.java` |
| 安全说明 | 明确密码由 SSO 管理 | 前端：`ProfilePage.tsx`；后端：`UserController.java`（`/password` 返回 501） |

### 2.10 管理后台模块

模块作用：提供管理员看板、用户封禁管理、系统开关配置，以及本地积分兑换码发放能力。

| 功能点 | 功能点作用 | 实现位置 |
| --- | --- | --- |
| 后台布局与路由 | 管理侧导航、子路由容器 | 前端：`frontend/src/components/layout/AdminLayout.tsx`、`frontend/src/App.tsx`（`/admin/*`） |
| 看板统计 | 总用户、今日新增、待审素材等 | 前端：`frontend/src/pages/Admin/Dashboard.tsx`；后端：`backend/src/main/java/com/ainovel/app/admin/AdminController.java`（`/dashboard`） |
| 用户管理 | 查看用户并封禁/解封 | 前端：`frontend/src/pages/Admin/UserManager.tsx`；后端：`AdminController.java`（`/users`、`/users/{id}/ban`、`/users/{id}/unban`） |
| 积分与兑换码 | 创建/查看本地兑换码、发放项目积分 | 前端：`frontend/src/pages/Admin/CreditsManager.tsx`；后端：`AdminController.java`（`/credits/grant`、`/redeem-codes`） |
| 系统设置 | 注册开关、维护模式、签到区间、SMTP 配置 | 前端：`frontend/src/pages/Admin/SystemSettings.tsx`；后端：`AdminController.java`（`/system-config`） |

### 2.11 前端共享基础模块

| 功能点 | 功能点作用 | 实现位置 |
| --- | --- | --- |
| 统一 API 调用层 | 封装 `/api/v1/*` 请求、token 注入、DTO 映射 | `frontend/src/lib/mock-api.ts` |
| 应用布局 | 业务侧侧边栏、用户菜单、管理员入口 | `frontend/src/components/layout/AppLayout.tsx` |
| 富文本编辑器 | TipTap 编辑、工具栏、气泡菜单、AI 润色 | `frontend/src/components/editor/TiptapEditor.tsx`、`frontend/src/components/editor/EditorToolbar.tsx` |

## 3. 后端业务模块与功能点

### 3.1 用户与资产域（`user` + `economy`）

模块作用：用户资料、统计、项目积分账本、签到/兑换码/AI 扣费与通用积分兑换统一出口。

核心实现：
- `backend/src/main/java/com/ainovel/app/user/UserController.java`
- `backend/src/main/java/com/ainovel/app/economy/EconomyService.java`
- `backend/src/main/java/com/ainovel/app/user/User.java`
- `backend/src/main/java/com/ainovel/app/user/UserRepository.java`

### 3.2 故事与大纲域（`story`）

模块作用：故事卡、角色卡、大纲结构维护。

核心实现：
- API：`backend/src/main/java/com/ainovel/app/story/StoryController.java`
- 业务：`backend/src/main/java/com/ainovel/app/story/StoryService.java`、`backend/src/main/java/com/ainovel/app/story/OutlineService.java`
- 持久化：`backend/src/main/java/com/ainovel/app/story/model/*`、`backend/src/main/java/com/ainovel/app/story/repo/*`

### 3.3 稿件域（`manuscript`）

模块作用：正文片段存储、场景生成、角色变化日志。

核心实现：
- API：`backend/src/main/java/com/ainovel/app/manuscript/ManuscriptController.java`
- 业务：`backend/src/main/java/com/ainovel/app/manuscript/ManuscriptService.java`
- 持久化：`backend/src/main/java/com/ainovel/app/manuscript/model/Manuscript.java`

### 3.4 世界观域（`world`）

模块作用：世界卡与模块化设定管理、发布状态推进与模块生成。

核心实现：
- API：`backend/src/main/java/com/ainovel/app/world/WorldController.java`
- 模块定义：`backend/src/main/java/com/ainovel/app/world/WorldDefinitionController.java`
- 业务：`backend/src/main/java/com/ainovel/app/world/WorldService.java`
- 持久化：`backend/src/main/java/com/ainovel/app/world/model/World.java`

### 3.5 素材域（`material`）

模块作用：素材 CRUD、导入任务、审核与检索。

核心实现：
- API：`backend/src/main/java/com/ainovel/app/material/MaterialController.java`
- 业务：`backend/src/main/java/com/ainovel/app/material/MaterialService.java`
- 持久化：`backend/src/main/java/com/ainovel/app/material/model/*`、`backend/src/main/java/com/ainovel/app/material/repo/*`

### 3.6 设置域（`settings`）

模块作用：提示词模板与全局配置读取更新。

核心实现：
- API：`backend/src/main/java/com/ainovel/app/settings/SettingsController.java`
- 业务：`backend/src/main/java/com/ainovel/app/settings/SettingsService.java`
- 持久化：`backend/src/main/java/com/ainovel/app/settings/model/*`、`backend/src/main/java/com/ainovel/app/settings/repo/*`

### 3.7 管理域（`admin`）

模块作用：管理员聚合视图与系统管理操作。

核心实现：
- API：`backend/src/main/java/com/ainovel/app/admin/AdminController.java`
- 依赖：`backend/src/main/java/com/ainovel/app/integration/UserAdminRemoteClient.java`

### 3.8 AI 能力域（`ai`）

模块作用：对外暴露模型列表、对话、润色能力，并对接外部 AI 微服务。

核心实现：
- API：`backend/src/main/java/com/ainovel/app/ai/AiController.java`
- 业务：`backend/src/main/java/com/ainovel/app/ai/AiService.java`
- 外部接入：`backend/src/main/java/com/ainovel/app/integration/AiGatewayGrpcClient.java`

## 4. 安全与外部集成支撑模块

| 模块 | 作用 | 实现位置 |
| --- | --- | --- |
| JWT 鉴权过滤 | 解析 token、组装 Spring Security 上下文 | `backend/src/main/java/com/ainovel/app/security/JwtAuthFilter.java` |
| 远程会话校验 | 用 `uid + sid` 向 UserService 校验会话有效性 | `backend/src/main/java/com/ainovel/app/security/remote/UserSessionValidator.java` |
| 会话发现与配置 | Consul 发现 userservice gRPC + 回退地址配置 | `backend/src/main/java/com/ainovel/app/security/remote/ConsulUserGrpcEndpointResolver.java`、`UserSessionValidationProperties.java` |
| 系统守卫 | 封禁用户拦截、维护模式拦截 | `backend/src/main/java/com/ainovel/app/security/SystemGuardFilter.java` |
| 安全配置 | 过滤链顺序、放行规则、CORS | `backend/src/main/java/com/ainovel/app/security/SecurityConfig.java` |
| 外部服务发现 | 通用 Consul 解析与缓存 | `backend/src/main/java/com/ainovel/app/integration/ConsulServiceResolver.java` |
| UserService 管理适配 | 管理端用户查询/封禁/解封 HTTP 适配 | `backend/src/main/java/com/ainovel/app/integration/UserAdminRemoteClient.java` |
| Billing 适配 | 通用积分查询、通用->项目兑换、冲回补偿 gRPC 适配 | `backend/src/main/java/com/ainovel/app/integration/BillingGrpcClient.java` |
| 公共异常处理 | 统一异常 -> `ApiError` 输出 | `backend/src/main/java/com/ainovel/app/common/GlobalExceptionHandler.java` |
| 启动种子数据 | 初始化 admin、故事、世界、素材、系统配置 | `backend/src/main/java/com/ainovel/app/config/DataInitializer.java` |

## 5. 数据库表与业务模块映射

| 业务模块 | 关键表 | 关键实现 |
| --- | --- | --- |
| 用户与权限 | `users`、`user_roles` | `backend/src/main/java/com/ainovel/app/user/User.java` |
| 故事与角色 | `stories`、`character_cards` | `backend/src/main/java/com/ainovel/app/story/model/*` |
| 大纲与稿件 | `outlines`、`manuscripts` | `backend/src/main/java/com/ainovel/app/story/model/Outline.java`、`backend/src/main/java/com/ainovel/app/manuscript/model/Manuscript.java` |
| 世界观 | `worlds` | `backend/src/main/java/com/ainovel/app/world/model/World.java` |
| 素材 | `materials`、`material_upload_jobs` | `backend/src/main/java/com/ainovel/app/material/model/*` |
| 提示词与系统配置 | `prompt_templates`、`world_prompt_templates`、`global_settings` | `backend/src/main/java/com/ainovel/app/settings/model/*` |

完整参考：`backend/sql/schema.sql`

## 6. 当前实现状态（已闭环与占位项）

以下是代码实扫后可确认的“占位/未完全闭环”点：

- 管理看板的积分消耗与错误率当前为固定值（`0.0`），未接入真实统计源。
  - 位置：`backend/src/main/java/com/ainovel/app/admin/AdminController.java`
- 素材查重与引用查询当前返回空列表。
  - 位置：`backend/src/main/java/com/ainovel/app/material/MaterialService.java`（`findDuplicates`、`citations`）
- 大纲“生成章节”与稿件“生成场景”为占位生成文本，并非真实大模型生成。
  - 位置：`backend/src/main/java/com/ainovel/app/story/OutlineService.java`（`addGeneratedChapter`）
  - 位置：`backend/src/main/java/com/ainovel/app/manuscript/ManuscriptService.java`（`generateForScene`）
- 世界模块自动生成当前为占位填充文本，发布流程是“状态机 + 占位内容”实现。
  - 位置：`backend/src/main/java/com/ainovel/app/world/WorldService.java`（`publish`、`generateModule`）
- 设置页前端目前只覆盖“读取/保存”，未暴露 reset/metadata 操作入口（后端已有接口）。
  - 前端：`frontend/src/pages/Settings/tabs/*.tsx`
  - 后端：`backend/src/main/java/com/ainovel/app/settings/SettingsController.java`
- 存在兼容/遗留页面文件：`frontend/src/pages/Workbench.tsx`、`frontend/src/pages/Settings.tsx`、`frontend/src/pages/Materials.tsx`（仅做重定向），以及 `frontend/src/pages/WorldBuilder/WorldBuilderPage.tsx`（当前路由未使用）。

## 7. 文档落位建议

为避免现有 `doc/modules/*.md` 分散且信息粒度不一致，建议后续按本文件拆分维护：

- 业务模块文档：认证、工作台、世界、素材、设置、个人中心、后台。
- 支撑模块文档：安全链路、外部服务集成、配置与初始化。

当前完整分析主文档：`doc/modules/module-analysis.md`

## 8. v2 增量实现（2026-02-17）

本次开发按 `design-doc/v2` 新增了 v2 API 基线与前端联调面板：

- 后端新增 `backend/src/main/java/com/ainovel/app/v2/`，包含 7 个 v2 Controller + 1 个访问守卫：
  - `V2ContextController`
  - `V2StyleController`
  - `V2AnalysisController`
  - `V2VersionController`
  - `V2ExportController`
  - `V2ModelController`
  - `V2WorkspaceController`
  - `V2AccessGuard`
- 前端工作台新增 `v2工作台` 标签，入口：`frontend/src/pages/Workbench/tabs/V2Studio.tsx`。
- 前端 API 层新增 `api.v2.*` 封装，位置：`frontend/src/lib/mock-api.ts`。
- 稿件模型新增 `currentBranchId` 字段（版本控制模块所需），位置：`backend/src/main/java/com/ainovel/app/manuscript/model/Manuscript.java`。


