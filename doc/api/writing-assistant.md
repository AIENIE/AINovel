# 写作助手 API

浏览器前缀为 `/api/v1/manuscripts/{manuscriptId}/scenes/{sceneId}/assistant`。普通用户会话、资源归属、稿件分支与版本检查沿用正文接口。旧 `/v1/ai/chat`、`/v1/ai/refine` 保留其他消费者；工作台助手使用本组持久回合接口。

## 会话与回合

- `GET ?branchId=...&before=...&limit=20`：当前稿件/分支/场景会话及分页回合，返回 `id`（无会话时为空）、`branchId`、`sceneId`、`h2Enabled`、`turns`、`hasMore`、`nextBefore`。读取不创建会话。
- `POST /turns`：携带 `Idempotency-Key`，提交 `branchId`、`expectedVersion`、`instruction`、`sourceHtml`、`selection`、可选 `parentProposalId` 及 `intent=AUTO|EDIT|AUTHOR_QA`，返回 `202 {turnId,operationId}`。
- `GET /turns/{turnId}`：回合状态、结果、来源、告知事项、用量及提案引用。

`selection=null` 表示当前场景普通正文；选区对象为 `{startBlockIndex,endBlockIndex,startOffset,endOffset}`。块序号对应编辑器顶层节点/HTML 顶层元素，偏移为段内 UTF-16 文本位置，硬换行计一个换行字符。首尾相同位置为插入点；固定结构不在本期自动改写范围。

`TurnDto` 包含 `id`、`operationId`、`status`、`resultKind`、`instruction`、`answer`、`proposalId`、`parentProposalId`、`sources`、`warnings`、`usage` 和 `createdAt`。`resultKind` 为 `ANSWER`、`NEEDS_INPUT`、`PROPOSAL_READY` 或尚无结果。任务成功不代表正文已保存。

来源包含 ID、类型、标题、实际使用内容、可选场景定位、hash、截取标记及原因。`usage` 将 `modelCalls`、`inputTokens`、`outputTokens`、`cost` 分开；模型业务调用次数不等于供应商内部尝试数。

同一键与相同输入返回原回合；同键不同输入拒绝。同会话仍有活动任务时，新键提交返回明确忙碌冲突，不将新指令绑定旧任务。

## 提案与作者处置

- `GET /proposals/{proposalId}`：完整 `beforeHtml/afterHtml`、来源版本/分支/hash、父提案、状态、是否可采纳及失效原因、修改前后汉字数与目标区间。
- `POST /proposals/{proposalId}/accept`：采纳已经展示的完整提案。
- `POST /proposals/{proposalId}/reject`：拒绝提案，不修改正文。
- `POST /proposals/{proposalId}/undo`：仅当前场景仍等于该次采纳结果时恢复采纳前正文。

处置携带 `Idempotency-Key` 和 `{expectedVersion,branchId}`，返回 `{proposalId,status,manuscript}`。状态为 `READY/ACCEPTED/REJECTED/UNDONE`；失效提案仍可阅读。客户端不提交自行拼接的 afterHTML，服务端只写入已经持久化的候选正文。采纳、拒绝、撤销不调用模型。

普通模式提案可以基于编辑器未保存正文，采纳前先将该作者基线通过正常版本检查保存。仅自身保存使版本前进且原文相同可采用最新预期版本；其他内容变化不自动合并。H2 发起前必须先保存，源版本戳保持严格校验。

未采纳提案继续打磨通过新回合的 parentProposalId 表达，仍与原作者基线比较；旧稿不覆盖。已采纳后继续改稿冻结当前正文。篇幅越界只标记供作者确认，不追加自动长度循环。

## 上下文、模型调用和恢复

`WRITING_ASSISTANT_TURN` 使用通用异步任务查询、恢复和取消接口。一回合至多两次模型业务调用、一次最多四项只读查询。查询直接返回带出处的摘录；无第三轮读取或自动格式修复。固定调用标识、完整请求、读取回执和结果持久化，恢复不查询新正文替换旧输入。

当前场景/选区完整保留，额外背景合计 3500 estimated-token；最多八个同用途历史回合，预算不足先省略旧历史并告知。现有 AI 准入与整体输入预算继续生效。超限返回明确业务错误，不截断目标正文。基础检索不调用 embedding/rerank。

H2 改稿只消费受控 SCENE 的 content/included；excluded 原文与作者广泛问答历史不进入改稿请求。作者查阅前文可以交付回答，但不能同回合用全知材料生成限知正文。新知情信息仍需现有作者入口确认。

网络失败、未知结果和取消保留原任务语义；已有回执先对账，不能换键重新调用。主动重新生成或继续打磨是新任务和新用量。已收到但格式不可解析的结果保留在回合记录中，不当作可采纳提案。

W4：提交前可使用稿件场景的 world-context/preview 审阅输入，并提交 contextHash、expectedSelectionRevision。轮次详情增加 worldContext（冻结世界材料范围）和 steps（各步实际读取来源）；只有已有模型结果的步骤标记已读。第二步只能访问冻结且获准的池，不能绕过手动排除与 H2。
