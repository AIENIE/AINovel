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
