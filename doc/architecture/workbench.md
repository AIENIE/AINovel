# 创作工作台

## 入口与标签

工作台路由为 `/workbench?tab=<tab>&id=<storyId>`，入口文件是 `frontend/src/pages/Workbench/Workbench.tsx`。

| tab | 当前职责 |
| --- | --- |
| `conception` | 一键构思故事与角色草稿 |
| `stories` | 编辑故事卡和角色卡 |
| `outline` | 管理章节、场景和世界观引用 |
| `writing` | 正文、生成、质量、版本、导出、统计和目标 |
| `search` | 检索素材片段 |
| `lorebook` | Lorebook、实体和上下文预览 |
| `graph` | 图谱查询与关系维护 |
| `analysis` | 长篇 drift 巡检 |
| `v2` | 当前 v2 API 的联调与结果观察 |

## 正文与生成

- 桌面端为大纲树、编辑器、右侧工具三栏布局，支持多场景标签、自动保存和专注模式。
- 指定场景生成使用 `mode=fast|crafted`；`crafted` 注入分类反套路约束和轮换叙事目标。
- 生成完成后加载文本质量与剧情质量记录，候选修订只有用户采纳后才写回正文。
- 服务端生成或候选修订写回时会取消目标场景待执行的自动保存，只替换该场景草稿并保留其他未保存场景，避免旧空正文覆盖生成结果。
- 前端把缺失目标 section 或仅含空编辑器 HTML 的生成响应视为失败。
- 提示词装配将稳定规则放在 system 消息，将故事、场景、角色、前文、素材和重试说明放在动态 user 消息。
- 素材检索最多装配 8 条片段；向量能力不可用时保留关键词检索。

## v2 能力

- Lorebook、图谱关系、风格画像、角色声音、版本与分支、导出任务、模型偏好、布局、会话、目标和快捷键均持久化。
- 所有资源访问复用 `ResourceAccessGuard` 与领域所有者校验。
- 前端 API 统一位于 `frontend/src/lib/api-client.ts`，接口详情见 [`../api/README.md`](../api/README.md)。
- 导出下载通过带 Bearer 的 Blob 请求完成，桌面和移动端共用同一鉴权与错误处理链路。
