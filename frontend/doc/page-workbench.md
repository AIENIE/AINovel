# 工作台

- 路由：`/workbench?tab=<tab>&id=<storyId>`；实现：`src/pages/Workbench/Workbench.tsx`。
- `tab` 允许 `conception`、`stories`、`outline`、`writing`、`search`、`lorebook`、`graph`、`analysis`、`v2`，缺省为 `writing`。
- 使用 shadcn/ui Tabs，故事 ID 通过查询参数传给故事、大纲、稿件、知识库和图谱子页面。

## 稿件编辑器

- 桌面端：大纲树、编辑器和右侧侧栏；支持场景标签、保存、版本、剧情诊断、导出、统计和目标。
- 场景生成调用 `/api/v1/manuscripts/{id}/scenes/{sceneId}/generate?mode=fast|crafted`。
- `fast` 是默认值；`crafted` 由顶部模式开关注入反套路约束和叙事目标。生成后加载文本质量和剧情质量记录。
- 移动端仅提供大纲、编辑和参考窗格，不提供场景生成或模式切换按钮。
