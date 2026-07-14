# 工作台模块

## 入口与布局

- 路由：`/workbench?tab=<tab>&id=<storyId>`。
- 入口文件：`frontend/src/pages/Workbench/Workbench.tsx`。
- 普通用户需通过 `ProtectedRoute`；工作台使用 shadcn/ui Tabs，不使用 Ant Design 或独立的 `/workbench/:tab` 路由。

## 标签页

| tab | 页面 | 用途 |
| --- | --- | --- |
| `conception` | `StoryConception` | 一键构思故事和角色草稿 |
| `stories` | `StoryManager` | 编辑故事卡和角色卡 |
| `outline` | `OutlineWorkbench` | 管理章节、场景和世界观引用 |
| `writing` | `ManuscriptWriter` | 稿件、场景正文、质量、版本和导出 |
| `search` | `MaterialSearchPanel` | 检索素材片段 |
| `lorebook` | `LorebookPanel` | Lorebook 条目、实体和上下文预览 |
| `graph` | `KnowledgeGraphTab` | 图谱查询和关系维护 |
| `analysis` | `AnalysisDashboard` | 长篇 drift 巡检 |
| `v2` | `V2Studio` | v2 接口联调面板 |

## 稿件写作

- 桌面端为可伸缩三栏：大纲树、编辑器、右侧功能面板；支持多场景标签、保存状态、专注模式和命令面板。
- 右侧面板包含 Copilot、上下文、剧情、版本、导出、统计和目标。
- 场景生成使用 `POST /api/v1/manuscripts/{id}/scenes/{sceneId}/generate`。`fast` 是默认模式；`crafted` 注入 15 条分类型反套路约束与 2 条轮换叙事目标，仍为单候选生成。
- 生成后会记录文本质量和剧情质量；用户可手动运行诊断，或生成并采纳剧情候选修订。
- 移动版提供大纲、编辑和参考窗格，但当前没有生成/模式切换控制。

## 相关接口

- v1：故事、大纲、稿件、素材、AI 润色与对话接口。
- v2：上下文、质量、版本、导出、模型和工作台体验接口。
- 具体请求和返回以 `doc/api/` 对应文件为准。
