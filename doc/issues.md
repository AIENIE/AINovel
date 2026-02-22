# AINovel 当前问题跟踪（更新于 2026-02-23）

## 当前待处理

- 暂无阻塞项。

## 今日已验证并关闭

1. `build.sh` 本地部署链路
- 时间：2026-02-23 03:11-03:14（Asia/Shanghai）
- 结果：前端测试+构建、后端测试+打包、`docker compose` 重建、`nginx -t` 均通过。

2. 测试域名与 HTTPS 可用性
- 时间：2026-02-23 03:16（Asia/Shanghai）
- 结果：`https://ainovel.seekerhut.com` 与 `https://ainovel.seekerhut.com/api/v3/api-docs` 均返回 200。

3. SSO + 积分兑换 + 记录链路
- 时间：2026-02-23 03:17-03:20（Asia/Shanghai）
- 账号：`goodboy95`
- 结果：
  - 真实 userservice SSO 登录成功。
  - 用户页可完成通用积分兑换项目积分（100 -> 100）。
  - 用户页可展示兑换前后余额及历史记录。
  - 项目积分流水与兑换历史可正常新增记录。

4. Admin 新能力链路
- 时间：2026-02-23 03:17-03:18（Asia/Shanghai）
- 结果：
  - `/api/v1/admin/dashboard` 正常返回。
  - 可创建兑换码并由用户成功兑换入账。

5. 兼容旧库 `redeem_codes` 表结构
- 状态：已修复
- 说明：兼容 legacy 列 `amount` / `used` 非空约束，避免创建兑换码失败。
- 代码：`backend/src/main/java/com/ainovel/app/economy/model/RedeemCode.java`

## 说明

- 旧文档中 `userservice 502`、`SMTP 注册阻塞`、`Playwright Browser is already in use` 等记录均为历史阶段性问题，不再代表当前状态。
- 本轮完整验收详情见：`doc/test/2026-02-23-full-acceptance.md`。
