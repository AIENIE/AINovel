# 提示词缓存与素材检索优化

## 目标

- 提高模型 prompt cache 命中率：稳定、长期不变的写作规则固定放在 `system` 消息开头。
- 扩大参考资料覆盖：正文生成前从素材库检索 chunk 级参考资料，补充故事、角色和前文上下文。
- 减少重复套路：生成提示词显式加入近期表达避让，生成后继续使用反 slop 质量门禁。

## 后端实现

- `backend/src/main/java/com/ainovel/app/prompt/PromptAssemblyService.java`
  - 输出稳定 `system` 消息和动态 `user` 消息。
  - 默认动态上下文预算 `128000` token，硬上限 `256000` token。
- `backend/src/main/java/com/ainovel/app/material/MaterialRetrievalService.java`
  - 将素材切分为重叠 chunk。
  - 关键词检索覆盖标题、标签、摘要和正文片段。
  - 语义检索通过 AiService embeddings + Qdrant；失败时保留关键词 fallback。
- `backend/src/main/java/com/ainovel/app/manuscript/ManuscriptService.java`
  - 场景生成调用新的 prompt assembly。
  - 按场景信息检索最多 8 条素材引用。
  - 从近期前文提取常见表达避让项。
  - 生成后继续执行反 slop 质量检查与保守修订。

## 前端可见性

- `frontend/src/pages/Workbench/tabs/MaterialSearchPanel.tsx` 展示 chunk 级素材搜索结果，包括来源、分数、片段序号和命中原因。
- `frontend/src/components/ai/CopilotSidebar.tsx` 在助手消息下展示 `cacheTokens` 和 `cacheHitRate`，用于观察缓存命中。

## 存储与配置

- 当前实现不新增 MySQL 表。
- Qdrant collection 默认名为 `ainovel_material_chunks`，可通过 `qdrant.material-collection` 调整。
- `QDRANT_ENABLED=false` 时不会使用向量索引；关键词检索仍可用。
