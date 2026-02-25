# 外部微服务接口发现说明

- 版本：v1.2（安全加固后）
- 最近验证时间：2026-02-25

## 目标
- 优先通过 Consul 发现 UserService / AiService / PayService gRPC 实例。
- 在 Consul 不可达时，按配置回退到静态地址，保证核心链路不中断。
- 对发现结果执行最小可用性校验（健康检查 + 关键调用）。

## 发现流程
1. 读取配置：
   - `CONSUL_HOST`、`CONSUL_PORT`、`CONSUL_SCHEME`
   - `*_GRPC_SERVICE_NAME`
   - `*_GRPC_ADDR`（fallback）
2. 查询健康实例：
   - `GET /v1/health/service/<service-name>?passing=true`
3. 解析地址：
   - 优先 `Service.Address + Service.Port`
   - 无可用实例时使用 `static://host:port` 回退配置
4. 发现后校验（按服务鉴权）：
   - user-service：带 `x-internal-token` 调用 `ValidateSession`
   - ai-service：带 HMAC metadata 调用 `ListModels`
   - pay-service：带 Bearer service JWT 调用 `GetProjectBalance` 或 `GetPublicBalance`

## 鉴权与反射注意事项
- user-service 反射/受保护方法默认要求 `x-internal-token`。
- ai-service 业务方法默认要求 `x-aienie-*` HMAC metadata。
- pay-service 业务方法默认要求 `authorization: Bearer <service_jwt>`。
- 仅当服务开启 reflection 且鉴权头正确时，`grpcurl list/describe` 才会成功。

## 运行时约束
- `APP_TIME_ZONE` 统一为 `Asia/Shanghai`（UTC+8），避免跨服务鉴权时间偏差。
- 生产与测试环境均禁止在代码中写死服务 IP；统一走环境变量与服务发现。
