# V2 Analysis API
- 鉴权：Bearer Token（故事所有者）
- 基础路径：`/api/v2/stories/{storyId}/analysis`

## Jobs
- `POST /beta-reader`：触发 Beta Reader 分析任务。
- `POST /continuity-check`：触发连续性检查任务。
- `GET /jobs`：任务列表。
- `GET /jobs/{jobId}`：任务详情。

## Reports
- `GET /reports`：报告列表。
- `GET /reports/{reportId}`：报告详情。

## Continuity Issues
- `GET /continuity-issues`：连续性问题列表。
- `PUT /continuity-issues/{issueId}`：更新问题状态/建议/严重级别。
