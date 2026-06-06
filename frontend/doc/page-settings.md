# 设置页（Settings）

- **路由/文件**：`/settings`
- **对应设计稿文件**：`src/pages/Settings/Settings.tsx`
- **子组件目录**：`src/pages/Settings/tabs/`
- **布局**：Tabs 三块：模型接入、工作区提示词、世界观提示词；右上返回按钮位于帮助页（在帮助页中返回）。

## Tab1 模型接入
- **对应文件**：`src/pages/Settings/tabs/ModelSettings.tsx`
- **UI**：Base URL、Model Name、API Key 输入；“保存”“测试连接”按钮，加载态与错误提示。
- **接口**：
  - 读取：`GET /api/v1/settings`（含 `apiKeyIsSet` 标记）。
  - 保存：`PUT /api/v1/settings` Body `{ baseUrl?, modelName?, apiKey? }`（如已有 Key 则 API Key 输入为可编辑开关）。
  - 测试：`POST /api/v1/settings/test` 复用当前或最新配置。

## Tab2 工作区提示词（故事/大纲/正文/润色）
- **对应文件**：`src/pages/Settings/tabs/WorkspacePrompts.tsx`
- **UI**：五个多行输入（storyCreation/outlineChapter/manuscriptSection/refineWithInstruction/refineWithoutInstruction），“保存”“恢复默认”按钮；从 metadata 展示模板变量提示。
- **接口**：
  - 读取：`GET /api/v1/prompt-templates` → `PromptTemplatesResponse`。
  - 读取元数据：`GET /api/v1/prompt-templates/metadata`。
  - 保存：`PUT /api/v1/prompt-templates` Body `PromptTemplatesUpdatePayload`（可部分字段）。
  - 重置：`POST /api/v1/prompt-templates/reset` Body `{}`。

## Tab3 世界观提示词
- **对应文件**：`src/pages/Settings/tabs/WorldPrompts.tsx`
- **UI**：三个子 Tab：
  - **模块生成模板**：按模块 key 编辑模板文本，并展示 metadata 中的模块中文名和字段；
  - **最终整合模板**：编辑 `finalTemplates`；默认配置为空时显示未启用说明；
  - **字段精修模板**：单文本；并提供“恢复默认”。
- **接口**：
  - 读取元数据：`GET /api/v1/world-prompts/metadata`（帮助页使用）。
  - 读取模板：`GET /api/v1/world-prompts` → `{ modules, finalTemplates, fieldRefine }`。
  - 保存：`PUT /api/v1/world-prompts` Body `{ modules?, finalTemplates?, fieldRefine? }`。
  - 重置：`POST /api/v1/world-prompts/reset` Body `{}`。

## 其他
- **导航**：页内按钮跳转到 `/settings/prompt-guide` 与 `/settings/world-prompts/help`。
- **待完善**：
  - 设置保存后的全局提示词缓存刷新策略；
  - API Key 掩码与重新编辑流程的提示；
  - tab 切换时未保存草稿的提醒。

## 开发对接指南 (Mock vs Real)

### 1. API Key 安全
- **当前 Mock**：`MOCK_SETTINGS` 明文存储，且 `apiKeyIsSet` 逻辑简单。
- **真实对接**：
  - **读取**：后端接口 `GET /api/v1/settings` **绝对不应** 返回真实的 API Key。应仅返回 `apiKeyIsSet: true/false` 或掩码后的 Key（如 `sk-****`）。
  - **保存**：前端仅在用户输入新 Key 时发送 `apiKey` 字段。
  - **测试连接**：测试请求应由**后端**发起（Backend-for-Frontend 模式），前端不应直接调用 OpenAI 接口，以免暴露 Key。

### 2. 提示词模板
- **当前实现**：
  - 提示词持久化在后端 `prompt_templates` 与 `world_prompt_templates`。
  - 前端通过真实接口读取、保存、恢复默认，并通过 metadata 接口渲染帮助页与变量提示。
- **对接约束**：
  - 恢复默认由后端维护系统默认模板；前端不硬编码默认值。
