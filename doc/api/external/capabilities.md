# 外部服务能力映射（2026-02-25）

- 来源：`~/aienie-services/aienie-doc` + 当前运行环境联调结果
- 适配状态：已按 2026-02 三服务安全加固完成升级

## UserService
- 核心用途：
  - SSO 登录/注册入口中转
  - `uid + sid` 远程会话权威校验
  - 用户目录与偏好能力
- gRPC 关键方法：
  - `UserAuthService.ValidateSession`
  - `UserDirectoryService.GetUserBasic`
  - `UserPreferenceService.GetProjectPreferences/UpsertProjectPreferences`
- 鉴权要求（加固后）：
  - 受保护方法必须携带 metadata：`x-internal-token`
  - AINovel 对应配置：`EXTERNAL_USER_INTERNAL_GRPC_TOKEN`

## AiService
- 核心用途：
  - 模型列表
  - 对话补全 / 向量 / OCR 网关能力
- gRPC 关键方法：
  - `AiGatewayService.ListModels`
  - `AiGatewayService.ChatCompletions`
  - `AiGatewayService.Embeddings`
  - `AiGatewayService.OcrParse`
- 鉴权要求（加固后）：
  - 必须携带 HMAC metadata：
    - `x-aienie-caller`
    - `x-aienie-ts`
    - `x-aienie-nonce`
    - `x-aienie-signature`
  - AINovel 对应配置：`EXTERNAL_AI_HMAC_CALLER`、`EXTERNAL_AI_HMAC_SECRET`

## PayService
- 核心用途：
  - 通用积分/项目积分查询
  - 通用积分兑换项目积分
  - 使用量扣费与账本查询
- gRPC 关键方法：
  - `BillingBalanceService.GetPublicBalance/GetProjectBalance`
  - `BillingConversionService.ConvertPublicToProject`
  - `BillingUsageService.DeductUsage/DeductPerCall`
- 鉴权要求（加固后）：
  - 所有业务 gRPC 请求都要带 metadata：`authorization: Bearer <service_jwt>`
  - JWT claim 需满足：
    - `role=SERVICE`
    - `iss=aienie-services`
    - `aud` 包含 `aienie-payservice-grpc`
    - `scopes` 包含 `billing.read`、`billing.write`
  - AINovel 对应配置：`EXTERNAL_PAY_SERVICE_JWT`
  - `build.sh` / `build_local.ps1` 支持在占位值、过期或 claim 不合法时自动重签发。

## AINovel 本地能力边界
- `/api/v1/user/credits/convert`：先调用 pay-service 扣减通用积分，再写入本地项目积分账本。
- `/api/v1/user/check-in`、`/api/v1/user/redeem`：AINovel 本地账本链路。
