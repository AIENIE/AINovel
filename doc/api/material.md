# Material API
- `POST /api/v1/materials`：创建素材，Body `{title,type,summary?,content,tags?}`。
- `GET /api/v1/materials`：素材列表。
- `GET /api/v1/materials/{id}`：素材详情。
- `PUT /api/v1/materials/{id}`：更新素材。
- `DELETE /api/v1/materials/{id}`：删除素材。
- `POST /api/v1/materials/upload` (multipart) ：兼容 TXT 上传入口，返回 `{id,fileName,status,progress,message,materialId}`；旧字段保持，新增 `materialId` 可为空。
- `GET /api/v1/materials/upload/{jobId}`：轮询任务状态，完成后附带 `progress=100`。
- `POST /api/v1/materials/search`：Body `{query,limit?}`，返回 chunk 级检索结果列表。每项包含：
  - `materialId`：素材 ID。
  - `chunkId`：稳定片段 ID。
  - `title`：素材标题。
  - `snippet`：命中的片段摘要。
  - `score`：综合相关性分数。
  - `chunkSeq`：片段序号，从 0 开始。
  - `source`：`keyword` 或 `vector`，表示最终采用的命中来源。
  - `matchReasons`：命中原因，如 `title` / `tags` / `summary` / `content` / `semantic`。
- `POST /api/v1/materials/editor/auto-hints`：正文自动提示，Body `{text,workspaceId?,limit?}`。
- `GET /api/v1/materials/review/pending`：待审核列表。
- `POST /api/v1/materials/{id}/review/approve|reject`：审核操作。
- `POST /api/v1/materials/find-duplicates`：管理员查重，返回候选对。候选基于标题、标签、摘要/正文的中文 token 与 n-gram 重合评分，每项包含 `sourceMaterialId`、`targetMaterialId`、`sourceTitle`、`targetTitle`、`score`、`reasons`。
- `POST /api/v1/materials/merge`：合并素材，Body `{sourceMaterialId,targetMaterialId,mergeTags?,mergeSummaryWhenEmpty?,note?}`。
- `GET /api/v1/materials/{id}/citations`：引用历史。服务端按素材标题、标签、摘要/正文信号扫描当前用户稿件正文片段，返回 `storyId`、`storyTitle`、`manuscriptId`、`sceneId`、`chapterTitle`、`sceneTitle`、`snippet`、`reason`。

## 当前检索策略（2026-10-03）

- 原文和不可变修订先保存，基础片段与语义索引分别处理。基础片段使用 Unicode code point，900 字符、120 字符重叠；审核前不参与检索。
- 默认使用基础检索，不调用旧供应商 embeddings。新接口融合作者已确认的实体/别名、标签精确路径和 MySQL ngram 全文候选；实体资料列表单独分页完整列举。
- 语义和重排默认关闭。启用后标准/Flash 1024 维使用独立索引，模型、维度、切分、输入模板版本固定；失败返回基础结果及降级原因，不混用模型向量。
- 旧搜索响应结构保留；当前旧入口返回 `keyword` 候选及原文匹配原因。普通写作和自动提示使用当前作品绑定资料，作者可主动扩展到个人或公共范围。
- 数据库召回、发送重排/生成前和结果展示前都复核权限、审核及资料版本。没有可见资料时不调用 embedding；日志不记录查询、原始模型响应或凭据。
- 灵感结果在界面标为“相近素材”，提示它只用于灵感参考，不能据此确认事实或判定重复。发起新查询或切换查找目的、资料范围时清空已有结果、来源预览及降级提示，并取消旧请求观察；晚返回的旧结果或原文不能替换新查询状态。

## 资料检索与证据接口

以下路径都以 `/api/v1/material-evidence` 为前缀，要求普通作者登录。

| 路径 | 行为 |
|---|---|
| `POST /search` | `{query,storyId,mode:fact/inspiration,scope:bound/personal/public,limit?}`，返回 `{mode,scope,items,degradation}`；最多 40 候选，界面默认 8 条 |
| `GET /chunks/{id}` | 当前可见原文、资料/修订 ID、版本、标题、code point 起止位置；撤权或旧版本不可读 |
| `GET/PUT /works/{story}/settings` | 作品绑定、索引配置、重排、自动提示及自动检查偏好；写入含 `expectedVersion,requestKey` |
| `GET/PUT /manuscripts/{id}/scenes/{scene}/package` | 场景固定/排除清单；固定最多 8 项；写入含预期版本和幂等键 |
| `POST /hints` | `{manuscriptId,sceneId,query}`；默认关闭，同场景每 30 秒最多一次；客户端停顿 1500ms 后观察，关闭/切换取消观察 |
| `GET /statuses` | 分开返回 `saved/review/basic/semantic`；保存成功不等于已审核或已可检索 |
| `POST /revisions/{revision}/semantic/{profile}/resume` | `{expectedVersion,requestKey}`；作者恢复待对账的当前资料索引，返回 `{revisionId,profile,status}`。沿用原网关用户、运行及批次请求编号；未知结果仍由网关拒绝重推 |
| `GET /entities?page=0`、`GET /entities/{id}/sources?story=...&page=0` | 每页 50 项完整列举；实体/别名及来源关系只在作者确认的资料版本有效 |
| `POST /entities` | `{name,aliases,materials:{materialId:expectedVersion},requestKey}`，手工确认实体及别名 |
| `GET /sources/{id}/revisions?page=0`、`GET /revisions/{id}/raw` | 不可变资料修订和完整原文；作者可读自有历史，公共历史读取仍要求当前公共资料有效且已审核 |
| `GET /duplicates` | 有界自有资料完全重复、格式差异和字符片段重叠候选；相似主题不能作为重复判定 |
| `POST /sources/merge` | 两份原资料 ID/预期版本、新标题/正文、`requestKey`；作者确认后创建新资料并保留来源，不覆盖原资料 |

