# AI 长任务与进度 API

需要多次调用 AI，或预计包含大输入、大输出的业务操作通过持久化后台任务执行。普通用户 Bearer 会话只能读取和操作自己的任务。

## 通用任务接口

- `GET /api/v1/ai-operations/{id}`：读取任务快照。
- `GET /api/v1/ai-operations/{id}/events`：SSE `progress` 事件；连接异常时前端退回轮询。
- `GET /api/v1/ai-operations/active?scopeType=&scopeId=`：恢复指定业务资源的活跃任务。
- `POST /api/v1/ai-operations/{id}/retry`：重试 `FAILED` 或 `RECOVERY_REQUIRED` 任务，返回 `202 {operationId}`。
- `POST /api/v1/ai-operations/{id}/cancel`：取消仍未结束的任务，返回 200 和任务快照；若业务提交先完成则保持成功结果。

创建任务返回 `202 {operationId}`。进度快照包含：

- `status`：`QUEUED`、`RUNNING`、`STREAMING`、`RECOVERY_REQUIRED`、`SUCCEEDED`、`FAILED` 或 `CANCELLED`。
- `currentStep`、`totalSteps`、`completedSteps`、`remainingSteps`。
- `currentStepOutputTokens` 与 `outputTokensEstimated`；流式阶段为实时值或估算值，调用完成后使用 ai-service 的权威 completion usage。
- `attemptCount`、`errorMessage`、时间字段；成功时 `resultJson` 保存业务响应，供原页面完成后继续跳转或刷新数据。

服务重启时，尚未开始的 `QUEUED` 任务会重新派发；已经进入远程调用的任务会标记为 `RECOVERY_REQUIRED`，避免静默重复生成和扣费。

## 业务启动接口

- `POST /api/v1/conception/operations`：故事构思与 AI 辅助初始化。
- `POST /api/v1/outlines/{outlineId}/chapters/operations`：生成章节。
- `POST /api/v1/worlds/{worldId}/publish/operations`：生成缺失模块并发布世界观。
- `POST /api/v1/worlds/{worldId}/generation/{moduleKey}/operations`：生成单个世界模块。
- `POST /api/v1/manuscripts/{manuscriptId}/scenes/{sceneId}/generate/operations?mode=fast|crafted`：生成场景正文并执行质量链路。
- `POST /api/v2/manuscripts/{manuscriptId}/scenes/{sceneId}/quality-runs/operations`：文本 Slop 诊断。
- `POST /api/v2/manuscripts/{manuscriptId}/slop-drift-runs/operations`：长篇 drift 巡检。
- `POST /api/v2/manuscripts/{manuscriptId}/scenes/{sceneId}/plot-quality-runs/operations`：剧情诊断。
- `POST /api/v2/manuscripts/{manuscriptId}/plot-quality-runs/{runId}/revision-candidate/operations`：生成剧情修订候选。

原同步接口暂时保留兼容；当前前端的长任务入口使用上述异步接口，并在全局进度面板显示当前步骤、已完成/剩余步骤和当前步骤 token。

## 创作链路恢复（2026-10-02）

- 运行表与步骤表 `request_id` 最大 160 字符，不改写历史请求编号或幂等键。
- 初始化远程失败或响应不可解析会保留失败状态。部分字段缺失时补齐基础结构，结果包含 `degraded: true`，前端提示核对设定。
- 重试优先恢复已保存的 `resultJson`；积分回执为 `RECONCILIATION_REQUIRED` 时返回 `409 AI_RESULT_RECONCILIATION_REQUIRED`，不重新推理、不释放此前冻结积分。仅本地预算在外发前明确拒绝的新请求会释放该次预留。
- 进度面板支持关闭、最小化和重新查看，显示状态按登录用户及任务存入 sessionStorage，刷新和跨页保留。关闭不取消任务；重试与取消执行期间禁用重复提交，错误保留在面板。

- 失败进度增加可选 `errorCode`；前端只映射已知代码，不回显原始异常。恢复已有结果后清除旧错误并将步骤标记完成。
- 新建世界未填主题按空列表保存；兼容历史 JSON `null`。模块字段生成包含世界简介，已有字段保持不变。
