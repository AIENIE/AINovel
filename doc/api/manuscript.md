# Manuscript API

- `GET /api/v1/outlines/{outlineId}/manuscripts`：按大纲获取稿件列表，返回 `ManuscriptDto[]`。
- `POST /api/v1/outlines/{outlineId}/manuscripts`：创建稿件，Body `{title, worldId?}`，返回 `ManuscriptDto`。
- `GET /api/v1/manuscripts/{id}`：稿件详情，返回 `ManuscriptDto`。
- `DELETE /api/v1/manuscripts/{id}`：删除稿件，返回 204。
- `POST /api/v1/manuscripts/{id}/scenes/{sceneId}/generate?mode=fast|crafted`：生成指定场景稿件内容，返回 `ManuscriptDto`。
- `POST /api/v1/manuscripts/{id}/scenes/{sceneId}/generate/operations?mode=fast|crafted`：同一生成与质量链路的异步进度版本；当前前端使用此接口，完成后重新读取稿件。
  - `fast` 为默认标准起草模式；省略或传入未知值时也使用 `fast`。
  - `crafted` 在标准提示词上注入 15 条按类别采样的反套路约束和 2 条轮换叙事目标；仍为单候选起草，不包含节拍规划或多候选选择。
  - 调用方必须确认响应包含目标 `sceneId` 的非空正文；前端以原子场景回写替换本地草稿，避免延迟自动保存覆盖服务端结果。
- `PUT /api/v1/manuscripts/{id}/sections/{sceneId}`：保存指定场景正文，Body `{content}`，返回 `ManuscriptDto`。
- （兼容旧接口）`POST /api/v1/manuscript/scenes/{sceneId}/generate` / `PUT /api/v1/manuscript/sections/{sectionId}`：仍可用，但会默认使用第一份稿件且生成固定为 `fast`（不建议）。
- `POST /api/v1/manuscripts/{id}/sections/analyze-character-changes`：分析角色变化，Body `{chapterNumber?, sectionNumber?, sectionContent, characterIds?}`，返回 `CharacterChangeLogDto[]`。
- `GET /api/v1/manuscripts/{id}/character-change-logs`：角色变化日志列表。
- `GET /api/v1/manuscripts/{id}/character-change-logs/{characterId}`：指定角色的变化日志列表。
- `POST /api/v1/ai/generate-dialogue`：对话生成，Body `{text, instruction?, contextType?}`，返回文本。

## 正文生成门禁（当前实现）

- 生成链路由 AI 直出正文，不再是固定占位文案。
- 正文提示词由 `PromptAssemblyService` 统一拼装为两段消息：
  - 稳定 `system` 消息包含 `AINOVEL_SCENE_DRAFT_RULES_V1` 和长期不变的写作规则，用于提升模型 prompt cache 命中率。
  - 动态 `user` 消息包含故事、章节、场景、角色、前文、参考资料、近期表达避让和重试说明。
- 动态上下文默认预算为 `128000` token，服务端硬上限为 `256000` token。
- 生成前会按当前场景检索素材库，将最多 8 条 chunk 级参考资料注入动态提示词；embeddings/Qdrant 不可用时仍使用关键词 fallback。
- 提示词会把近期前文中出现过的常见套路表达加入“近期表达避让”，生成后仍继续执行反 slop 质量门禁。
- 精雕模式的负面模式来自 Flyway `V2__slop_patterns.sql` 建立的 `slop_patterns` 表。相同场景和相同模式库会得到稳定的约束样本；模式库变更后样本会随之变化。
- 每节正文汉字数门禁：`2800-3200`。
- 最多重试：`3` 次；每次会基于上次字数偏差自动加“扩写/压缩”约束。
- 超长文本会在服务端裁剪到上限（3200 汉字）后再入库。
- 连续失败会直接返回错误，提示当前字数与目标区间。

## 数据结构

- `ManuscriptDto`：`{id, outlineId, title, worldId, sections, updatedAt}`，其中 `sections` 为 `sceneId -> content` 的映射。
- `CharacterChangeLogDto`：`{id, characterId, summary, createdAt}`。
