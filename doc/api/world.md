# World API
- `GET /api/v1/worlds/entry-types`：内置条目类型及自由条目。
- `GET /api/v1/worlds/{id}/draft`：返回 `{world,entries,relations,latestPublished,unpublishedChanges}`。world 沿用世界详情字段。
- `PUT /api/v1/worlds/{id}/draft`：Body `{metadata?,entries,relations,expectedRevision}`；entries 和 relations 为完整草稿集合，遗漏表示删除；元数据、条目、关系同事务保存。冲突返回 409，客户端不得丢弃本地输入。
- `GET /api/v1/worlds/{id}/versions`：版本摘要列表；`GET .../versions/{versionId}` 返回完整只读快照。origin 区分 `PUBLISHED` 与 `MIGRATION_BASELINE`。
- `GET /api/v1/worlds/{id}/diff?fromVersionId=...&toVersionId=...`：比较完整版本；省略 toVersionId 并提供 draftRevision 时比较指定草稿 revision。changes 含 kind、id、label、before、after。
- `POST /api/v1/worlds/{id}/publish`：Body `{expectedRevision,releaseNote?,requestKey}`。允许未填完整的草稿，不调用 AI；返回不可变 Version。相同请求幂等，相同内容复用已有正式版本。
- `POST /api/v1/worlds/{id}/entries/{entryId}/generate/operations`：Body `{expectedRevision}`。空条目补空，先报价；返回异步任务。世界输入变化或目标非空时不写入，已收到的结果仍可查看。
- `POST /api/v1/worlds/{id}/entries/{entryId}/refine`：Body `{text,instruction?,model?}`。先报价，返回精修候选，不保存草稿；客户端核对输入基线后由作者采纳。新条目需要单独配置 generate/refine，未配置返回 `AI_FEATURE_UNCONFIGURED`。
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
- `GET /api/v1/worlds/{id}/publish/preview`：旧模块生成预检，保留兼容；新版发布确认使用 draft/diff，不调用此接口。
- `POST /api/v1/worlds/{id}/publish/operations`：兼容旧入口，仅补充缺失模块至草稿，返回可跟踪进度的任务；不会发布版本。
- `GET /api/v1/worlds/{id}/generation`：生成队列状态。
- `POST /api/v1/worlds/{id}/generation/{moduleKey}`：调用 AI 生成单模块缺失字段。AI 返回空内容或调用失败时不会写入占位内容，会将模块状态记录为 `FAILED` 并在生成状态中返回错误信息。
- `POST /api/v1/worlds/{id}/generation/{moduleKey}/operations`：单模块生成的异步进度版本。
- `POST /api/v1/worlds/{id}/generation/{moduleKey}/retry`：重试生成。

后台世界任务冻结提交时的业务内容基线、待生成模块和每字段已获得的结果。恢复沿用原任务及调用身份，跳过本任务已经应用的模块，复用已保存字段；元数据或模块被外部编辑后返回 `WORLD_VERSION_CONFLICT`，不将旧设定的结果自动写入新内容。自身进度、状态与模块应用不会使恢复基线失效。历史任务缺少基线时返回 `WORLD_TASK_BASELINE_REQUIRED`，不推测旧输入或重新收费生成。

- `GET /api/v1/worlds/{worldId}/operations/{operationId}/generated-results`：读取当前用户该世界任务已保存的生成字段。返回 `{operationId,worldId,modules:{moduleKey:{fieldKey:text}},appliedModules:string[]}`，供失败或冲突后查看、复制；读取不触发 AI 或写入世界。该结果与通用任务的成功 `resultJson` 分开，部分输出不等于整个任务成功。


## W3 独立世界协作

下列路径均以 `/api/v1/worlds/{worldId}` 为前缀；复用现有认证、归属校验、报价与异步任务恢复。所有写入使用 `Idempotency-Key`。

