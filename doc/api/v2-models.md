# V2 Model Collaboration API
- 鉴权：Bearer Token（管理员端点需 ADMIN）
- 基础路径：`/api/v2`

## Model Registry
- `GET /models`：可用模型列表。
- `GET /models/{modelKey}`：模型详情。

## Routing (Admin)
- `GET /admin/model-routing`：查询任务路由规则。
- `PUT /admin/model-routing/{taskType}`：更新路由规则。

## User Preferences
- `GET /users/me/model-preferences`：用户偏好列表。
- `PUT /users/me/model-preferences/{taskType}`：设置任务偏好模型。
- `DELETE /users/me/model-preferences/{taskType}`：重置任务偏好。

## Usage
- `GET /users/me/model-usage`：用量汇总。
- `GET /users/me/model-usage/details`：调用明细。

## Compare
- `POST /stories/{storyId}/compare-models`：双模型对比生成并记录用量日志。
