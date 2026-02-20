# User API

## 个人资料
- `GET /api/v1/user/profile`：获取当前登录用户资料，返回 `{id,username,email,avatar,role,credits,projectCredits,publicCredits,totalCredits,isBanned,lastCheckIn}`。
- `GET /api/v1/user/summary`：用户概览统计，返回 `{novelCount,worldCount,totalWords,totalEntries}`（用于 `/dashboard`）。

## 签到/积分
- `POST /api/v1/user/check-in`：每日签到（本地账本），返回 `{success,points,newTotal,projectCredits,publicCredits,totalCredits,message}`；同一天重复签到 `success=false`。
- `POST /api/v1/user/redeem`：兑换码充值（本地兑换码），请求 `{code}`，返回 `{success,points,newTotal,projectCredits,publicCredits,totalCredits,message}`。
- `POST /api/v1/user/credits/convert`：通用积分兑换项目积分（1:1），请求 `{amount,idempotencyKey}`，返回 `{orderNo,amount,projectCredits,publicCredits,totalCredits}`。
- `GET /api/v1/user/credits/ledger?page=0&size=20`：查询本地项目积分流水。

## 密码
- `POST /api/v1/user/password`：密码由统一登录服务管理，本接口固定返回 `501` 与 `{success:false,message:"PASSWORD_MANAGED_BY_SSO"}`。
