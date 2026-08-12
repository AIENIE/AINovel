# V2 Analysis API
- 鉴权：Bearer Token（故事所有者）
- 基础路径：`/api/v2/stories/{storyId}/analysis`

## Jobs
- `POST /beta-reader`：当前未接入真实分析，完成故事所有权校验后返回 HTTP 501，Body `{code:"ANALYSIS_NOT_IMPLEMENTED",message:"..."}`；不会创建任务或报告。
- `POST /continuity-check`：当前未接入真实分析，完成故事所有权校验后返回同一稳定 501；不会创建伪连续性问题。
- `GET /jobs`：任务列表。
- `GET /jobs/{jobId}`：任务详情。

## Reports
- `GET /reports`：报告列表。
- `GET /reports/{reportId}`：报告详情。

## Continuity Issues
- `GET /continuity-issues`：连续性问题列表。
- `PUT /continuity-issues/{issueId}`：更新问题状态/建议/严重级别。

列表与详情接口只用于兼容读取历史数据；不得把历史固定分数报告视为真实评估结果。