| 路径 | 方法与请求 | 结果 |
| --- | --- | --- |
| `/assistant/sessions` | GET；POST `{title}` | 话题列表 / 新话题 |
| `/assistant/sessions/{sessionId}` | PUT `{title}` | 改名 |
| `/assistant?sessionId=&before=&limit=` | GET | 话题历史，含 `hasMore/nextBefore` |
| `/assistant/turns` | POST `{sessionId,intent,instruction,sourceTurnIds?,suggestionId?,parentProposalId?,expectedDraftRevision}` | `{turnId,operationId,sessionId}`；intent 为 DISCUSS/PROPOSE/REFINE |
| `/assistant/turns/{turnId}` | GET | 持久回答、建议、实际来源、潜在矛盾及提案 ID |
| `/assistant/proposals?status=&type=&sessionId=&before=` | GET | 提案分页列表 |
| `/assistant/proposals/{proposalId}` | GET；PUT `{expectedRevision,candidate}` | 完整前文、模型原候选、作者候选、当前稿和状态 |
| `/assistant/proposals/{proposalId}/accept` | POST `{expectedRevision,expectedDraftRevision,acknowledgeContextChange}` | 原子保存对应目标和提案状态；不发布、不升级绑定 |
| `/assistant/proposals/{proposalId}/reject` | POST `{expectedRevision}` | 拒绝并保留候选 |
| `/entries/{entryId}/refine/operations` | POST `{expectedRevision,instruction?}` | 一次调用形成持久提案 |
| `/overview/integrate/operations` | POST `{expectedRevision,instruction?}` | 完整读取已保存设定，一次调用形成概览提案 |

目标类型为 METADATA（按 `fieldKey`）、ENTRY、RELATION、OVERVIEW。仅 CREATE/UPDATE；删除继续由编辑器完成。新关系等待两端条目采纳。目标改变阻止采纳；仅背景改变时，需要读取当前稿后明确 `acknowledgeContextChange=true`。概览依赖完整来源，任何来源改变都阻止旧概览直接采纳。

`draft` 的 metadata 及不可变快照增加 `overview`；空值读取兼容历史快照，不改写历史 JSON 或摘要。`/api/v1/world-prompts` 模板数据增加 `actions`，支持 entryGenerate/discuss/propose/refine/overview。旧模块和字段精修模板保留，finalTemplates 作为概览整合附加要求。

普通讨论、整理及细化最多 plan/final 两次调用，final 仅在请求补读时执行；报价包含所有可能步骤。单条精修和概览一次调用。受理冻结草稿、话题、候选、模板和模型选择；恢复沿用原任务与请求身份，没有格式修复或自动重试调用。

## W4 写作世界上下文

接口前缀为 `/api/v1`。大纲资源 `/outlines/{id}/world-context` 与稿件场景资源 `/manuscripts/{id}/scenes/{sceneId}/world-context` 均支持 GET、PUT 和 POST `/preview`。读取只使用实际绑定的不可变世界版本，失效绑定不会回退到草稿。

PUT 请求为 `{worldId,versionId,expectedRevision,choices}`，choices 按 `metadata/overview/notes/entry:{稳定ID}/relation:{稳定ID}` 记录 `AUTO/INCLUDE/EXCLUDE`；AUTO 清除覆盖。响应包含 revision、核对版本和来源摘要。相同世界升级保留稳定 ID 的选择；缺失必选、必选关系端点排除及已变化必选需处理后生成。

场景 preview 请求 `{purpose:"SCENE",mode:"fast"|"crafted"}`；助手 preview 为 `{purpose:"ASSISTANT",...TurnRequest}`。大纲 preview 使用下述大纲任务请求。响应含 `contextHash/preferences/selection/compiledContext/firstSources/availableSources`；selection 说明绑定层级、版本、来源完整内容、命中或排除原因和容量。助手 availableSources 是冻结可补读范围，不能视为已读。预览与偏好保存均不调用模型。

场景和助手生成请求增加 `contextHash` 与 `expectedSelectionRevision`，由预览取得。报价持久化完整输入及来源；绑定、配置、输入或选择变化后须重新预览和报价。容量问题在模型调用前阻断，不能以截断概览或必选原文继续。H2 编辑预览明确说明世界材料隔离，首步及补读均不得读取未分类世界来源；AUTHOR_QA 不允许正文修改。
