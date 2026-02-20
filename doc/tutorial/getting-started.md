# AINovel 上手与部署指南

本文用于新同学快速理解并启动 AINovel，重点覆盖：

1. 如何启动项目前后端
2. 新环境部署时要调整哪些配置（连接三方服务）
3. 项目包含哪些模块、功能和对应文件
5. 文件树与各文件主要功能
6. 提示词在哪里调整，如何做提示词优化
7. 其他上手信息

> 端口基线：前端 `11040`，后端 `11041`。  
> 域名基线：测试 `ainovel.seekerhut.com`，正式 `ainovel.aienie.com`。

## 环境准备

- Node.js：建议 `20.x`（至少满足 Vite 6 运行要求）
- JDK：`17+`（`backend/pom.xml` 使用 Java 17）
- Maven：`3.9+`
- Docker + Docker Compose（用于容器化启动）
- Bash 或 PowerShell（执行 `build.sh` / `build_prod.sh` / `build_local.sh` 与对应 `.ps1`）

---

## 1、如何启动项目前后端

### 方案 A：本地进程启动（开发推荐）

1. 启动后端（`11041`）
```bash
cd backend
mvn spring-boot:run
```

2. 启动前端（`11040`）
```bash
cd frontend
npm ci --legacy-peer-deps
npm run dev
```

3. 访问入口

- 前端：`http://127.0.0.1:11040`
- 后端 OpenAPI：`http://127.0.0.1:11041/api/v3/api-docs`
- 后端 Swagger：`http://127.0.0.1:11041/api/swagger-ui/index.html`

说明：

- `frontend/vite.config.ts` 已将 `/api` 代理到 `http://127.0.0.1:11041`。
- 后端所有接口挂载在 `/api` 下（见 `backend/src/main/resources/application.yml` 的 `spring.mvc.servlet.path=/api`）。

### 方案 B：一键构建 + Docker 启动（联调/部署）

```bash
bash build.sh
```

该脚本会：

1. 构建前端并产出 `frontend/dist`
2. 编译后端并产出 `backend/target/app.jar`
3. 用 `docker-compose.yml`（或 Windows 下 `docker-compose.windows.yml`）启动前后端容器

额外说明：

- `build.sh` 会读取 `SUDO_PASSWORD` 环境变量（有 sudo 操作时使用）。
- `build_prod.sh` 会在 `build.sh` 基础上补充生产 Nginx/证书流程。

### 方案 C：一键本地编译并启动（非 Docker）

```bash
bash build_local.sh
```

PowerShell：

```powershell
powershell -ExecutionPolicy Bypass -File .\build_local.ps1
```

说明：

- 会编译前端与后端，然后以本地进程启动服务（不通过 Docker 启动应用）。
- 脚本会自动尝试加载项目根目录的 `.env` 或 `env.txt`（任一存在即可）。

### 方案 D：仅启动本地依赖（MySQL/Redis）

如你只需要数据库和缓存，可单独起 `deploy/docker-compose.yml`：

```bash
cd deploy
docker compose up -d
```

默认映射端口：

- MySQL：`3308 -> 3306`
- Redis：`6381 -> 6379`

如果使用这组端口，要同步调整后端环境变量（见第 2 节）。

---

## 2、新环境部署时需要调整哪些配置（连接三方服务）

### 2.1 必调配置总览

