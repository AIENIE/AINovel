# User API

## 个人资料

- `GET /api/v1/user/profile`
  - 返回：`{id,username,email,avatar,role,credits,projectCredits,publicCredits,totalCredits,isBanned,lastCheckIn}`
- `GET /api/v1/user/summary`
  - 返回：`{novelCount,worldCount,totalWords,totalEntries}`

## 项目积分与通用积分

- `POST /api/v1/user/check-in`
  - 功能：每日签到（本地项目账本）
  - 返回：`{success,points,newTotal,projectCredits,publicCredits,totalCredits,message}`

- `POST /api/v1/user/redeem`
  - 功能：兑换码入账（本地项目账本）
  - 请求：`{code}`
  - 返回：`{success,points,newTotal,projectCredits,publicCredits,totalCredits,message}`

- `POST /api/v1/user/credits/convert`
  - 功能：通用积分兑换项目积分（1:1）
  - 请求：`{amount,idempotencyKey}`
  - 返回：
    - `orderNo`
    - `amount`
    - `projectBefore` / `projectAfter`
    - `publicBefore` / `publicAfter`
    - `totalCredits`

- `GET /api/v1/user/credits/ledger?page=0&size=20`
  - 功能：查询项目积分流水（签到/兑换码/AI扣费/转换入账/管理员加分）
  - 返回：数组（按时间倒序）

- `GET /api/v1/user/credits/conversions?page=0&size=20`
  - 功能：查询通用积分兑换历史
  - 返回：数组（按时间倒序）
  - 每条记录包含：订单号、请求量、实际兑换量、兑换前后通用积分与项目积分、状态、创建时间

## 密码

- `POST /api/v1/user/password`
  - 固定返回 `501`
  - 响应：`{success:false,message:"PASSWORD_MANAGED_BY_SSO"}`
