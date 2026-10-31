# V2 Analysis API
- 鉴权：Bearer Token（故事所有者）
- 基础路径：`/api/v2/stories/{storyId}/analysis`

## Jobs
- 旧 `POST /beta-reader` 和 `POST /continuity-check` 占位触发接口已退役，返回 HTTP 404。现有历史读取接口继续提供。
- `GET /jobs`：任务列表。
- `GET /jobs/{jobId}`：任务详情。

## Reports
- `GET /reports`：报告列表。
- `GET /reports/{reportId}`：报告详情。

## Continuity Issues
- `GET /continuity-issues`：连续性问题列表。
- `PUT /continuity-issues/{issueId}`：更新问题状态/建议/严重级别。

列表与详情接口只用于兼容读取历史数据；不得把历史固定分数报告视为真实评估结果。
