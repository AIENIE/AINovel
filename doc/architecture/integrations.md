# 外部服务与基础设施集成

本文记录 AINovel 如何消费外部能力，不替代跨服务正式契约。

## 配置来源

- Windows 本地开发与验收由 `scripts/windows/Start-Local.ps1` 读取 `%LOCALAPPDATA%\Aienie\secrets\ainovel-localbase.env`。安全目录和配置文件的 owner 必须是当前用户或本机 Administrators，初始化不得自动夺取所有权；目录必须是非重解析普通目录、关闭 ACL 继承，并以 `(OI)(CI)` Full Control 只允许当前用户、SYSTEM 和本机 Administrators；配置文件必须是非重解析普通文件、关闭 ACL 继承，并以显式 Full Control 只允许同三个主体。
- Windows 本地固定使用 `127.0.0.1:11040`；MySQL、Redis、Qdrant 位于 `172.20.0.2`，三项业务服务通过本机 loopback 访问，不使用旧测试域名作为隐式 fallback。
- 非 Windows Docker 部署才使用仓库内 Git 忽略、权限为 `0600` 的 `env.txt`；宿主同名环境变量不能补齐或覆盖该文件契约。
- 两条路径都只消费既有外部依赖，不负责创建 MySQL、Redis、Qdrant 或三项业务服务。

## 三服务边界

| 服务 | AINovel 使用方式 | 鉴权 |
| --- | --- | --- |
| user-service | SSO 页面、授权码交换、用户目录和 `uid + sid` 会话校验 | HTTP SSO；gRPC `x-internal-token` |
| ai-service | 模型列表、对话、嵌入和写作生成 | HMAC metadata |
| pay-service | 通用积分余额与通用转专属扣减 | Bearer service JWT |

正式契约优先读取与 `aienie-projects` 同级的 `aienie-doc` 仓库 `interfaces/<service>/`；标准布局下从项目根目录解析为 `..\..\aienie-doc\interfaces\<service>\`，不依赖某个用户的 home 目录。当前 ai-service 契约提供 `ListModels.supports_streaming` 与 `ChatCompletionsStream`；AINovel 在长任务调用前确认模型支持真实流式输出，并按 `STARTED -> CONTENT_DELTA -> COMPLETED` 严格校验请求标识、事件序号和单调 token 进度。仓库内 proto 只保留 AINovel 实际消费的兼容子集。

## 会话校验

1. AINovel 按本地 JWT 密钥、配置的 issuer 和 audience 验证令牌；任一校验失败即拒绝，不解析未验签 payload，也不调用远端校验作为 fallback。
2. 本地签名令牌还必须包含有效的 `sub + uid + sid`。
3. 启用会话校验时，`UserSessionValidator` 才使用已验签的 `uid + sid` 调用 `UserAuthService.ValidateSession`；上游无效或不可达时拒绝建立本地登录态。

## 部署约束

- Windows 本地只使用 PowerShell 7 的 `Start-Local.ps1` / `Stop-Local.ps1`，并只停止本工作树记录的进程。
- `build.sh` 只用于非 Windows Docker Compose 构建与部署；它校验同一份 `env.txt` 后完成 Compose 插值并只读挂载进后端容器。
- 外部安全配置由 `ExternalSecurityStartupValidator` 在启动期校验。
- gRPC TLS/plaintext 由 `EXTERNAL_GRPC_TLS_ENABLED` 和 `EXTERNAL_GRPC_PLAINTEXT_ENABLED` 控制。
- 数据库结构只通过 Flyway 演进，不使用 Hibernate 自动 DDL 管理运行库。
