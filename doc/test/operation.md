# 操作步骤（端到端）

> 当前基线端口：前端 `11040`，后端 `11041`。

1. **部署**
   - 执行：`sudo -E bash build.sh`
   - 访问：`https://ainovel.localhut.com/`

2. **普通用户登录（SSO）**
   - 访问 `/login` 或点击首页“登录”，前端会请求 `/api/v1/sso/login` 并跳转 userservice。
   - 成功后回跳 `/sso/callback?code=...&state=...`，前端校验 `state` 后调用 `/api/v1/sso/session` 兑换 token，再写入 `localStorage.token`。
   - 若 `state` 缺失/不匹配，前端拒绝兑换 token 并返回 `/login`。

3. **管理员登录（本地账密）**
   - 访问 `/admin/login`。
   - 使用 `ADMIN_USERNAME` / `ADMIN_PASSWORD` 调用 `/api/v1/admin-auth/login`。
   - 成功后写入 `localStorage.admin_token` 并进入 `/admin/dashboard`。

4. **个人中心（积分/兑换）**
   - 进入 `/profile`，确认项目积分/通用积分/总余额展示。
   - 输入兑换码执行兑换，确认余额变化。
   - 执行“通用积分兑换项目积分”，确认前后余额正确。
   - `POST /api/v1/user/password` 固定返回 501（密码由 SSO 管理）。

5. **后台管理（管理员）**
   - `/admin/dashboard`：业务运营指标。
   - `/admin/users`：项目用户与创作资产统计。
   - `/admin/materials`：待审素材与重复候选。
   - `/admin/assets`：故事/世界观/稿件只读审计。
   - `/admin/quality`：质量巡检运行记录。
   - `/admin/credits`：本地项目专属兑换码与积分流水。
   - `/admin/ops`：请求指标、依赖健康、结构化记录、派生告警与脱敏诊断。
   - `/admin/settings`：维护模式。

6. **故事与大纲**
   - `/novels/create` 创建故事。
   - `/workbench?id=...` 执行故事管理、角色管理。
   - 创建大纲并调用章节生成：目标 20 章，每章 5-7 节。

7. **稿件与 AI 生成**
   - 创建稿件后逐节生成正文：`/api/v1/manuscripts/{id}/scenes/{sceneId}/generate`。
   - 验证每节汉字数 `2800-3200`。
   - 验证自动保存/手动保存与重新加载一致。

8. **素材库**
   - `/materials` 手工创建素材。
   - 上传 TXT 并轮询完成，审核通过后列表可见。
   - 可编辑/删除素材，工作台检索可命中。

9. **世界构建**
   - `/worlds/create` 创建世界草稿。
   - `/world-editor?id=...` 保存模块字段、预检/发布并检查状态更新。

10. **设置与提示词**
   - `/settings` 更新工作区提示词与世界提示词。
   - 验证帮助页路由与变量元数据可用。

11. **可选接口烟测**
   - 未登录访问 `/api/v1/user/profile` 应 403。
   - 管理员登录后：`/api/v1/admin/users` 应 200。
   - 管理员登录后：`/api/v1/admin/ops/summary` 应 200。
   - 管理员登录后：`/api/v1/admin/ops/dependencies` 应返回依赖状态列表。
   - 管理员登录后：`/api/v1/admin/ops/events` 在 ES 不可用时应返回 `available=false`。
   - 角色列表：`/api/v1/story-cards/{id}/character-cards` 应 200。

12. **网络代理注意事项**
   - 若机器配置了 `http_proxy/https_proxy`，本地域名调试请使用：
     - `curl --noproxy '*' -k https://ainovel.localhut.com/api/...`
