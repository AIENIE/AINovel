# Manuscript API

- `GET /api/v1/outlines/{outlineId}/manuscripts`：按大纲获取稿件列表，返回 `ManuscriptDto[]`。
- `POST /api/v1/outlines/{outlineId}/manuscripts`：创建稿件，Body `{title, worldId?}`，返回 `ManuscriptDto`。
- `GET /api/v1/manuscripts/{id}`：稿件详情，返回 `ManuscriptDto`。
- `DELETE /api/v1/manuscripts/{id}`：删除稿件，返回 204。
- `POST /api/v1/manuscripts/{id}/scenes/{sceneId}/generate?mode=fast|crafted`：生成指定场景稿件内容，返回 `ManuscriptDto`。
- `POST /api/v1/manuscripts/{id}/scenes/{sceneId}/generate/operations?mode=fast|crafted`：同一生成与质量链路的异步进度版本；当前前端使用此接口，按 `SAVED` / `NEEDS_REVIEW` 区分正式保存与候选交付。
  - `fast` 为默认标准起草模式；省略或传入未知值时也使用 `fast`。
  - `crafted` 在标准提示词上注入 15 条按类别采样的反套路约束和 2 条轮换叙事目标；仍为单候选起草，不包含节拍规划或多候选选择。
  - 调用方必须确认响应包含目标 `sceneId` 的非空正文；前端仅在 `SAVED` 时读取正文，并保留调用期间产生的未保存编辑。
- `PUT /api/v1/manuscripts/{id}/sections/{sceneId}`：保存指定场景正文，Body `{content}`，返回 `ManuscriptDto`。
- `GET /api/v1/manuscripts/{id}/scenes/{sceneId}/generation-runs?limit=10`：按时间倒序返回生成历史；`limit` 默认 10，并在服务端限制为 `1..50`。重新生成会将旧记录标记为 `SUPERSEDED`，版本回滚只更新实际受影响场景的记录。
- `PATCH /api/v1/manuscripts/{id}/scenes/{sceneId}/generation-runs/{runId}/feedback`：更新 `{tags, note, preferenceConfirmed}`。备注按 Unicode code point 最长 500 字；标签固定为 `PLOT_CAUSALITY`、`CHARACTER_MOTIVATION`、`CONTINUITY_SETTING`、`VOICE_DIALOGUE`、`PACING`、`STYLE_SPECIFICITY`、`AI_CLICHE`、`OTHER`。将 `preferenceConfirmed` 设为 `true` 时必须至少保留一个标签或一条备注。
- （兼容旧接口）`POST /api/v1/manuscript/scenes/{sceneId}/generate` / `PUT /api/v1/manuscript/sections/{sectionId}`：仍可用，但会默认使用第一份稿件且生成固定为 `fast`（不建议）。
- `GET /api/v1/manuscripts/{id}/character-change-logs`：历史角色变化日志只读列表，每项 `verificationStatus: "UNVERIFIED_LEGACY"`，不作为已确认事实。
- `GET /api/v1/manuscripts/{id}/character-change-logs/{characterId}`：指定角色的变化日志列表。
- `POST /api/v1/ai/generate-dialogue`：对话生成，Body `{text, instruction?, contextType?}`，返回文本。

## 正文生成门禁（当前实现）

- 生成链路由 AI 直出正文，不再是固定占位文案。
- 正文提示词由 `PromptAssemblyService` 统一拼装为两段消息：
  - 稳定 `system` 消息包含 `AINOVEL_SCENE_DRAFT_RULES_V1` 和长期不变的写作规则，用于提升模型 prompt cache 命中率。
  - 动态 `user` 消息包含故事、章节、场景、角色、前文、参考资料、近期表达避让和重试说明。
- 动态提示词总预算默认 `128000` token，服务端硬上限为 `256000` token；其中 `scene-draft-v2` 场景上下文编译片段固定使用 `3500` estimated-token 预算。
- `scene-draft-v2` 固定装入整体、章节和场景 planning 与绑定世界，按“场景契约与硬设定 → 最近两场跨章正文 → 全局风格与相关角色声音 → 最多两个历史相关窗口”分配预算。只有命中当前场景锚点且已启用的 Lorebook 会被选择；图谱关系必须两端条目均入选。
- 编译 manifest 不含正文，记录来源类型与 ID、入选原因、估算 Token、截断状态、预算占用和上下文 SHA-256 指纹。带 `manuscriptId + sceneId` 的上下文预览使用同一个编译器。
- 生成前会按当前场景检索素材库，将最多 8 条 chunk 级参考资料注入动态提示词；embeddings/Qdrant 不可用时仍使用关键词 fallback。
- 提示词会把近期前文中出现过的常见套路表达加入“近期表达避让”，生成后仍继续执行反 slop 质量门禁。
- 精雕模式的 38 条负面模式来自 classpath 版本化 JSON 注册表，按 `PHRASE`、`BODY_ACTION`、`IMAGERY`、`ENDING_CLICHE`、`NARRATIVE_MECHANIC` 配额采样。相同场景和同一版本规则库得到稳定样本；旧 `slop_patterns` 表只保留为迁移历史。
- 正文沿用场景 planning 的 `minHan/maxHan`；未设置为 2800–3200 汉字，按 Unicode `Script=Han` 统计，首稿瞄准中点。
- 普通与 H2 最多一次首稿加一次篇幅修订。修订携带完整上一稿、实际数、中点差额及保留事件/事实/决定/结尾的要求。
- 任务冻结首稿完整输入和修订请求；不截断上一稿、不扩大预算。输入超限交付已有候选。独立质量功能维持原开关，其调用不包含在“两次正文调用”中。
- 返回正文立即独立持久化；最终质量处理后的文本重新计算汉字数。仍越界为 `NEEDS_REVIEW`，不启动第三次正文生成。第二次调用失败保留初稿，任务维持真实失败/待恢复状态。
- 每次成功场景生成会在同一数据库事务内写入正文、`snapshotType=generation` 的内部版本快照和 `scene_generation_runs` 记录；任一写入失败时整体回滚。
- generation run 保存模式、创建者、模型、`scene-draft-v2`、实际成功尝试次数、上下文 manifest/hash、实际提示词 hash 和最终输出 hash，不保存 prompt 消息、上下文正文或指令正文。
- 人工正文保存提交后，以独立事务执行 Unicode code-point Myers diff；等长替换不会被净长度掩盖。归因失败只留下结构化错误和待重算状态，不回滚作者正文；查询历史时会比较当前正文 hash 并重试计算。
- run 状态只使用 `GENERATED`、`EDITED`、`SUPERSEDED`、`REVERTED`。首次和末次编辑时间分开保存；同一场景再次生成通过 `previousRunId` 关联前后两次 run。

