# 外部服务与基础设施集成

本文记录 AINovel 如何消费外部能力，不替代跨服务正式契约。

## 配置来源

- Linux 部署只从 config-center 提供的 `0600` 普通文件 `env.txt` 加载实际运行地址与凭据，由发版中心只读挂载进后端容器；宿主同名环境变量不能补齐或覆盖该文件契约。
- 本地测试网站使用 `localainovel.testhut.top`。
- 本地三服务地址由同一份 `env.txt` 显式配置，不使用旧 `testaiservice`、`testpayservice` 或 `testuserservice` 域名作为隐式 fallback。
- MySQL、Redis、Qdrant 等共享基础设施地址由环境提供，发版包不创建这些依赖。

## 三服务边界

| 服务 | AINovel 使用方式 | 鉴权 |
| --- | --- | --- |
| user-service | SSO 页面、授权码交换、用户目录和 `uid + sid` 会话校验 | HTTP SSO；gRPC `authorization: Bearer <JWT>` 携带 AINovel 独立密钥签发的短期 caller JWT；旧 `x-internal-token` 禁用 |
| ai-service | 模型列表、对话、嵌入和写作生成 | HMAC metadata |
| pay-service | 通用积分余额与通用转专属扣减 | Bearer service JWT |

正式契约优先读取工作区相对目录 `../../aienie-doc/service-integration/<service>/`。当前 ai-service 契约提供 `ListModels.supports_streaming` 与 `ChatCompletionsStream`；AINovel 在长任务调用前确认模型支持真实流式输出，并按 `STARTED -> CONTENT_DELTA -> COMPLETED` 严格校验请求标识、事件序号和单调 token 进度。仓库内 proto 只保留 AINovel 实际消费的兼容子集。

## 会话校验

1. AINovel 按本地 JWT 密钥、配置的 issuer 和 audience 验证令牌；任一校验失败即拒绝，不解析未验签 payload，也不调用远端校验作为 fallback。
2. 本地签名令牌还必须包含有效的 `sub + uid + sid`。
3. 启用会话校验时，`UserSessionValidator` 才使用已验签的 `uid + sid` 调用 `UserAuthService.ValidateSession`；上游无效或不可达时拒绝建立本地登录态。
4. 每次远程校验都从 `UserServiceJwtProvider` 获取最长 900 秒、`aud=aienie-userservice-grpc` 且仅含 `user.auth.session.read` 的 HS256 JWT；JWT 必须带 `iss/sub/iat/nbf/exp/jti/scopes`，并在到期前轮换。禁止恢复共享静态 token 或复用其他产品的 caller secret。

## 部署约束

- Linux 服务器发布唯一入口是发版中心执行的 `ci/build-release.sh`；仓库不再提供本地 Compose 部署脚本。
- `env.txt` 由 config-center 提供、发版中心以 `0600` 普通文件只读挂载进后端容器加载完整运行时配置。
- 外部安全配置由 `ExternalSecurityStartupValidator` 在启动期校验。
- user-service caller JWT 使用 `EXTERNAL_USER_SERVICE_JWT_{CALLER_ID,ISSUER,SECRET,AUDIENCE,TTL_SECONDS,SCOPES}`；其中 secret 只进入受保护配置，audience 和 scope 必须保持规范值。
- gRPC TLS/plaintext 由 `EXTERNAL_GRPC_TLS_ENABLED` 和 `EXTERNAL_GRPC_PLAINTEXT_ENABLED` 控制。
- 数据库结构只通过 Flyway 演进，不使用 Hibernate 自动 DDL 管理运行库。
# v1.0 审计整改补充（2026-08-17）

- SSO 本地身份由不可变 `(issuer, remote_uid)` 唯一映射；username 仅作为同 UID 的可变展示字段，冲突登录会失败并记录安全事件。
- ai-service、pay-service、user-service 使用独立 deadline；远程调用不得占用本地数据库事务。
- 公共积分转换使用稳定 `remoteRequestId` 的可恢复 Saga，瞬态失败保留 `PENDING` 并退避重放，确定性拒绝才进入 `FAILED`。
- `test`/`production` 强制 MySQL `VERIFY_IDENTITY`、Redis TLS+ACL、Qdrant HTTPS+API key；`local` 明文仅允许带显式启动告警运行。
