# Story & Outline API

## 故事与角色

- `GET /api/v1/story-cards`：当前用户故事列表。
- `GET /api/v1/story-cards/{id}`：故事详情。
- `POST /api/v1/stories`：创建故事，Body `title,synopsis,genre,tone,worldId?`。
- `PUT /api/v1/story-cards/{id}`：更新故事字段。
- `DELETE /api/v1/stories/{id}`：删除故事及关联。
- `GET /api/v1/story-cards/{id}/character-cards`：角色列表。
- `POST /api/v1/story-cards/{id}/characters`：新增角色，Body `name,synopsis,details,relationships`。
- `PUT /api/v1/character-cards/{id}` / `DELETE /api/v1/character-cards/{id}`：修改/删除角色。
- `POST /api/v1/story-cards/{id}/refine`：文本润色，Body `{text,instruction?,contextType?}`。
- `POST /api/v1/character-cards/{id}/refine`：角色字段润色。
- `POST /api/v1/conception`：根据一句话灵感快速创建故事与角色，Body 同创建故事。

## 大纲

- `GET /api/v1/story-cards/{storyId}/outlines`：某故事下的大纲列表。
- `POST /api/v1/story-cards/{storyId}/outlines`：创建空大纲，Body `{title?,worldId?}`。
- `GET /api/v1/outlines/{id}`：大纲详情。
- `PUT /api/v1/outlines/{id}`：保存大纲，Body `{title,worldId,chapters:[{id?,title,summary,order,scenes:[{id?,title,summary,content,order}]}]}`。
- `DELETE /api/v1/outlines/{id}`：删除大纲。

## AI 章节生成

- `POST /api/v1/outlines/{outlineId}/chapters`：新增 AI 章节，Body `{chapterNumber?,sectionsPerChapter?,wordsPerSection?,worldId?}`。
- `sectionsPerChapter` 会被后端强制归一化到 `5-7`。
- 生成逻辑要求返回结构化 JSON（章节标题、章节摘要、节标题与节摘要）；若 AI 返回异常则使用兜底文案。

## 章节/场景编辑

- `PUT /api/v1/chapters/{id}`：更新章节标题/摘要/顺序，Body `{title?,summary?,order?}`，返回 `OutlineDto`。
- `PUT /api/v1/scenes/{id}`：更新场景标题/摘要/正文/顺序，Body `{title?,summary?,content?,order?}`，返回 `OutlineDto`。
- `POST /api/v1/outlines/scenes/{id}/refine`：场景润色。
