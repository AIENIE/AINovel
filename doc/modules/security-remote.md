# 远程会话校验模块

## 职责

- 先尝试按本地 JWT 密钥验证令牌。
- 对 user-service 签发但本地无法验签的令牌，提取 `uid + sid` 并调用 `UserAuthService.ValidateSession`。
- 只有远程会话有效时才建立 AINovel 登录态；失效或不可达时拒绝请求。

## 当前实现

- `UserSessionValidationProperties` 读取 `sso.session-validation.enabled`、`timeout-ms` 和 `grpc-address`。
- `ConsulUserGrpcEndpointResolver` 仅保留历史类名，当前直接解析配置的 `USER_GRPC_ADDR`，不请求 Consul。
- `UserSessionValidator` 使用 `x-internal-token` 调用受保护 gRPC 方法。

## 配置

- `SSO_SESSION_VALIDATION_ENABLED`
- `USER_SESSION_GRPC_TIMEOUT_MS`
- `USER_GRPC_ADDR`
- `EXTERNAL_USER_INTERNAL_GRPC_TOKEN`

共享账号重新登录可能刷新旧 `sid`，旧令牌返回 403 属于正常会话失效行为。
