# 外部服务与基础设施集成

本文记录 AINovel 如何消费外部能力，不替代跨服务正式契约。

## 配置来源

- 实际运行地址来自 `env.txt` 或同名环境变量。
- 开发网站使用 `ainovel.localhut.com`。
- `aienie-projects` 的开发默认上游为 `aiservice.seekerhut.com`、`payservice.seekerhut.com`、`userservice.seekerhut.com`，部署环境可显式覆盖。
- MySQL、Redis、Qdrant 等共享基础设施地址由环境提供，`build.sh` 不创建这些依赖。

## 三服务边界

| 服务 | AINovel 使用方式 | 鉴权 |
| --- | --- | --- |
| user-service | SSO 页面、授权码交换、用户目录和 `uid + sid` 会话校验 | HTTP SSO；gRPC `x-internal-token` |
| ai-service | 模型列表、对话、嵌入和写作生成 | HMAC metadata |
| pay-service | 通用积分余额与通用转专属扣减 | Bearer service JWT |

正式契约优先读取 `/home/duwei/aienie-doc/interfaces/<service>/`。当前存在 user-service 契约；若 ai-service 或 pay-service 契约目录缺失，以本仓库固定 proto 副本和客户端实现定位当前依赖，并将补齐正式契约列为跨服务协调事项。

## 会话校验

1. AINovel 先按本地 JWT 密钥验证令牌。
2. 对 user-service 签发且本地不能验签的令牌，提取 `uid + sid`。
3. `UserSessionValidator` 调用 `UserAuthService.ValidateSession`；上游无效或不可达时拒绝建立本地登录态。

## 部署约束

- `build.sh` 只执行 Docker Compose 构建与部署。
- `env.txt` 存在时通过 Compose `--env-file` 插值，并只读挂载进后端容器加载完整运行时配置。
- 外部安全配置由 `ExternalSecurityStartupValidator` 在启动期校验。
- gRPC TLS/plaintext 由 `EXTERNAL_GRPC_TLS_ENABLED` 和 `EXTERNAL_GRPC_PLAINTEXT_ENABLED` 控制。
- 数据库结构只通过 Flyway 演进，不使用 Hibernate 自动 DDL 管理运行库。
