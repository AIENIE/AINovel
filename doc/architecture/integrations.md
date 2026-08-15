# 外部服务与基础设施集成

本文记录 AINovel 如何消费外部能力，不替代跨服务正式契约。

## 配置来源

- 标准部署只从仓库外、本地 Git 忽略的 `0600` 普通文件 `env.txt` 加载实际运行地址与凭据；宿主同名环境变量不能补齐或覆盖该文件契约。
- 本地测试网站使用 `localainovel.testhut.top`。
- 本地三服务地址由同一份 `env.txt` 显式配置，不使用旧 `testaiservice`、`testpayservice` 或 `testuserservice` 域名作为隐式 fallback。
- MySQL、Redis、Qdrant 等共享基础设施地址由环境提供，`build.sh` 不创建这些依赖。

## 三服务边界

| 服务 | AINovel 使用方式 | 鉴权 |
| --- | --- | --- |
| user-service | SSO 页面、授权码交换、用户目录和 `uid + sid` 会话校验 | HTTP SSO；gRPC `x-internal-token` |
| ai-service | 模型列表、对话、嵌入和写作生成 | HMAC metadata |
| pay-service | 通用积分余额与通用转专属扣减 | Bearer service JWT |

正式契约优先读取工作区相对目录 `../../aienie-doc/interfaces/<service>/`。当前 ai-service 契约提供 `ListModels.supports_streaming` 与 `ChatCompletionsStream`；AINovel 在长任务调用前确认模型支持真实流式输出，并按 `STARTED -> CONTENT_DELTA -> COMPLETED` 严格校验请求标识、事件序号和单调 token 进度。仓库内 proto 只保留 AINovel 实际消费的兼容子集。

## 会话校验

1. AINovel 按本地 JWT 密钥、配置的 issuer 和 audience 验证令牌；任一校验失败即拒绝，不解析未验签 payload，也不调用远端校验作为 fallback。
2. 本地签名令牌还必须包含有效的 `sub + uid + sid`。
3. 启用会话校验时，`UserSessionValidator` 才使用已验签的 `uid + sid` 调用 `UserAuthService.ValidateSession`；上游无效或不可达时拒绝建立本地登录态。

## 部署约束

- `build.sh` 只执行 Docker Compose 构建与部署。
- `env.txt` 必须是 `0600` 普通文件；`build.sh` 校验同一文件后通过 Compose `--env-file` 插值，并只读挂载进后端容器加载完整运行时配置。
- 外部安全配置由 `ExternalSecurityStartupValidator` 在启动期校验。
- gRPC TLS/plaintext 由 `EXTERNAL_GRPC_TLS_ENABLED` 和 `EXTERNAL_GRPC_PLAINTEXT_ENABLED` 控制。
- 数据库结构只通过 Flyway 演进，不使用 Hibernate 自动 DDL 管理运行库。
