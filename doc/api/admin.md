# Admin API（需管理员权限）

## 仪表盘
- `GET /api/v1/admin/dashboard`：返回 `{totalUsers,todayNewUsers,totalCreditsConsumed,todayCreditsConsumed,apiErrorRate,pendingReviews}`。

## 用户管理
- `GET /api/v1/admin/users?search=demo`：透传 UserService 查询用户列表。
- `POST /api/v1/admin/users/{id}/ban?days=7&reason=违规行为`：封禁用户，返回 `true`。
- `POST /api/v1/admin/users/{id}/unban`：解封用户，返回 `true`。

## 全局配置（注册/维护/签到/SMTP）
- `GET /api/v1/admin/system-config`：获取全局配置（注册、维护、签到范围、SMTP 状态）。
- `PUT /api/v1/admin/system-config`：更新全局配置（可选字段更新，SMTP 密码仅在传入时覆盖）。

## 已下线本地能力（v1.1）
- 本地模型管理：`/api/v1/admin/models*`
- 本地积分日志/兑换码管理：`/api/v1/admin/logs`、`/api/v1/admin/redeem-codes*`
- 本地邮箱测试接口：`/api/v1/admin/email/*`
