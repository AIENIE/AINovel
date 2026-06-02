# Material API
- `POST /api/v1/materials`：创建素材，Body `{title,type,summary?,content,tags?}`。
- `GET /api/v1/materials`：素材列表。
- `GET /api/v1/materials/{id}`：素材详情。
- `PUT /api/v1/materials/{id}`：更新素材。
- `DELETE /api/v1/materials/{id}`：删除素材。
- `POST /api/v1/materials/upload` (multipart) ：TXT 上传，返回任务 `{id,status,progress}`。
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
- `POST /api/v1/materials/review/pending`：待审核列表。
- `POST /api/v1/materials/{id}/review/approve|reject`：审核操作。
- `POST /api/v1/materials/find-duplicates`：查重（当前返回空数组）。
- `POST /api/v1/materials/merge`：合并素材，Body `{sourceMaterialId,targetMaterialId,mergeTags?,mergeSummaryWhenEmpty?,note?}`。
- `GET /api/v1/materials/{id}/citations`：引用历史（当前空数组）。

## 检索策略

- 已审核通过的素材会被切分为约 900 字符的重叠片段，检索结果直接返回片段级命中，前端不再逐条回查素材详情。
- 检索同时走关键词和语义向量两条路径：关键词覆盖标题、标签、摘要和片段内容；语义向量通过 AiService embeddings 写入/查询 Qdrant。
- Qdrant 或 embeddings 不可用时，服务端保留关键词 fallback，不影响基本素材检索。
