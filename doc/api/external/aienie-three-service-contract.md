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

- 默认 gRPC 地址为 `static://payservice.localhut.com:10021`，仍允许 `PAY_GRPC_ADDR` 环境变量覆盖。
- pay-service 字段语义以 `credits` 为准；`tokens` 仅为旧字段兼容。AINovel 读取余额、兑换结果、签到、兑换码返回时优先使用 `*_credits`，仅在 credits 为空时 fallback 到 deprecated `tokens` 字段。
- AINovel 发起 `ConvertPublicToProject`、`GrantPublicTokens` 等请求时设置 `credits` 字段，并暂时镜像 deprecated `tokens` 字段以兼容旧端。
- pay-service 是 AINovel 运行时项目积分账户、流水、签到、兑换码、AI 扣费、管理员发放和通用积分兑换的权威来源。AINovel 仅保留通用积分兑换订单快照用于兼容展示，不再将本地账本作为余额或流水来源。
