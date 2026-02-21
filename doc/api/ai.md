# AI API（需登录）
> 说明：本组接口在 v1.1 已改为 AiService 网关透传实现，本地不再维护模型池与 OpenAI 直连配置。

## 模型列表
- `GET /api/v1/ai/models`：返回第三方 AiService 模型列表（给 Copilot 下拉选择）。
  - 响应项包含 `modelType`（`text` / `embedding` / `unspecified`），用于默认模型筛选。

## Copilot 对话
- `POST /api/v1/ai/chat`：请求 `{modelId,context?,messages:[{role,content}]}`，返回 `{role,content,usage:{inputTokens,outputTokens,cost},remainingCredits}`。
  - 当 `modelId` 为空时，服务端会优先选择 `modelType=text` 的模型，避免默认命中 embedding/ocr 模型导致对话失败。

## 文本润色
- `POST /api/v1/ai/refine`：请求 `{text,instruction?,modelId}`，返回 `{result,usage,remainingCredits}`。
