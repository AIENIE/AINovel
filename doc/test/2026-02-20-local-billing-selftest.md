# 2026-02-20 本地计费迁移自测记录

## 执行信息
- 时间：2026-02-20（Asia/Shanghai）
- 部署脚本：`build_local.ps1`
- 访问入口：`http://127.0.0.1:11040`
- 证据目录：`artifacts/test/2026-02-20-local-billing/`

## 自测步骤
1. 执行 `build_local.ps1`，完成前后端构建与启动。
2. Playwright 访问首页 `http://127.0.0.1:11040/`，页面正常渲染。
3. Playwright 访问 `http://127.0.0.1:11040/profile`，被重定向到 SSO 登录页。
4. 使用 `admin/password` 尝试登录，SSO 返回 `USER_NOT_FOUND`，未能进入 `/profile`。

## 结果结论
- 通过：
  - 本地构建与启动成功。
  - 首页可访问、控制台无阻断级报错。
- 阻塞：
  - 个人中心链路依赖外部 SSO 账号，当前测试账号不存在，无法完成登录后 UI 验证（双余额、兑换入口）。

## 后续建议
1. 提供可用 SSO 测试账号后，复测 `/profile` 并验证：
   - 项目积分/通用积分/总余额展示。
   - 每日签到、兑换码、通用积分兑换为项目积分流程。
2. 复测管理员路径：
   - `POST /api/v1/admin/credits/grant`
   - `GET/POST /api/v1/admin/redeem-codes`
