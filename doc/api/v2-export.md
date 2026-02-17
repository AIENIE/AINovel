# V2 Export API
- 鉴权：Bearer Token（稿件/模板所有者）
- 基础路径：`/api/v2`

## Export Jobs
- `POST /manuscripts/{manuscriptId}/export`：发起导出任务。
- `GET /manuscripts/{manuscriptId}/export/jobs`：导出任务列表。
- `GET /manuscripts/{manuscriptId}/export/jobs/{jobId}`：导出任务详情。
- `GET /manuscripts/{manuscriptId}/export/jobs/{jobId}/download`：下载导出文件（文本预览流）。

## Templates
- `GET /export-templates`：查询模板（系统模板 + 当前用户模板）。
- `POST /export-templates`：创建用户模板。
- `PUT /export-templates/{id}`：更新用户模板。
- `DELETE /export-templates/{id}`：删除用户模板。
