# Admin API（需管理员权限）

## 仪表盘

- `GET /api/v1/admin/dashboard`
  - 返回：`{totalUsers,todayNewUsers,totalCreditsConsumed,todayCreditsConsumed,apiErrorRate,pendingReviews}`

## 用户管理

- `GET /api/v1/admin/users?search=demo`
- `POST /api/v1/admin/users/{id}/ban?days=7&reason=违规行为`
- `POST /api/v1/admin/users/{id}/unban`

## 系统配置

- `GET /api/v1/admin/system-config`
- `PUT /api/v1/admin/system-config`

## 项目积分与兑换码

- `POST /api/v1/admin/credits/grant`
  - 功能：管理员给用户发放项目积分（仅加分）

- `GET /api/v1/admin/redeem-codes`
- `POST /api/v1/admin/redeem-codes`
  - 请求示例：`{code,grantAmount,maxUses,startsAt,expiresAt,enabled,stackable,description}`
  - 说明：字段名为 `maxUses`（不是 `maxUsages`）

- `GET /api/v1/admin/credits/conversions?page=0&size=20`
  - 功能：查询全站通用积分兑换订单
  - 含兑换前后通用积分与项目积分

- `GET /api/v1/admin/credits/ledger?page=0&size=20`
  - 功能：查询全站项目积分流水
