# World API
- `GET /api/v1/world-building/definitions`：模块/字段定义列表。
- `GET /api/v1/worlds`：世界列表。
- `POST /api/v1/worlds`：创建草稿世界，Body `{name,tagline?,themes?,creativeIntent?,notes?}`。
- `GET /api/v1/worlds/{id}`：世界详情（含 modules/map 和数值 `revision`）。`revision` 是编辑冲突检查值，与展示用 `version` 不同。
- `PUT /api/v1/worlds/{id}`：一次保存基础信息和模块。Body `{name?,tagline?,themes?,creativeIntent?,notes?,modules?:{key:{field:value}},expectedRevision?:number}`；新编辑器必须带读取时的 `expectedRevision`。同一事务检查版本并写入，成功返回保存后的详情和 `revision`；过期返回 `409 WORLD_VERSION_CONFLICT`，本次基础信息及模块均不写入。提供的模块整组替换，未提供模块保留。旧的只保存基础信息且不带 revision 的请求继续兼容。
- `DELETE /api/v1/worlds/{id}`：删除草稿世界。
- `PUT /api/v1/worlds/{id}/modules`：兼容批量保存模块，Body `{modules: {key: {field:value}},expectedRevision?:number}`。
- `PUT /api/v1/worlds/{id}/modules/{moduleKey}`：兼容单模块保存，Body `{fields:{field:value},expectedRevision?:number}`。
- `POST /api/v1/worlds/{id}/modules/{moduleKey}/fields/{fieldKey}/refine`：字段精修（调用 LLM 并扣费），返回 `AiRefineResponse {result,usage,remainingCredits}`。
- `GET /api/v1/worlds/{id}/publish/preview`：发布预检。
- `POST /api/v1/worlds/{id}/publish`：进入生成状态。
- `POST /api/v1/worlds/{id}/publish/operations`：生成全部缺失模块并发布，返回可跟踪进度的后台任务。
- `GET /api/v1/worlds/{id}/generation`：生成队列状态。
- `POST /api/v1/worlds/{id}/generation/{moduleKey}`：调用 AI 生成单模块缺失字段。AI 返回空内容或调用失败时不会写入占位内容，会将模块状态记录为 `FAILED` 并在生成状态中返回错误信息。
- `POST /api/v1/worlds/{id}/generation/{moduleKey}/operations`：单模块生成的异步进度版本。
- `POST /api/v1/worlds/{id}/generation/{moduleKey}/retry`：重试生成。

后台世界任务冻结提交时的业务内容基线、待生成模块和每字段已获得的结果。恢复沿用原任务及调用身份，跳过本任务已经应用的模块，复用已保存字段；元数据或模块被外部编辑后返回 `WORLD_VERSION_CONFLICT`，不将旧设定的结果自动写入新内容。自身进度、状态与模块应用不会使恢复基线失效。历史任务缺少基线时返回 `WORLD_TASK_BASELINE_REQUIRED`，不推测旧输入或重新收费生成。

- `GET /api/v1/worlds/{worldId}/operations/{operationId}/generated-results`：读取当前用户该世界任务已保存的生成字段。返回 `{operationId,worldId,modules:{moduleKey:{fieldKey:text}},appliedModules:string[]}`，供失败或冲突后查看、复制；读取不触发 AI 或写入世界。该结果与通用任务的成功 `resultJson` 分开，部分输出不等于整个任务成功。
