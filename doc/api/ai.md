# AI API（需登录）

文本调用经当前环境 ai-service 网关执行。模型、思考与用户售价统一来自[功能模型配置与报价](v2-models.md)，不再固定为统一模型，也不接受客户端生效价格。

## 模型与通用工具

- `GET /api/v1/ai/models`：返回本地同步目录中的可用文本模型，含流式能力；不触发自动同步或供应商探测。
- `POST /api/v1/ai/chat`：`{modelId?,context?,messages:[{role,content}]}`，服务端固定归类 `generic.chat`。
- `POST /api/v1/ai/refine`：`{text,instruction?,modelId?}`，归类 `generic.refine`。
- `POST /api/v1/ai/version-summary`：聊天消息及稿件上下文，归类 `versions.summary`。
- `POST /api/v1/ai/outline-refine`：`{text,instruction?,kind}`；`kind=scene` 使用 `outline.scene_refine`，其余使用 `outline.chapter_refine`。

旧 `modelId` 仅保留请求兼容，实际模型由已确认的功能选项确定；客户端 `featureKey` 不作为功能或价格依据。聊天响应包含 `{role,content,usage,remainingCredits,selection,actualModelKey}`，润色响应保留 `{result,usage,remainingCredits}`。

`usage` 包含 `inputTokens`、`outputTokens`、`cacheTokens`、`cacheHitRate`、实际结算的项目积分 `cost`；缓存命中率限制在 0–1，缓存已包含在输入用量中，推理 Token 不额外重复收费。

## 受理、进度与恢复

- 普通计费 AI 操作先取得 10 分钟有效报价，经用户确认后携带 `X-AI-Quote-Id`；缺少报价返回 `428 AI_QUOTE_REQUIRED`。免费功能无需收费确认。
- 通用聊天/润色和异步提交继续要求 `Idempotency-Key`（1–128 字符）。同一逻辑请求复用原键与快照，结果不确定时待对账，不换键重发。
- 调用前预留项目积分，成功后按原选项价格与权威 usage 结算。固定价格每成功逻辑调用计费；Token 费用合并取整、最低 1 积分。明确未外发才释放预留，网络失败不当作未执行。
- 余额不足以补扣时保存结果并返回 `402 AI_PAYMENT_REQUIRED`。补足积分后恢复原操作，已完成调用只结算、不重新推理；设置页提供待结算回执入口。
- 长任务仍使用[异步进度接口](ai-operations.md)。支持流式的模型使用真实流式；非流式模型使用一元调用，保留阶段进度。已受理操作的子任务继承同一快照。
- Slop 审校、手动 Slop 诊断及剧情诊断的 JSON 报告，将报价快照中的输出额度传为网关 `max_output_tokens`（当前默认 4096，包含思考 Token），避免继承供应商较大的正文输出默认值。正文起草和全文修订沿用原有输出行为；思考模式仍以作者确认的模型选项为准。
- 消息、context 和并发准入沿用既有校验；资源权限、作者逐项采纳与功能开关保持原边界。

工作台助手使用独立的[正文助手](writing-assistant.md)及[故事助手](story-assistant.md)接口，各意图的计划和最终输出分别配置；正文汉字数与模型 Token 用量分开显示。embedding、rerank 和资料结构化核验保留原链路与计费。
