# 远程会话校验模块

## 职责
- 在 JWT 解析后，对 `uid + sid` 执行远程会话有效性校验。
- 优先通过 Consul 发现 userservice gRPC 实例，发现失败时回退静态地址。
- 在远程依赖不可达时快速失败，避免接口长时间阻塞。
- 对 userservice 下发但签名密钥与本服务不同的 token，允许在 `uid + sid` 远程校验通过后完成鉴权落地。

## 关键实现
- `UserSessionValidator`：
  - 维护 gRPC 连接；
  - 调用 `validateSession`；
  - 对不可达目标执行短超时 TCP 探测。
- `JwtAuthFilter`：
  - 先按本地密钥做标准 JWT 验签；
  - 验签失败时，仅在 `UserSessionValidator` 可用且 `uid + sid` 校验通过的前提下，使用 token payload 建立登录态；
  - 对无法远程验证的未验签 token 一律拒绝。
- `ConsulUserGrpcEndpointResolver`：
  - 调用 Consul `v1/health/service/{service}`；
  - 缓存健康实例结果。
- `UserSessionValidationProperties`：
  - 管理超时、Consul 地址、service 名、回退 gRPC 地址等配置。

## 关键配置
- `sso.session-validation.enabled`
- `sso.session-validation.timeout-ms`
- `sso.session-validation.consul.*`
- `sso.session-validation.grpc-fallback-address`