## 数据结构

- `ManuscriptDto`：`{id, outlineId, title, worldId, sections, lastGenerationRun?, updatedAt}`，其中 `sections` 为 `sceneId -> content`。`lastGenerationRun` 只在同步或异步生成结果中返回 `{id,generationVersionId,status,createdAt}`；普通列表、详情和保存响应省略该字段。
- `SceneGenerationRunDto`：`{id,manuscriptId,sceneId,createdBy,mode,status,modelKey,promptVersion,attemptCount,contextHash,contextManifest,generationVersionId,previousRunId,firstEditedAt,lastEditedAt,addedCharacters,deletedCharacters,retentionRate,recalculationPending,feedbackTags,feedbackNote,preferenceConfirmed,createdAt,updatedAt}`。待重算时三个差异指标为 `null`。
- `contextManifest`：`{promptVersion,tokenBudget,tokenUsed,sources}`；source 为 `{sourceType,sourceId,label,reason,estimatedTokens,truncated}`。
- `CharacterChangeLogDto`：`{id, characterId, summary, createdAt}`。
# v1.0 审计整改补充（2026-08-17）

- 稿件响应新增单调递增的 `version`。
- `PUT /v1/manuscripts/{manuscriptId}/sections/{sceneId}` 请求必须包含 `expectedVersion`；服务端版本不一致时返回 `409`，客户端应重新加载并显式处理冲突。
- 场景 AI 生成以读取时版本为提交前提：AI 在事务外执行，正文已被其他请求修改时不会覆盖新内容。

## 创建回执（2026-10-02）

`POST /v1/outlines/{outlineId}/manuscripts` 可携带 UUID 格式的 `Idempotency-Key`。同用户、同大纲、同键和相同请求体返回原始创建响应；内容不同返回 `409 CREATION_REQUEST_CONFLICT`；原稿已删除返回 `410 CREATED_MANUSCRIPT_DELETED`，不会静默重建。未传键仍允许多次创建同名稿件。

创建在数据库 flush 后构建响应，`version` 与非空 `updatedAt` 可直接用于后续编辑。前端一次创建意图使用固定键，延迟响应期间禁用重复点击，失败重试沿用原键，成功后选中新稿件并关闭创建框。

## 场景候选与业务结果（V36）

异步成功结果 `resultJson`：`{outcome: "SAVED"|"NEEDS_REVIEW", manuscriptId, sceneId, candidateIds, minHan, maxHan, actualHan, revisionCount, reviewReason, manuscript}`。`NEEDS_REVIEW` 的 `manuscript` 为 null，通用任务 `SUCCEEDED` 只代表已交付候选。兼容旧任务仍按原稿件响应读取。同步接口达标仍返回 `ManuscriptDto`，越界返回 422 `SCENE_LENGTH_OUT_OF_RANGE`。

- `GET /api/v1/manuscripts/{id}/scenes/{sceneId}/generation-candidates?branchId=...`：摘要列表，不读取正文/请求 LOB。包含候选 ID、任务 ID、分支、生成序号、原稿件版本、min/max/actualHan、来源与质量状态、创建时间。
- `GET .../generation-candidates/{candidateId}?branchId=...`：按需正文与来源指纹详情。
- `POST .../generation-candidates/{candidateId}/adopt`：`{expectedVersion,branchId}`；路径携带候选 ID。复用归属、版本、分支、H2 上下文检查及正文/版本快照/归因保存事务。写入 `lengthOutOfRange` 标记；主动采纳不触发额外付费质量或素材检查。对已采纳且正文未变化的相同候选重复请求返回当前稿件。

`sourceKind=GENERATED` 可按完整来源采纳；`RECOVERED`（及旧来源不完整记录）只能载入编辑器手动保存。`qualityStatus=NOT_PROCESSED|PROCESSED|UNKNOWN` 不表示作者已认可文学质量。旧 H2 字段与记录保留。

“恢复原任务”保留任务、请求和计费身份；新生成使用新任务。缺少新流程输入的旧任务返回 `SCENE_LEGACY_RECOVERY_REQUIRED`，不套用新提示重发。历史事故 `a9917c9d-919b-44b1-986b-5afd7cd68cb3` 仅从三条完成调用恢复，恢复工具不更新原任务、正文或计费记录。

上述事故的一次性恢复入口已退役。已恢复候选继续通过标准候选接口读取；旧工具及操作记录可从 Git 历史查询。

旧角色变化占位触发入口已删除，返回 404。现有角色变化日志读取及证据与状态的真实抽取流程继续提供。
