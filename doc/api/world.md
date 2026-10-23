# World API
- `GET /api/v1/worlds/entry-types`：内置条目类型及自由条目。
- `GET /api/v1/worlds/{id}/draft`：返回 `{world,entries,relations,latestPublished,unpublishedChanges}`。world 沿用世界详情字段。
- `PUT /api/v1/worlds/{id}/draft`：Body `{metadata?,entries,relations,expectedRevision}`；entries 和 relations 为完整草稿集合，遗漏表示删除；元数据、条目、关系同事务保存。冲突返回 409，客户端不得丢弃本地输入。
- `GET /api/v1/worlds/{id}/versions`：版本摘要列表；`GET .../versions/{versionId}` 返回完整只读快照。origin 区分 `PUBLISHED` 与 `MIGRATION_BASELINE`。
- `GET /api/v1/worlds/{id}/diff?fromVersionId=...&toVersionId=...`：比较完整版本；省略 toVersionId 并提供 draftRevision 时比较指定草稿 revision。changes 含 kind、id、label、before、after。
- `POST /api/v1/worlds/{id}/publish`：Body `{expectedRevision,releaseNote?,requestKey}`。允许未填完整的草稿，不调用 AI；返回不可变 Version。相同请求幂等，相同内容复用已有正式版本。
- `GET/PUT /api/v1/{story-cards|outlines|manuscripts}/{id}/world-binding`：查看或变更绑定。PUT Body `{fromVersionId,toVersionId,expectedBindingRevision}`；toVersionId=null 表示故事解绑或旧覆盖改为继承。GET 包含 explicitVersionId、bindingRevision、effective 的来源与实际版本，以及故事下独立覆盖清单。
- `GET .../world-binding/diff?toVersionId=...`：预览绑定切换的完整前后差异。新绑定只允许故事层选择正式发布版本；旧下层覆盖仅升级同一世界或解除。

旧 worldId 字段继续返回；普通资料请求中未提供或保持原 worldId 不改变绑定，不能通过该字段清除绑定、切换世界或跟随最新版本。

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
- `POST /api/v1/worlds/{id}/publish/operations`：兼容旧入口，仅补充缺失模块至草稿，返回可跟踪进度的任务；不会发布版本。
- `GET /api/v1/worlds/{id}/generation`：生成队列状态。
- `POST /api/v1/worlds/{id}/generation/{moduleKey}`：调用 AI 生成单模块缺失字段。AI 返回空内容或调用失败时不会写入占位内容，会将模块状态记录为 `FAILED` 并在生成状态中返回错误信息。
- `POST /api/v1/worlds/{id}/generation/{moduleKey}/operations`：单模块生成的异步进度版本。
- `POST /api/v1/worlds/{id}/generation/{moduleKey}/retry`：重试生成。

后台世界任务冻结提交时的业务内容基线、待生成模块和每字段已获得的结果。恢复沿用原任务及调用身份，跳过本任务已经应用的模块，复用已保存字段；元数据或模块被外部编辑后返回 `WORLD_VERSION_CONFLICT`，不将旧设定的结果自动写入新内容。自身进度、状态与模块应用不会使恢复基线失效。历史任务缺少基线时返回 `WORLD_TASK_BASELINE_REQUIRED`，不推测旧输入或重新收费生成。

- `GET /api/v1/worlds/{worldId}/operations/{operationId}/generated-results`：读取当前用户该世界任务已保存的生成字段。返回 `{operationId,worldId,modules:{moduleKey:{fieldKey:text}},appliedModules:string[]}`，供失败或冲突后查看、复制；读取不触发 AI 或写入世界。该结果与通用任务的成功 `resultJson` 分开，部分输出不等于整个任务成功。
