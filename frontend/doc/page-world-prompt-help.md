# 世界提示词帮助页（WorldPromptHelpPage）

- **路由/文件**：`/settings/world-prompts/help`
- **对应设计稿文件**：`src/pages/Settings/WorldPromptHelpPage.tsx`
- **布局**：卡片表格；段落包含可用变量表、模块字段表、函数表和示例；顶部提供“返回设置”。
- **接口**：`GET /api/v1/world-prompts/metadata`，返回：
  - `variables[] { name, type, description }`
  - `functions[] { name, description, example }`
  - `modules[] { key, label, fields[] { key, label, maxLength } }`
  - `examples[]` 文本片段。
- **用途**：指导用户编写世界观的模块草稿模板、正式模板、字段精修模板。
- **待完善**：
  - 示例与当前世界的实时对照；
  - FAQ 可链接到实际模板编辑位置；
  - 支持一键复制推荐片段。

## 开发对接指南 (Mock vs Real)

### 1. 动态元数据
- **当前实现**：组件加载 `GET /api/v1/world-prompts/metadata` 后渲染变量、函数、模块字段和示例。
- **维护约束**：`modules` 列表应与世界观定义的模块保持一致；新增模块时先补后端 metadata，再由前端自动展示。
