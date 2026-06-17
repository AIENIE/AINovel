# aienie 三服务契约记录

日期：2026-05-23

AINovel 以 `/home/pi/aienie-projects/aienie-doc` 中 ai-service、user-service、pay-service 的 proto 与接入说明为外部契约来源。本项目只对接统一用户、统一 AI 网关与公共积分来源，不把 pay-service 作为小说项目内账本。

## user-service

- 主运行路径仍为 SSO 登录后在本地建档，并通过 `UserAuthService.ValidateSession` 校验远端 `user_id + session_id`。
- 本地 `users.remote_uid` 保存 user-service 真实用户 ID。对外调用 ai-service 与 pay-service 时只能使用该 ID。
- 本地 UUID 只用于 AINovel 数据归属，不可哈希或转换成远端 user-service 用户 ID。

## ai-service

- `ListModelsRequest` 必须带 `user_id`，调用方传入当前登录用户的 `remote_uid`。
- `ChatCompletionsRequest.user_id` 必须是 user-service 的真实用户 ID；未绑定 `remote_uid` 的本地账号在本地拒绝调用。
- `messages` 至少包含一条 `role=user` 且 `content` 非空的消息；缺失时 AINovel 本地拒绝，避免把空上下文透传到 ai-service。

## pay-service

- 默认 gRPC 地址为 `static://payservice.seekerhut.com:443`，TLS，仍允许 `PAY_GRPC_ADDR` 环境变量覆盖。
- pay-service 字段语义以 `credits` 为准；`tokens` 仅为旧字段兼容。
- AINovel 只把 pay-service 作为通用积分余额与通用积分扣减来源。
- AINovel 项目专属积分账户、流水、兑换码、AI 扣费和管理员发放由本项目本地数据库维护。
- 通用积分兑换 AINovel 专属积分时，AINovel 调用 `BillingConversionService.ConvertPublicToProject` 扣减通用积分，然后写入本地项目专属积分账户、流水和兑换订单快照。
