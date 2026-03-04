# 集成/系统测试用例（更新于 2026-03-04）

- **端口与联调配置**：前端 `11040`、后端 `11041`；前端 `/api` 请求经 Nginx/Vite 转发到后端 `11041`。
- **普通用户认证链路（SSO）**：点击“登录/注册”或访问 `/login`/`/register` → 请求 AINovel 后端 `/api/v1/sso/login|register` → 后端 302 到 userservice → 回跳 `/sso/callback#access_token=...&state=...` → 前端 `state` 校验通过后写入 `localStorage.token`。
- **管理员认证链路（本地账密）**：访问 `/admin/login`，调用 `/api/v1/admin-auth/login` 成功后写入 `localStorage.admin_token`，再通过 `/api/v1/admin-auth/me` 做路由守卫。
- **SSO 安全回归**：篡改回跳 hash 中 `state` 后访问 `/sso/callback`，应拒绝落 token 并跳回 `/login`。
- **微服务发现（Consul）**：会话校验优先通过 Consul `health/service` 发现 userservice gRPC；发现失败时回退 `USER_GRPC_ADDR`，应在超时内失败且不阻塞主请求。

- **管理后台能力**：
  - `/admin/dashboard` 指标加载成功。
  - `/admin/users` 用户列表可查询，封禁/解封可执行。
  - `/admin/settings` 注册/维护/签到区间与 SMTP 参数保存后回显一致。
  - `/api/v1/admin/credits/grant`、`/api/v1/admin/redeem-codes` 可正常调用。

- **用户侧主流程**：
  - `/dashboard`、`/novels`、`/novels/create`、`/workbench`、`/worlds/create`、`/materials`、`/settings`、`/profile` 路由可达。
  - 个人中心签到、兑换码、通用积分兑换链路可达，流水可查询。

- **故事/大纲/稿件链路**：
  - 一句话构思（`/api/v1/conception`）成功创建故事。
  - 大纲生成支持 20 章，每章小节数强制 `5-7`。
  - 正文生成（`/api/v1/manuscripts/{id}/scenes/{sceneId}/generate`）应满足 `2800-3200` 汉字门禁。

- **AI Copilot**：
  - `/api/v1/ai/chat` 可返回内容并产生积分扣减。
  - 模型回退策略可避免默认命中 embedding/ocr 模型导致失败。

- **素材流程**：创建素材→上传 TXT→轮询完成→审核通过→列表可见；检索接口返回得分排序。
- **世界管理/编辑**：`/worlds` 列表加载、创建、编辑与发布预检可完成并更新状态。
- **设置/提示词**：读取、更新、重置提示词配置；帮助元数据接口可用于渲染帮助页。

- **部署验证**：执行 `sudo -E bash build.sh` 后，前后端容器可用，域名 `https://ainovel.seekerhut.com` 可访问。
- **OpenAPI 验证**：默认 `SPRINGDOC_*` 为 `false`，仅在显式开启时才验证 `/api/v3/api-docs` 与 `/api/swagger-ui/*`。
- **公共依赖连通**：MySQL、Redis、Qdrant 连通；在 16K page size 环境下，本地 qdrant 需使用 `v1.8.3`。