`items` 包含 `chunkId,materialId,revisionId,sourceVersion,title,text,start,end,reasons,rank`。位置均针对不可变修订原文，区间为左闭右开；`rank` 和重排分数不表示事实成立概率。原文差异是保守字符区间比较，变化区间可能含未变片段；长区间预览明确截断，完整修订仍可打开。

语义索引恢复只适用于原任务作者及当前已审核修订。功能关闭、资料版本变化、网关用户变化、原请求身份缺失或任务不是待对账状态均明确拒绝；不生成替代请求键。相同恢复键及内容返回原回执，同键不同内容返回 409。旧任务缺少可证明的请求身份时保留历史待对账状态。

网关明确返回“预算中已有外发，本请求尚未外发”时，索引/核验任务沿原请求身份等待 5、15、30 秒，最多自动等待三次；计数及时间持久化，重启不清零。网络断开、超时、未知结果和其他拒绝均不进入此队列。超过等待次数进入待对账/人工恢复；未外发的等待不消耗供应商调用额度，不释放主动任务原冻结积分。

### 原文写入与导入

- `POST /sources`：`{input:{title,type,summary?,content,tags?},requestKey}`。
- `PUT /sources/{id}`：`{input:{...},expectedVersion,requestKey}`。
- `POST /sources/upload`：multipart `file,requestKey`。只接收 UTF-8 TXT，最多 2 MiB、正文最多 400000 code points；解码失败明确拒绝。导入原文、修订、基础任务及回执同事务提交，后续处理独立执行；上传资料保持待审核。
- 同用户/范围/键/相同请求恢复原结果；同键不同请求 409，结果资料已删除 410。超时重试不能换键。前端保留未确认完成的导入意图，确认完成后允许新的导入意图。
- 旧创建、编辑、上传、搜索等入口继续保留；新写入契约不追溯伪造旧幂等键。

### 来源关系

- `POST /manuscripts/{id}/scenes/{scene}/citations` 返回 `{id}`；输入包含修订、分支、正文版本、`CONFIRMED` 类型、来源原文/CP位置、正文逐字引文/块 ID、预期稿件版本及请求键。
- `GET /manuscripts/{id}/scenes/{scene}/citation-body` 返回当前已保存正文块及稿件版本，用于逐字定位；确认前仍在事务内重新核对版本和归属。
- `GET /manuscripts/{id}/citations` 分开列出 `REFERENCE`（实际送入生成）、`CONFIRMED`（作者确认）、`POSSIBLE`（机器候选）与 `CURRENT/REVIEW_REQUIRED` 状态。普通历史信号匹配接口不是作者确认引用。
- 核验报告同一疑点中同时逐字定位正文与资料时，原子保存 `POSSIBLE` 关联及 `report_task_id/report_finding_index`；它只表示机器提示的可能关联，不确认引用、事实或人物知情。每份报告至多 64 条关联，超限明确记入范围说明；过期或已取消报告不生成有效关联。
- 资料修订、正文改动、分支变化或撤权进入待复核；恢复旧文字不能自动恢复确认。已撤权原文不向调用者展示。

### 抽取及跨章节核验任务

- `POST /tasks/preview` 输入 `kind:EXTRACT/CHECK`、资料修订或稿件/分支/正文版本/场景范围、问题、预期版本、请求键，返回可用性、原因、费用上限、计划外发及输入指纹。
- `POST /tasks` 发送 `{request,previewFingerprint,acceptedMaximumCredits}`，返回稳定任务 ID；主动任务冻结已接受积分上限，权威 usage 结算，禁止超上限补扣。
- `GET /tasks?manuscript=...`、`GET /tasks/{id}`、`POST /tasks/{id}/cancel`、`POST /tasks/{id}/resume` 支持历史、取消和恢复。恢复优先使用已持久结果；未知结果保持冻结且沿原任务/网关键对账，不换键重推。
- `POST /tasks/{id}/confirm` 输入 `{candidateIndex,expectedVersion,requestKey}`；抽取结果逐字证据校验后由作者确认，元数据候选不写入第二份小说事实账本。
- `GET /revisions/{id}/annotations` 返回当前资料版本的作者确认标注。
- 自动检查在成功生成提交后异步调度，每份不可变生成结果至多一次，每用户每日最多 10 个、同时 1 个；平台承担成本。默认关闭，限额不足返回跳过原因。
- 报告区分世界事实、人物信念、读者披露、作者计划，允许证据不足和无法判断；只检查冻结证据，不自动改稿/确认事实/改变知情。自动最多 8 次、主动最多 24 次外发，达到范围/预算限制返回部分覆盖。
- 当前使用服务端受控检索、原文和相邻段落预取后的一元结构化检查；报告不构成全书无矛盾的证明。完整 H3–H6 状态仍由路线图维护。

## 查重与引用闭环

- 查重会排除 `rejected` 素材，并按分数倒序返回候选；它不会自动合并，仍需调用 `/merge` 执行人工确认后的合并。
- 引用查询仅返回当前素材所有者名下稿件中的命中片段；HTML 正文会先剥离标签再做信号匹配。
# v1.0 审计整改补充（2026-08-17）

- 审核事务只更新素材状态、chunk 投影及持久索引任务；embedding/Qdrant 写入由可重试 worker 完成。
- 关键词检索在数据库按 `approved` 与 `owner/public` 权限过滤并分页。
- Qdrant payload 包含 material/chunk/owner/status/text，服务端同时过滤；返回前再次以数据库投影核验，陈旧或已删除向量不会泄露。
