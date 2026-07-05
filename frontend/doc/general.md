# AI Novel 项目总体说明

## 产品定位与目标
- **定位**：AI 驱动的长篇小说创作工作台，覆盖从灵感→大纲→正文→素材引用→世界观设定的全流程。目标用户为网络文学作者、编剧及写作爱好者。
- **核心价值**：提供结构化创作流程、可复用的世界观与素材库、AI 生成与润色、角色状态追踪，降低设定跑偏与断更风险。

## 功能全景
- **用户与权限**：普通用户通过后端 SSO 跳转到 user-service 登录/注册，回调后兑换本项目 JWT；`AuthContext` 全局守护；`ProtectedRoute` 保护工作台、素材库、世界观等页面；ACL 粒度到 Workspace / Material。
- **故事构思与管理**：一键生成故事卡+角色卡，支持手工创建/编辑/删除故事与角色，AI 润色字段。
- **大纲工作台**：按章节生成场景树，支持世界观引用、章节/场景编辑、AI 润色、版本化保存。
- **小说创作**：基于大纲的场景级写作；AI 生成正文、保存稿件、自动素材建议；角色变化分析、关系图谱、成长路径、记忆驱动对话。
- **素材库（混合检索）**：手动创建、TXT 上传解析（Tika+Qdrant）、自动切片与嵌入；待审工作台；查重合并；引用历史；编辑/删除；检索与写作自动提示。
- **世界观工作台**：世界 CRUD、模块化编辑、自动生成流水线、字段精修、发布/版本化、进度轮询；引用到故事/大纲/稿件与提示词。
- **提示词与设置**：模型/Key/Base URL 配置；故事/大纲/正文/润色模板管理与重置；世界观提示词模板管理；两页帮助文档（变量/函数/示例）。
- **辅助与布局**：工作台多标签页（故事构思、故事管理、大纲、小说创作、素材检索）；统一头部、Refine Modal、WorldSelect/Drawer。

## 技术栈与架构
- **前端**：React 18 + TypeScript + Vite；UI 以 Ant Design 为主，辅以 Tailwind；路由 `react-router-dom`；状态通过 Context（Auth）+ Hooks；API 封装于 `src/services/api.ts` 并集中处理错误。
- **后端**：Java 25 + Spring Boot（分层 Controller/Service/Repository）；Spring Security + JWT；Spring Retry/Async；OpenAI 集成；MySQL 主库；Qdrant 向量库；Apache Tika 解析 TXT；AOP 权限注解 `@CheckPermission`。
- **向量与检索**：HybridSearchService 结合 Qdrant 语义向量与 MySQL FULLTEXT，RRF 融合 semantics/title/keywords/fulltext。
- **容器与部署**：`build.sh` 一键构建前后端并 `docker compose` 启动 (backend + `serve@14` 静态前端容器；依赖外层反向代理统一承接 `/api`、gzip 与缓存头)。建议前后端分域部署，由外层代理终结公网流量。

## 已实现主要功能
- SSO 普通用户认证链路、管理员本地登录链路与设置中心；API Key 校验测试接口。
- 故事卡/角色卡 CRUD + AI 润色；故事自动生成（含角色）。
- 大纲：按章节生成场景、树形查看、章节/场景表单编辑、世界引用、保存/删除。
- 稿件：多稿件管理、场景级生成与保存、世界上下文、角色变化分析、对话生成、角色关系/成长视图、自动素材建议。
- 素材库 V2：手工创建、TXT 上传解析与状态轮询、混合检索、自动提示、待审工作台（批准/驳回）、去重与合并、引用历史、权限控制。
- 世界观：元数据加载、草稿/发布列表、模块表单、自动生成流水线（队列+重试）、字段精修、发布校验与预览、版本信息、删除/重命名草稿。
- 提示词：故事/大纲/正文/润色模板读取、保存、重置；世界观模板的草稿/正式/字段精修管理；两份帮助页列出变量/函数/示例。

