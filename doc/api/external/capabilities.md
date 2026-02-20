# 外部服务能力映射（v1.1）

- 来源：注册中心 + HTTP OpenAPI + gRPC 反射
- 获取时间：2026-02-16

## UserService
- 管理端用户查询：`GET /api/admin/users`
- 封禁/解封：`POST /api/admin/users/{id}/ban`、`POST /api/admin/users/{id}/unban`
- gRPC：
  - `UserAuthService.ValidateSession`
  - `UserDirectoryService.GetUserBasic`
  - `UserBanService.BanUser/UnbanUser/GetBanStatus`

## AiService
- gRPC：
  - `AiGatewayService.ListModels`
  - `AiGatewayService.ChatCompletions`
- 本地 `/api/v1/ai/*` 仅作为网关透传。

## PayService
- gRPC：
  - `BillingBalanceService.GetPublicBalance/GetProjectBalance`
  - `BillingConversionService.ConvertPublicToProject`
  - `BillingGrantService.GrantPublicTokens`（本地入账失败时冲回补偿）
- 本地 `/api/v1/user/check-in` 与 `/api/v1/user/redeem` 已迁回 AINovel 本地账本。
- 本地 `/api/v1/user/credits/convert` 使用 PayService 完成通用积分扣减，再在 AINovel 本地入账项目积分。
