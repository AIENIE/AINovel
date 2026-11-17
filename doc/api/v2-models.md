# 模型配置、偏好与报价 API

基础路径 `/api/v2`。普通用户接口使用现有 SSO Bearer；管理员接口使用独立的同源 HttpOnly 管理员会话。页面入口为 `/admin/models`。

## 调用方模型目录

- `GET /admin/model-catalog`：同步状态、包含历史不可用项的文本模型目录，以及 embedding、rerank、结构化核验的只读配置。
- `POST /admin/model-catalog/sync`：通过当前环境 ai-service 的 `ListCallerModels` 手动同步，不传普通用户 ID，不调用供应商、不探测、不推理、不收费。
- 模型使用稳定 `modelKey`。返回名称、供应商、流式与思考能力、可选强度、带空值的服务默认开关/强度和同步时间。默认值缺失是“未配置”，不是关闭。
- 成功同步按稳定键更新能力，消失项标记不可用并保留引用；成功空列表照实处理。失败返回 `502 AI_CATALOG_SYNC_FAILED`，保留上次成功目录。同步不修改零售方案、用户展示名、售价或默认项，不自动换模型。

`thinking.declared=false` 时只允许 `DEFAULT`。已声明能力时，`OFF` 必须支持关闭；`ON` 必须支持开启。`ON` 的 `thinkingEffort=null` 使用模型/服务默认强度，指定值必须属于目录的 `options`，不按型号推断支持。

## 职责配置（管理员）

- `GET /admin/model-routing` 返回职责配置。每项包含 `definition`、覆盖的 `operations`、`revision`、`billingEnabled`、`defaultOptionId`、`options`、`unavailableOptions` 和 `outputTokenLimits`。旧计费口径有冲突时，`legacyDefaults` 列出仍在生效的操作和旧方案。
- `PUT /admin/model-routing/{roleKey}` 保存完整职责配置。职责键为 `role.*`，沿用管理员操作证明和审计。提交旧操作键时返回 `AI_CONFIGURATION_CHANGED`，页面重新载入后可编辑对应职责。

```json
{
  "revision": 0,
  "billingEnabled": true,
  "defaultOptionId": "a92eb81e-ed47-40c0-af6e-15b692292502",
  "outputTokenLimits": { "a92eb81e-ed47-40c0-af6e-15b692292502": 32768 },
  "options": [{
    "id": "a92eb81e-ed47-40c0-af6e-15b692292502",
    "modelKey": "目录中的稳定模型键",
    "label": "用户可见方案名称",
    "thinkingMode": "DEFAULT",
    "thinkingEffort": null,
    "pricingMode": "TOKENS",
    "fixedCredits": null,
    "inputPer1k": 0.01,
    "outputPer1k": 0.01
  }]
}
```

免费职责配置一个方案。计费职责至少配置一个方案，并从中指定默认项。相同模型可以提供不同强度、名称和价格的多个方案。`outputTokenLimits` 可选，按方案 ID 指定含思考的总输出上限。每项须为正整数。固定计价使用 `pricingMode=FIXED` 与正整数 `fixedCredits`。Token 价格最多六位小数，输入或输出至少一项大于零。

服务端用 `AiFeatures` 定义操作，再由 `AiModelResponsibilities` 指定共享职责。例如地形、气候、世界条目和引导世界观的补全共用一个默认方案。原操作键继续用于报价、调用次数和历史记录。自主创作协调的方案须具备原生工具预算能力。专用检索和结构化核验仍在目录中只读展示。

初始化合并同职责、同计费口径的存量方案。计费口径有冲突时，管理员明确保存统一方案后生效。空库按环境配置初始化普通职责，自主创作协调由管理员配置。对同一操作，原有方案 ID 可解析到等价的共享方案。

## 用户选型

