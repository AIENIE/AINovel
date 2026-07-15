# G1 引导创作 API

所有接口需要普通用户 Bearer 会话，服务端按工作流所属用户校验访问权限。路径经应用前缀后为 `/api/v1/creation-workflows`。

## 运行

- `POST /api/v1/creation-workflows`：创建草稿。Body `{seedIdea,genre?,tone?,targetChapterCount?,autoRun?}`；章节数范围 `3-12`，默认 `6`。
- `GET /api/v1/creation-workflows`：按更新时间倒序列出当前用户草稿。
- `GET /api/v1/creation-workflows/{id}`：读取草稿、各步候选、标准实体引用和当前后台任务。

创建自动草稿后首步立即入队。手动草稿由前端调用生成接口启动当前步骤。

## 步骤操作

- `POST /api/v1/creation-workflows/{id}/steps/{step}/generate`：为当前步骤生成三个候选，Body `{hint?}`，返回 `202 {workflowId,jobId}`。
- `POST /api/v1/creation-workflows/{id}/steps/{step}/confirm`：确认并可编辑候选，Body `{candidateId,editedPayload?,version?}`。
- `POST /api/v1/creation-workflows/{id}/steps/world/skip?version=`：跳过世界设定。
- `POST /api/v1/creation-workflows/{id}/auto-run`：从当前状态采用推荐候选并自动推进，Body `{targetChapterCount?}`，返回 `202`。
- `POST /api/v1/creation-workflows/{id}/retry`：重试 `FAILED` 或 `RECOVERY_REQUIRED` 任务，返回 `202`。

`step` 取值为 `premise`、`world`、`characters`、`outline`。同一运行/步骤只有一个持久化任务；失败重试复用原任务和计费引用。

## 响应重点

`WorkflowResponse` 提供 `status,currentStep,steps,storyId,worldId,outlineId,activeJob,errorMessage,version`。前端在 `activeJob.status` 为 `QUEUED/RUNNING/CALLING_AI` 或运行状态为 `AUTO_RUNNING` 时轮询详情。

运行状态：`WAITING_USER`、`AUTO_RUNNING`、`FAILED`、`COMPLETED`。任务状态：`QUEUED`、`RUNNING`、`CALLING_AI`、`SUCCEEDED`、`FAILED`、`RECOVERY_REQUIRED`。
