# V2 Context Memory API
- 鉴权：Bearer Token（故事所有者）
- 基础路径：`/api/v2/stories/{storyId}`

## Lorebook
- `GET /lorebook`：获取 Lorebook 条目列表（按优先级排序）。
- `POST /lorebook`：创建条目，支持 `displayName/content/category/priority/tokenBudget/enabled`。
- `PUT /lorebook/{entryId}`：更新条目字段。
- `DELETE /lorebook/{entryId}`：删除条目。
- `POST /lorebook/import`：批量导入条目，Body `{ entries: [...] }`。

## Graph
- `GET /graph`：按当前 Lorebook 生成图谱节点集。
- `GET /graph/query?keyword=&limit=`：按关键词筛选子图。
- `POST /graph/sync`：触发图谱同步（当前实现为即时完成）。

## Entity Extraction
- `POST /extract-entities`：从文本创建实体提取记录，Body 至少包含 `text`。
- `GET /extractions`：查询提取记录列表。
- `PUT /extractions/{id}/review`：审核提取记录，Body `{ reviewAction, linkedLorebookId? }`。

## Context Preview
- `GET /context/preview?chapterIndex=&sceneIndex=&tokenBudget=`：预览上下文拼装结果与 Token 预算占用。
