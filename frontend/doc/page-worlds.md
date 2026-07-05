# 世界编辑页（WorldEditor）

- **路由/文件**：`/world-editor` -> `src/pages/WorldEditor.tsx`
- **列表入口**：`/worlds` -> `src/pages/WorldManager.tsx`
- **子组件目录**：`src/pages/WorldBuilder/components/`
- **布局**：
  - 顶部 Header：返回世界列表、保存、预检/发布。
  - 中部表单区：`WorldMetadataForm`（基础信息）+ `WorldModuleEditor`（逐模块编辑与字段润色）。
  - 无独立左侧 selector；世界选择在列表页完成。
- **页面初始化**：
  - 读取元数据：`GET /api/v1/world-building/definitions`（模块列表、字段规则、AI 模板说明）。
  - 根据 URL 中的 `id` 读取世界详情：`GET /api/v1/worlds/{id}`。

## 世界 CRUD 与基础信息
- 新建草稿：由 `/worlds/create` 处理，成功后跳转到 `/world-editor?id=...`。
- 选择世界：由 `/worlds` 列表页处理，再跳转到编辑页。
- 重命名草稿：通过 `PUT /api/v1/worlds/{id}` 更新 name/fields。
- 删除草稿：在 `/worlds` 列表页触发 `DELETE /api/v1/worlds/{id}`（仅草稿可删）。
- 保存基础信息：`PUT /api/v1/worlds/{id}` Body `{ name, tagline, themes[], creativeIntent, notes? }`。

## 模块编辑
- 表单数据映射到 `WorldModule.fields`，支持字段级精修按钮（使用 `refineWorldField`）。
- 批量保存：`PUT /api/v1/worlds/{id}/modules` Body `{ modules: [{ key, fields }] }`。
- 单模块保存：`PUT /api/v1/worlds/{id}/modules/{moduleKey}` Body `{ fields }`。
- 字段精修：`POST /api/v1/worlds/{id}/modules/{moduleKey}/fields/{fieldKey}/refine` Body `{ text, instruction? }`，返回 `{ result }`。

## 自动生成流水线
- 发布前预检：`GET /api/v1/worlds/{id}/publish/preview` 返回缺失字段、需生成/复用模块列表。
- 触发生成/发布：`POST /api/v1/worlds/{id}/publish` → `WorldPublishResponse { modulesToGenerate, modulesToReuse }` 并进入 `GENERATING`。
- 轮询进度：`GET /api/v1/worlds/{id}/generation`，包含队列 `queue[{ moduleKey, status, attempts, error }]`。
- 执行单模块：`POST /api/v1/worlds/{id}/generation/{moduleKey}`；失败可 `POST .../{moduleKey}/retry`（最多 3 次，前端 `MAX_MODULE_GENERATION_RETRIES`）。
- 生成完成：自动刷新世界详情并重置脏标记；`GenerationProgressModal` 关闭。

## 发布与版本
- 世界状态：`DRAFT / GENERATING / ACTIVE / ARCHIVED`；`moduleProgress` 按模块标记 `EMPTY/IN_PROGRESS/READY/AWAITING_GENERATION/GENERATING/COMPLETED/FAILED`。
- 发布后版本号递增；版本与状态主要在 `/worlds` 列表页呈现。

## 与其他模块的耦合
- `WorldSelect` 组件在故事构思、大纲生成、稿件生成、素材检索中复用，用于引用已发布世界。
- 世界元数据、模板帮助页与设置页共享 `/api/v1/world-prompts` / `.../metadata`。

## 待完善
- 生成流程目前同步，长任务会阻塞请求；可改为异步队列/回调。
- 模块表单缺少字段级验证提示（依赖 `definitions.validation`）。
- 草稿/发布的权限与协作（目前基于 workspace 权限）。
- 发布/删除的确认与依赖（引用故事/素材时的提示）。

## 开发对接指南 (Mock vs Real)

### 1. 模块定义 (Definitions)
- **当前 Mock**：`MOCK_WORLD_DEFINITIONS` 是硬编码在前端的。
- **真实对接**：
  - 必须从后端 `GET /api/v1/world-building/definitions` 获取。
  - 这样后端可以动态调整世界观的模块结构（例如新增“宗教”、“魔法”模块），而无需重新部署前端。

### 2. 自动生成流水线
- **当前 Mock**：编辑页能调用真实的发布预检和模块生成接口，但仍是串行、同步式流程。
- **真实对接**：
  - 这是一个复杂的异步流程。
  - 前端需实现 `GenerationProgressModal`，通过轮询 `GET /api/v1/worlds/{id}/generation` 接口来实时展示每个模块的生成状态（等待中、生成中、完成、失败）。
  - 需处理生成失败的情况，提供“重试”按钮。

### 3. 字段精修
- **当前 Mock**：无实际效果。
- **真实对接**：
  - 调用 LLM 接口，将当前字段值 + 用户指令发送给后端，返回修改后的文本并自动填入输入框。
