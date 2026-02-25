# AINovel 当前问题跟踪（更新于 2026-02-25）

## 当前待处理

- 暂无阻塞项。
- 已知非阻塞告警：当前环境后端从容器内访问 `CONSUL_HOST=192.168.1.4:60000` 不通，`UserSessionValidator` 会回退 `USER_GRPC_ADDR`，业务可用但日志有 fallback 告警。

## 今日已验证并关闭

1. 三服务安全加固对接升级
- 时间：2026-02-25 11:55-12:25（Asia/Shanghai）
- 结果：
  - ai-service HMAC metadata 调用链路可用。
  - user-service `x-internal-token` 会话校验链路可用。
  - pay-service Bearer service JWT 链路可用（含启动前 claim 校验与脚本自动重签发）。
  - 关键接口 `profile/summary/models/model-preferences` 返回 200。

2. UTC+8 统一时区
- 时间：2026-02-25 11:55（Asia/Shanghai）
- 结果：后端容器启动日志与业务时间戳均为 `+08:00`，鉴权调用未出现时区偏差问题。

3. Playwright 全链路回归
- 时间：2026-02-25 12:14-12:24（Asia/Shanghai）
- 结果：
  - 登录 -> 工作台 -> 小说管理 -> 世界构建 -> 素材库 -> 设置（模型偏好/工作台体验）-> 个人中心 -> 退出登录 -> 再登录 全流程通过。
  - 使用独立账号 `queueuser26` 验证，避免共享账号会话互踢导致的偶发 403 误判。

4. `build.sh` 本地部署链路
- 时间：2026-02-23 03:11-03:14（Asia/Shanghai）
- 结果：前端测试+构建、后端测试+打包、`docker compose` 重建、`nginx -t` 均通过。

5. 测试域名与 HTTPS 可用性
- 时间：2026-02-23 03:16（Asia/Shanghai）
- 结果：`https://ainovel.seekerhut.com` 与 `https://ainovel.seekerhut.com/api/v3/api-docs` 均返回 200。

6. SSO + 积分兑换 + 记录链路
- 时间：2026-02-23 03:17-03:20（Asia/Shanghai）
- 账号：`goodboy95`
- 结果：
  - 真实 userservice SSO 登录成功。
  - 用户页可完成通用积分兑换项目积分（100 -> 100）。
  - 用户页可展示兑换前后余额及历史记录。
  - 项目积分流水与兑换历史可正常新增记录。

7. Admin 新能力链路
- 时间：2026-02-23 03:17-03:18（Asia/Shanghai）
- 结果：
  - `/api/v1/admin/dashboard` 正常返回。
  - 可创建兑换码并由用户成功兑换入账。

8. 兼容旧库 `redeem_codes` 表结构
- 状态：已修复
- 说明：兼容 legacy 列 `amount` / `used` 非空约束，避免创建兑换码失败。
- 代码：`backend/src/main/java/com/ainovel/app/economy/model/RedeemCode.java`

## 说明

- 旧文档中 `userservice 502`、`SMTP 注册阻塞`、`Playwright Browser is already in use` 等记录均为历史阶段性问题，不再代表当前状态。
- 本轮完整验收详情见：`doc/test/2026-02-23-full-acceptance.md`。
