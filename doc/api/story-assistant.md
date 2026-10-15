# 故事级助手 API

控制器前缀为 `/v1/story-cards/{storyId}/assistant`，浏览器代理前缀为 `/api/v1/story-cards/{storyId}/assistant`。普通用户会话和资源归属沿用故事接口；本组接口不要求稿件、正文分支或场景。正文修改仍使用[正文写作助手](writing-assistant.md)，两类对话的用途与上下文分开。

## 话题与回合

| 方法与相对路径 | 请求 | 返回及用途 |
|---|---|---|
| `GET /sessions` | 无 | 当前故事最近更新的最多 50 个话题，包含 `id/storyId/title/createdAt/updatedAt` |
| `POST /sessions` | `{title}`，`Idempotency-Key` | 新建话题，标题必填且不超过 120 字符 |
| `PUT /sessions/{sessionId}` | `{title}`，`Idempotency-Key` | 手动修改话题标题 |
| `GET ?sessionId=...&before=...&limit=20` | 可选话题、分页游标 | `SessionDto`，含 `id/storyId/title/turns/hasMore/nextBefore`；读取不隐式新建话题 |
| `POST /turns` | `TurnRequest`，`Idempotency-Key` | `202 {turnId,operationId}`，提交独立异步任务 |
| `GET /turns/{turnId}` | 无 | 状态、答复、建议、提案引用、实际来源及用量 |

`TurnRequest` 为 `{sessionId,intent,instruction,outlineId?,sourceTurnIds?,suggestionId?,parentProposalId?}`。`instruction` 必填，不超过 8000 字符；`outlineId` 仅选当前故事下的一个大纲。

省略 `sessionId` 的读取返回最近更新话题，无话题时返回空会话，不创建记录。回合按新到旧返回，`limit` 限于 1–50；分页使用响应中的 `nextBefore` 原值。显式 `sourceTurnIds` 最多 8 个且不可重复，必须来自当前话题的已完成回合；不传时选择最近最多 8 个已完成回合。`suggestionId` 必须出现在所选讨论来源中。

- `DISCUSS`：普通作者讨论，只返回回答和可选整理建议，不创建可采纳资料提案。
- `PROPOSE`：作者点击整理后提交的新任务，生成角色卡或所选大纲的章节/场景资料提案；可关联建议及讨论来源。
- `REFINE`：继续打磨同话题中仍适用且状态为 `READY` 的提案，通过 `parentProposalId` 保留父提案关系，旧提案不被覆盖。已经采纳、拒绝或撤销的提案不继续派生，需基于当前资料重新讨论和整理。

`TurnDto` 包含 `id/operationId/status/resultKind/instruction/answer/intent/outlineId/suggestions/proposalIds/parentProposalId/sources/warnings/usage/createdAt`。`resultKind` 为 `ANSWER|NEEDS_INPUT|PROPOSALS` 或尚无结果。建议为 `{id,title,reason,sourceTurnIds}`，随讨论结果返回；建议来源保留该回合冻结历史的最近七条及当前回合，最多八条。没有建议时，前端手动整理也从所点回合的已冻结讨论来源恢复上下文，避免混入后来的讨论。仅显示建议不发起另一次模型调用。任务成功只表示讨论或候选已返回，不表示资料已保存。

`Source` 包含 `id/type/title/content/hash/truncated/reason`。`REFINE` 的 `PARENT_PROPOSAL` 来源与实际请求使用同一父提案快照，包含目标、父关系、基线、候选、理由和讨论回合标识，不嵌套复制父提案的全部历史来源全文。`usage` 分别记录 `modelCalls/inputTokens/outputTokens/cost`；业务调用次数不能冒充供应商内部实际尝试数。同键同请求复用原回合，同键不同请求拒绝；活动任务期间的新指令不得被静默绑定到旧任务。

## 提案与字段

`GET /proposals/{proposalId}` 返回 `ProposalDto`：

- 身份与目标：`id/turnId/parentProposalId/action/resourceType/targetId/outlineId/parentId`。`action` 为 `CREATE|UPDATE`，`resourceType` 为 `CHARACTER|CHAPTER|SCENE`；场景的 `parentId` 标识所属章节。
- 内容：`before` 是冻结基线，`candidate` 是当前可采纳候选，`modelCandidate` 保留模型原候选，`current` 为当前资源字段；新增项没有已有基线。`rationale/sources/sourceTurnIds` 解释来源与整理理由。
- 状态：`status=READY|ACCEPTED|REJECTED|UNDONE`，`revision` 为提案修订号，`resourceRevision` 为当前资源版本；`applicable/staleReason/undoAllowed` 告知当前可用操作。状态变化不删除旧提案或回执。

允许的候选字段如下；身份、顺序和正文不作为可编辑候选字段：

| 类型 | 字段 |
|---|---|
| 角色 | `name/synopsis/details/relationships/role/archetype` |
| 章节 | `title/summary/planning`；仅新增章节可附 `scenes` |
| 场景 | `title/summary/planning` |

