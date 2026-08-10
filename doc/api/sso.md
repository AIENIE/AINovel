# SSO API

## 统一登录入口中转
- `GET /api/v1/sso/login`：由后端解析 user-service HTTP 地址并 `302` 到 user-service 登录页。
- `GET /api/v1/sso/register`：由后端解析 user-service HTTP 地址并 `302` 到 user-service 注册页。

## 请求参数
- `next`（可选）：登录/注册成功后前端内部回跳路径；仅接受站内相对路径（默认 `/workbench`）。
- `state`（必填）：前端生成的一次性随机值，用于回调防伪校验。

## 响应
- `302`：`Location` 指向 user-service 的 `/sso/login` 或 `/register`，并携带 `redirect` 与 `state`。
- `400`：`state` 为空（`STATE_REQUIRED`）。
- `502`：无法解析 user-service 地址（`USER_SERVICE_UNAVAILABLE`）。

## 回调换取会话
- user-service 完成登录/注册后回跳 `/sso/callback?code=...&state=...`，不再在 URL fragment 中返回 `access_token`。
- 前端先校验 `state`，再调用 `POST /api/v1/sso/session`。
- 请求体：
  - `code`：user-service 回跳的一次性授权码。
  - `redirect`：发起登录时传给 user-service 的原始 callback URL；只移除回跳追加的 `code`、`state`，保留 `next`。
- 注意：`redirect` 必须与授权码签发时绑定的 callback 字符串一致；前端重建该 URL 时保留 `next=/path` 中的路径斜杠，不应重新编码为 `next=%2Fpath`。
- 后端用 `application/x-www-form-urlencoded` 调用 user-service `POST /sso/token`，表单字段为 `code` 与 `redirect`。
- 成功交换 user-service 授权码后，AINovel 签发自身的短期访问令牌，并返回 `accessToken/userId/username/sessionId/rememberDays/expiresIn`；前端沿用既有 `acceptToken` 建立本地登录态。

## 环境变量配置
- `USER_HTTP_ADDR`：提供 user-service HTTP 入口的静态地址。
- `SSO_CALLBACK_ORIGIN`：可选。配置后，后端回调 `redirect` 固定使用该 origin；未配置时按请求头（`X-Forwarded-*`/`Origin`/`Referer`）推断。
- `VITE_SSO_ENTRY_BASE_URL`：前端构建变量，控制 `buildSsoUrl()` 的后端入口基址；为空时使用当前页面 `window.location.origin`。

## 业务令牌校验
- `/api/v1/sso/session` 返回的 `accessToken` 必须通过 AINovel 配置的签名密钥、`JWT_ISSUER` 与 `JWT_AUDIENCE` 校验，并包含有效的 `sub + uid + sid`。
- 签名、issuer 或 audience 任一校验失败即拒绝访问（`403`）；后端不会解析未验签 payload，也不会以远程 `validateSession` 成功作为降级或 fallback。
- 本地令牌通过密码学校验后，启用会话校验时才以其中的 `uid + sid` 调用 user-service `ValidateSession`；上游无效或不可达时同样拒绝建立登录态。
- 普通 SSO 令牌中的 `ROLE_ADMIN` 不授予本地后台权限；后台只接受不透明 Cookie 会话解析出的 `AUTH_LOCAL_ADMIN`。
