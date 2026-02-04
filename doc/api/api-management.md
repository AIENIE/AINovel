# 接口管理（ApiManagementController）

本文档对应后端 `com.ainovel.app.admin.ApiManagementController`，用于“接口管理页面 + 机器可读接口”。

统一前缀：`/api/admin/api-management`（本项目 `spring.mvc.servlet.path=/api`）。

权限：需要管理员权限（`ROLE_ADMIN`）。

---

## 1) 获取接口管理摘要

`GET /api/admin/api-management/summary`

用途：
- 返回服务基本信息与相关入口 URL（Swagger、OpenAPI 代理、Bundle）。

返回：
- `service`：服务信息（name/version/environment 等）
- `endpoints`：`swaggerUi` / `openapiJson` / `bundle`
- `generatedAt`：生成时间（ISO-8601 字符串）

---

## 2) 获取 OpenAPI（代理 springdoc /v3/api-docs）

`GET /api/admin/api-management/openapi`

用途：
- 以管理员权限统一获取 OpenAPI JSON（内部请求 `/api/v3/api-docs`），供前端与 AI Agent 使用。

返回：
- OpenAPI JSON（`application/json`）

---

## 3) 获取 Bundle（summary + openapi）

`GET /api/admin/api-management/bundle`

用途：
- 一次返回 `summary + openapi + openapiSha256`，用于 AI Coding Agent 拉取并缓存接口信息。

请求头（可选）：
- `If-None-Match: "<etag>"`：若与当前 `openapiSha256` 匹配，返回 `304 Not Modified`

返回：
- `summary`：同 `/summary`
- `openapi`：OpenAPI JSON（对象）
- `openapiSha256`：OpenAPI JSON 的 sha256（hex）
- `generatedAt`：生成时间（ISO-8601 字符串）
