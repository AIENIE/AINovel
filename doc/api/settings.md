# Settings & Prompts API
- `GET/PUT/POST /api/v1/settings*`：v1.1 已移除（模型接入配置由外部服务负责）。
- 本服务保留提示词模板设置接口：`/api/v1/prompt-templates*`、`/api/v1/world-prompts*`。

> 说明：v1.1 起前端已移除“模型接入”页面，系统主配置入口为 `GET/PUT /api/v1/admin/system-config`（注册/维护/签到/SMTP）。

## 工作区提示词
- `GET /api/v1/prompt-templates`：获取故事/大纲/稿件/润色模板。
- `PUT /api/v1/prompt-templates`：更新对应字段。
- `POST /api/v1/prompt-templates/reset`：恢复默认模板。
- `GET /api/v1/prompt-templates/metadata`：帮助页元数据（变量/函数/示例）。

## 世界观提示词
- `GET /api/v1/world-prompts`：获取世界模板（modules/finalTemplates/fieldRefine）。
- `PUT /api/v1/world-prompts`：更新部分字段。
- `POST /api/v1/world-prompts/reset`：恢复默认。
- `GET /api/v1/world-prompts/metadata`：世界提示词帮助元数据。
