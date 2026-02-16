# User API

## 个人资料
- `GET /api/v1/user/profile`：获取当前登录用户资料，返回 `{id,username,email,avatar,role,credits,isBanned,lastCheckIn}`。
- `GET /api/v1/user/summary`：用户概览统计，返回 `{novelCount,worldCount,totalWords,totalEntries}`（用于 `/dashboard`）。

## 签到/积分
- `POST /api/v1/user/check-in`：每日签到（调用 PayService），返回 `{success,points,newTotal}`；同一天重复签到 `success=false`。
- `POST /api/v1/user/redeem`：兑换码充值（调用 PayService），请求 `{code}`，返回 `{success,points,newTotal}`。

## 密码
- `POST /api/v1/user/password`：密码由统一登录服务管理，本接口固定返回 `501` 与 `{success:false,message:"PASSWORD_MANAGED_BY_SSO"}`。
