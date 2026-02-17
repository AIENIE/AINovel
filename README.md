# AINovel

AINovel 是一个 AI 小说创作平台，采用前后端分离架构：

- 前端：React 18 + TypeScript + Vite + Tailwind CSS + shadcn/ui
- 后端：Spring Boot（REST + gRPC 集成 + JWT/SSO）
- 默认端口：前端 `10010`，后端 `10011`
- 测试域名：`ainovel.seekerhut.com`，正式域名：`ainovel.aienie.com`

## 快速入口

- 上手详解文档：`doc/tutorial/getting-started.md`
- 项目结构说明：`doc/structure.md`
- 后端 API 文档索引：`doc/api/`
- 模块说明文档：`doc/modules/`
- 测试与联调记录：`doc/test/`

## 1. 如何启动项目前后端

详细步骤见 `doc/tutorial/getting-started.md` 的「1、如何启动项目前后端」。

常用命令如下：

1. 启动后端（本地进程）
```bash
cd backend
mvn spring-boot:run
```

2. 启动前端（本地进程）
```bash
cd frontend
npm ci --legacy-peer-deps
npm run dev
```

3. 一键构建并以 Docker 方式启动前后端
```bash
bash build.sh
```

## 2. 新环境部署时需调整的三方配置

详细清单见 `doc/tutorial/getting-started.md` 的「2、新环境部署时需要调整哪些配置（连接三方服务）」。

至少要确认以下配置：

- 数据库与缓存：`MYSQL_*`、`REDIS_*`
- SSO/JWT：`JWT_SECRET`、`SSO_SESSION_VALIDATION_ENABLED`、`USER_GRPC_*`
- 服务发现：`CONSUL_*`
- 外部微服务：`USER_HTTP_ADDR` / `USER_HTTP_SERVICE_NAME`、`AI_GRPC_ADDR` / `AI_GRPC_SERVICE_NAME`、`PAY_GRPC_ADDR` / `PAY_GRPC_SERVICE_NAME`
- 前端 SSO 地址：`VITE_SSO_BASE_URL`

主要配置文件：

- `.env`
- `backend/src/main/resources/application.yml`
- `docker-compose.yml`
- `docker-compose.windows.yml`
- `frontend/src/lib/sso.ts`

## 3. 项目模块、功能与对应文件

详细对照表见 `doc/tutorial/getting-started.md` 的「3、项目主要模块、功能、对应文件」。

核心模块（摘要）：

- 认证与会话：`backend/src/main/java/com/ainovel/app/security/`，`frontend/src/contexts/AuthContext.tsx`
- 故事/大纲：`backend/src/main/java/com/ainovel/app/story/`，`frontend/src/pages/Workbench/tabs/StoryConception.tsx`，`frontend/src/pages/Workbench/tabs/OutlineWorkbench.tsx`
- 稿件与 AI Copilot：`backend/src/main/java/com/ainovel/app/manuscript/`，`frontend/src/pages/Workbench/tabs/ManuscriptWriter.tsx`
- v2 能力集：`backend/src/main/java/com/ainovel/app/v2/`，`frontend/src/pages/Workbench/tabs/V2Studio.tsx`
- 世界观：`backend/src/main/java/com/ainovel/app/world/`，`frontend/src/pages/WorldBuilder/`
- 素材库：`backend/src/main/java/com/ainovel/app/material/`，`frontend/src/pages/Material/`
- 设置与提示词：`backend/src/main/java/com/ainovel/app/settings/`，`frontend/src/pages/Settings/`
- 管理后台：`backend/src/main/java/com/ainovel/app/admin/`，`frontend/src/pages/Admin/`

## 5. 文件树与各文件主要功能

详细文件树见 `doc/tutorial/getting-started.md` 的「5、文件树与各文件主要功能」。

## 6. 提示词在哪里调整，如何做提示词优化

详细步骤见 `doc/tutorial/getting-started.md` 的「6、提示词在哪里调整，如何优化」。

关键位置：

- 前端设置页：`frontend/src/pages/Settings/Settings.tsx`
- 前端提示词编辑：`frontend/src/pages/Settings/tabs/WorkspacePrompts.tsx`、`frontend/src/pages/Settings/tabs/WorldPrompts.tsx`
- 后端提示词接口：`backend/src/main/java/com/ainovel/app/settings/SettingsController.java`
- 后端默认模板：`backend/src/main/java/com/ainovel/app/settings/SettingsService.java`
- 提示词持久化表：`sql/schema.sql` 中 `prompt_templates`、`world_prompt_templates`
- v2 API 文档：`doc/api/v2-context.md`、`doc/api/v2-style.md`、`doc/api/v2-analysis.md`、`doc/api/v2-version.md`、`doc/api/v2-export.md`、`doc/api/v2-models.md`、`doc/api/v2-workspace.md`

## 7. 其他上手信息

- 本项目已切换统一登录（SSO），`/login` 与 `/register` 会跳转 SSO，不使用本地账号密码表单。
- 开发环境首次启动会由 `DataInitializer` 注入种子数据，包括管理员账号 `admin / password`（仅开发环境使用）。
- 脚本注意：`build.sh --init` 与 `build_prod.sh --init` 依赖 `deploy/nginx/*.conf`，当前仓库未包含该目录，执行前需先补齐 Nginx 配置文件。
- 本地依赖端口差异：`deploy/docker-compose.yml` 默认 MySQL/Redis 映射为 `3308/6381`，与后端默认 `3306/6379` 不同，需同步调整环境变量。
- Java 运行基线：后端编译目标是 Java 17（`backend/pom.xml`），容器运行镜像为 `eclipse-temurin:21-jre`，建议团队统一版本策略以降低环境差异。
