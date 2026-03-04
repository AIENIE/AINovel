# Manuscript API

- `GET /api/v1/outlines/{outlineId}/manuscripts`：按大纲获取稿件列表，返回 `ManuscriptDto[]`。
- `POST /api/v1/outlines/{outlineId}/manuscripts`：创建稿件，Body `{title, worldId?}`，返回 `ManuscriptDto`。
- `GET /api/v1/manuscripts/{id}`：稿件详情，返回 `ManuscriptDto`。
- `DELETE /api/v1/manuscripts/{id}`：删除稿件，返回 204。
- `POST /api/v1/manuscripts/{id}/scenes/{sceneId}/generate`：生成指定场景稿件内容，返回 `ManuscriptDto`。
- `PUT /api/v1/manuscripts/{id}/sections/{sceneId}`：保存指定场景正文，Body `{content}`，返回 `ManuscriptDto`。
- （兼容旧接口）`POST /api/v1/manuscript/scenes/{sceneId}/generate` / `PUT /api/v1/manuscript/sections/{sectionId}`：仍可用，但会默认使用第一份稿件（不建议）。
- `POST /api/v1/manuscripts/{id}/sections/analyze-character-changes`：分析角色变化，Body `{chapterNumber?, sectionNumber?, sectionContent, characterIds?}`，返回 `CharacterChangeLogDto[]`。
- `GET /api/v1/manuscripts/{id}/character-change-logs`：角色变化日志列表。
- `GET /api/v1/manuscripts/{id}/character-change-logs/{characterId}`：指定角色的变化日志列表。
- `POST /api/v1/ai/generate-dialogue`：对话生成，Body `{text, instruction?, contextType?}`，返回文本。

## 正文生成门禁（当前实现）

- 生成链路由 AI 直出正文，不再是固定占位文案。
- 每节正文汉字数门禁：`2800-3200`。
- 最多重试：`3` 次；每次会基于上次字数偏差自动加“扩写/压缩”约束。
- 超长文本会在服务端裁剪到上限（3200 汉字）后再入库。
- 连续失败会直接返回错误，提示当前字数与目标区间。

## 数据结构

- `ManuscriptDto`：`{id, outlineId, title, worldId, sections, updatedAt}`，其中 `sections` 为 `sceneId -> content` 的映射。
- `CharacterChangeLogDto`：`{id, characterId, summary, createdAt}`。
