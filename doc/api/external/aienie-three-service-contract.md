# Aienie 三服务接入边界

> 当前仓库实现记录。跨服务正式契约优先读取 `/home/duwei/aienie-doc/interfaces/` 下对应服务目录；本仓库不通过 Swagger、reflection 或运行时发现生成契约。

## user-service

- 浏览器登录通过 SSO 跳转、回调授权码和 `/sso/token` 交换完成。
- AINovel 保存远端用户 ID，并使用 `UserAuthService.ValidateSession` 校验 `uid + sid`。
- 业务 gRPC 使用 `EXTERNAL_USER_INTERNAL_GRPC_TOKEN` 作为 `x-internal-token`。

## ai-service

- 提供模型列表、对话、嵌入和素材检索所需的网关能力。
- 当前写作入口固定使用本服务允许的文本模型；调用凭据由 HMAC 配置提供。
- AINovel 不在本地维护外部模型池或 API Key。

## pay-service

- 提供通用积分余额和通用积分转换扣减。
- AINovel 本地维护项目专属积分账户、流水、兑换码和 AI 消耗记录。
- 调用使用 `EXTERNAL_PAY_SERVICE_JWT`；签发、续期和密钥管理属于外部运维流程。
