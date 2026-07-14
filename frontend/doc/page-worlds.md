# 世界观页面

- 列表路由：`/worlds`；编辑路由：`/world-editor?id=<worldId>`；实现分别为 `WorldManager.tsx` 与 `WorldEditor.tsx`。
- 创建页 `/worlds/create` 创建草稿后跳转编辑器。
- 编辑器读取模块定义和世界详情，支持保存基础信息、保存模块、字段精修、发布预检和模块生成/重试。
- 模块生成失败会显示失败状态和错误，不写入占位文本。删除仅由世界列表对草稿执行。