| 配置类别 | 关键变量 | 典型位置 | 作用 |
| --- | --- | --- | --- |
| 数据库 | `MYSQL_HOST` `MYSQL_PORT` `MYSQL_DB` `MYSQL_USER` `MYSQL_PASSWORD` | `.env` / `docker-compose*.yml` / `application.yml` | 连接 MySQL |
| 缓存 | `REDIS_HOST` `REDIS_PORT` `REDIS_PASSWORD` `REDIS_KEY_PREFIX` | 同上 | Redis 缓存与 key 前缀 |
| JWT | `JWT_SECRET` | `.env` / compose | 鉴权签名密钥 |
| SSO 会话校验 | `SSO_SESSION_VALIDATION_ENABLED` `USER_GRPC_ADDR` `USER_GRPC_SERVICE_NAME` `USER_GRPC_SERVICE_TAG` | `.env` / compose / `application.yml` | 远程校验 uid+sid |
| 服务发现 | `CONSUL_ENABLED` `CONSUL_SCHEME` `CONSUL_HOST` `CONSUL_PORT` `CONSUL_DATACENTER` `CONSUL_CACHE_SECONDS` | `.env` / compose / `application.yml` | Consul 发现外部服务 |
| UserService HTTP | `USER_HTTP_SERVICE_NAME` `USER_HTTP_ADDR` | `application.yml` | 管理后台用户查询/封禁透传 |
| AiService gRPC | `AI_GRPC_SERVICE_NAME` `AI_GRPC_ADDR` | `application.yml` | 模型列表/对话/润色 |
| PayService gRPC | `PAY_GRPC_SERVICE_NAME` `PAY_GRPC_ADDR` `EXTERNAL_PROJECT_KEY` | `application.yml` | 通用积分查询、通用->项目兑换、冲回补偿 |
| SSO 页面中转 | `USER_HTTP_SERVICE_NAME` `USER_HTTP_ADDR` | `application.yml` | 后端 `/api/v1/sso/*` 解析 user-service 地址并发起 302 |

### 2.2 推荐落地方式

1. 复制并维护环境变量文件（例如 `.env`）
2. 按环境覆盖 compose 中的默认值（避免写死内网 IP）
3. 检查后端 `application.yml` 中的 fallback 地址是否适合当前环境
4. 配置后端 `USER_HTTP_SERVICE_NAME` / `USER_HTTP_ADDR`，确保后端可解析 user-service HTTP 地址（Consul 优先，fallback 兜底）

### 2.3 新环境最容易漏掉的点

- `docker-compose.yml` 默认 MySQL/Redis 指向 `192.168.5.141`，不改会直连错误目标。
- `docker-compose.windows.yml` 依赖外部网络 `ainovel-deps_default`，并假设该网络内存在 `mysql`/`redis` 服务。
- 若关闭 Consul（`CONSUL_ENABLED=false`），必须保证所有 fallback 地址可达。
- SSO token 中需包含 `sub/uid/sid/role`，否则 `JwtAuthFilter` 无法建立登录态。

---

## 3、项目主要模块、功能、对应文件

### 3.1 前端模块

| 模块 | 主要功能 | 关键文件 |
| --- | --- | --- |
| 路由与权限 | 公共页、用户页、管理页路由与守卫 | `frontend/src/App.tsx` |
| 登录态管理 | token 持久化、用户信息刷新、管理员识别 | `frontend/src/contexts/AuthContext.tsx` |
| SSO 跳转与回调 | 前端请求后端 SSO 中转、回调解析 token + `state` 校验 | `frontend/src/lib/sso.ts` `frontend/src/pages/auth/SsoCallback.tsx` `backend/src/main/java/com/ainovel/app/auth/SsoController.java` |
| 工作台 | 故事构思/故事管理/大纲/写作/素材检索 | `frontend/src/pages/Workbench/Workbench.tsx` + `frontend/src/pages/Workbench/tabs/*` |
| v2 能力页面 | 知识库、知识图谱、增强写作页与联调入口 | `frontend/src/pages/Workbench/tabs/LorebookPanel.tsx` `frontend/src/pages/Workbench/tabs/KnowledgeGraphTab.tsx` `frontend/src/pages/Workbench/tabs/ManuscriptWriter.tsx` `frontend/src/pages/Workbench/tabs/V2Studio.tsx` |
| 世界观 | 世界列表、编辑、模块化构建 | `frontend/src/pages/WorldBuilder/WorldBuilderPage.tsx` + `frontend/src/pages/WorldBuilder/components/*` |
| 素材库 | 新建、上传、审核、检索、查重合并 | `frontend/src/pages/Material/MaterialPage.tsx` + `frontend/src/pages/Material/tabs/*` |
| 设置与体验 | 提示词、风格画像、模型偏好、工作台体验配置 | `frontend/src/pages/Settings/Settings.tsx` `frontend/src/pages/Settings/tabs/*` |
| 管理后台 | 仪表盘、用户管理、系统设置 | `frontend/src/pages/Admin/*` |
| API 访问层 | 统一请求、token 注入、DTO 转换 | `frontend/src/lib/mock-api.ts` |

