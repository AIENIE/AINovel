# AINovel 架构总览

基线：2026-07-15，G1-A P0 与 G2 Step 1 已合入 `master`。本文只描述当前实现；后续工作统一见 [`../roadmap.md`](../roadmap.md)。

## 顶层边界

- `frontend/`：React 18、TypeScript、Vite、Tailwind CSS 和 shadcn/ui 前端。
- `backend/`：Spring Boot 后端、Flyway 迁移和三服务客户端。
- `doc/`：研发、接口、运维、规划和研究文档。
- `user-doc/`：创作者与管理员使用手册。
- `build.sh`：唯一 Docker Compose 构建与部署入口。

## 产品入口

| 区域 | 主要路由 | 当前职责 |
| --- | --- | --- |
| 公开区 | `/`、`/pricing`、`/login`、`/register`、`/sso/callback` | 产品入口与统一登录 |
| 项目管理 | `/dashboard`、`/novels`、`/worlds` | 小说、世界观和统计入口 |
| 引导创作 | `/novels/quick-create` | G1-A P0 的 Step0-4、三候选和后台自动推进 |
| 创作工作台 | `/workbench` | 构思、大纲、正文、素材、上下文、质量、版本和导出 |
| 用户设置 | `/materials`、`/settings`、`/profile` | 素材、提示词、风格、模型偏好和积分 |
| G2 评审 | `/g2-evaluations/:id/review` | 受邀用户匿名评审 |
| 管理后台 | `/admin/*` | 运营、治理、质量、积分、G2 活动和运维观测 |

普通用户路由由 `ProtectedRoute` 校验 SSO 会话，管理员路由使用独立的本地管理员令牌。

## 后端模块

| 包 | 职责 |
| --- | --- |
| `auth`、`security` | SSO 中转、JWT 与 user-service 远程会话校验、资源所有权保护 |
| `workflow` | G1 引导创作、持久化后台任务、恢复和标准实体落库 |
| `story`、`manuscript`、`world` | 故事、角色、大纲、正文和世界观 |
| `material` | 素材创建、TXT 导入、检索、审核、查重、合并和引用查询 |
| `quality` | 生成门禁、手动文本诊断、长篇 drift 和剧情质量 |
| `g2evaluation` | G2 活动、邀请、隔离样本、匿名投票和失败退款 |
| `v2`、`style` | Lorebook、图谱、风格、分析、版本、导出、模型和工作台偏好 |
| `economy`、`user` | 项目专属积分、通用积分转换、兑换码和用户资料 |
| `admin`、`adminauth` | 管理后台、管理员认证和运维观测 |
| `integration` | ai-service、user-service、pay-service 的静态地址客户端和启动校验 |

## 数据与迁移

- 数据库结构以 `backend/src/main/resources/db/migration/V{n}__*.sql` 为准。
- `V1` 是完整基线，`V2` 初始化 slop 模式，`V3` 补齐 v2 持久化，`V4` 增加 G2 盲测，`V5` 增加 G1 工作流与 `async_jobs`。
- `backend/sql/schema.sql` 仅是历史入口说明，不接受新 DDL。
- Redis 用于运行时能力，Qdrant 用于向量检索；依赖均由部署环境提供。

## 文档职责

- 架构与当前行为：本目录。
- HTTP API：[`../api/README.md`](../api/README.md)。
- 开发、部署与验收：[`../operations/`](../operations/)。
- 后续路线：[`../roadmap.md`](../roadmap.md)。
- 用户操作：[`../../user-doc/README.md`](../../user-doc/README.md)。
