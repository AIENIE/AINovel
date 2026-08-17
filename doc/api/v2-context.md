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
- `GET /graph`：按当前 Lorebook 和已保存关系生成图谱节点集。
- `GET /graph/query?keyword=&limit=`：按关键词筛选子图。
- `POST /graph/relationships`：创建图谱关系。
- `DELETE /graph/relationships/{relationshipId}`：删除图谱关系。
- `POST /graph/sync`：触发图谱同步（当前实现为即时完成，并保存关系元数据）。

## Entity Extraction
- `POST /extract-entities`：从文本创建实体提取记录，Body 至少包含 `text`。
- `GET /extractions`：查询提取记录列表。
- `PUT /extractions/{id}/review`：审核提取记录，Body `{ reviewAction, linkedLorebookId? }`。

## Context Preview
- `GET /context/preview?chapterIndex=&sceneIndex=&tokenBudget=`：兼容预览，继续按 Lorebook 优先级拼装并返回原有分段字段，同时以 `legacy-context-preview-v1` 补充 `promptVersion/contextHash/sources` 元数据。
- `GET /context/preview?manuscriptId=&sceneId=`：使用正文生成同款 `scene-draft-v2` 编译器。`manuscriptId` 与 `sceneId` 必须同时提供；该路径固定使用 3500 token，兼容 query 中的 `tokenBudget` 不会改变编译结果；场景类型只读取大纲中的 `planning.sceneType`，不接受请求覆盖。
- `scene-draft-v2` 的默认和硬上限均为 3500 token；响应额外包含 `promptVersion/compilerVersion/contextHash/compiledContext/manifest/sources`。manifest 记录实际注入来源、入选原因、截断状态、预算、绑定世界、场景类型与各片段内容哈希。
- 编译内容包括整体/章节/场景 planning、绑定世界、激活风格与角色声音、跨章最近两场正文尾部、相关 Lorebook/图谱，以及最多两个只来自目标场景之前的历史锚点窗口。
