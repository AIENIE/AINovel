# 叙事证据与状态 API

需真实用户认证；基路径 `/api/v2/manuscripts/{manuscriptId}/branches/{branchId}/narrative`。所有资源按稿件所有者及分支校验；同故事不同稿件隔离。

| 方法与路径 | 请求/结果 |
|---|---|
| POST `/scene-approvals` | `{sceneId, expectedManuscriptVersion, expectedCanonRevision}` → `{approvalId, extractionId, operationId}` |
| GET `/extractions/{id}` | 候选、任务状态、来源是否失效、模型、提示词版本、实际用量、原始审阅 |
| POST `/extractions/{id}/review` | `{expectedManuscriptVersion, expectedCanonRevision, decisions, additions}` → `{commitId, canonRevision, recordIds}` |
| GET `/state` | 可选 `sceneId, characterId, kind, status, canonRevision` → `{canonRevision, manuscriptVersion, branchId, records, extractions}` |
| GET `/scene-approvals/{id}/evidence` | 不可变版本、位置、`blocks[{id,text}]`、文本哈希及确认时间 |

两个 POST 都要求 `Idempotency-Key`（1–128 字符）；相同键与相同请求重放返回原回执，键绑定不同请求返回 409。客户端遇到不确定网络结果必须保留原请求与键，收到确定的冲突后重新读取并审阅。普通保存不自动创建抽取。

`decisions` 必须恰好覆盖全部候选：`{candidateId, decision: "ACCEPT"|"REJECT", edited: Assertion|null}`。接受时可传修改后的完整 Assertion；`additions` 是作者补充的 Assertion 数组。

```json
{
  "subject": "林青",
  "characterId": null,
  "statement": "林青声称桥断了",
  "kind": "UTTERANCE",
  "holderCharacterId": null,
  "worldTime": null,
  "uncertainty": "正文未证实桥的实际状态",
  "evidence": [{"blockId": "b1", "quote": "桥断了"}],
  "supersedesId": null
}
```

类型为 `FACT/UTTERANCE/BELIEF/RUMOR/INFERENCE`，BELIEF 必须有本故事人物持有人。证据是准确引文，服务端重算 `start/end`（Unicode code point，左闭右开）；无匹配或同块歧义不允许入账。补充/修改与候选使用相同校验，格式正确仍须作者语义判断。

账本记录状态为 `CONFIRMED/STALE/SUPERSEDED`。`canonRevision` 查询保存旧版本的有效性；当前查询会检测来源变化。当前没有有效项不等于从未分析。抽取状态包括既有任务状态及 `READY`、`INVALID_OUTPUT`；`READY` 且零候选表示没有抽取到变化，单项 `validationError` 表示部分无效，可拒绝或修正。取消任务不能提交审阅。

确认要求当前活动分支及预期版本，固定快照不自动入账。新分支空账本，合并不导入其他分支记录。来源或实际输入依赖变化返回 409 并保留候选；审阅需重新确认正文。错误包括 404 资源不匹配、409 版本/幂等/分支/来源冲突、422 无效证据或不完整审阅。每次成功审阅只改变一次账本修订，全部接受项原子保存。

`NARRATIVE_STATE_EXTRACT` 通过现有 [AI 任务接口](ai-operations.md)取消/重试。一次抽取只进行一次模型任务；格式错误不自动付费修复，已完成调用按原调用记录重放。`usage` 由 AI 服务真实返回，尚未取得用量时为 null，不显示虚构费用。
