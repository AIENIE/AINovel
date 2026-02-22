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

## 环境变量配置
- `USER_HTTP_SERVICE_NAME` / `USER_HTTP_ADDR`：控制 user-service HTTP 入口解析（Consul 优先，fallback 兜底）。
- `SSO_CALLBACK_ORIGIN`：可选。配置后，后端回调 `redirect` 固定使用该 origin；未配置时按请求头（`X-Forwarded-*`/`Origin`/`Referer`）推断。
- `VITE_SSO_ENTRY_BASE_URL`：前端构建变量，控制 `buildSsoUrl()` 的后端入口基址；为空时使用当前页面 `window.location.origin`。

## 鉴权兼容说明
- 回跳后的 `access_token` 由 user-service 签发，AINovel 在访问业务接口（如 `GET /api/v1/user/profile`）时会按以下顺序鉴权：
  1. 优先按本地 JWT 密钥验签并解析；
  2. 若验签失败，但 token 中可解析到 `uid + sid`，且远程 `validateSession` 校验通过，则允许建立登录态；
  3. 未通过远程会话校验的 token 将被拒绝（`403`）。
