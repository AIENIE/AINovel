# Story & Outline API

## 故事与角色

- `GET /api/v1/story-cards`：当前用户故事列表。
- `GET /api/v1/story-cards/{id}`：故事详情。
- `POST /api/v1/stories`：创建故事，Body `title,synopsis,genre,tone,worldId?`。
- `PUT /api/v1/story-cards/{id}`：更新故事字段。
- `DELETE /api/v1/stories/{id}`：删除故事，并由数据库级联清理角色卡、大纲、稿件、版本、质量记录和其它故事派生数据；返回 204。
- `GET /api/v1/story-cards/{id}/character-cards`：角色列表。
- `POST /api/v1/story-cards/{id}/characters`：新增角色，Body `name,synopsis,details,relationships`。
- `PUT /api/v1/character-cards/{id}` / `DELETE /api/v1/character-cards/{id}`：修改/删除角色。
- `POST /api/v1/story-cards/{id}/refine`：文本润色，Body `{text,instruction?,contextType?}`。
- `POST /api/v1/character-cards/{id}/refine`：角色字段润色。
- `POST /api/v1/conception`：根据一句话灵感快速创建故事与角色，Body 同创建故事。
- `POST /api/v1/conception/operations`：上述构思的异步进度版本，返回 `202 {operationId}`。

角色响应 `CharacterDto` 包含 `revision`。角色更新可传 `expectedRevision`；有值时必须与当前资源版本一致，否则返回 `409 STORY_RESOURCE_CONFLICT`，不覆盖更新。角色资料更新后返回最新版本。旧调用不传该字段仍兼容；工作台与故事级提案的并行编辑使用版本检查。

## 大纲

- `GET /api/v1/story-cards/{storyId}/outlines`：某故事下的大纲列表。
- `POST /api/v1/story-cards/{storyId}/outlines`：创建空大纲，Body `{title?,worldId?}`。
- `GET /api/v1/outlines/{id}`：大纲详情。
- `PUT /api/v1/outlines/{id}`：保存大纲，Body `{title,worldId,planning?,expectedRevision?,chapters:[{id?,title,summary,order,planning?,scenes:[{id?,title,summary,content,order,planning?}]}]}`。
- `DELETE /api/v1/outlines/{id}`：删除大纲。

大纲响应 `OutlineDto` 包含 `revision`。大纲整体保存、章节更新及场景更新的 `expectedRevision` 均指所属大纲版本；有值且不匹配时返回 `409 STORY_RESOURCE_CONFLICT`。保存刷新版本并沿用来源变更事件，前端不得用旧版本静默覆盖资料提案或其他页面的更新。

## AI 章节生成

- `POST /api/v1/outlines/{outlineId}/chapters`：新增 AI 章节，Body `{chapterNumber?,sectionsPerChapter?,wordsPerSection?,worldId?}`。
- `POST /api/v1/outlines/{outlineId}/chapters/operations`：章节生成的异步进度版本。
- `sectionsPerChapter` 会被后端强制归一化到 `5-7`。
- 生成逻辑要求返回结构化 JSON（章节标题、章节摘要、节标题与节摘要）；空输出或格式异常会保留原结果并阻止追加，不创建占位章节。

## 章节/场景编辑

- `PUT /api/v1/chapters/{id}`：更新章节标题/摘要/顺序，Body `{title?,summary?,order?,expectedRevision?}`，返回 `OutlineDto`。
- `PUT /api/v1/scenes/{id}`：更新场景标题/摘要/正文/顺序，Body `{title?,summary?,content?,order?,expectedRevision?}`，返回 `OutlineDto`。
- `POST /api/v1/outlines/scenes/{id}/refine`：场景润色。

故事级讨论和逐项资料整理使用独立的[故事级助手 API](story-assistant.md)，不直接把讨论答复提交给本组保存接口。其新增操作只末尾追加，不能代替本组人工结构编辑能力。

## W4 持久大纲 AI 结果

`POST /api/v1/outlines/{id}/ai-operations`，携带 `Idempotency-Key`。请求 `{action,nodeId?,instruction?,text?,chapterNumber?,sectionsPerChapter?,expectedRevision?,contextHash?,expectedSelectionRevision?}`，action 为 CHAPTER_GENERATE、CHAPTER_REFINE 或 SCENE_REFINE。润色必须标明实际节点 ID。沿用 outline.chapter、outline.chapter_refine、outline.scene_refine 配置。响应 `{resultId,operationId}`。

`GET /api/v1/outlines/{id}/ai-results` 返回最近 50 项；`GET /ai-results/{resultId}` 返回完整候选、原结果、修改前内容、参数和冻结来源。正常章节在同一事务核对大纲、绑定与选择后追加；变化时保留 READY 结果。`POST /ai-results/{resultId}/accept` 请求 `{expectedRevision,expectedOutlineRevision,contextHash,requestKey}`，由作者审阅当前预览后确认追加。相同处置键只追加一次。润色候选由前端采纳到编辑草稿，沿用人工保存接口。

旧 chapters 及 chapters/operations 入口转用相同冻结和持久结果服务。带场景 ID 的旧润色校验归属并读取绑定世界；无资源身份的 `/api/v1/ai/outline-refine` 仅处理输入文本。旧 worldId 不覆盖正式绑定。无法判定输入的旧章节任务停止恢复，不按 W4 重建请求；既有结果与费用保留。
