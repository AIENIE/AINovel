# AINovel：接口管理页面 + 机器可读接口（可选 MCP）开发方案

本文以 `AINovel` 为例，按与 `ai-service` 相同的思路落地“统一接口管理页面”，实现目标：

- **REST（本服务对外接口）**：提供 Swagger UI 体验（**前端内嵌 swagger-ui-react**，后端仅提供 OpenAPI JSON）。
- **gRPC**：本服务**不对外提供 gRPC Server**（仅作为 gRPC Client 调用 userservice 的 ValidateSession），因此不需要 grpcui（可选：仅展示依赖微服务的 grpcui 链接）。
- **统一入口页（A 方式）**：复用前端 `/admin/*` 管理后台，新增 `/admin/api-management` 聚合入口。
- **机器可读接口**：提供 `/api/admin/api-management/*`，输出 OpenAPI + bundle，供其它 AI Coding Agent 拉取。
- **可选 MCP**：sidecar 把机器可读接口封装为 MCP tools（本仓库不强制实现）。

---

## 1. 关键结论：为什么不直接用后端 Swagger UI

本项目在引入 `springdoc-openapi-starter-webmvc-ui` 时曾出现 Spring MVC `PatternParseException`（`**` 路径解析报错）导致启动失败。

因此采用更稳妥的组合：

- 后端只引入 **springdoc API**：`org.springdoc:springdoc-openapi-starter-webmvc-api`
- 前端页面用 **swagger-ui-react** 直接加载 `/api/v3/api-docs`

---

## 2. 路由与端口规划

### 2.1 服务端口（项目约定）

- 前端：对外 `10001`
- 后端：对外 `20001`
- 依赖：MySQL `3308`、Redis `6381`（均为默认端口 +2）

### 2.2 后端统一前缀（重要）

后端通过 `spring.mvc.servlet.path: /api` 提供统一前缀：

- 业务接口对外表现为 `/api/*`
- OpenAPI 对外为 `/api/v3/api-docs`

---

## 3. 面向人：统一接口管理页面（A 方式）

入口：`/admin/api-management`

页面能力：

- 一键加载内嵌 Swagger UI（OpenAPI 来自 `/api/v3/api-docs`）
- 一键拉取并展示机器可读 bundle（`/api/admin/api-management/bundle`）
- 调用时自动携带 `localStorage.token` 作为 `Authorization: Bearer ...`

---

## 4. 面向机器：机器可读接口（给 AI Agent）

统一前缀：`/api/admin/api-management`（管理员可访问）

- `GET /api/admin/api-management/summary`
  - 返回服务元信息（name/version/环境）+ 关键端点 URL（openapi/bundle/ui）
- `GET /api/admin/api-management/openapi`
  - 代理 `GET http://127.0.0.1:{port}/api/v3/api-docs`，用于稳定拉取 OpenAPI JSON
- `GET /api/admin/api-management/bundle`
  - 一次返回 `{ summary, openapi, openapiSha256, generatedAt }`
  - 支持 `ETag`/`If-None-Match`，减少重复传输

权限建议：

- `/api/v3/api-docs/**`：允许匿名访问（便于 Swagger UI 加载；生产可配合宿主机 Nginx 限制）
- `/api/admin/api-management/**`：仅管理员 `ROLE_ADMIN`

---

## 5. 后端开发要点（AINovel 实现）

### 5.1 依赖与配置

- `backend/pom.xml`：添加 `org.springdoc:springdoc-openapi-starter-webmvc-api`
- `backend/src/main/resources/application.yml`：
  - `spring.mvc.servlet.path: /api`
  - `api-management.enabled`（默认开启，可通过环境变量关闭）

### 5.2 控制器与服务

- `backend/src/main/java/com/ainovel/app/admin/ApiManagementController.java`
  - `@PreAuthorize(\"hasAuthority('ROLE_ADMIN')\")`
  - 提供 `summary/openapi/bundle`
- `backend/src/main/java/com/ainovel/app/admin/ApiManagementService.java`
  - 代理拉取 OpenAPI
  - 生成 bundle，并计算 `sha256` 与 `ETag`

### 5.3 安全策略

`backend/src/main/java/com/ainovel/app/security/SecurityConfig.java` 放行 OpenAPI：

- `/api/v3/api-docs/**`、`/v3/api-docs/**`

---

## 6. 前端开发要点（AINovel 实现）

### 6.1 依赖

- 安装 `swagger-ui-react` 与 `swagger-ui`（用于内嵌 Swagger UI）

### 6.2 页面与路由

- `frontend/src/pages/Admin/ApiManagement.tsx`
  - “显示/隐藏 Swagger UI”
  - “获取 Bundle”
  - swagger 请求通过 `requestInterceptor` 追加 Authorization
- `frontend/src/App.tsx`：增加 `/admin/api-management`
- `frontend/src/components/layout/AdminLayout.tsx`：新增菜单入口“接口管理”

---

## 7. 部署与运行（Linux vs Windows）

### 7.1 Linux（推荐）：host 网络模式

- `docker-compose.yml`：前后端均 `network_mode: host`
- `frontend/nginx.conf`：`/api` 反向代理到 `127.0.0.1:20001`

### 7.2 Windows（Docker Desktop）：端口映射 + 容器网络

- `docker-compose.windows.yml`：端口映射 10001/20001；后端加入外部网络 `ainovel-deps_default` 直连 `mysql/redis`
- `frontend/nginx.windows.conf`：`/api` 反向代理到 `backend:20001`（容器内 DNS 解析 service 名）
- `deploy/docker-compose.yml`：启动依赖 MySQL/Redis（3308/6381）

注意：`deploy/mysql/data/`、`deploy/redis/data/` 需要是目录（已通过 `.gitkeep` 固定目录结构），并建议在 `.gitignore` 忽略实际数据文件。

---

## 8. 测试与验收

### 8.1 后端单测（建议保留）

- `backend/src/test/java/com/ainovel/app/admin/ApiManagementControllerTest.java`
  - 验证 `/admin/api-management/bundle`（注意测试环境下 servlet path 已是 `/api`）

### 8.2 Playwright（端到端）

在 Windows 无法写 hosts 的情况下，可用以下地址做自动化验证：

- `http://127.0.0.1:10001/admin/api-management`

验证点：

- 点击“获取 Bundle”后 textarea 出现 JSON（包含 `summary/openapi`）
- 点击“显示 Swagger UI”后 Swagger UI 渲染，并从 `/api/v3/api-docs` 加载 spec

---

## 9. 可选：MCP sidecar（给其它 AI Agent）

当需要 MCP tools 方式暴露接口信息时：

- tool：`get_api_bundle` / `get_openapi`
- tool 实现：HTTP 调用本服务
  - `GET /api/admin/api-management/bundle`
  - `GET /api/admin/api-management/openapi`

安全建议：

- MCP 使用专用 token（不要复用浏览器 Admin JWT）
- 或仅在内网/测试环境启用 MCP

---

## 10. 验收清单

- `/admin/api-management` 可加载 Swagger UI，并可拉取 bundle
- `GET /api/admin/api-management/bundle` 返回结构稳定（含 openapi）
- Windows compose 下 `/api/*` 前端代理正常（使用 `nginx.windows.conf`）
- 生产默认：bundle 仅管理员可访问；OpenAPI 可按需对外放行（建议通过宿主机 Nginx 做访问控制）
