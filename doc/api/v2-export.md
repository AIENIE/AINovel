# V2 Export API
- 鉴权：Bearer Token（稿件/模板所有者）
- 基础路径：`/api/v2`

## Export Jobs
- `POST /manuscripts/{manuscriptId}/export`：发起导出任务。
- `GET /manuscripts/{manuscriptId}/export/jobs`：导出任务列表。
- `GET /manuscripts/{manuscriptId}/export/jobs/{jobId}`：导出任务详情。
- `GET /manuscripts/{manuscriptId}/export/jobs/{jobId}/download`：携带 Bearer Token 下载导出文件。任务元数据持久化到 `export_jobs`；文件字节按当前稿件内容即时生成，不写入数据库。Web 前端使用鉴权 fetch 获取 Blob，不使用无法携带 Authorization 的原生导航链接。

## Templates
- `GET /export-templates`：查询模板（系统模板 + 当前用户模板）。
- `POST /export-templates`：创建用户模板。
- `PUT /export-templates/{id}`：更新用户模板。
- `DELETE /export-templates/{id}`：删除用户模板。
# v1.0 审计整改补充（2026-08-17）

- 创建导出任务会冻结稿件正文快照并立即返回 `queued`，不在 HTTP 请求线程渲染文件。
- 后台 worker 使用数据库租约恢复过期任务，在系统临时文件中生成并通过 JDBC 流式写入 BLOB。
- 状态响应新增 `checksum`、`sizeBytes`、`startedAt`、`completedAt`；下载接口仅流式读取已完成产物。
- 用户配额在锁定用户记录后检查；过期任务会清除大对象和冻结快照。
