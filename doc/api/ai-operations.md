# AI 长任务与进度 API

需要多次调用 AI，或预计包含大输入、大输出的业务操作通过持久化后台任务执行。普通用户 Bearer 会话只能读取和操作自己的任务。

## 通用任务接口

- `GET /api/v1/ai-operations/{id}`：读取任务快照。
- `GET /api/v1/ai-operations/{id}/events`：SSE `progress` 事件；连接异常时前端退回轮询。
- `GET /api/v1/ai-operations/active?scopeType=&scopeId=`：恢复指定业务资源的活跃任务。
- `POST /api/v1/ai-operations/{id}/retry`：重试 `FAILED` 或 `RECOVERY_REQUIRED` 任务，返回 `202 {operationId}`。
- `POST /api/v1/ai-operations/{id}/cancel`：取消仍未结束的任务，返回 204。

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