章节 `planning` 沿用 `purpose/informationRelease/twistRole/selectedTwistId/revealFocus/tensionShift`。场景 `planning` 沿用 `minHan/maxHan/sceneType/goal/conflict/infoRelease/foreshadowId/revealFor/foreshadowHint/misdirectionAction/revealTrigger/payoffPlan/memeUsage`，篇幅与场景类型复用原有领域约束。更新省略的字段保留原值，允许清空的文本字段用 `null` 表达。

新章及其首批子场景组成一个不可拆提案。子场景只携带场景候选字段与服务器分配的稳定身份；编辑候选不能改变这组子场景的身份、数量或顺序。章节只追加到大纲末尾，场景只追加到所属章节末尾；更新原位保留既有内容结构，不删除或重排。

## 编辑、采纳、拒绝与撤销

- `PUT /proposals/{proposalId}`：`{expectedRevision,candidate}` 与 `Idempotency-Key`，保存作者手工编辑的候选，返回更新后的 `ProposalDto`。
- `POST /proposals/{proposalId}/accept`：`{expectedRevision,expectedResourceRevision?}` 与 `Idempotency-Key`，逐项采纳。
- `POST /proposals/{proposalId}/reject`：同样的处置请求，拒绝该项，不修改领域资料。
- `POST /proposals/{proposalId}/undo`：同样的处置请求，仅撤销仍适用的更新项。

处置返回 `{proposal,resourceRevision}`。客户端使用最近读取的提案修订号与资源版本；资源存在时传回其 `resourceRevision`，不能把新资料覆盖为旧提案。来源基线发生变化、目标消失或父章节变化时明确返回冲突。一个项目冲突不应阻止读取和采纳其他仍适用的项目；版本冲突后重新读取当前状态供作者比较，不自动三方合并。

新增项采纳后不支持自动撤销；更新项仅当前目标仍等于该项采纳后的快照时允许撤销，后续编辑不会被覆盖。编辑候选、采纳、拒绝和撤销均不调用模型。重复处置复用原回执，不重复创建角色、章节或场景。

常见冲突返回 HTTP 409：`STORY_ASSISTANT_TURN_IN_PROGRESS`（话题有活动任务）、`STORY_ASSISTANT_IDEMPOTENCY_CONFLICT`（同键不同输入）、`STORY_PROPOSAL_VERSION_CONFLICT`（候选已被编辑）、`STORY_RESOURCE_CONFLICT`（资源版本变化）、`STORY_PROPOSAL_STALE`（目标内容变化）、`STORY_PROPOSAL_DECIDED`（已处置）、`STORY_CREATE_UNDO_UNSUPPORTED`（新增项不支持撤销）。资料缺失或父章节变化另返回 `STORY_RESOURCE_MISSING/STORY_PARENT_CHANGED`。

非法候选、越出本期字段/结构范围、未选择大纲或未实际读取更新目标返回 422，如 `STORY_CANDIDATE_FIELD_INVALID`、`STORY_CANDIDATE_STRUCTURE_CHANGED`、`STORY_OUTLINE_REQUIRED`、`STORY_TARGET_NOT_READ`；超出上下文限额返回 `STORY_ASSISTANT_CONTEXT_TOO_LARGE`。这些结果不触发自动格式修复或新增模型调用。

角色资料按故事共享，大纲资料按所选大纲共享，不属于单个正文分支。前端在整理或采纳前处理目标未保存编辑：保存必须成功，或者由作者取消编辑；服务器只比较已持久化基线，无法代替客户端判断尚未提交的输入。

## 上下文与任务恢复

`STORY_ASSISTANT_TURN` 单任务最多两次模型业务调用，首次可以直接交付，或提出一次最多四项确定性只读查询后第二次交付。不调用 embedding/rerank，不循环查找或自动修复非法模型格式。完整输入、每步请求、读取结果和稳定调用身份持久化，恢复沿用原任务冻结内容；未知结果保留原对账语义，不能换键重推。

上下文范围为当前故事、角色卡、选定单大纲及当前话题讨论，不默认摄入全书正文。整理/打磨是新任务和新用量；创建或改名话题、查看记录、作者处置不触发模型。调用任务的查询、恢复和取消沿用[异步任务 API](ai-operations.md)。

只读资料预算为 3500 estimated-token，资源目录最多 64 项，列入目录不代表已读取完整资料。来源列表保留两步实际使用资源的并集，以及作者请求和已选择讨论；未读数量及超预算省略通过 `warnings` 告知。第二步在相同预算内选择自己的资料，普通更新目标必须在最终请求中完整可见，仅首步读过不能替代最终上下文。完整父提案和最多 8 个历史回合也计入既有整体请求准入，超限明确拒绝，不静默截断目标候选。

采纳只写现有角色卡和大纲资源，大纲保存沿用原有来源变更事件；不自动写 H1 正式账本、H2 本场计划、背景或人物知情。故事级作者讨论历史不加入 H2 正文编辑请求；普通生成继续使用原资料消费机制，H2 继续使用既有作者确认投影。
