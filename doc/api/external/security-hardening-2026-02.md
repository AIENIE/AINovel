# 三服务安全加固对接说明（2026-02）

## 目标
- 对齐 ai-service、user-service、pay-service 的最新鉴权规范。
- 在启动阶段做 fail-fast，避免运行时才暴露配置问题。
- 统一时区为 UTC+8（`Asia/Shanghai`），降低跨服务鉴权时间偏差风险。

## 必填配置
- `EXTERNAL_SECURITY_FAIL_FAST=true`
- `APP_TIME_ZONE=Asia/Shanghai`
- `EXTERNAL_GRPC_TLS_ENABLED` / `EXTERNAL_GRPC_PLAINTEXT_ENABLED`（至少一个为 `true`）

### ai-service
- `EXTERNAL_AI_HMAC_CALLER`
- `EXTERNAL_AI_HMAC_SECRET`
- 每次业务调用附加 metadata：
  - `x-aienie-caller`
  - `x-aienie-ts`
  - `x-aienie-nonce`
  - `x-aienie-signature`

### user-service
- `EXTERNAL_USER_INTERNAL_GRPC_TOKEN`
- 受保护 gRPC 方法附加 metadata：
  - `x-internal-token: <token>`
- AINovel 会基于 `uid + sid` 调用 `ValidateSession` 做会话权威校验。

### pay-service
- `EXTERNAL_PAY_SERVICE_JWT`
- metadata：
  - `authorization: Bearer <service_jwt>`
- JWT claim 要求：
  - `role=SERVICE`
  - `iss=aienie-services`
  - `aud` 包含 `aienie-payservice-grpc`
  - `scopes` 包含 `billing.read,billing.write`
  - `exp` 未过期

## 启动期校验（ExternalSecurityStartupValidator）
- 校验三服务鉴权关键配置非空且非占位值。
- 校验 pay-service JWT 的结构与关键 claims。
- 配置不合法时直接阻止服务启动（fail-fast）。

## 构建脚本行为
- `build.sh` / `build_local.ps1` 在以下场景会自动重签发 `EXTERNAL_PAY_SERVICE_JWT`：
  - token 为空或占位值
  - token claim 不合法
  - token 已过期/即将过期
- `build_prod.sh` 调用 `build.sh`，与 `build.sh` 逻辑保持同步，差异仅为域名默认值。

## 运行时说明
- UserSession 校验优先走 Consul；失败时回退 `USER_GRPC_ADDR`。
- 若使用共享测试账号，其他会话重新登录会刷新 `sid`，旧 token 会被正常拒绝（表现为 403）。