- `GET /ai-features`：返回用户可见的完整功能选项、默认项、`preferredOptionId`、`preferenceInvalid` 和不可用项。免费功能只展示后台配置。
- `GET /users/me/model-preferences`：保留历史偏好，并增加 `preferredOptionId`。
- `PUT /users/me/model-preferences/{featureKey}`：请求 `{ "preferredOptionId": "方案 ID" }`，返回该功能的当前视图；传 `null` 跟随后台默认。免费功能不能设置用户偏好。
- `DELETE /users/me/model-preferences/{featureKey}`：清除偏好。失效偏好必须显式重新选择，不静默回退。
- `GET /models`、`GET /models/{modelKey}`：保留目录兼容接口，不能用其历史费率字段推算用户售价。

## 操作报价与提交

`POST /ai-quotes`：请求 `{ target, body, selections }`。`target` 是不带 `/api` 的真实业务路径和查询参数；`body` 必须与提交的业务 JSON 相同；`selections` 是 `{ "具体功能键": "方案 ID" }`，不接受客户端生效价格。

服务端根据业务入口确定操作步骤，返回 `{ id, expiresAt, steps, estimatedCredits }`。每步含所选完整方案、必需或条件标记、调用次数说明、估算输入和输出 Token、费用。分块、条件修订和既有尝试上限随步骤展示。配置了总输出上限的方案把该上限用于报价和实际请求，其余方案沿用操作原有的估算与执行方式。

用户确认后，以原 `Idempotency-Key`、原路径和内容、`X-AI-Quote-Id` 提交业务入口。计费操作缺少报价返回 `428 AI_QUOTE_REQUIRED`。报价 10 分钟内首次受理有效，受理前业务输入或配置变化、报价过期均需重新确认。已受理任务及其子任务恢复时读取冻结的模型、思考、展示名、价格和输出上限。免费操作直接受理，带幂等键时同样保留首次受理快照。世界模块 `/retry` 按原生成路径识别报价，调用方仍须复用原键和报价。

主要错误：`AI_QUOTE_EXPIRED`、`AI_QUOTE_INPUT_CHANGED`、`AI_QUOTE_ALREADY_USED`、`AI_PREFERENCE_UNAVAILABLE`、`AI_OPTION_UNAVAILABLE`、`AI_MODEL_UNAVAILABLE`、`AI_THINKING_UNSUPPORTED`。不会自动换模型、档位或价格。

## 结算与恢复

- 每次成功逻辑调用分别收费；已返回成功后解析或业务失败，保留其费用。条件步骤未执行、明确未外发的调用不收费；结果不确定则保留原键待对账；同一输入存在执行中、待结算或待对账结果时，不能通过新报价绕过原请求。
- 固定计价预留固定金额；Token 计价预留估算值，按 ai-service 权威输入/输出 usage 合并后向上取整，最低 1 积分。缓存包含在输入内；推理 Token 已包含在输出中，不重复计费。
- 免费调用保留结果、用量和幂等记录，不预留或扣减项目积分，不写零金额积分流水。
- 实际费用超出预留且余额不足时返回 `402 AI_PAYMENT_REQUIRED`，结果为 `RESULT_READY`。补足积分后重试原请求完成结算；已返回的逻辑调用不重新推理，未执行的后续步骤继续使用原快照。
- `GET /ai-quotes/pending`：返回当前用户的待结算原操作回执。同步操作返回原路径、内容、请求键、报价 ID；异步操作返回原任务重试入口。设置页可据此恢复，浏览器不持久化原文或认证信息。
- G2 使用原投稿时快照，独立任务回执复用报价存储。`PAYMENT_PENDING` 可通过 `POST /api/v1/g2-evaluations/samples/{id}/retry` 恢复；`RECOVERY_REQUIRED` 不自动重发。确定失败样本按关联预留、补扣与返还净额退款，匿名评审内容与门槛不变。
- `GET /users/me/model-usage` 和 `/details` 来自真实调用/结算记录，明细含实际模型、方案及价格快照、用量、已结算积分和待结算状态。正文、权威用量与实际模型在同一结果事务保存。历史记录不编造模型归因；历史演示对比日志仍保留。

V39 追加目录能力、方案、同步状态、报价/任务及调用快照，保持 V1–V38 原字节与历史账本。

## 历史本地对比


旧模型比较演示入口已删除，返回 404；不再生成拼接候选、固定评分或虚构用量。
