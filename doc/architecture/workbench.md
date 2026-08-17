# 创作工作台

## 入口与标签

工作台路由为 `/workbench?tab=<tab>&storyId=<storyId>&outlineId=<outlineId>&manuscriptId=<manuscriptId>&sceneId=<sceneId>`，入口文件是 `frontend/src/pages/Workbench/Workbench.tsx`。旧 `id` 参数只用于兼容历史链接，进入后会规范为 `storyId`。

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
- 成功生成还会原子写入内部 `generation` 版本与场景生成记录。人工编辑提交后，以独立事务先保存待重算标记，再按 Unicode code point 精确计算；计算失败不影响作者正文，后续查询会通过正文 hash 漂移重试。重新生成和版本回滚分别保留 `SUPERSEDED`、`REVERTED` 历史。
- 服务端生成或候选修订写回时会取消目标场景待执行的自动保存，只替换该场景草稿并保留其他未保存场景，避免旧空正文覆盖生成结果。
- 前端把缺失目标 section 或仅含空编辑器 HTML 的生成响应视为失败。
- 提示词装配将稳定规则放在 system 消息，将故事、场景、角色、前文、素材和重试说明放在动态 user 消息。
- 场景起草使用版本化的 `scene-draft-v2` 上下文编译器：在固定 3500 estimated-token 预算内统一装配 planning、绑定世界、激活风格/相关角色声音、跨章最近正文、按当前场景锚点匹配且已启用的 Lorebook、两端条目均已入选的图谱关系和最多两个历史锚点，并为实际输入生成不含正文的来源 manifest 与 SHA-256 hash。
- G2 fast/crafted 样本对共享同一次上下文编译结果；V2 context preview 在提供 `manuscriptId + sceneId` 时复用同一编译器，未提供时保留旧 Lorebook 预览行为。
- 素材检索最多装配 8 条片段；向量能力不可用时保留关键词检索。

## v2 能力

- Lorebook、图谱关系、风格画像、角色声音、版本与分支、导出任务、模型偏好、布局、会话、目标和快捷键均持久化。
- 所有资源访问复用 `ResourceAccessGuard` 与领域所有者校验。
- 前端 API 统一位于 `frontend/src/lib/api-client.ts`，接口详情见 [`../api/README.md`](../api/README.md)。
- 导出下载通过带 Bearer 的 Blob 请求完成，桌面和移动端共用同一鉴权与错误处理链路。
# v1.0 审计整改补充（2026-08-17）

- 编辑器保存携带稿件乐观版本，冲突不会静默覆盖其他标签页或设备上的更新。
- 通用 AI、G2、导出、素材索引均以数据库状态和租约为权威；线程池拒绝或进程重启后由 dispatcher 恢复。
- G2 fast/crafted 候选独立持久化；恢复时只补缺失候选。通用 AI 已产生流式输出的异常任务进入 `RECOVERY_REQUIRED`。