## 主要后端接口速览（与前端直连）
- 认证与设置：`GET /api/v1/sso/login`、`GET /api/v1/sso/register`、`GET /api/v1/sso/session`、`GET /api/auth/validate`、`GET/PUT /api/v1/settings`、`POST /api/v1/settings/test`。
- 故事与角色：`GET /api/v1/story-cards`、`GET /api/v1/story-cards/{id}`、`GET /api/v1/story-cards/{id}/character-cards`、`PUT /api/v1/story-cards/{id}`、`POST /api/v1/story-cards/{id}/characters`、`PUT/DELETE /api/v1/character-cards/{id}`、`POST /api/v1/stories`、`DELETE /api/v1/stories/{id}`、`POST /api/v1/conception`、`POST /api/v1/story-cards/{id}/refine`、`POST /api/v1/character-cards/{id}/refine`。
- 大纲：`GET /api/v1/story-cards/{storyId}/outlines`、`POST /api/v1/story-cards/{storyId}/outlines`、`GET/PUT/DELETE /api/v1/outlines/{id}`、`POST /api/v1/outlines/{outlineId}/chapters`、`PUT /api/v1/chapters/{id}`、`PUT /api/v1/scenes/{id}`、`POST /api/v1/outlines/scenes/{id}/refine`。
- 稿件与角色分析：`GET /api/v1/outlines/{outlineId}/manuscripts`、`POST /api/v1/outlines/{outlineId}/manuscripts`、`DELETE /api/v1/manuscripts/{id}`、`GET /api/v1/manuscripts/{id}`、`POST /api/v1/manuscript/scenes/{sceneId}/generate`、`PUT /api/v1/manuscript/sections/{sectionId}`、`POST /api/v1/manuscripts/{id}/sections/analyze-character-changes`、`GET /api/v1/manuscripts/{id}/character-change-logs`、`GET /api/v1/manuscripts/{id}/character-change-logs/{characterId}`、`POST /api/v1/ai/generate-dialogue`。
- 素材库：`POST /api/v1/materials`、`GET /api/v1/materials`、`GET /api/v1/materials/{id}`、`PUT /api/v1/materials/{id}`、`DELETE /api/v1/materials/{id}`、`POST /api/v1/materials/upload`、`GET /api/v1/materials/upload/{jobId}`、`POST /api/v1/materials/search`、`POST /api/v1/materials/editor/auto-hints`、`GET /api/v1/materials/review/pending`、`POST /api/v1/materials/find-duplicates`、`POST /api/v1/materials/merge`、`GET /api/v1/materials/{id}/citations`、`POST /api/v1/materials/{id}/review/approve|reject`。
- 世界观：`GET /api/v1/world-building/definitions`、`GET/POST /api/v1/worlds`、`GET /api/v1/worlds/{id}`、`PUT /api/v1/worlds/{id}`、`DELETE /api/v1/worlds/{id}`、`PUT /api/v1/worlds/{id}/modules`、`PUT /api/v1/worlds/{id}/modules/{moduleKey}`、`POST /api/v1/worlds/{id}/modules/{moduleKey}/generate`、`POST /api/v1/worlds/{id}/modules/{moduleKey}/fields/{fieldKey}/refine`、`GET /api/v1/worlds/{id}/publish/preview`、`POST /api/v1/worlds/{id}/publish`、`GET /api/v1/worlds/{id}/generation`、`POST /api/v1/worlds/{id}/generation/{moduleKey}`、`POST /api/v1/worlds/{id}/generation/{moduleKey}/retry`。
- 提示词与帮助：`GET /api/v1/prompt-templates`、`PUT /api/v1/prompt-templates`、`POST /api/v1/prompt-templates/reset`、`GET /api/v1/prompt-templates/metadata`、`GET/PUT /api/v1/world-prompts`、`POST /api/v1/world-prompts/reset`、`GET /api/v1/world-prompts/metadata`。

## 待办与潜在改进
- 后端说明中数据库/模型名称仍留占位（README）；生产需落地具体配置与密钥管理。
- 世界生成当前同步队列，尚未后台异步/消息化；长流程可能阻塞。
- 需要补充 AI 请求/结果审计与错误上报；统一前端 message/error 组件。
- 数据迁移建议引入 Flyway/Liquibase，关闭生产环境 `ddl-auto=update`。
- 素材导入仅支持 `.txt`；如需 PDF/Doc 需扩展解析与限流。
- 自动建议与检索依赖 Qdrant/MySQL，需监控超时与降级策略。
- 角色关系图/成长路径依赖日志完整性；可考虑可视化缓存与搜索。
