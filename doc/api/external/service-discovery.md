# 外部微服务地址与鉴权说明

- 版本：当前实现
- 地址来源：`env.txt` 或同名环境变量；不使用 Consul 运行时发现。

## 地址

| 服务 | HTTP/gRPC 配置 |
| --- | --- |
| user-service | `USER_HTTP_ADDR`、`USER_GRPC_ADDR` |
| ai-service | `AI_GRPC_ADDR` |
| pay-service | `PAY_GRPC_ADDR` |

服务地址可使用 `static://host:port`、`host:port` 或带协议的地址。TLS/plaintext 行为由 `EXTERNAL_GRPC_TLS_ENABLED` 与 `EXTERNAL_GRPC_PLAINTEXT_ENABLED` 控制。

## 最小校验

- user-service：带 `x-internal-token` 校验会话。
- ai-service：带 HMAC metadata 调用业务方法。
- pay-service：带 Bearer service JWT 读取余额或执行转换。

外部安全配置不完整时，`ExternalSecurityStartupValidator` 会按 fail-fast 配置阻止服务启动。地址、令牌或上游健康问题应在部署环境修复，不通过临时 host 映射或运行时绕过处理。