### 3.2 后端模块

| 模块 | 主要功能 | 关键文件 |
| --- | --- | --- |
| 应用入口 | Spring Boot 启动 | `backend/src/main/java/com/ainovel/app/AiNovelApplication.java` |
| 鉴权与安全 | JWT 解析、会话校验、系统开关 | `backend/src/main/java/com/ainovel/app/security/*` |
| SSO 入口中转 | `/api/v1/sso/login|register` 302 到 user-service | `backend/src/main/java/com/ainovel/app/auth/SsoController.java` |
| 用户中心 | 资料、统计、本地签到、兑换码、通用积分兑换 | `backend/src/main/java/com/ainovel/app/user/UserController.java` |
| 故事与大纲 | 故事/角色/大纲增删改查 | `backend/src/main/java/com/ainovel/app/story/*` |
| 稿件 | 稿件生成、保存、角色变化分析 | `backend/src/main/java/com/ainovel/app/manuscript/*` |
| v2 业务域 | v2 七大模块 API 与访问守卫 | `backend/src/main/java/com/ainovel/app/v2/*` |
| 世界观 | 定义、编辑、发布预检、模块生成 | `backend/src/main/java/com/ainovel/app/world/*` |
| 素材 | 素材 CRUD、上传任务、审核、检索 | `backend/src/main/java/com/ainovel/app/material/*` |
| 提示词与设置 | 提示词模板 CRUD、元数据、全局配置 | `backend/src/main/java/com/ainovel/app/settings/*` |
| 管理后台 API | 用户管理聚合、系统配置 | `backend/src/main/java/com/ainovel/app/admin/AdminController.java` |
| 外部服务集成 | Consul 发现、User/AI/Pay 客户端 | `backend/src/main/java/com/ainovel/app/integration/*` |
| gRPC 协议 | AI/支付/用户服务 proto | `backend/src/main/proto/*` |

---

## 5、文件树与各文件主要功能

```text
AINovel/
├─ frontend/                      # React 前端
│  ├─ src/
│  │  ├─ App.tsx                  # 路由总入口（含权限路由）
│  │  ├─ contexts/AuthContext.tsx # 登录态与用户上下文
│  │  ├─ lib/mock-api.ts          # 统一 API 访问层
│  │  ├─ lib/sso.ts               # SSO 中转 URL 组装与 state 管理
│  │  ├─ pages/Workbench/         # 工作台主流程
│  │  │  ├─ tabs/LorebookPanel.tsx      # 知识库管理
│  │  │  ├─ tabs/KnowledgeGraphTab.tsx  # 知识图谱查询
│  │  │  ├─ tabs/ManuscriptWriter.tsx   # 增强写作台（上下文/版本/导出）
│  │  │  └─ tabs/V2Studio.tsx           # v2 联调与验收入口
│  │  ├─ pages/WorldBuilder/      # 世界观编辑器
│  │  ├─ pages/Material/          # 素材中心
│  │  ├─ pages/Settings/          # 提示词设置与帮助页
│  │  └─ pages/Admin/             # 管理后台页面
│  ├─ vite.config.ts              # 开发端口/代理
│  ├─ nginx.conf                  # Linux 容器内前端反代
│  └─ nginx.windows.conf          # Windows 容器内前端反代
├─ backend/                       # Spring Boot 后端
│  ├─ src/main/java/com/ainovel/app/
│  │  ├─ security/                # JWT 与远程会话校验
│  │  ├─ auth/                    # SSO 登录/注册入口中转
│  │  ├─ integration/             # 外部服务发现与调用
│  │  ├─ story/ manuscript/       # 创作主业务
│  │  ├─ v2/                      # v2 七大模块 API
│  │  ├─ material/ world/         # 素材与世界观
│  │  ├─ settings/ admin/ user/   # 设置、管理、用户中心
│  │  └─ AiNovelApplication.java  # 应用入口
│  ├─ src/main/resources/application.yml # 后端核心配置
│  ├─ src/main/proto/             # gRPC 协议定义
│  └─ pom.xml                     # Maven 依赖与构建
├─ sql/schema.sql                 # 数据表结构参考
├─ docker-compose.yml             # 前后端容器编排（Linux/通用）
├─ docker-compose.windows.yml     # Windows 容器编排
├─ build.sh                       # 构建+部署脚本（测试服，Docker）
├─ build_prod.sh                  # 生产部署脚本（Docker）
├─ build_local.sh                 # 本地编译并启动（非 Docker）
├─ build.ps1                      # build.sh 的 PowerShell 版本
├─ build_prod.ps1                 # build_prod.sh 的 PowerShell 版本
├─ build_local.ps1                # build_local.sh 的 PowerShell 版本
├─ doc/                           # 项目文档（API/模块/测试/教程）
└─ design-doc/                    # 版本设计文档
```

