# User API

## 当前用户与个人资料

- `GET /api/v1/user/identity` 返回 `{id,username,email,avatar,role,isBanned}`。沿用普通用户会话校验，供前端建立和恢复登录态。
- `GET /api/v1/user/profile`
  - 返回：`{id,username,email,avatar,role,credits,projectCredits,publicCredits,totalCredits,isBanned}`
  - 兼容聚合查询。通用余额暂不可用时返回 HTTP 503 和 `PUBLIC_BALANCE_UNAVAILABLE`。
- `GET /api/v1/user/summary`
  - 返回：`{novelCount,worldCount,totalWords,totalEntries}`

## 项目专属积分与通用积分

- `GET /api/v1/user/credits/balance` 返回 `{projectCredits,publicCredits,totalCredits}`，由个人中心独立请求。通用余额暂不可用时仍返回 HTTP 200 和实际项目余额，`publicCredits`、`totalCredits` 为 `null`。查询到的零余额返回 `0`。

- `POST /api/v1/user/redeem`
  - 功能：兑换 AINovel 本地项目专属积分兑换码
  - 请求：`{code}`
  - 返回：`{success,points,newTotal,projectCredits,publicCredits,totalCredits,message}`

- `POST /api/v1/user/credits/convert`
  - 功能：通用积分兑换 AINovel 项目专属积分（1:1）
  - 边界：pay-service 扣减通用积分；AINovel 写入本地项目专属积分账户、流水和兑换订单快照。
  - 请求：`{amount,idempotencyKey}`
  - 返回：
    - `orderNo`
    - `amount`
    - `projectBefore` / `projectAfter`
    - `publicBefore` / `publicAfter`
    - `totalCredits`

- `GET /api/v1/user/credits/ledger?page=0&size=20`
  - 功能：查询 AINovel 本地项目专属积分流水（兑换码、AI 扣费、转换入账、管理员加分）
  - 返回：数组（按时间倒序）

- `GET /api/v1/user/credits/conversions?page=0&size=20`
  - 功能：查询通用积分兑换历史
  - 返回：数组（按时间倒序）
  - 每条记录包含：订单号、请求量、实际兑换量、兑换前后通用积分与项目积分、状态、创建时间
