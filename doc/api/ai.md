# AI API（需登录）
> 说明：本组接口在 v1.1 已改为 AiService 网关透传实现，本地不再维护模型池与 OpenAI 直连配置。

## 模型列表
- `GET /api/v1/ai/models`：返回 AINovel 当前允许使用的 AiService 文本模型列表。
  - 当前仅返回 `deepseek-v4-flash`（显示名 `DeepSeek V4 Flash`）。
  - 响应项包含 `modelType=text`，用于前端展示；AINovel 不再暴露其它文本、embedding、OCR 或图片模型给写作入口。

## Copilot 对话
- `POST /api/v1/ai/chat`：请求 `{modelId,context?,messages:[{role,content}]}`，返回 `{role,content,usage:{inputTokens,outputTokens,cacheTokens,cacheHitRate,cost},remainingCredits}`。
  - `modelId` 字段保留兼容旧前端和脚本，但服务端会忽略请求值，统一使用 ai-service 的 `deepseek-v4-flash`。
  - `cacheTokens` 为 AiService/模型供应商上报的缓存命中输入 token 数；`cacheHitRate = cacheTokens / inputTokens`，服务端限制在 `0-1`。
  - 前端 Copilot 会在助手消息下显示缓存命中 token 和命中率，用于观察提示词缓存优化效果。

## 文本润色
- `POST /api/v1/ai/refine`：请求 `{text,instruction?,modelId}`，返回 `{result,usage,remainingCredits}`。
  - `modelId` 同样仅为兼容字段，实际调用固定使用 `deepseek-v4-flash`。