---

## 6、提示词在哪里调整，如何优化

### 6.1 调整入口

运行时配置（推荐）：

- 页面：`/settings`
- 前端文件：`frontend/src/pages/Settings/tabs/WorkspacePrompts.tsx`、`frontend/src/pages/Settings/tabs/WorldPrompts.tsx`
- 后端接口：`/api/v1/prompt-templates*`、`/api/v1/world-prompts*`

后端默认模板（代码级）：

- `backend/src/main/java/com/ainovel/app/settings/SettingsService.java`
  - `defaultPromptTemplates(...)`
  - `defaultWorldPrompts(...)`

数据库持久化：

- `prompt_templates`（工作区提示词）
- `world_prompt_templates`（世界观提示词）
- 定义位于 `sql/schema.sql`

### 6.2 提示词优化建议流程

1. 先在设置页编辑并保存，不要先改代码默认值。
2. 用同一输入样本做 A/B（例如同一段 `idea` 或同一场景 `sceneSummary`）。
3. 固定输出结构要求（JSON/章节数/字段完整性），减少模型自由度。
4. 把变量显式化（如 `{tone}`、`{worldContext}`），避免在提示词里依赖隐式上下文。
5. 对失败案例沉淀到提示词帮助元数据中（`getPromptMetadata` / `getWorldPromptMetadata`）。
6. 当“默认模板”确实需要升级，再回写 `SettingsService` 中的默认值。

### 6.3 额外注意

- 世界观字段精修在 `WorldService.refineField(...)` 有兜底指令，未传 `instruction` 会使用默认文案。
- 若要统一团队默认提示词，建议配套一次 DB 初始化脚本，而不是只改 Java 默认值。

---

## 7、其他上手信息

1. 统一登录链路：`/login`、`/register` 会先请求后端 `/api/v1/sso/login|register`，由后端 302 到 user-service 页面；回调页 `/sso/callback` 从 URL hash 读取 `access_token` 并强制校验 `state` 后再写入本地存储。
2. 开发种子数据：`DataInitializer` 会在首次启动时创建示例用户/故事/世界/素材，包含管理员账号 `admin / password`。
3. 脚本依赖缺口：`build.sh --init` 与 `build_prod.sh --init` 使用 `deploy/nginx/ainovel.conf`、`deploy/nginx/ainovel_prod.conf`，当前仓库未包含该目录，需自行提供。
4. 端口差异：`deploy/docker-compose.yml` 的 MySQL/Redis 暴露端口是 `3308/6381`，若与后端默认端口不一致，需同步覆盖 `MYSQL_PORT`、`REDIS_PORT`。
5. Java 运行基线：后端编译目标是 Java 17（`pom.xml`），容器运行镜像为 `eclipse-temurin:21-jre`，建议团队统一版本策略以降低环境差异。

---

## 附：常用排查清单

1. 前端页面空白：检查 `frontend/dist` 是否存在、`nginx.conf` 是否正确挂载。
2. `/api` 401：确认浏览器已写入 `token`，或确认 SSO 回调是否成功。
3. 通用积分兑换失败：优先检查 PayService 地址（Consul 或 fallback）与 `EXTERNAL_PROJECT_KEY`；本地签到/兑换码失败优先检查 AINovel 本地数据库与系统配置。
4. Copilot 无模型：检查 AiService gRPC 连通性和 `AI_GRPC_*` 配置。
5. 管理端用户列表失败：检查 `USER_HTTP_*` 以及管理员 JWT 是否透传。
