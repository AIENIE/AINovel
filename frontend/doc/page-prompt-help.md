# 提示词帮助页（PromptHelpPage）

- **路由/文件**：`/settings/prompt-guide`
- **对应设计稿文件**：`src/pages/Settings/PromptHelpPage.tsx`
- **布局**：居中卡片，顶部标题+“返回设置”按钮；内容依次为各模板变量表、函数表、语法提示和示例列表。
- **功能**：
  - 加载提示词元数据并呈现为表格/列表。
  - 仅展示，不修改数据。
- **接口**：`GET /api/v1/prompt-templates/metadata`，返回：
  - `functions[] { name, description, example }`，
  - `templates[]` 每个含 `variables[] { name, type, description }`，
  - `syntaxTips[]`、`examples[]`。
- **待完善**：
  - 增加搜索/过滤；
  - 支持复制到剪贴板；
  - 与模板编辑联动高亮变量。

## 开发对接指南 (Mock vs Real)

### 1. 动态元数据
- **当前实现**：组件加载 `GET /api/v1/prompt-templates/metadata` 后渲染变量、函数、语法提示和示例。
- **维护约束**：后端新增模板变量时同步更新 metadata，前端帮助页无需硬编码变量列表。
